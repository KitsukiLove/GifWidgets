package app.gifwidgets.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.gifwidgets.R
import app.gifwidgets.audio.KixkiAudio
import app.gifwidgets.ui.theme.LocalGifDesign
import app.gifwidgets.ui.theme.LocalThemeController
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

// Kixki as a quiet guide. In the library she strolls slowly along the top of the GIF windows (like on the page,
// only calmer); in the editor and the widget settings she sits in the title bar. Tapping her gives an easy tip.
// She only moves while the screen is visible, and she is not shown at all when the user turned the tips off in
// Settings. She does NOT depend on the "Fondo animado" rules (battery saver, system animations): she is tiny and
// slow, and she used to stand still for everyone whose phone has battery saver on. Her steps are driven by the
// frame clock, not by Compose animations, so the system's animation scale does not freeze her either.

private const val TIP_SHOW_MILLIS = 7_000L
private const val WALK_DP_PER_SECOND = 28f
private const val HOP_MILLIS = 650
private val CELL = 2.dp

/** The cream speech bubble of the page: a small "KIXKI" label and the text. Tap it to close it. */
@Composable
fun TipBubble(text: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = design.cardShape
    Column(
        modifier = modifier
            .widthIn(max = 280.dp)
            .hardShadow(design.shadow, 4.dp, shape, downOnly = true)
            .clip(shape)
            .background(scheme.surface, shape)
            .border(3.dp, design.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.kixki_label),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.primary,
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
    }
}

/** Runs [onValue] with eased 0..1 values over [millis], one per frame (the frame clock, so no animation scale). */
private suspend fun glide(millis: Int, ease: (Float) -> Float, onValue: (Float) -> Unit) {
    val start = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val t = ((now - start) / 1_000_000f / millis).coerceIn(0f, 1f)
        onValue(ease(t))
        if (t >= 1f) return
    }
}

/** Where Kixki is going to hop: from one window (at a fraction of its width) to another. */
private class Hop(val fromKey: String, val fromF: Float, val toKey: String, val toF: Float)

/**
 * Library only. Put it over the grid (same box): she walks on the top edge of the cards in [cardKeys] and, now and
 * then, hops to another visible card. Tap her for a tip from [tips].
 */
