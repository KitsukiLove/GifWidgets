package app.gifwidgets.control

import org.junit.Assert.assertEquals
import org.junit.Test

class TipsSettingsTest {
    @Test
    fun theWelcomeIsPendingAndTheTipsAreOnByDefault() {
        val s = AppSettings()
        assertEquals(false, s.welcomeSeen)
        assertEquals(true, s.tipsOn)
    }

    @Test
    fun theTipFlagsRoundTrip() {
        val s = AppSettings(welcomeSeen = true, tipsOn = false)
        assertEquals(s, AppSettings.fromMap(s.toMap()))
    }

    @Test
    fun oldFilesWithoutTheTipFlagsShowTheWelcomeOnceAndKeepTheTips() {
        val old = AppSettings().toMap().filterKeys { it !in setOf("welcomeSeen", "tips") }
        val loaded = AppSettings.fromMap(old)
        assertEquals(false, loaded.welcomeSeen)
        assertEquals(true, loaded.tipsOn)
    }

    @Test
    fun garbageFallsBackToDefaults() {
        val s = AppSettings.fromMap(mapOf("welcomeSeen" to "yes", "tips" to 7))
        assertEquals(AppSettings(), s)
    }
}
