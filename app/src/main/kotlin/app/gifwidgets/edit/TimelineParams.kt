package app.gifwidgets.edit

data class TimelineParams(
    /** First source frame kept (0-based). */
    val trimStart: Int = 0,
    /** Last source frame kept; -1 = the last one. */
    val trimEnd: Int = -1,
    /** Source frame indices removed. */
    val deleted: Set<Int> = emptySet(),
    val reverse: Boolean = false,
    /** Forward then backward (end frames are not repeated). */
    val pingPong: Boolean = false,
    /** Keep one frame of every N (the kept frame lasts as long as the whole group). */
    val keepOneOfN: Int = 1,
    /** Output frames per second (constant). The widget's ViewFlipper has a single interval. */
    val fps: Int = DEFAULT_FPS,
    /** Output frame index shown by the still widget. */
    val restFrame: Int = 0,
) {
    companion object {
        const val DEFAULT_FPS = 12
        const val MIN_FPS = 1
        const val MAX_FPS = 60
    }
}
