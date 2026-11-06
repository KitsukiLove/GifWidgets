package app.gifwidgets.control

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalPauseTest {
    @Test
    fun nothingBlocksByDefault() {
        assertEquals(GlobalPause.NONE, GlobalPauseLogic.reason(true, true, false, false))
    }

    @Test
    fun theMasterSwitchWinsOverEverythingElse() {
        assertEquals(GlobalPause.MASTER_OFF, GlobalPauseLogic.reason(false, true, true, true))
    }

    @Test
    fun batterySaverOnlyCountsWhenTheSettingIsOn() {
        assertEquals(GlobalPause.BATTERY_SAVER, GlobalPauseLogic.reason(true, true, true, false))
        assertEquals(GlobalPause.NONE, GlobalPauseLogic.reason(true, false, true, false))
    }

    @Test
    fun nightComesAfterBatterySaver() {
        assertEquals(GlobalPause.NIGHT, GlobalPauseLogic.reason(true, true, false, true))
        assertEquals(GlobalPause.BATTERY_SAVER, GlobalPauseLogic.reason(true, true, true, true))
        assertEquals(GlobalPause.NIGHT, GlobalPauseLogic.reason(true, false, true, true))
    }
}
