package app.gifwidgets.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactAndBackupSettingsTest {
    @Test
    fun newFieldsRoundTrip() {
        val s = AppSettings(lastBackupMs = 111L, backupSnoozeUntilMs = 222L, compactList = true)
        assertEquals(s, AppSettings.fromMap(s.toMap()))
    }

    @Test
    fun olderFilesGetTheDefaults() {
        val s = AppSettings.fromMap(mapOf("master" to true))
        assertEquals(0L, s.lastBackupMs)
        assertEquals(0L, s.backupSnoozeUntilMs)
        assertFalse(s.compactList)
        assertTrue(s.masterEnabled)
    }
}
