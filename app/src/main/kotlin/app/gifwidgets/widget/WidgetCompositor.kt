package app.gifwidgets.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/**
 * Draws one animation frame into a widget-sized bitmap: background, scaled frame, brightness/opacity,
 * rounded corners and border are all baked into the pixels (we never rely on the system's widget background,
 * which Android 12 clips with its own corner radius).
 */
object WidgetCompositor {

    /**
     * [unit] = pixels per dp for THIS bitmap (the density, times width/requested width when the memory budget
     * made the bitmap smaller than the widget).
     */
    fun compose(
        src: Bitmap,
        width: Int,
        height: Int,
        settings: WidgetSettings,
        unit: Float,
        /** Bitmap width / widget width in px: how big a stored frame is drawn in CENTER mode (1 = real pixels). */
        naturalScale: Float = 1f,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, config)
        val canvas = Canvas(out)
        val full = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = (settings.cornerDp * unit).coerceAtMost(minOf(width, height) / 2f)
        val border = settings.borderDp * unit

        // A separate layer (a full extra bitmap per frame) is only needed to cut rounded corners.
        val layer = if (radius > 0f) canvas.saveLayer(full, null) else -1

        if (settings.bgOpacity > 0) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = settings.bgColor or 0xFF000000.toInt()
                alpha = settings.bgOpacity * 255 / 100
            }
            canvas.drawRect(full, bg)
        }

        val inset = settings.paddingDp * unit + border
        val inner = RectF(inset, inset, width - inset, height - inset)
        if (inner.width() > 1f && inner.height() > 1f) {
            val dest = ScaleMath.destRect(
                settings.scale, src.width, src.height,
                inner.left, inner.top, inner.right, inner.bottom,
                naturalScale = naturalScale,
                zoomPercent = settings.zoomPercent,
            )
            val paint = Paint().apply {
                isFilterBitmap = !settings.sharp
                isAntiAlias = false
                alpha = settings.opacity * 255 / 100
                if (settings.brightness != 100) {
                    val b = settings.brightness / 100f
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(b, b, b, 1f) })
                }
            }
            canvas.save()
            canvas.clipRect(inner)
            canvas.drawBitmap(src, null, RectF(dest[0], dest[1], dest[2], dest[3]), paint)
            canvas.restore()
        }

        // Round the corners: keep only what is inside the rounded rectangle.
        if (radius > 0f) {
            val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                color = 0xFFFFFFFF.toInt()
            }
            canvas.drawRoundRect(full, radius, radius, mask)
        }
        if (layer >= 0) canvas.restoreToCount(layer)

        if (border > 0f) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = border
                color = settings.borderColor
            }
            val half = border / 2f
            canvas.drawRoundRect(
                RectF(half, half, width - half, height - half),
                (radius - half).coerceAtLeast(0f), (radius - half).coerceAtLeast(0f), stroke,
            )
        }
        return out
    }
}