@Composable
fun KixkiWalker(
    gridState: LazyGridState,
    cardKeys: List<String>,
    tips: List<String>,
    lifecycle: Lifecycle,
    modifier: Modifier = Modifier,
) {
    val controller = LocalThemeController.current
    if (controller == null || !controller.tipsOn || tips.isEmpty()) return
    val density = LocalDensity.current
    val spriteW = with(density) { (CELL * SheepSprite.WIDTH).toPx() }
    val spriteH = with(density) { (CELL * SheepSprite.HEIGHT).toPx() }
    val padX = with(density) { 4.dp.toPx() }
    val padY = with(density) { 6.dp.toPx() }
    val arcPx = with(density) { 34.dp.toPx() }
    val borderPx = with(density) { LocalGifDesign.current.borderWidth.toPx() }
    val keySet by rememberUpdatedState(cardKeys.toSet())

    var curKey by remember { mutableStateOf<String?>(null) }
    val curF = remember { mutableFloatStateOf(0.5f) }
    var facing by remember { mutableIntStateOf(1) }
    var walking by remember { mutableStateOf(false) }
    var hop by remember { mutableStateOf<Hop?>(null) }
    val hopT = remember { mutableFloatStateOf(0f) }
    var tipIndex by remember { mutableIntStateOf(Random.nextInt(tips.size)) }
    var bubble by remember { mutableStateOf<String?>(null) }
    var bubbleId by remember { mutableIntStateOf(0) }

    /** A card whose top edge is fully on screen, so she is never half hidden. */
    fun eligible(): List<Pair<String, Rect>> {
        val info = gridState.layoutInfo
        return info.visibleItemsInfo.mapNotNull { item ->
            val key = item.key as? String ?: return@mapNotNull null
            if (key !in keySet) return@mapNotNull null
            val top = item.offset.y.toFloat()
            if (top < spriteH + 4f || top > info.viewportEndOffset - 32f) return@mapNotNull null
            key to Rect(item.offset.x.toFloat(), top, (item.offset.x + item.size.width).toFloat(), top + item.size.height)
        }
    }

    fun rectOf(key: String?): Rect? {
        if (key == null) return null
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return null
        val x = item.offset.x.toFloat()
        val y = item.offset.y.toFloat()
        return Rect(x, y, x + item.size.width, y + item.size.height)
    }

    /** Top-left of the sprite when she stands on [key] at [f] (0 = left end of the card, 1 = right end). */
    fun anchor(key: String?, f: Float): Offset? {
        val r = rectOf(key) ?: return null
        return Offset(r.left + (r.width - spriteW) * f, r.top - spriteH + borderPx)
    }

    fun position(): Offset? {
        val h = hop ?: return anchor(curKey, curF.floatValue)
        val a = anchor(h.fromKey, h.fromF)
        val b = anchor(h.toKey, h.toF)
        val t = hopT.floatValue
        val p = when {
            a != null && b != null -> Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            else -> b ?: a ?: return null
        }
        return Offset(p.x, p.y - sin(PI * t).toFloat() * arcPx)
    }

    // She never stays on a card that scrolled away or was filtered out: she appears on another visible one.
    LaunchedEffect(gridState) {
        snapshotFlow { curKey in eligible().map { it.first } || hop != null }.collect { ok ->
            if (!ok) {
                curKey = eligible().randomOrNull()?.first
                curF.floatValue = 0.15f + Random.nextFloat() * 0.7f
            }
        }
    }

    // The slow life of Kixki: wait, then walk along the card or hop to another one. Only while the screen is visible.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // A hop cut short by leaving the screen: finish it where it was going.
            hop?.let { curKey = it.toKey; curF.floatValue = it.toF }
            hop = null
            walking = false
            while (true) {
                delay(Random.nextLong(3_500L, 8_000L))
                if (bubble != null) continue
                val here = curKey ?: continue
                val cards = eligible()
                if (cards.isEmpty()) continue
                val others = cards.filter { it.first != here }
                if (others.isEmpty() || Random.nextFloat() < 0.6f) {
                    val width = rectOf(here)?.width ?: continue
                    val target = 0.05f + Random.nextFloat() * 0.9f
                    val from = curF.floatValue
                    facing = if (target >= from) 1 else -1
                    val millis = (abs(target - from) * width / density.density / WALK_DP_PER_SECOND * 1000f).toInt().coerceIn(700, 9_000)
                    walking = true
                    glide(millis, { it }) { curF.floatValue = from + (target - from) * it }
                    walking = false
                } else {
                    val (toKey, toRect) = others.random()
                    val fromRect = rectOf(here)
                    val toF = 0.1f + Random.nextFloat() * 0.8f
                    facing = if (toRect.center.x >= (fromRect?.center?.x ?: 0f)) 1 else -1
                    hop = Hop(here, curF.floatValue, toKey, toF)
                    hopT.floatValue = 0f
                    glide(HOP_MILLIS, { FastOutSlowInEasing.transform(it) }) { hopT.floatValue = it }
                    curKey = toKey
                    curF.floatValue = toF
                    hop = null
                }
            }
        }
    }

    LaunchedEffect(bubbleId) {
        if (bubbleId > 0) {
            delay(TIP_SHOW_MILLIS)
            bubble = null
        }
    }

    val tapLabel = stringResource(R.string.kixki_tap)
    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier = Modifier
                .offset {
                    val p = position()
                    if (p == null) {
                        IntOffset(-100_000, -100_000)
                    } else {
                        // A little hop of one sprite pixel for each step while she walks.
                        val step = if (walking && ((curF.floatValue / 0.05f).toInt() % 2 == 1)) CELL.roundToPx() else 0
                        IntOffset((p.x - padX).roundToInt(), (p.y - padY).roundToInt() - step)
                    }
                }
                .semantics { contentDescription = tapLabel }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                ) {
                    KixkiAudio.baa()
                    bubble = tips[tipIndex % tips.size]
                    tipIndex++
                    bubbleId++
                }
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .graphicsLayer { scaleX = if (facing < 0) -1f else 1f },
        ) {
            KixkiMini(cell = CELL)
        }
        bubble?.let { text ->
            val margin = with(density) { 8.dp.roundToPx() }
            val gap = with(density) { 6.dp.roundToPx() }
            Box(
                modifier = Modifier.fillMaxSize().layout { measurable, c ->
                    val p = measurable.measure(Constraints(maxWidth = (c.maxWidth - 2 * margin).coerceAtLeast(0)))
                    layout(c.maxWidth, c.maxHeight) {
                        val pos = position()
                        if (pos != null) {
                            val cx = pos.x + spriteW / 2f
                            val x = (cx - p.width / 2f).roundToInt().coerceIn(margin, (c.maxWidth - p.width - margin).coerceAtLeast(margin))
                            val above = pos.y - p.height - gap
                            val y = if (above >= 0f) above else pos.y + spriteH + gap
                            p.place(x, y.roundToInt())
                        }
                    }
                },
            ) {
                TipBubble(text, onClick = { bubble = null })
            }
        }
    }
}

/** Editor and widget settings: a small still Kixki for the title bar. Tap her for a tip (shown under her). */
@Composable
fun KixkiCorner(tips: List<String>) {
    val controller = LocalThemeController.current
    if (controller == null || !controller.tipsOn || tips.isEmpty()) return
    var index by remember { mutableIntStateOf(Random.nextInt(tips.size)) }
    var open by remember { mutableStateOf(false) }
    var openId by remember { mutableIntStateOf(0) }
    LaunchedEffect(openId) {
        if (openId > 0) {
            delay(TIP_SHOW_MILLIS)
            open = false
        }
    }
    val tapLabel = stringResource(R.string.kixki_tap)
    Box {
        Box(
            modifier = Modifier
                .semantics { contentDescription = tapLabel }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                ) {
                    KixkiAudio.baa()
                    if (open) index = (index + 1) % tips.size
                    open = true
                    openId++
                }
                .padding(horizontal = 6.dp, vertical = 9.dp),
        ) {
            KixkiMini(cell = CELL)
        }
        if (open) {
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val x = (anchorBounds.right - popupContentSize.width).coerceIn(16, (windowSize.width - popupContentSize.width - 16).coerceAtLeast(16))
                        return IntOffset(x, anchorBounds.bottom + 8)
                    }
                },
                onDismissRequest = { open = false },
            ) {
                TipBubble(tips[index], onClick = { open = false })
            }
        }
    }
}

/** Kixki's welcome: shown once, says who she is and that she gives tips when tapped. Any way of closing it counts as seen. */
@Composable
fun KixkiWelcome(onDone: () -> Unit) {
    Dialog(onDismissRequest = onDone) {
        Box(Modifier.padding(bottom = 8.dp)) {
            WindowCard(
                title = stringResource(R.string.welcome_title),
                modifier = Modifier.fillMaxWidth(),
                onClose = onDone,
                closeDescription = stringResource(R.string.win_close),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    KixkiMini(cell = 6.dp)
                    Text(
                        text = stringResource(R.string.welcome_body),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.welcome_ok))
                    }
                }
            }
        }
    }
}
