package app.gifwidgets.edit

/** The memory meter's maths and "Ajustar automáticamente" (frames first, then size). Pure. */
object AutoFit {
    /** Never thin the animation below this many frames while shrinking the frame size is still possible. */
    const val MIN_FRAMES = 12

    /** Size of one output frame as far as it is known now (manual crop or whole image; auto-crop is unknown here). */
    fun frameSize(srcW: Int, srcH: Int, image: ImageParams): Pair<Int, Int> {
        var w = srcW
        var h = srcH
        val crop = if (image.autoCrop) null else image.manualCrop
        if (crop != null) {
            val r = ImageMath.clampRect(crop, srcW, srcH)
            w = r.width
            h = r.height
        }
        return ImageMath.scaledSize(w, h, image.maxSide)
    }

    fun bytesPerPixel(params: EditorParams, sourceHasAlpha: Boolean): Int {
        val hasAlpha = sourceHasAlpha || params.background.mode != BackgroundMode.KEEP
        return if (params.saveMemory && !hasAlpha) 2 else 4
    }

    fun usedBytes(delaysCs: IntArray, srcW: Int, srcH: Int, params: EditorParams, bytesPerPixel: Int): Long {
        val plan = Timeline.plan(delaysCs, params.timeline)
        val (w, h) = frameSize(srcW, srcH, params.image)
        return plan.frameCount.toLong() * w * h * bytesPerPixel
    }

    /** Returns params whose estimated widget memory is within [maxBytes] (lower fps first, then smaller size). */
    fun fit(
        params: EditorParams,
        delaysCs: IntArray,
        srcW: Int,
        srcH: Int,
        bytesPerPixel: Int,
        maxBytes: Long,
    ): EditorParams {
        var p = params
        // Step 1: fewer frames (lower fps keeps the duration), but not below MIN_FRAMES.
        while (usedBytes(delaysCs, srcW, srcH, p, bytesPerPixel) > maxBytes) {
            val plan = Timeline.plan(delaysCs, p.timeline)
            if (p.timeline.fps <= TimelineParams.MIN_FPS || plan.frameCount <= MIN_FRAMES) break
            p = p.copy(timeline = p.timeline.copy(fps = p.timeline.fps - 1))
        }
        // Step 2: smaller frames, 5 % at a time.
        var side = frameSize(srcW, srcH, p.image).let { maxOf(it.first, it.second) }
        while (usedBytes(delaysCs, srcW, srcH, p, bytesPerPixel) > maxBytes && side > 1) {
            side = maxOf(1, minOf(side - 1, (side * 0.95).toInt()))
            p = p.copy(image = p.image.copy(maxSide = side))
        }
        return p
    }
}
