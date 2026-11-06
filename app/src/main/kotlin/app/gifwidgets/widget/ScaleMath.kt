package app.gifwidgets.widget

import kotlin.math.max
import kotlin.math.min

/** Where a source image of size [sw] x [sh] is drawn inside the rectangle (left, top, right, bottom). */
object ScaleMath {
    /** Returns [left, top, right, bottom] of the destination. [naturalScale] is used by CENTER. */
    fun destRect(
        mode: ScaleMode,
        sw: Int,
        sh: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        naturalScale: Float,
        zoomPercent: Int = 100,
    ): FloatArray {
        val iw = max(0f, right - left)
        val ih = max(0f, bottom - top)
        val fit = min(iw / sw, ih / sh)
        val fill = max(iw / sw, ih / sh)
        val base = when (mode) {
            ScaleMode.AUTO -> autoScale(fit, fill)
            ScaleMode.FILL -> fill
            ScaleMode.CENTER -> naturalScale
            ScaleMode.FIT -> fit
        }
        val s = base * zoomPercent.coerceIn(50, 300) / 100f
        val dw = sw * s
        val dh = sh * s
        val l = left + (iw - dw) / 2f
        val t = top + (ih - dh) / 2f
        return floatArrayOf(l, t, l + dw, t + dh)
    }

    /** How many widget pixels one stored pixel gets when the image is drawn in a [w] x [h] area (> 1 = enlarged). */
    fun drawnScale(mode: ScaleMode, sw: Int, sh: Int, w: Int, h: Int, zoomPercent: Int = 100): Float {
        val d = destRect(mode, sw, sh, 0f, 0f, w.toFloat(), h.toFloat(), naturalScale = 1f, zoomPercent = zoomPercent)
        return (d[2] - d[0]) / sw
    }

    /**
     * The most of the image AUTO is willing to cut off (0.35 = about a third). A banner in a widget of a similar
     * shape fills it completely; in a much squarer widget it is enlarged until exactly this share is cut, so the
     * empty bars are as small as that allows, instead of the whole image shrinking to a thin strip.
     * (It was 0.45 all-or-nothing, then 0.10: both looked wrong with banners.)
     */
    const val AUTO_MAX_CROP = 0.35f

    /** AUTO's scale from the scale that shows the whole image ([fit]) and the one that fills the area ([fill]). */
    fun autoScale(fit: Float, fill: Float): Float {
        if (fill <= 0f || fit <= 0f) return fit
        val crop = 1f - fit / fill
        return if (crop <= AUTO_MAX_CROP) fill else fit / (1f - AUTO_MAX_CROP)
    }
}
