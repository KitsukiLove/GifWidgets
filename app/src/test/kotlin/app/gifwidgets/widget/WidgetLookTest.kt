package app.gifwidgets.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLookTest {
    private val styled = WidgetSettings(
        animationId = "other", scale = ScaleMode.FILL, zoomPercent = 150, paddingDp = 8, cornerDp = 20, borderDp = 3,
        borderColor = 0xFFFF0000.toInt(), bgColor = 0xFF00FF00.toInt(), bgOpacity = 60, brightness = 120, opacity = 80,
        sharp = false, showButton = true, buttonCorner = ButtonCorner.TOP_START, tap = TapAction.OPEN_APP,
        speedPercent = 200, rotation = listOf("x", "y"), autoRotateMinutes = 60,
    )

    @Test
    fun copyingALookKeepsTheAnimationTapAndRotation() {
        val mine = WidgetSettings(animationId = "mine", tap = TapAction.TOGGLE_ALL, speedPercent = 100, rotation = listOf("m1", "m2"))
        val r = mine.withLookFrom(styled)
        assertEquals("mine", r.animationId)
        assertEquals(TapAction.TOGGLE_ALL, r.tap)
        assertEquals(100, r.speedPercent)
        assertEquals(listOf("m1", "m2"), r.rotation)
        assertEquals(0, r.autoRotateMinutes)
        // and everything visual comes over
        assertEquals(styled.visualKey(), r.visualKey())
        assertEquals(true, r.showButton)
        assertEquals(ButtonCorner.TOP_START, r.buttonCorner)
    }

    @Test
    fun eachResetOnlyTouchesItsOwnBlock() {
        val framing = styled.resetFraming()
        assertEquals(ScaleMode.AUTO, framing.scale)
        assertEquals(100, framing.zoomPercent)
        assertEquals(0, framing.paddingDp)
        assertEquals(20, framing.cornerDp) // the frame block is untouched

        val frame = styled.resetFrame()
        assertEquals(0, frame.cornerDp)
        assertEquals(0, frame.borderDp)
        assertEquals(0, frame.bgOpacity)
        assertEquals(150, frame.zoomPercent)

        val colors = styled.resetColors()
        assertEquals(100, colors.brightness)
        assertEquals(100, colors.opacity)
        assertFalse(colors.sharp) // "Píxel nítido" is not part of the colors reset

        val button = styled.resetButton()
        assertFalse(button.showButton)
        assertEquals(ButtonCorner.BOTTOM_END, button.buttonCorner)

        assertEquals(100, styled.resetPlayback().speedPercent)
    }

    @Test
    fun autoRotationSurvivesSavingAndLoading() {
        val s = WidgetSettings(rotation = listOf("a", "b"), autoRotateMinutes = 180, rotatedAtMs = 12345L)
        val back = WidgetSettings.fromMap(s.toMap())
        assertEquals(s, back)
        assertTrue(back.autoRotates)
    }

    @Test
    fun oldFilesWithoutAutoRotationLoadAsOff() {
        val back = WidgetSettings.fromMap(mapOf("v" to 2, "speed" to 100))
        assertEquals(0, back.autoRotateMinutes)
        assertEquals(0L, back.rotatedAtMs)
    }

    @Test
    fun absurdIntervalsAreClamped() {
        assertEquals(7 * 24 * 60, WidgetSettings(autoRotateMinutes = 999_999).sanitized().autoRotateMinutes)
        assertEquals(0, WidgetSettings(autoRotateMinutes = -5).sanitized().autoRotateMinutes)
    }
}
