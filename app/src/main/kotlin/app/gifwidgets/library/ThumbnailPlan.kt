package app.gifwidgets.library

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure maths for library thumbnails: how many frames to keep and how small to draw them. */
data class ThumbnailPlan(val stride: Int, val frames: Int, val width: Int, val height: Int) {
    companion object {
        const val MAX_FRAMES = 24
        const val MAX_SIDE_PX = 192

        /** Keeps one frame of every [ThumbnailPlan.stride], so at most [maxFrames] frames remain. */
        fun plan(
            frameCount: Int,
            srcWidth: Int,
            srcHeight: Int,
            maxFrames: Int = MAX_FRAMES,
            maxSide: Int = MAX_SIDE_PX,
        ): ThumbnailPlan {
            require(frameCount > 0 && srcWidth > 0 && srcHeight > 0 && maxFrames > 0 && maxSide > 0)
            val stride = max(1, (frameCount + maxFrames - 1) / maxFrames)
            val kept = (frameCount + stride - 1) / stride
            val scale = min(1.0, maxSide.toDouble() / max(srcWidth, srcHeight))
            val w = max(1, (srcWidth * scale).roundToInt())
            val h = max(1, (srcHeight * scale).roundToInt())
            return ThumbnailPlan(stride, kept, w, h)
        }

        /** Time each kept frame stays on screen so the thumbnail plays at the animation's speed. */
        fun frameMillis(fps: Int, stride: Int): Long =
            (1000L * stride / max(fps, 1)).coerceIn(33L, 1000L)
    }
}
