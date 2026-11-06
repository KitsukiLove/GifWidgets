package app.gifwidgets.edit

import app.gifwidgets.gif.GifReader

/** One preview frame, already shrunk (see [EditorSession.previewFrame]); [pixels] are untouched source colours. */
class PreviewFrame(val pixels: IntArray, val width: Int, val height: Int)

/**
 * The GIF open in the editor. Keeps the reader (the file bytes) in memory while the editor is on screen and
 * decodes a single frame on demand, stopping right after it. Pure Kotlin; call from a background thread.
 */
class EditorSession(private val reader: GifReader) {
    val width: Int get() = reader.info.width
    val height: Int get() = reader.info.height
    val frameCount: Int get() = reader.info.frameCount
    val delaysCs: IntArray get() = reader.info.delaysCs
    val usesTransparency: Boolean get() = reader.info.usesTransparency

    /** Decodes frame [index] and shrinks it so its longest side is at most [maxSide] (area average). */
    fun previewFrame(
        index: Int,
        maxSide: Int = PREVIEW_MAX_SIDE,
        /** Checked between frames: a scrub that was already replaced by a newer one stops decoding at once. */
        isCancelled: () -> Boolean = { false },
    ): PreviewFrame {
        val target = index.coerceIn(0, frameCount - 1)
        var result: PreviewFrame? = null
        reader.decode { i, _, canvas ->
            if (isCancelled()) return@decode false
            if (i == target) {
                val (dw, dh) = ImageMath.scaledSize(width, height, maxSide)
                val px = if (dw == width && dh == height) {
                    canvas.copyOf(width * height)
                } else {
                    ImageMath.scaleArea(canvas, width, height, dw, dh)
                }
                result = PreviewFrame(px, dw, dh)
                false
            } else {
                true
            }
        }
        return result ?: PreviewFrame(IntArray(width * height), width, height)
    }

    companion object {
        const val PREVIEW_MAX_SIDE = 720
    }
}
