package app.gifwidgets.memory

/*
 * The launcher keeps the bitmaps of EVERY animated widget in memory at once. Each widget may use up to 80 % of
 * the platform cap (6 screens), which is fine for one or two widgets but not for ten: the launcher becomes slow
 * (long presses, drags and the widget menu stop answering). So the animated widgets share one total; with few
 * widgets nothing changes. Pure Kotlin.
 *
 * VERIFY: TOTAL_SCREENS is a judgement, not a measured value.
 */
object WidgetShare {
    /** All animated widgets together may hold about this many screens' worth of bitmaps (the cap of ONE widget is 6). */
    const val TOTAL_SCREENS = 16
    private const val CAP_SCREENS = 6
    const val MIN_PERCENT = 12

    /** The share of the platform cap, in percent, one of [animatedWidgets] flipping widgets may use. */
    fun usagePercent(animatedWidgets: Int): Int {
        val n = animatedWidgets.coerceAtLeast(1)
        return (TOTAL_SCREENS * 100 / (n * CAP_SCREENS)).coerceIn(MIN_PERCENT, 100)
    }
}
