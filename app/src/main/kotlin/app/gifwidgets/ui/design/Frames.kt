package app.gifwidgets.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The hard (not blurred) drop shadow of the pixel windows: the same shape, moved down and, unless [downOnly], right.
 * The folder cards of the page only cast it downwards.
 */
fun Modifier.hardShadow(color: Color, offset: Dp, shape: Shape, downOnly: Boolean = false): Modifier =
    if (offset.value <= 0f || color == Color.Transparent) {
        this
    } else {
        drawBehind {
            val o = offset.toPx()
            translate(left = if (downOnly) 0f else o, top = o) {
                drawOutline(shape.createOutline(size, layoutDirection, this), color)
            }
        }
    }

/** Lets a child of a padded list draw edge to edge: it is measured [bleed] wider on each side. */
fun Modifier.fullBleed(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val wide = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

/** A 12 x 12 pixel icon. [outline], [main] and [second] colour the 'o', 'a' and 'b' cells of the grid. */
@Composable
fun PixelIcon(
    icon: List<String>,
    outline: Color,
    main: Color,
    second: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier.size(size)) {
        val cell = this.size.width / 12f
        icon.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                val color = when (ch) {
                    'o' -> outline
                    'a' -> main
                    'b' -> second
                    else -> null
                }
                if (color != null) {
                    // +0.5 px of overlap avoids hairline gaps between neighbouring cells
                    drawRect(
                        color = color,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}
