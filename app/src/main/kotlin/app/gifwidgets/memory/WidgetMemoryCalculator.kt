package app.gifwidgets.memory

import kotlin.math.sqrt

/*
 * Pure (no android.* imports) implementation of the widget bitmap memory
 * budget described in "ARQUITECTURA DEL WIDGET / Presupuesto de memoria".
 *
 * VERIFY: the platform is said to cap the bitmaps of one widget at
 * 6 x screenWidthPx x screenHeightPx. The only source is a fork of Android
 * (not the official AOSP tree):
 * https://gitlab.e.foundation/e/os/android_frameworks_base/-/commit/037faad0f437c5fdc5435c030ef03d61de75090f
 * Treat PLATFORM_LIMIT_MULTIPLIER as an unconfirmed constant. We only ever
 * target DEFAULT_USAGE_PERCENT (80 %) of it, which also leaves headroom.
 * All arithmetic is integer, so the budget never depends on floating point.
 */

enum class BitmapFormat(val bytesPerPixel: Int) {
    ARGB_8888(4),

    // VERIFY: 2 bytes per pixel, and visually acceptable for opaque content.
    RGB_565(2),
}

/** Dimensions and pixel format of one rendered widget frame. */
data class FrameSize(
    val widthPx: Int,
    val heightPx: Int,
    val format: BitmapFormat = BitmapFormat.ARGB_8888,
) {
    init {
        require(widthPx > 0) { "widthPx must be > 0, was $widthPx" }
        require(heightPx > 0) { "heightPx must be > 0, was $heightPx" }
    }

    val bytes: Long get() = widthPx.toLong() * heightPx.toLong() * format.bytesPerPixel
}

/** Total bytes used by a set of frames compared with the allowed maximum. */
data class WidgetMemoryBudget(val maxBytes: Long, val usedBytes: Long) {
    init {
        require(maxBytes >= 0) { "maxBytes must be >= 0, was $maxBytes" }
        require(usedBytes >= 0) { "usedBytes must be >= 0, was $usedBytes" }
    }

    val isOverBudget: Boolean get() = usedBytes > maxBytes
    val remainingBytes: Long get() = (maxBytes - usedBytes).coerceAtLeast(0)
}

/**
 * Result of [WidgetMemoryCalculator.planAutoFit] ("Ajustar automáticamente").
 *
 * Frames kept are the ones at indices 0, [frameStride], 2 * [frameStride], ...
 * of the original sequence. Every kept frame is [widthPx] x [heightPx].
 * [fits] is false only when even a 1 x 1 frame cannot satisfy the budget
 * (practically a degenerate screen size); the numbers are then best effort.
 */
data class AutoFitPlan(
    val frameStride: Int,
    val keptFrames: Int,
    val widthPx: Int,
    val heightPx: Int,
    val format: BitmapFormat,
    val usedBytes: Long,
    val maxBytes: Long,
    val fits: Boolean,
    val framesReduced: Boolean,
    val sizeReduced: Boolean,
)

object WidgetMemoryCalculator {

    const val DEFAULT_USAGE_PERCENT = 80

    /**
     * Default floor for frame thinning. The brief says "first drop frames,
     * then reduce the size"; without a floor that would reduce a 96-frame
     * animation to 5 frames before touching the size. With the floor,
     * thinning stops at this many frames and the size is reduced instead.
     * Set it to 1 for the literal "frames first, down to one" behaviour.
     * (Decision recorded in DECISIONS.md.)
     */
    const val DEFAULT_MIN_KEPT_FRAMES = 12

    /**
     * The smooth plan never shrinks a frame below this many pixels on its long side before it drops frames
     * (a 480 px GIF may go down to half; a 300 px one to 240; one smaller than this is never shrunk).
     */
    const val SMOOTH_MIN_LONG_SIDE = 240

    // VERIFY: unconfirmed against the official sources, see the file header.
    private const val PLATFORM_LIMIT_MULTIPLIER = 6L

    /** The (unverified) platform ceiling for the bitmaps of one widget. */
    fun platformLimitBytes(screenWidthPx: Int, screenHeightPx: Int): Long {
        require(screenWidthPx > 0) { "screenWidthPx must be > 0, was $screenWidthPx" }
        require(screenHeightPx > 0) { "screenHeightPx must be > 0, was $screenHeightPx" }
        return PLATFORM_LIMIT_MULTIPLIER * screenWidthPx.toLong() * screenHeightPx.toLong()
    }

