package app.gifwidgets.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gifwidgets.ui.theme.LocalGifDesign
import app.gifwidgets.ui.theme.PixelShape
import app.gifwidgets.ui.theme.PixelifySans
import kotlin.math.roundToInt

// The small pieces of the KixKi Store pages: window cards, pink tags, pill chips, the search box, the "+" tile
// and the "+" tile. The background is SceneBackdrop (Scenes.kt).

private fun smallShape(pixel: Boolean): Shape = if (pixel) PixelShape(2.dp) else RoundedCornerShape(6.dp)

/** The pastel of a folder card (pink, lilac, mint, butter), the same for the same [key]. Darker versions at night. */
@Composable
fun cardTint(key: String): Color {
    val dark = LocalGifDesign.current.dark
    val light = longArrayOf(0xFFFFE4F1, 0xFFEADFFF, 0xFFDDF6E8, 0xFFFFF0C9)
    val night = longArrayOf(0xFF3A2068, 0xFF2D2370, 0xFF1F3A5E, 0xFF45286A)
    val i = (key.hashCode() and 0x7fffffff) % light.size
    return Color(if (dark) night[i] else light[i])
}

/**
 * A card that looks like a program window: gradient title bar (name on the left, [actions] and, if given, a close
 * button on the right) and a body. [onClose] is what the X does; [closeDescription] is its screen-reader label.
 */
@Composable
fun WindowCard(
    title: String,
    modifier: Modifier = Modifier,
    body: Color = MaterialTheme.colorScheme.surface,
    onClose: (() -> Unit)? = null,
    closeDescription: String = "",
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val design = LocalGifDesign.current
    val shape = design.cardShape
    Column(
        modifier = modifier
            .hardShadow(design.shadow, design.shadowOffset, shape, downOnly = true)
            .clip(shape)
            .background(body, shape)
            .border(design.borderWidth, design.outline, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(design.titleStart, design.titleEnd)))
                .heightIn(min = 44.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = design.titleInk,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
            if (onClose != null) {
                WindowCloseButton(onClose = onClose, description = closeDescription, touch = 44.dp, faceSize = 26.dp)
            }
        }
        // The frame under the title bar, as thick as the one around the card.
        Box(Modifier.fillMaxWidth().height(design.borderWidth).background(design.outline))
        content()
    }
}

/** A small button for a title bar: a 12 x 12 pixel icon on a 40 dp touch area. */
@Composable
fun BarIconButton(icon: List<String>, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null) {
    val design = LocalGifDesign.current
    val ink = tint ?: design.titleInk
    Box(
        modifier = modifier.size(44.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(icon = icon, outline = ink, main = ink, second = ink, size = 20.dp)
    }
}

/** The pink information pill of the page ("Nuevo", "9 apps"...). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val design = LocalGifDesign.current
    val shape = smallShape(design.isPixel)
    val bg = if (design.dark) Color(0xFF3D2480) else Color(0xFFFFD9EC)
    val ink = if (design.dark) Color(0xFFFFB3DC) else Color(0xFF8A1F5E)
    Text(
        text = text,
        color = ink,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = modifier.clip(shape).background(bg, shape).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** A framed pill the user can tap (filters, the sort menu). [count] is shown in bold, like the page's chips. */
@Composable
fun PillChip(
    label: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = if (design.isPixel) PixelShape(3.dp) else RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surface, shape)
            .border(if (selected) 3.dp else 2.dp, if (selected) scheme.primary else design.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1)
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = scheme.primary,
                maxLines = 1,
            )
        }
    }
}

/** The search box of the page: framed, with a pixel magnifier on the left and a muted hint. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = design.cardShape
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = PixelifySans, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        modifier = modifier.hardShadow(design.shadow, design.shadowOffset, shape, downOnly = true),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(shape)
                    .background(scheme.surface, shape)
                    .border(design.borderWidth, design.outline, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PixelIcon(
                    icon = PixelIcons.SEARCH,
                    outline = scheme.onSurfaceVariant,
                    main = scheme.surfaceVariant,
                    second = scheme.onSurfaceVariant,
                    size = 22.dp,
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

/** The "+" tile: a dashed frame like an empty folder on the desktop. */
@Composable
fun AddTile(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = design.cardShape
    val dash = design.outline
    Column(
        modifier = modifier
            .clip(shape)
            .background(scheme.surface.copy(alpha = 0.6f), shape)
            .drawBehind {
                val stroke = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
                )
                drawOutline(shape.createOutline(size, layoutDirection, this), dash, style = stroke)
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 150.dp)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelIcon(
            icon = PixelIcons.PLUS,
            outline = design.ink,
            main = design.accent,
            second = design.pink,
            size = 48.dp,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 2,
        )
    }
}
