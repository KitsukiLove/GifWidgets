package app.gifwidgets.edit

import app.gifwidgets.gif.GifKind
import app.gifwidgets.memory.BitmapFormat
import app.gifwidgets.memory.FrameSize
import app.gifwidgets.memory.WidgetMemoryCalculator

/**
 * The automatic choice of speed and size for "Elegir GIF" and multi-import (no editor involved). Pure.
 *
 * - Speed: the GIF's own fps up to [MAX_FPS].
 * - Size: never larger than [MAX_SIDE] on the long side.
 * - Encoding: lossless only for pixel art and transparency; photos are lossy (fast and small).
 * - Memory: all frames must fit the widget budget of a full screen. Small pixel-art GIFs drop frames before
 *   losing sharpness (as before); everything else keeps all its frames and loses a little resolution first
 *   ([WidgetMemoryCalculator.planSmoothFit]). Frames are dropped by lowering the fps, which keeps the duration.
 */
object QuickFit {
    /** Playing from the launcher costs CPU and battery per flip: 15 is fluid for GIFs and half of 30. */
    const val MAX_FPS = 15
    const val MIN_FPS = 5
    const val MAX_SIDE = 960

    /** [lossless] false = lossy WebP: photos without transparency encode and decode several times faster. */
    class Choice(val fps: Int, val maxSide: Int, val pixelArt: Boolean, val lossless: Boolean)

    fun choose(
        delaysCs: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        nativeFps: Int,
        hasAlpha: Boolean,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): Choice {
        val pixelArt = GifKind.isPixelArt(srcWidth, srcHeight)
        val baseFps = nativeFps.coerceIn(MIN_FPS, MAX_FPS)
        val (w, h) = ImageMath.scaledSize(srcWidth, srcHeight, MAX_SIDE)
        val frames = Timeline.plan(delaysCs, TimelineParams(fps = baseFps)).frameCount
        val size = FrameSize(w, h, BitmapFormat.ARGB_8888) // alpha or not: the widget may need 4 bytes per pixel
        val plan = if (pixelArt) {
            WidgetMemoryCalculator.planAutoFit(screenWidthPx, screenHeightPx, frames, size)
        } else {
            WidgetMemoryCalculator.planSmoothFit(screenWidthPx, screenHeightPx, frames, size)
        }
        val fps = if (plan.keptFrames >= frames) {
            baseFps
        } else {
            (baseFps.toLong() * plan.keptFrames / frames).toInt().coerceAtLeast(TimelineParams.MIN_FPS)
        }
        val maxSide = if (plan.sizeReduced) maxOf(plan.widthPx, plan.heightPx) else if (w != srcWidth || h != srcHeight) MAX_SIDE else 0
        return Choice(fps, maxSide, pixelArt, lossless = pixelArt || hasAlpha)
    }
}
