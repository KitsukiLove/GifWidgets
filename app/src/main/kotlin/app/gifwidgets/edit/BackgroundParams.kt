package app.gifwidgets.edit

/** How the background of every frame is treated. Pixels are ARGB ints (0xAARRGGBB), not premultiplied. */
enum class BackgroundMode {
    /** Keep the GIF as it is (its own transparency, if any, is respected). */
    KEEP,

    /** "Colour key": remove pixels close to [BackgroundParams.keyColor]. */
    COLOR_KEY,

    /** "Black to transparent": alpha = max(R,G,B), colour recovered by dividing by it. */
    BLACK_TO_ALPHA,
}

data class BackgroundParams(
    val mode: BackgroundMode = BackgroundMode.KEEP,
    /** Colour to remove in COLOR_KEY mode. Default black. */
    val keyColor: Int = 0xFF000000.toInt(),
    /** 0..100. 0 = exact match only, 100 = everything. */
    val tolerance: Int = 20,
    /** true = only regions connected to the image border; false = everywhere. */
    val contiguous: Boolean = true,
    /** 0..3 px of feathering on the kept side of the edge. */
    val edgeSoften: Int = 0,
    /** Replace the colour of edge pixels with the colour of the pixels just inside. */
    val defringe: Boolean = false,
    /** BLACK_TO_ALPHA: values at or below this brightness become fully transparent (0..254). */
    val blackThreshold: Int = 0,
    /** BLACK_TO_ALPHA: alpha gain in percent (100 = none). */
    val blackGain: Int = 100,
) {
    fun sanitized() = copy(
        tolerance = tolerance.coerceIn(0, 100),
        edgeSoften = edgeSoften.coerceIn(0, 3),
        blackThreshold = blackThreshold.coerceIn(0, 254),
        blackGain = blackGain.coerceIn(10, 1000),
    )
}
