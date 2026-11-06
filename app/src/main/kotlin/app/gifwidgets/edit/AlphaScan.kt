package app.gifwidgets.edit

/** Does a picture really have see-through pixels? (Pure Kotlin, so it is unit tested.) */
object AlphaScan {
    /** True when at least one ARGB pixel is not fully opaque. */
    fun anyTransparent(pixels: IntArray): Boolean {
        for (px in pixels) if ((px ushr 24) != 0xFF) return true
        return false
    }
}
