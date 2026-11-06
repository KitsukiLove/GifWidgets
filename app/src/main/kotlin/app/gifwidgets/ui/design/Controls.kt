package app.gifwidgets.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import app.gifwidgets.audio.KixkiAudio
import app.gifwidgets.ui.theme.LocalGifDesign
import app.gifwidgets.ui.theme.PixelShape

// Drop-in replacements for the Material buttons, card and switch with the same names and parameters the app
// already uses, so a screen only has to change its import. They draw the Kixki look: a frame, a hard shadow that
// "presses in" on tap (Pixel) or a rounded button (Colina XP). Colours come from the Material theme.

private val BUTTON_MIN_HEIGHT = 48.dp

@Composable
private fun GifButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    contentColor: Color,
    border: Color?,
    shadow: Color?,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    body: @Composable RowScope.() -> Unit,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = design.buttonShape
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = if (pressed && enabled && design.isPixel) design.shadowOffset else 0.dp
    val bg = when {
        !enabled -> if (container == Color.Transparent) Color.Transparent else scheme.onSurface.copy(alpha = 0.12f)
        pressed && !design.isPixel -> container.copy(alpha = 0.8f)
        pressed && container == Color.Transparent -> scheme.primary.copy(alpha = 0.12f)
        else -> container
    }
    val fg = if (enabled) contentColor else scheme.onSurface.copy(alpha = 0.38f)
    Box(
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = {
                KixkiAudio.blip(if (container == Color.Transparent) 520.0 else 700.0, .06)
                onClick()
            },
        ),
    ) {
        var visual = Modifier
            .offset(press, press)
            .hardShadow(if (enabled && pressed.not() && shadow != null) shadow else Color.Transparent, design.shadowOffset, shape)
            .clip(shape)
            .background(bg, shape)
        if (border != null) {
            visual = visual.border(design.borderWidth, if (enabled) border else border.copy(alpha = 0.3f), shape)
        }
        Row(
            modifier = visual
                .defaultMinSize(minHeight = BUTTON_MIN_HEIGHT)
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides fg) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) { body() }
            }
        }
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    // The page's primary button: violet with a dark frame (light), yellow with a gold frame (dark).
    GifButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = scheme.primary,
        contentColor = scheme.onPrimary,
        border = if (design.dark) androidx.compose.ui.graphics.Color(0xFFC58F2A) else androidx.compose.ui.graphics.Color(0xFF2A1650),
        shadow = if (design.dark) androidx.compose.ui.graphics.Color(0xFF7A5313) else androidx.compose.ui.graphics.Color(0xFF3F1FA0),
        horizontalPadding = 18.dp,
        body = content,
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    GifButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = scheme.surface,
        contentColor = scheme.onSurface,
        border = design.outline,
        shadow = design.shadow,
        horizontalPadding = 16.dp,
        body = content,
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    GifButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        border = null,
        shadow = null,
        horizontalPadding = 12.dp,
        body = content,
    )
}

