package app.gifwidgets.widget

import app.gifwidgets.memory.BitmapFormat
import app.gifwidgets.memory.FrameSize
import app.gifwidgets.memory.WidgetMemoryCalculator
import kotlin.math.roundToInt

/**
 * How one widget is rendered: which stored frames are kept, at what size, and how long each one stays.
 * [width] x [height] may be smaller than the widget when the memory budget forces it (the launcher scales it up).
 */
class WidgetPlan(
    val keptIndices: List<Int>,
    val stride: Int,
    val width: Int,
    val height: Int,
    val intervalMs: Int,
    val fits: Boolean,
    val framesReduced: Boolean,
    val sizeReduced: Boolean,
) {
    val keptFrames: Int get() = keptIndices.size
}

object WidgetPlanner {
    const val MIN_INTERVAL_MS = 20
    const val MAX_INTERVAL_MS = 10_000

    fun plan(
        frameCount: Int,
        fps: Int,
        speedPercent: Int,
        widthPx: Int,
        heightPx: Int,
        screenWidthPx: Int,
        screenHeightPx: Int,
        usagePercent: Int,
        format: BitmapFormat,
        /** true = keep every frame and lose a little resolution first (photos, banners); false = crisp pixels first. */
        smooth: Boolean = false,
    ): WidgetPlan {
        require(frameCount >= 1 && widthPx >= 1 && heightPx >= 1)
        val size = FrameSize(widthPx, heightPx, format)
        val usage = usagePercent.coerceIn(1, 100)
        val fit = if (smooth) {
            WidgetMemoryCalculator.planSmoothFit(screenWidthPx, screenHeightPx, frameCount, size, usage)
        } else {
            WidgetMemoryCalculator.planAutoFit(screenWidthPx, screenHeightPx, frameCount, size, usage)
        }
        val indices = (0 until frameCount step fit.frameStride).toList()
        val effectiveFps = fps.coerceAtLeast(1) * speedPercent.coerceIn(1, 1000) / 100.0
        val interval = (1000.0 * fit.frameStride / effectiveFps).roundToInt()
            .coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        return WidgetPlan(
            keptIndices = indices,
            stride = fit.frameStride,
            width = fit.widthPx,
            height = fit.heightPx,
            intervalMs = interval,
            fits = fit.fits,
            framesReduced = fit.framesReduced,
            sizeReduced = fit.sizeReduced,
        )
    }
}
