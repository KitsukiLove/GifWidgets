package app.gifwidgets.edit

import app.gifwidgets.gif.GifKind

/**
 * The editor parameters for the "Máxima calidad" frames: the same edits the user made (trim, background,
 * crop...), but at the GIF's own speed (up to [MAX_FPS]) and up to [MAX_SIDE] px, with no memory fit.
 * Photos are lossy (fast to decode at 30 fps); pixel art and transparency stay lossless. Pure.
 */
object HighQualityParams {
    const val MAX_FPS = 30
    const val MAX_SIDE = 1280

    fun from(base: EditorParams?, srcWidth: Int, srcHeight: Int, nativeFps: Int, hasAlpha: Boolean): EditorParams {
        val params = base ?: EditorParams()
        // Never slower than what the user saved (an edited speed) nor than the GIF itself.
        val fps = maxOf(params.timeline.fps, nativeFps.coerceAtLeast(1)).coerceIn(TimelineParams.MIN_FPS, MAX_FPS)
        val keepsLossless = GifKind.isPixelArt(srcWidth, srcHeight) || hasAlpha ||
            params.background.mode != BackgroundMode.KEEP
        return params.copy(
            timeline = params.timeline.copy(fps = fps),
            image = params.image.copy(maxSide = MAX_SIDE),
            lossless = keepsLossless,
            quality = 92,
        )
    }
}
