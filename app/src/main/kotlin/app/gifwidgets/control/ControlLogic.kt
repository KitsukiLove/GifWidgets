package app.gifwidgets.control

/** Everything that decides whether ONE widget animates right now. */
data class ControlInputs(
    /** The universal switch (Quick Settings tile / app switch). */
    val masterEnabled: Boolean = true,
    /** The widget's own switch outside the night window. */
    val manualSwitch: Boolean = true,
    val nightActive: Boolean = false,
    /** The widget's override during the CURRENT night window (null = none). Already checked against the window key. */
    val nightOverride: Boolean? = null,
    val powerSaveActive: Boolean = false,
    val pauseOnBatterySaver: Boolean = true,
    /** Tap action "play once": the widget rests still and only moves while [playingOnce]. */
    val playOnceMode: Boolean = false,
    val playingOnce: Boolean = false,
)

enum class Block { NONE, MASTER_OFF, BATTERY_SAVER }

class ToggleResult(val block: Block, val manualSwitch: Boolean, val nightOverride: Boolean?)

/** Pure rules of stage 5. No Android here; see ControlLogicTest for the table. */
object ControlLogic {

    /** What the widget would do without the global rules. At night the default is still unless overridden. */
    fun current(i: ControlInputs): Boolean =
        if (i.playOnceMode) i.playingOnce
        else if (i.nightActive) (i.nightOverride ?: false)
        else i.manualSwitch

    fun blockedBy(i: ControlInputs): Block = when {
        !i.masterEnabled -> Block.MASTER_OFF
        i.pauseOnBatterySaver && i.powerSaveActive -> Block.BATTERY_SAVER
        else -> Block.NONE
    }

    fun effective(i: ControlInputs): Boolean = blockedBy(i) == Block.NONE && current(i)

    /**
     * The widget's button / "toggle" tap. When the master switch or the battery saver blocks animation nothing
     * changes (the caller shows a Toast). At night it sets the override, otherwise the manual switch.
     */
    fun toggle(i: ControlInputs): ToggleResult {
        val block = blockedBy(i)
        if (block != Block.NONE) return ToggleResult(block, i.manualSwitch, i.nightOverride)
        return if (i.nightActive) {
            ToggleResult(Block.NONE, i.manualSwitch, !(i.nightOverride ?: false))
        } else {
            ToggleResult(Block.NONE, !i.manualSwitch, i.nightOverride)
        }
    }
}

/** Why ALL widgets are still right now, for the message in the library (the first reason that applies). */
enum class GlobalPause { NONE, MASTER_OFF, BATTERY_SAVER, NIGHT }

object GlobalPauseLogic {
    fun reason(masterEnabled: Boolean, pauseOnBatterySaver: Boolean, powerSave: Boolean, nightActive: Boolean): GlobalPause = when {
        !masterEnabled -> GlobalPause.MASTER_OFF
        pauseOnBatterySaver && powerSave -> GlobalPause.BATTERY_SAVER
        nightActive -> GlobalPause.NIGHT
        else -> GlobalPause.NONE
    }
}

object Rotation {
    /**
     * The animation after [current] in [list] that [isValid] accepts (wraps around). Null when nothing else is
     * valid. If [current] is not in the list the search starts at the beginning.
     */
    fun next(list: List<String>, current: String?, isValid: (String) -> Boolean): String? {
        if (list.isEmpty()) return null
        val start = list.indexOf(current)
        for (step in 1..list.size) {
            val candidate = list[(start + step).mod(list.size)]
            if (candidate != current && isValid(candidate)) return candidate
        }
        return null
    }
}
