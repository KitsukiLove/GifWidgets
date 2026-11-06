package app.gifwidgets.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRotateTest {
    private val minute = 60_000L

    @Test
    fun onlyWidgetsWithTwoOrMoreAnimationsRotate() {
        val one = WidgetSettings(autoRotateMinutes = 30, rotation = listOf("a"))
        val two = WidgetSettings(autoRotateMinutes = 30, rotation = listOf("a", "b"))
        val off = WidgetSettings(autoRotateMinutes = 0, rotation = listOf("a", "b"))
        assertNull(AutoRotate.entryOf(1, one))
        assertNull(AutoRotate.entryOf(3, off))
        assertEquals(AutoRotate.Entry(2, 30, 0L), AutoRotate.entryOf(2, two))
    }

    @Test
    fun dueWhenTheIntervalHasPassed() {
        val e = listOf(AutoRotate.Entry(1, 15, 1_000_000L), AutoRotate.Entry(2, 60, 1_000_000L))
        assertEquals(emptyList<Int>(), AutoRotate.due(e, 1_000_000L + 14 * minute))
        assertEquals(listOf(1), AutoRotate.due(e, 1_000_000L + 15 * minute))
        assertEquals(listOf(1, 2), AutoRotate.due(e, 1_000_000L + 61 * minute))
    }

    @Test
    fun nextWakeIsTheEarliestDueTimeButNotTooSoon() {
        val now = 10_000_000L
        val e = listOf(AutoRotate.Entry(1, 60, now), AutoRotate.Entry(2, 30, now))
        assertEquals(now + 30 * minute, AutoRotate.nextWake(e, now))
        // overdue: never sooner than the minimum gap
        val overdue = listOf(AutoRotate.Entry(1, 15, 0L))
        assertEquals(now + AutoRotate.MIN_GAP_MS, AutoRotate.nextWake(overdue, now))
        assertNull(AutoRotate.nextWake(emptyList(), now))
    }

    @Test
    fun choicesStartWithOffAndNeverBeatTheMinimumGap() {
        assertEquals(0, AutoRotate.CHOICES_MIN.first())
        assertTrue(AutoRotate.CHOICES_MIN.filter { it > 0 }.all { it * minute >= AutoRotate.MIN_GAP_MS })
        assertFalse(WidgetSettings(autoRotateMinutes = 15).autoRotates)
    }
}
