package app.gifwidgets.control

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlLogicTest {
    private fun inp(
        master: Boolean = true, manual: Boolean = true, night: Boolean = false, override: Boolean? = null,
        saver: Boolean = false, pauseOnSaver: Boolean = true, once: Boolean = false, playing: Boolean = false,
    ) = ControlInputs(master, manual, night, override, saver, pauseOnSaver, once, playing)

    @Test
    fun stateTable() {
        // master, manual, night, override, saver -> effective
        assertTrue(ControlLogic.effective(inp()))
        assertFalse(ControlLogic.effective(inp(master = false)))
        assertFalse(ControlLogic.effective(inp(manual = false)))
        assertFalse(ControlLogic.effective(inp(night = true)))                      // night: still by default
        assertFalse(ControlLogic.effective(inp(night = true, manual = true)))       // manual does not matter at night
        assertTrue(ControlLogic.effective(inp(night = true, override = true)))      // user turned it on at night
        assertFalse(ControlLogic.effective(inp(night = true, override = false)))
        assertTrue(ControlLogic.effective(inp(night = false, manual = true, override = false))) // stale override ignored by day
        assertFalse(ControlLogic.effective(inp(saver = true)))
        assertTrue(ControlLogic.effective(inp(saver = true, pauseOnSaver = false)))
        assertFalse(ControlLogic.effective(inp(master = false, night = true, override = true)))
        assertFalse(ControlLogic.effective(inp(night = true, override = true, saver = true)))
    }

    @Test
    fun playOnceModeMovesOnlyWhilePlaying() {
        assertFalse(ControlLogic.effective(inp(once = true)))
        assertTrue(ControlLogic.effective(inp(once = true, playing = true)))
        assertFalse(ControlLogic.effective(inp(once = true, playing = true, master = false)))
    }

    @Test
    fun toggleByDayFlipsTheManualSwitch() {
        val r = ControlLogic.toggle(inp(manual = true))
        assertEquals(Block.NONE, r.block)
        assertFalse(r.manualSwitch)
        assertNull(r.nightOverride)
        assertTrue(ControlLogic.toggle(inp(manual = false)).manualSwitch)
    }

    @Test
    fun toggleAtNightSetsTheOverrideNotTheManualSwitch() {
        val on = ControlLogic.toggle(inp(night = true, manual = true))
        assertEquals(true, on.nightOverride)
        assertTrue(on.manualSwitch) // untouched
        val off = ControlLogic.toggle(inp(night = true, override = true))
        assertEquals(false, off.nightOverride)
    }

    @Test
    fun toggleIsBlockedByMasterAndBatterySaver() {
        val m = ControlLogic.toggle(inp(master = false, manual = true))
        assertEquals(Block.MASTER_OFF, m.block)
        assertTrue(m.manualSwitch)
        val b = ControlLogic.toggle(inp(saver = true, manual = true))
        assertEquals(Block.BATTERY_SAVER, b.block)
        assertTrue(b.manualSwitch)
        // master wins when both apply
        assertEquals(Block.MASTER_OFF, ControlLogic.blockedBy(inp(master = false, saver = true)))
    }

    @Test
    fun rotationWrapsAndSkipsInvalid() {
        val l = listOf("a", "b", "c")
        assertEquals("b", Rotation.next(l, "a") { true })
        assertEquals("a", Rotation.next(l, "c") { true })
        assertEquals("c", Rotation.next(l, "a") { it != "b" })
        assertNull(Rotation.next(l, "a") { it == "a" })
        assertNull(Rotation.next(emptyList(), "a") { true })
        assertEquals("a", Rotation.next(l, "zzz") { true }) // unknown current: start at the beginning
    }

    private fun t(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, d, h, m)

    @Test
    fun nightWindowAcrossMidnight() {
        val s = NightSchedule(true, 23 * 60, 7 * 60)
        assertTrue(s.isNight(t(14, 23, 0)))
        assertTrue(s.isNight(t(15, 0, 30)))
        assertTrue(s.isNight(t(15, 6, 59)))
        assertFalse(s.isNight(t(15, 7, 0)))
        assertFalse(s.isNight(t(14, 12, 0)))
        assertFalse(s.isNight(t(14, 22, 59)))
    }

    @Test
    fun nightWindowSameDayAndDisabled() {
        val s = NightSchedule(true, 13 * 60, 15 * 60)
        assertTrue(s.isNight(t(14, 13, 0)))
        assertFalse(s.isNight(t(14, 15, 0)))
        assertFalse(NightSchedule(false, 23 * 60, 7 * 60).isNight(t(14, 23, 30)))
        assertFalse(NightSchedule(true, 600, 600).isNight(t(14, 10, 0))) // empty window
        assertNull(NightSchedule(true, 600, 600).nextBoundary(t(14, 10, 0)))
    }

    @Test
    fun windowKeyIsStableThroughTheNightAndChangesNextNight() {
        val s = NightSchedule(true, 23 * 60, 7 * 60)
        val a = s.windowKey(t(14, 23, 30))
        val b = s.windowKey(t(15, 6, 0))
        assertEquals(a, b) // same night, either side of midnight
        assertNull(s.windowKey(t(15, 12, 0)))
        assertNotEquals(a, s.windowKey(t(15, 23, 30)))
        // editing the schedule changes the key, so an old override stops applying
        assertNotEquals(a, NightSchedule(true, 22 * 60, 7 * 60).windowKey(t(14, 23, 30)))
    }

    @Test
    fun nextBoundary() {
        val s = NightSchedule(true, 23 * 60, 7 * 60)
        assertEquals(t(14, 23, 0), s.nextBoundary(t(14, 12, 0)))
        assertEquals(t(15, 7, 0), s.nextBoundary(t(14, 23, 0))) // strictly after
        assertEquals(t(15, 7, 0), s.nextBoundary(t(15, 2, 0)))
        assertEquals(t(15, 23, 0), s.nextBoundary(t(15, 7, 0)))
        // month end
        assertEquals(LocalDateTime.of(2026, 11, 1, 7, 0), s.nextBoundary(LocalDateTime.of(2026, 10, 31, 23, 30)))
    }

    @Test
    fun appSettingsRoundTrip() {
        val s = AppSettings(false, true, 22 * 60 + 30, 6 * 60, false, 20, false, false, ThemeMode.DARK)
        assertEquals(s, AppSettings.fromMap(s.toMap()))
        assertEquals(AppSettings(), AppSettings.fromMap(emptyMap()))
        assertEquals(1439, AppSettings.fromMap(mapOf("nightStart" to 99999)).nightStartMin)
        assertEquals(25, AppSettings.fromMap(mapOf("defaultFps" to 99)).defaultFps)
        assertEquals(5, AppSettings.fromMap(mapOf("defaultFps" to 0)).defaultFps)
        assertEquals(ThemeMode.SYSTEM, AppSettings.fromMap(mapOf("theme" to "NOPE")).theme)
    }
}
