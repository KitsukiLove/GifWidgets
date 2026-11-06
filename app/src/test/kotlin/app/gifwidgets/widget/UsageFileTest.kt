package app.gifwidgets.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageFileTest {
    @Test
    fun parsesTheNewAndTheOldFormat() {
        assertEquals(UsageFile.Value(48, 53), UsageFile.parse("48,53"))
        assertEquals(UsageFile.Value(80, null), UsageFile.parse("80"))
        assertEquals(UsageFile.Value(80, null), UsageFile.parse(" 80\n"))
    }

    @Test
    fun garbageIsNotAValue() {
        assertNull(UsageFile.parse(null))
        assertNull(UsageFile.parse(""))
        assertNull(UsageFile.parse("abc,3"))
    }

    @Test
    fun formatThenParseRoundTrips() {
        assertEquals(UsageFile.Value(40, 26), UsageFile.parse(UsageFile.format(40, 26)))
    }

    @Test
    fun aLevelForcedUnderAnotherShareIsForgotten() {
        val forced = UsageFile.Value(40, 26)
        assertEquals(40, UsageFile.levelFor(forced, 26, 80))
        assertEquals(80, UsageFile.levelFor(forced, 53, 80)) // fewer widgets now: start again from the default
        assertEquals(80, UsageFile.levelFor(null, 53, 80))
        assertEquals(48, UsageFile.levelFor(UsageFile.Value(48, null), 100, 80)) // old file: valid under any share
    }
}
