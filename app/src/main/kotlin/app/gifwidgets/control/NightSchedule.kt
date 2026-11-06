package app.gifwidgets.control

import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The nightly pause window, in minutes after local midnight. It may cross midnight (23:00 to 07:00).
 * Pure: the caller passes the local date-time, so it is easy to test. start == end means "no window".
 */
data class NightSchedule(
    val enabled: Boolean = false,
    val startMin: Int = 23 * 60,
    val endMin: Int = 7 * 60,
) {
    private val start get() = startMin.coerceIn(0, 1439)
    private val end get() = endMin.coerceIn(0, 1439)
    private val hasWindow get() = enabled && start != end

    fun isNight(now: LocalDateTime): Boolean {
        if (!hasWindow) return false
        val m = now.hour * 60 + now.minute
        return if (start < end) m in start until end else m >= start || m < end
    }

    /**
     * Identifies the current night window (null when it is not night). The widget's "night override" is only
     * valid while this key is unchanged, so it disappears by itself at the end of the night and as soon as the
     * schedule is edited (the key contains the start and end times).
     */
    fun windowKey(now: LocalDateTime): String? {
        if (!isNight(now)) return null
        val m = now.hour * 60 + now.minute
        val startDate = if (start < end || m >= start) now.toLocalDate() else now.toLocalDate().minusDays(1)
        val startAt = startDate.atTime(start / 60, start % 60)
        return "${startAt.toEpochSecond(ZoneOffset.UTC)}|$start|$end"
    }

    /** The next moment (strictly after [now]) when night starts or ends; null when there is no window. */
    fun nextBoundary(now: LocalDateTime): LocalDateTime? {
        if (!hasWindow) return null
        val today = now.toLocalDate()
        val candidates = listOf(
            today.atTime(start / 60, start % 60),
            today.atTime(end / 60, end % 60),
            today.plusDays(1).atTime(start / 60, start % 60),
            today.plusDays(1).atTime(end / 60, end % 60),
        )
        return candidates.filter { it.isAfter(now) }.minOrNull()
    }
}
