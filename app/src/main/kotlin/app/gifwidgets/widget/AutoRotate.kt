package app.gifwidgets.widget

/**
 * Automatic rotation: which widgets are due for their next animation, and when the single alarm must wake us.
 * Pure (no Android), so it is easy to test. The alarm is inexact and non-waking; every wake-up recomputes
 * from the clock, so a late alarm only delays a change.
 */
object AutoRotate {
    /** Choices offered on the configuration screen, in minutes (0 = off). */
    val CHOICES_MIN = listOf(0, 15, 30, 60, 180, 360, 1440)

    /** Never wake more often than this, whatever the choices say. */
    const val MIN_GAP_MS = 5 * 60_000L

    data class Entry(val id: Int, val minutes: Int, val lastMs: Long)

    fun entryOf(id: Int, s: WidgetSettings): Entry? = if (s.autoRotates) Entry(id, s.autoRotateMinutes, s.rotatedAtMs) else null

    fun dueAt(e: Entry): Long = e.lastMs + e.minutes * 60_000L

    /** Ids whose time has come. A widget never rotated yet (lastMs = 0) is due at once. */
    fun due(entries: List<Entry>, nowMs: Long): List<Int> = entries.filter { dueAt(it) <= nowMs }.map { it.id }

    /**
     * When to ask the system to wake us next: the earliest due time, but not before [MIN_GAP_MS] from now.
     * Null when no widget rotates by itself (the alarm is cancelled).
     */
    fun nextWake(entries: List<Entry>, nowMs: Long): Long? {
        val earliest = entries.minOfOrNull { dueAt(it) } ?: return null
        return maxOf(earliest, nowMs + MIN_GAP_MS)
    }
}
