package app.gifwidgets.control

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignStyleTest {
    @Test
    fun styleDefaultsToPixelAndRoundTrips() {
        assertEquals(DesignStyle.PIXEL, AppSettings().style)
        val xp = AppSettings(style = DesignStyle.XP, theme = ThemeMode.DARK)
        assertEquals(xp, AppSettings.fromMap(xp.toMap()))
        assertEquals(DesignStyle.XP, AppSettings.fromMap(xp.toMap()).style)
    }

    @Test
    fun unknownOrMissingStyleFallsBackToPixel() {
        assertEquals(DesignStyle.PIXEL, AppSettings.fromMap(mapOf("style" to "NOPE")).style)
        assertEquals(DesignStyle.PIXEL, AppSettings.fromMap(emptyMap()).style)
        assertEquals(DesignStyle.PIXEL, AppSettings.fromMap(mapOf("style" to 7)).style)
    }

    @Test
    fun oldSettingsFilesWithoutStyleStillLoad() {
        val old = AppSettings().toMap().filterKeys { it != "style" }
        assertEquals(AppSettings(), AppSettings.fromMap(old))
    }
}