/** A framed card: cream surface, outline, and the hard shadow in the Pixel style. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val design = LocalGifDesign.current
    val shape = design.cardShape
    Column(
        modifier = modifier
            .hardShadow(design.shadow, design.shadowOffset, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(design.borderWidth, design.outline, shape),
        content = content,
    )
}

/** On / off switch with a square (Pixel) or rounded (XP) track. Pass a null [onCheckedChange] when a parent row handles the tap. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val trackShape = if (design.isPixel) PixelShape(2.dp) else RoundedCornerShape(50)
    val thumbShape = if (design.isPixel) PixelShape(2.dp) else RoundedCornerShape(50)
    val alpha = if (enabled) 1f else 0.4f
    val track = (if (checked) scheme.primary else scheme.surfaceVariant).copy(alpha = alpha)
    val thumb = (if (checked) scheme.onPrimary else scheme.outline).copy(alpha = alpha)
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = {
            KixkiAudio.blip(if (it) 880.0 else 440.0, .06)
            onCheckedChange(it)
        })
    } else {
        Modifier
    }
    Box(modifier = modifier.size(width = 56.dp, height = 48.dp).then(toggle), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(trackShape)
                .background(track, trackShape)
                .border(2.dp, design.outline.copy(alpha = alpha), trackShape),
        ) {
            Box(
                modifier = Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 4.dp)
                    .size(18.dp)
                    .background(thumb, thumbShape),
            )
        }
    }
}


/**
 * A slider with a square thumb and a framed track, same parameters as the Material one. With [steps] > 0 it snaps
 * to [steps] + 1 equal intervals. It makes no sound while dragging (that would be a buzz).
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val lo = valueRange.start
    val hi = valueRange.endInclusive
    val span = (hi - lo).coerceAtLeast(1e-6f)
    val fraction = ((value - lo) / span).coerceIn(0f, 1f)
    val alpha = if (enabled) 1f else 0.4f
    val track = scheme.surfaceVariant
    val fill = scheme.primary.copy(alpha = alpha)
    val frame = design.outline.copy(alpha = alpha)
    val thumbFace = design.accent.copy(alpha = alpha)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(lo, hi), lo..hi, steps)
                if (enabled) {
                    setProgress { target ->
                        change(target.coerceIn(lo, hi))
                        true
                    }
                }
            }
            .pointerInput(enabled, lo, hi, steps) {
                if (!enabled) return@pointerInput
                val half = 11.dp.toPx()
                fun valueAt(x: Float): Float {
                    var f = ((x - half) / (size.width - half * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    if (steps > 0) f = (f * (steps + 1)).roundToInt() / (steps + 1f)
                    return lo + f * (hi - lo)
                }
                detectTapGestures { p ->
                    change(valueAt(p.x))
                    finished?.invoke()
                }
            }
            .pointerInput(enabled, lo, hi, steps) {
                if (!enabled) return@pointerInput
                val half = 11.dp.toPx()
                fun valueAt(x: Float): Float {
                    var f = ((x - half) / (size.width - half * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    if (steps > 0) f = (f * (steps + 1)).roundToInt() / (steps + 1f)
                    return lo + f * (hi - lo)
                }
                detectHorizontalDragGestures(
                    onDragStart = { p -> change(valueAt(p.x)) },
                    onDragEnd = { finished?.invoke() },
                    onDragCancel = { finished?.invoke() },
                ) { pointer, _ ->
                    pointer.consume()
                    change(valueAt(pointer.position.x))
                }
            },
    ) {
        val half = 11.dp.toPx()
        val barH = 10.dp.toPx()
        val stroke = 2.dp.toPx()
        val top = (size.height - barH) / 2f
        val left = half
        val width = size.width - half * 2f
        drawRect(frame, Offset(left - stroke, top - stroke), Size(width + stroke * 2f, barH + stroke * 2f))
        drawRect(track, Offset(left, top), Size(width, barH))
        val cx = left + width * fraction
        if (cx > left) drawRect(fill, Offset(left, top), Size(cx - left, barH))
        if (steps in 1..30) {
            val dot = 3.dp.toPx()
            for (i in 0..steps + 1) {
                val x = left + width * i / (steps + 1f)
                drawRect(frame.copy(alpha = alpha * .5f), Offset(x - dot / 2f, top + barH + 3.dp.toPx()), Size(dot, dot))
            }
        }
        val thumb = 22.dp.toPx()
        val tx = cx - thumb / 2f
        val ty = (size.height - thumb) / 2f
        if (enabled) drawRect(design.shadow, Offset(tx + 3.dp.toPx(), ty + 3.dp.toPx()), Size(thumb, thumb))
        drawRect(frame, Offset(tx, ty), Size(thumb, thumb))
        drawRect(thumbFace, Offset(tx + stroke * 1.5f, ty + stroke * 1.5f), Size(thumb - stroke * 3f, thumb - stroke * 3f))
    }
}
