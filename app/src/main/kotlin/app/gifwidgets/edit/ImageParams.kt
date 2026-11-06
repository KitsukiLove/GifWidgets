package app.gifwidgets.edit

/** Rectangle in pixels; [right] and [bottom] are exclusive. */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class ImageParams(
    /** Crop to the rectangle that contains every non-transparent pixel of all kept frames. */
    val autoCrop: Boolean = false,
    /** Manual crop in source pixels (ignored when [autoCrop] is on). */
    val manualCrop: CropRect? = null,
    /** Maximum length of the longest side, in px. 0 = no limit. Never enlarges. */
    val maxSide: Int = 0,
    /** true = nearest neighbour (crisp pixel art), false = smooth area average. */
    val sharpPixels: Boolean = true,
)
