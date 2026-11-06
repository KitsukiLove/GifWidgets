package app.gifwidgets.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNudgeTest {
    private val day = BackupNudge.DAY_MS
    private val now = 100 * day

    @Test
    fun staysQuietWithFewAnimations() {
        assertFalse(BackupNudge.shouldShow(now, 0, 0, now - 90 * day, animationCount = 2))
    }

    @Test
    fun neverBackedUpUsesTheOldestAnimationAsBaseline() {
        assertFalse(BackupNudge.shouldShow(now, 0, 0, now - 10 * day, 5))
        assertTrue(BackupNudge.shouldShow(now, 0, 0, now - 31 * day, 5))
    }

    @Test
    fun aRecentBackupSilencesIt() {
        assertFalse(BackupNudge.shouldShow(now, now - 5 * day, 0, now - 90 * day, 5))
        assertTrue(BackupNudge.shouldShow(now, now - 30 * day, 0, now - 90 * day, 5))
    }

    @Test
    fun snoozeHidesItForAWeek() {
        val snooze = BackupNudge.snoozeUntil(now)
        assertFalse(BackupNudge.shouldShow(now + 6 * day, now - 60 * day, snooze, 1, 5))
        assertTrue(BackupNudge.shouldShow(now + 8 * day, now - 60 * day, snooze, 1, 5))
    }

    @Test
    fun daysSinceNeverGoesNegative() {
        assertEquals(0, BackupNudge.daysSince(now, now + day))
        assertEquals(3, BackupNudge.daysSince(now, now - 3 * day - 5))
    }

    @Test
    fun noBaselineMeansNoReminder() {
        assertFalse(BackupNudge.shouldShow(now, 0, 0, 0, 5))
    }
}
