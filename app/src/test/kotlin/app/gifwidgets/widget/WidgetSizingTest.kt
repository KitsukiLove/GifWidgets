package app.gifwidgets.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSizingTest {
    // A 4x4 widget as the launcher reports it: portrait 250 x 270, landscape 400 x 150 (bigger area!).
    private val sizes = listOf(250f to 270f, 400f to 150f)

    @Test
    fun portraitPhoneUsesThePortraitShapeEvenWhenLandscapeHasMoreArea() {
        assertEquals(250f to 270f, WidgetSizing.pick(sizes, 250, 400, 150, 270, landscape = false))
    }

    @Test
    fun landscapePhoneUsesTheWideShape() {
        assertEquals(400f to 150f, WidgetSizing.pick(sizes, 250, 400, 150, 270, landscape = true))
    }

    @Test
    fun withoutSizeListUsesMinMaxOptions() {
        assertEquals(250f to 270f, WidgetSizing.pick(emptyList(), 250, 400, 150, 270, false))
        assertEquals(400f to 150f, WidgetSizing.pick(emptyList(), 250, 400, 150, 270, true))
    }

    @Test
    fun partialOrMissingValues() {
        assertEquals(110f to 110f, WidgetSizing.pick(emptyList(), 110, 0, 0, 110, false))
        assertNull(WidgetSizing.pick(emptyList(), 0, 0, 0, 0, false))
        assertEquals(100f to 50f, WidgetSizing.pick(listOf(0f to 0f, 100f to 50f), 0, 0, 0, 0, true))
    }
}
