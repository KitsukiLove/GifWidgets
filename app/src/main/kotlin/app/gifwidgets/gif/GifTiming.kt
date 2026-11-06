package app.gifwidgets.gif

/** Pure helpers for GIF frame delays (GIF stores delays in centiseconds). */
object GifTiming {
    /** Delays of 0 or 1 cs are treated as 10 cs, like browsers do. Configurable constant. */
    const val DEFAULT_DELAY_CS = 10
    const val MIN_FPS = 1
    const val MAX_FPS = 60
    const val FALLBACK_FPS = 12

    fun normalizeDelayCs(delayCs: Int): Int = if (delayCs <= 1) DEFAULT_DELAY_CS else delayCs

    /** Average frames per second of an animation, rounded, clamped to 1..60. */
    fun averageFps(delaysCs: IntArray): Int {
        if (delaysCs.isEmpty()) return FALLBACK_FPS
        var sum = 0L
        for (d in delaysCs) sum += d
        if (sum <= 0) return FALLBACK_FPS
        val n = delaysCs.size.toLong()
        val fps = (100L * n + sum / 2) / sum // rounded integer division
        return fps.toInt().coerceIn(MIN_FPS, MAX_FPS)
    }
}