    /** [platformLimitBytes] scaled down to [usagePercent] percent. */
    fun maxBudgetBytes(
        screenWidthPx: Int,
        screenHeightPx: Int,
        usagePercent: Int = DEFAULT_USAGE_PERCENT,
    ): Long {
        require(usagePercent in 1..100) { "usagePercent must be in 1..100, was $usagePercent" }
        return platformLimitBytes(screenWidthPx, screenHeightPx) * usagePercent / 100
    }

    /** Compares the total memory of [frames] with the computed budget. */
    fun budgetFor(
        screenWidthPx: Int,
        screenHeightPx: Int,
        frames: List<FrameSize>,
        usagePercent: Int = DEFAULT_USAGE_PERCENT,
    ): WidgetMemoryBudget =
        WidgetMemoryBudget(
            maxBytes = maxBudgetBytes(screenWidthPx, screenHeightPx, usagePercent),
            usedBytes = frames.sumOf { it.bytes },
        )

    /** Number of frames kept when keeping one of every [stride] (indices 0, stride, 2*stride...). */
    fun keptFrameCount(frameCount: Int, stride: Int): Int {
        require(frameCount >= 0) { "frameCount must be >= 0, was $frameCount" }
        require(stride >= 1) { "stride must be >= 1, was $stride" }
        return (frameCount + stride - 1) / stride
    }

    /**
     * "Ajustar automáticamente" for [frameCount] frames that all have size
     * [frame].
     *
     * Step 1 - drop frames: the smallest stride N (keep 1 of every N) that
     * fits, but never keeping fewer than [minKeptFrames] frames (clamped to
     * [frameCount]).
     * Step 2 - only if step 1 cannot fit even at the floor: keep the floor
     * number of frames and shrink the frame size uniformly (aspect ratio
     * preserved to within one pixel) until the total fits.
     */
    fun planAutoFit(
        screenWidthPx: Int,
        screenHeightPx: Int,
        frameCount: Int,
        frame: FrameSize,
        usagePercent: Int = DEFAULT_USAGE_PERCENT,
        minKeptFrames: Int = DEFAULT_MIN_KEPT_FRAMES,
    ): AutoFitPlan {
        require(frameCount >= 1) { "frameCount must be >= 1, was $frameCount" }
        require(minKeptFrames >= 1) { "minKeptFrames must be >= 1, was $minKeptFrames" }
        val maxBytes = maxBudgetBytes(screenWidthPx, screenHeightPx, usagePercent)
        val floor = minOf(frameCount, minKeptFrames)
        val bytesPerPixel = frame.format.bytesPerPixel

        // Largest stride that still keeps at least `floor` frames. A stride
        // above frameCount keeps the same single frame as stride == frameCount,
        // so the search stops there (otherwise floor == 1 would never end).
        var maxStride = 1
        while (maxStride < frameCount && keptFrameCount(frameCount, maxStride + 1) >= floor) {
            maxStride++
        }

        // Step 1: thin frames only.
        for (stride in 1..maxStride) {
            val kept = keptFrameCount(frameCount, stride)
            if (kept * frame.bytes <= maxBytes) {
                return AutoFitPlan(
                    frameStride = stride,
                    keptFrames = kept,
                    widthPx = frame.widthPx,
                    heightPx = frame.heightPx,
                    format = frame.format,
                    usedBytes = kept * frame.bytes,
                    maxBytes = maxBytes,
                    fits = true,
                    framesReduced = stride > 1,
                    sizeReduced = false,
                )
            }
        }

        // Step 2: keep the floor, shrink the frames.
        val kept = keptFrameCount(frameCount, maxStride)
        val pixelsAllowed = maxBytes / kept / bytesPerPixel
        val originalPixels = frame.widthPx.toLong() * frame.heightPx
        val scale = sqrt(pixelsAllowed.toDouble() / originalPixels.toDouble())
        var w = maxOf(1L, (frame.widthPx * scale).toLong())
        var h = maxOf(1L, (frame.heightPx * scale).toLong())
        // Guard against floating point rounding: shrink the relatively larger side.
        while (w * h > pixelsAllowed && (w > 1 || h > 1)) {
            val widthIsRelativelyLarger = w * frame.heightPx >= h * frame.widthPx
            if (widthIsRelativelyLarger) {
                if (w > 1) w-- else h--
            } else {
                if (h > 1) h-- else w--
            }
        }
        val used = kept * w * h * bytesPerPixel
        return AutoFitPlan(
            frameStride = maxStride,
            keptFrames = kept,
            widthPx = w.toInt(),
            heightPx = h.toInt(),
            format = frame.format,
            usedBytes = used,
            maxBytes = maxBytes,
            fits = used <= maxBytes,
            framesReduced = maxStride > 1,
            sizeReduced = true,
        )
    }

