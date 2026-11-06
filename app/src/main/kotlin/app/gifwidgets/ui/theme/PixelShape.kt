package app.gifwidgets.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * A rectangle whose four corners are missing one square "pixel" (the notched look of the Kixki pages).
 *
 * It is a [CornerBasedShape] because Material's `Shapes` only accepts those: the size of the missing pixel is
 * the "corner size" (so it is also scaled down by Compose when the box is tiny). It is rounded to whole screen
 * pixels so the edges stay crisp.
 */
class PixelShape(corner: CornerSize) : CornerBasedShape(corner, corner, corner, corner) {

    constructor(step: Dp) : this(CornerSize(step))

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val s = topStart.roundToInt().coerceAtLeast(1).toFloat().coerceAtMost(minOf(size.width, size.height) / 3f)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(s, 0f)
            lineTo(w - s, 0f)
            lineTo(w - s, s)
            lineTo(w, s)
            lineTo(w, h - s)
            lineTo(w - s, h - s)
            lineTo(w - s, h)
            lineTo(s, h)
            lineTo(s, h - s)
            lineTo(0f, h - s)
            lineTo(0f, s)
            lineTo(s, s)
            close()
        }
        return Outline.Generic(path)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = PixelShape(topStart)

    override fun equals(other: Any?): Boolean = other is PixelShape && other.topStart == topStart

    override fun hashCode(): Int = topStart.hashCode()

    override fun toString(): String = "PixelShape(step = $topStart)"
}
