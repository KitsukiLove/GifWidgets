package app.gifwidgets.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSettingsTest {
    @Test
    fun roundTripKeepsEverything() {
        val s = WidgetSettings(
            animationId = "id1", animating = false, speedPercent = 150, scale = ScaleMode.FILL, zoomPercent = 140,
            tap = TapAction.NEXT_ANIMATION, rotation = listOf("a", "b"), nightOverride = true, nightOverrideKey = "k",
            playingOnce = true, showButton = true, highQuality = true,
        )
        assertEquals(s, WidgetSettings.fromMap(s.toMap()))
    }

    @Test
    fun playOnceEndTimeSurvivesSaving() {
        val s = WidgetSettings(animationId = "id1", playingOnce = true, playUntilMs = 1_700_000_000_000L)
        assertEquals(s, WidgetSettings.fromMap(s.toMap()))
        // Settings saved before the end time existed read as "not playing".
        assertEquals(0L, WidgetSettings.fromMap(mapOf("playingOnce" to true)).playUntilMs)
    }

    @Test
    fun settingsFromBeforeVersion2GetAutoSizeAndNoButton() {
        val old = mapOf<String, Any?>("scale" to "FIT", "showButton" to true, "tap" to "TOGGLE", "speed" to 100)
        val s = WidgetSettings.fromMap(old)
        assertEquals(ScaleMode.AUTO, s.scale)
        assertFalse(s.showButton)
        // a version-2 file keeps the user's explicit choice
        val v2 = WidgetSettings.fromMap(mapOf("v" to 2, "scale" to "FIT", "showButton" to true))
        assertEquals(ScaleMode.FIT, v2.scale)
        assertTrue(v2.showButton)
    }

    @Test
    fun overrideOnlyCountsInItsOwnNightWindow() {
        val s = WidgetSettings(nightOverride = true, nightOverrideKey = "night1")
        assertEquals(true, s.nightOverrideFor("night1"))
        assertNull(s.nightOverrideFor("night2"))
        assertNull(s.nightOverrideFor(null))
        assertNull(WidgetSettings().nightOverrideFor("night1"))
    }

    @Test
    fun zoomIsClamped() {
        assertEquals(300, WidgetSettings(zoomPercent = 9999).sanitized().zoomPercent)
        assertEquals(50, WidgetSettings.fromMap(mapOf("zoom" to 1)).zoomPercent)
    }

    @Test
    fun autoFillsWhenShapesMatchAndFitsWhenTheyDoNot() {
        // square GIF in a square 2x2 widget: fills
        var r = ScaleMath.destRect(ScaleMode.AUTO, 100, 100, 0f, 0f, 110f, 110f, 1f)
        assertEquals(110f, r[2] - r[0], 0.01f)
        // square GIF in a 2:1 widget: filling would cut 50 %, so it is enlarged only until 35 % is cut
        r = ScaleMath.destRect(ScaleMode.AUTO, 100, 100, 0f, 0f, 220f, 110f, 1f)
        assertEquals(110f / 0.65f, r[3] - r[1], 0.05f)
        assertEquals(r[3] - r[1], r[2] - r[0], 0.01f) // aspect ratio kept
        // 4:3 GIF in a square widget cuts 25 %: fills
        r = ScaleMath.destRect(ScaleMode.AUTO, 120, 90, 0f, 0f, 100f, 100f, 1f)
        assertEquals(100f, r[3] - r[1], 0.01f)
        // banner 3:1 in a 2:1 widget cuts 33 %: fills
        r = ScaleMath.destRect(ScaleMode.AUTO, 300, 100, 0f, 0f, 200f, 100f, 1f)
        assertEquals(100f, r[3] - r[1], 0.01f)
        // banner 3:1 in a square widget would cut 67 %: shows 65 % of its width, not a thin strip
        r = ScaleMath.destRect(ScaleMode.AUTO, 300, 100, 0f, 0f, 100f, 100f, 1f)
        assertEquals(100f / 0.65f, r[2] - r[0], 0.05f)
    }

    @Test
    fun onlySmallGifsGetCrispPixelsByDefault() {
        assertTrue(WidgetSettings.forAnimation("a", 128, 128, globalSharp = true).sharp)
        assertFalse(WidgetSettings.forAnimation("a", 480, 270, globalSharp = true).sharp)
        // the global setting is the master switch
        assertFalse(WidgetSettings.forAnimation("a", 128, 128, globalSharp = false).sharp)
    }

    @Test
    fun zoomScalesAroundTheCenter() {
        val r = ScaleMath.destRect(ScaleMode.FIT, 100, 100, 0f, 0f, 100f, 100f, 1f, zoomPercent = 200)
        assertEquals(200f, r[2] - r[0], 0.01f)
        assertEquals(-50f, r[0], 0.01f)
        val small = ScaleMath.destRect(ScaleMode.FIT, 100, 100, 0f, 0f, 100f, 100f, 1f, zoomPercent = 50)
        assertEquals(25f, small[0], 0.01f)
    }

    @Test
    fun drawnScaleTellsHowMuchTheImageIsEnlarged() {
        // 480x270 GIF, whole picture in a 1000x420 widget: limited by the height, 420/270 = 1.56x
        assertEquals(1.555f, ScaleMath.drawnScale(ScaleMode.FIT, 480, 270, 1000, 420), 0.01f)
        // bigger than the widget: shrunk (< 1)
        assertTrue(ScaleMath.drawnScale(ScaleMode.FIT, 960, 540, 400, 200) < 1f)
    }
}
