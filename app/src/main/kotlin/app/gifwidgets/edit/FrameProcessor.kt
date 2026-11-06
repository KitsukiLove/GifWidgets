package app.gifwidgets.edit

class ProcessedFrame(val pixels: IntArray, val width: Int, val height: Int)

/** One frame through the whole chain: background, crop, scale. Pure; the caller handles decoding and WebP. */
object FrameProcessor {

    /** Applies the background (on a copy; [src] is not modified). */
    fun withBackground(src: IntArray, w: Int, h: Int, bg: BackgroundParams): IntArray {
        val copy = src.copyOf(w * h)
        BackgroundRemover.apply(copy, w, h, bg)
        return copy
    }

    /**
     * [rect] is the crop in source pixels (null = whole image). [px] must already have the background applied.
     */
    fun cropAndScale(px: IntArray, w: Int, h: Int, rect: CropRect?, image: ImageParams): ProcessedFrame {
        var cur = px
        var cw = w
        var ch = h
        if (rect != null) {
            val r = ImageMath.clampRect(rect, w, h)
            if (r.left != 0 || r.top != 0 || r.right != w || r.bottom != h) {
                cur = ImageMath.crop(px, w, r)
                cw = r.width
                ch = r.height
            }
        }
        val (dw, dh) = ImageMath.scaledSize(cw, ch, image.maxSide)
        if (dw != cw || dh != ch) {
            cur = if (image.sharpPixels) {
                ImageMath.scaleNearest(cur, cw, ch, dw, dh)
            } else {
                ImageMath.scaleArea(cur, cw, ch, dw, dh)
            }
            cw = dw
            ch = dh
        }
        return ProcessedFrame(cur, cw, ch)
    }

    /** The crop rectangle to use: auto bounds (all frames), the manual rectangle, or none. */
    fun effectiveCrop(image: ImageParams, autoBounds: CropRect?): CropRect? =
        if (image.autoCrop) autoBounds else image.manualCrop
}
