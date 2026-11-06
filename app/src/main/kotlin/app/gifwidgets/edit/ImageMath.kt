package app.gifwidgets.edit

import kotlin.math.max
import kotlin.math.roundToInt

/** Pure pixel maths: crop bounds, crop, and our own scaling loops (no Bitmap.createScaledBitmap). */
object ImageMath {

    /** Bounding box of the pixels with alpha > 0, or null if the image is fully transparent. */
    fun opaqueBounds(px: IntArray, w: Int, h: Int): CropRect? {
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((px[row + x] ushr 24) != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else CropRect(minX, minY, maxX + 1, maxY + 1)
    }

    fun union(a: CropRect?, b: CropRect?): CropRect? = when {
        a == null -> b
        b == null -> a
        else -> CropRect(
            minOf(a.left, b.left), minOf(a.top, b.top),
            maxOf(a.right, b.right), maxOf(a.bottom, b.bottom),
        )
    }

    /** Clamps [r] to the image and makes sure it is at least 1 x 1. */
    fun clampRect(r: CropRect, w: Int, h: Int): CropRect {
        val l = r.left.coerceIn(0, w - 1)
        val t = r.top.coerceIn(0, h - 1)
        val rr = r.right.coerceIn(l + 1, w)
        val bb = r.bottom.coerceIn(t + 1, h)
        return CropRect(l, t, rr, bb)
    }

    fun crop(px: IntArray, w: Int, rect: CropRect): IntArray {
        val out = IntArray(rect.width * rect.height)
        for (y in 0 until rect.height) {
            System.arraycopy(px, (rect.top + y) * w + rect.left, out, y * rect.width, rect.width)
        }
        return out
    }

    /** Size after limiting the longest side to [maxSide]; 0 or a larger value keeps the size (never enlarges). */
    fun scaledSize(w: Int, h: Int, maxSide: Int): Pair<Int, Int> {
        val longest = max(w, h)
        if (maxSide <= 0 || longest <= maxSide) return w to h
        val s = maxSide.toDouble() / longest
        return max(1, (w * s).roundToInt()) to max(1, (h * s).roundToInt())
    }

    fun scaleNearest(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        val out = IntArray(dw * dh)
        for (y in 0 until dh) {
            val sy = (((y + 0.5) * sh / dh).toInt()).coerceIn(0, sh - 1)
            for (x in 0 until dw) {
                val sx = (((x + 0.5) * sw / dw).toInt()).coerceIn(0, sw - 1)
                out[y * dw + x] = src[sy * sw + sx]
            }
        }
        return out
    }

    /** Box (area average) downscale with fractional edge weights; alpha-weighted so colours do not bleed. */
    fun scaleArea(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        val out = IntArray(dw * dh)
        val xr = sw.toDouble() / dw
        val yr = sh.toDouble() / dh
        for (y in 0 until dh) {
            val y0 = y * yr
            val y1 = (y + 1) * yr
            val sy0 = y0.toInt()
            val sy1 = minOf(sh - 1, Math.ceil(y1).toInt() - 1)
            for (x in 0 until dw) {
                val x0 = x * xr
                val x1 = (x + 1) * xr
                val sx0 = x0.toInt()
                val sx1 = minOf(sw - 1, Math.ceil(x1).toInt() - 1)
                var wSum = 0.0
                var aSum = 0.0
                var rSum = 0.0
                var gSum = 0.0
                var bSum = 0.0
                for (sy in sy0..sy1) {
                    val wy = minOf(y1, sy + 1.0) - maxOf(y0, sy.toDouble())
                    if (wy <= 0) continue
                    for (sx in sx0..sx1) {
                        val wx = minOf(x1, sx + 1.0) - maxOf(x0, sx.toDouble())
                        if (wx <= 0) continue
                        val wgt = wx * wy
                        val c = src[sy * sw + sx]
                        val a = (c ushr 24).toDouble()
                        wSum += wgt
                        aSum += a * wgt
                        val wa = a * wgt
                        rSum += ((c shr 16) and 0xFF) * wa
                        gSum += ((c shr 8) and 0xFF) * wa
                        bSum += (c and 0xFF) * wa
                    }
                }
                if (wSum <= 0 || aSum <= 0) {
                    out[y * dw + x] = 0
                } else {
                    val a = (aSum / wSum).roundToInt().coerceIn(0, 255)
                    val r = (rSum / aSum).roundToInt().coerceIn(0, 255)
                    val g = (gSum / aSum).roundToInt().coerceIn(0, 255)
                    val b = (bSum / aSum).roundToInt().coerceIn(0, 255)
                    out[y * dw + x] = if (a == 0) 0 else (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return out
    }
}
