package app.gifwidgets.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonLogicTest {
    @Test
    fun calendarFollowsThePageRules() {
        assertEquals(Season.HALLOWEEN, SeasonLogic.fromDate(10, 27))
        assertEquals(Season.HALLOWEEN, SeasonLogic.fromDate(10, 10))
        assertEquals(Season.HALLOWEEN, SeasonLogic.fromDate(11, 2))
        assertEquals(Season.OTONO, SeasonLogic.fromDate(11, 3))
        assertEquals(Season.OTONO, SeasonLogic.fromDate(10, 9))
        assertEquals(Season.NAVIDAD, SeasonLogic.fromDate(12, 25))
        assertEquals(Season.NAVIDAD, SeasonLogic.fromDate(1, 6))
        assertEquals(Season.INVIERNO, SeasonLogic.fromDate(1, 7))
        assertEquals(Season.PRIMAVERA, SeasonLogic.fromDate(4, 15))
        assertEquals(Season.VERANO, SeasonLogic.fromDate(7, 1))
    }

    @Test
    fun autoUsesTheDateAndAFixedChoiceIgnoresIt() {
        assertEquals(Season.HALLOWEEN, SeasonLogic.resolve(SeasonChoice.AUTO, 10, 31))
        assertEquals(Season.VALLE, SeasonLogic.resolve(SeasonChoice.VALLE, 10, 31))
        assertEquals(Season.CLASICA, SeasonLogic.resolve(SeasonChoice.CLASICA, 12, 25))
    }

    @Test
    fun theHeroButtonCyclesThroughEveryChoiceAndComesBack() {
        var c = SeasonChoice.AUTO
        val seen = mutableSetOf<SeasonChoice>()
        repeat(SeasonChoice.entries.size) {
            seen += c
            c = SeasonLogic.next(c)
        }
        assertEquals(SeasonChoice.AUTO, c)
        assertEquals(SeasonChoice.entries.toSet(), seen)
    }

    @Test
    fun everyDayOfTheYearHasASeason() {
        for (month in 1..12) for (day in 1..31) assertTrue(SeasonLogic.fromDate(month, day) in Season.entries)
    }
}