    /**
     * "Smooth first" fit, for pictures that look fine a little softer (photos, banners, recordings).
     * Keeps EVERY frame and shrinks the frame size uniformly, but never so that its long side is under [minLongSidePx].
     * Only when even that is not enough does it thin frames, at the minimum size. The result is a smoother
     * animation than [planAutoFit], which drops frames down to [DEFAULT_MIN_KEPT_FRAMES] before touching the size
     * (and a 24-frame GIF then shows 12 frames: it looks jerky).
     */
    fun planSmoothFit(
        screenWidthPx: Int,
        screenHeightPx: Int,
        frameCount: Int,
        frame: FrameSize,
        usagePercent: Int = DEFAULT_USAGE_PERCENT,
        minLongSidePx: Int = SMOOTH_MIN_LONG_SIDE,
    ): AutoFitPlan {
        require(frameCount >= 1) { "frameCount must be >= 1, was $frameCount" }
        require(minLongSidePx >= 1) { "minLongSidePx must be >= 1, was $minLongSidePx" }
        val minScale = minOf(1.0, minLongSidePx.toDouble() / maxOf(frame.widthPx, frame.heightPx))
        val maxBytes = maxBudgetBytes(screenWidthPx, screenHeightPx, usagePercent)
        val bpp = frame.format.bytesPerPixel
        val originalPixels = frame.widthPx.toLong() * frame.heightPx

        fun result(stride: Int, w: Long, h: Long): AutoFitPlan {
            val kept = keptFrameCount(frameCount, stride)
            val used = kept * w * h * bpp
            return AutoFitPlan(
                frameStride = stride,
                keptFrames = kept,
                widthPx = w.toInt(),
                heightPx = h.toInt(),
                format = frame.format,
                usedBytes = used,
                maxBytes = maxBytes,
                fits = used <= maxBytes,
                framesReduced = stride > 1,
                sizeReduced = w.toInt() != frame.widthPx || h.toInt() != frame.heightPx,
            )
        }

        // Everything fits as it is.
        if (frameCount * frame.bytes <= maxBytes) return result(1, frame.widthPx.toLong(), frame.heightPx.toLong())

        // Step 1: every frame, smaller frames (not below minScale).
        val pixelsForAll = maxBytes / frameCount / bpp
        if (pixelsForAll >= 1 && sqrt(pixelsForAll.toDouble() / originalPixels) >= minScale) {
            val (w, h) = shrinkTo(frame, pixelsForAll)
            return result(1, w, h)
        }

        // Step 2: frames at the minimum size, then thin frames until they fit.
        val (minW, minH) = shrinkTo(frame, (originalPixels * minScale * minScale).toLong().coerceAtLeast(1))
        for (stride in 1..frameCount) {
            if (keptFrameCount(frameCount, stride) * minW * minH * bpp <= maxBytes) return result(stride, minW, minH)
        }
        // Not even one frame fits at the minimum size (degenerate screen): the literal fit, frames down to one.
        return planAutoFit(screenWidthPx, screenHeightPx, frameCount, frame, usagePercent, minKeptFrames = 1)
    }

    /** Largest w x h with the frame's aspect ratio (within one pixel) and at most [pixelsAllowed] pixels. */
    private fun shrinkTo(frame: FrameSize, pixelsAllowed: Long): Pair<Long, Long> {
        val originalPixels = frame.widthPx.toLong() * frame.heightPx
        if (pixelsAllowed >= originalPixels) return frame.widthPx.toLong() to frame.heightPx.toLong()
        val scale = sqrt(pixelsAllowed.toDouble() / originalPixels.toDouble())
        var w = maxOf(1L, (frame.widthPx * scale).toLong())
        var h = maxOf(1L, (frame.heightPx * scale).toLong())
        while (w * h > pixelsAllowed && (w > 1 || h > 1)) {
            val widthIsRelativelyLarger = w * frame.heightPx >= h * frame.widthPx
            if (widthIsRelativelyLarger) {
                if (w > 1) w-- else h--
            } else {
                if (h > 1) h-- else w--
            }
        }
        return w to h
    }
}
