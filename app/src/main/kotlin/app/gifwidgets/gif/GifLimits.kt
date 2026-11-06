package app.gifwidgets.gif

/**
 * Hard limits applied to every GIF we accept (untrusted input).
 *
 * [maxCanvasPixels] is our own extra memory guard: the decoder keeps a few
 * full-canvas IntArrays (4 bytes per pixel) alive at once, so a 4096 x 4096
 * GIF (64 MB per buffer) could crash the app. 6 291 456 px = 4096 x 1536,
 * about 24 MB per buffer. Documented in DECISIONS.md; change it there too.
 */
data class GifLimits(
    val maxFileBytes: Long = 50L * 1024 * 1024,
    val maxSide: Int = 4096,
    val maxFrames: Int = 400,
    val maxCanvasPixels: Long = 6_291_456L,
)
