package app.gifwidgets.control

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneryAndSoundSettingsTest {
    @Test
    fun defaultsAreAutoSeasonMusicOffSoundsOnAnimatedBackground() {
        val s = AppSettings()
        assertEquals(SeasonChoice.AUTO, s.season)
        assertEquals(false, s.musicOn)
        assertEquals(true, s.soundOn)
        assertEquals(true, s.animatedBackground)
    }

    @Test
    fun newFieldsRoundTrip() {
        val s = AppSettings(season = SeasonChoice.NAVIDAD, musicOn = true, soundOn = false, animatedBackground = false)
        assertEquals(s, AppSettings.fromMap(s.toMap()))
    }

    @Test
    fun oldFilesWithoutTheNewFieldsStillLoadWithDefaults() {
        val old = AppSettings().toMap().filterKeys { it !in setOf("season", "music", "sound", "animBg") }
        assertEquals(AppSettings(), AppSettings.fromMap(old))
    }

    @Test
    fun garbageFallsBackToDefaults() {
        val s = AppSettings.fromMap(mapOf("season" to "NOPE", "music" to "yes", "sound" to 3, "animBg" to null))
        assertEquals(AppSettings(), s)
    }
}
