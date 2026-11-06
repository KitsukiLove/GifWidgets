package app.gifwidgets.library

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationNamesSuggestTest {
    @Test
    fun dropsExtensionAndTidiesSeparators() {
        assertEquals("Happy dance", AnimationNames.suggest("happy_dance.gif", "GIF"))
        assertEquals("Kixki walk cycle", AnimationNames.suggest("kixki-walk-cycle.GIF", "GIF"))
    }

    @Test
    fun dropsLongNumberTails() {
        assertEquals("Giphy", AnimationNames.suggest("giphy_1699999999123.gif", "GIF"))
        // short numbers belong to the name
        assertEquals("Loop 2", AnimationNames.suggest("loop_2.gif", "GIF"))
    }

    @Test
    fun fallsBackWhenNothingUsable() {
        assertEquals("GIF", AnimationNames.suggest(null, "GIF"))
        assertEquals("GIF", AnimationNames.suggest("   .gif", "GIF"))
        assertEquals("GIF", AnimationNames.suggest("1234567890.gif", "GIF"))
    }

    @Test
    fun neverLongerThanTheLimit() {
        val long = "a".repeat(200) + ".gif"
        assertEquals(AnimationNames.MAX_LENGTH, AnimationNames.suggest(long, "GIF").length)
    }
}
