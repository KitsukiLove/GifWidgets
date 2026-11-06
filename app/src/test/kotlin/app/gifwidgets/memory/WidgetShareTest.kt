package app.gifwidgets.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetShareTest {
    @Test
    fun oneOrTwoWidgetsKeepTheirFullShare() {
        assertEquals(100, WidgetShare.usagePercent(1))
        assertEquals(100, WidgetShare.usagePercent(2))
        assertEquals(100, WidgetShare.usagePercent(0))
    }

    @Test
    fun theShareShrinksAsWidgetsAreAdded() {
        assertEquals(88, WidgetShare.usagePercent(3))
        assertEquals(53, WidgetShare.usagePercent(5))
        assertEquals(26, WidgetShare.usagePercent(10))
    }

    @Test
    fun theShareNeverGoesBelowTheFloor() {
        assertEquals(WidgetShare.MIN_PERCENT, WidgetShare.usagePercent(100))
        assertTrue(WidgetShare.usagePercent(30) >= WidgetShare.MIN_PERCENT)
    }
}
