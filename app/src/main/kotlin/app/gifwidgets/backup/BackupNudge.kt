package app.gifwidgets.backup

/**
 * The gentle "you have not backed up in a while" reminder of the library. Pure: the caller passes the clock.
 * It only shows when there is something worth saving, at most once a week after "Más tarde".
 */
object BackupNudge {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val AFTER_DAYS = 30
    const val SNOOZE_DAYS = 7
    const val MIN_ANIMATIONS = 3

    /**
     * [lastBackupMs] 0 = never exported: then the age of the oldest animation ([oldestCreatedMs]) is the baseline,
     * so a brand new library is not nagged about on day one.
     */
    fun shouldShow(nowMs: Long, lastBackupMs: Long, snoozeUntilMs: Long, oldestCreatedMs: Long, animationCount: Int): Boolean {
        if (animationCount < MIN_ANIMATIONS) return false
        if (nowMs < snoozeUntilMs) return false
        val baseline = if (lastBackupMs > 0L) lastBackupMs else oldestCreatedMs
        if (baseline <= 0L) return false
        return daysSince(nowMs, baseline) >= AFTER_DAYS
    }

    fun daysSince(nowMs: Long, thenMs: Long): Int = ((nowMs - thenMs).coerceAtLeast(0L) / DAY_MS).toInt()

    fun snoozeUntil(nowMs: Long): Long = nowMs + SNOOZE_DAYS * DAY_MS
}
