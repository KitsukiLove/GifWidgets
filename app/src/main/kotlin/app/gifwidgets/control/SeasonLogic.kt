package app.gifwidgets.control

/**
 * What the user picks for the background world: follow the calendar (AUTO) or one fixed season.
 * Same list as the KixKi Store page (Clásico, Valle, Primavera, Verano, Otoño, Invierno, Halloween, Navidad).
 */
enum class SeasonChoice { AUTO, CLASICA, VALLE, PRIMAVERA, VERANO, OTONO, INVIERNO, HALLOWEEN, NAVIDAD }

/** A concrete season the scenery is drawn in. */
enum class Season { CLASICA, VALLE, PRIMAVERA, VERANO, OTONO, INVIERNO, HALLOWEEN, NAVIDAD }

/** Pure rules: no Android, no Compose, so they run in plain JUnit. */
object SeasonLogic {

    /** The page's `seasonKey`: northern hemisphere dates. Month is 1..12. */
    fun fromDate(month: Int, day: Int): Season {
        val v = month * 100 + day
        return when {
            (month == 10 && day >= 10) || (month == 11 && day <= 2) -> Season.HALLOWEEN
            month == 12 || (month == 1 && day <= 6) -> Season.NAVIDAD
            v < 320 -> Season.INVIERNO
            v < 621 -> Season.PRIMAVERA
            v < 922 -> Season.VERANO
            else -> Season.OTONO
        }
    }

    fun resolve(choice: SeasonChoice, month: Int, day: Int): Season =
        if (choice == SeasonChoice.AUTO) fromDate(month, day) else Season.valueOf(choice.name)

    /** The hero button: AUTO -> Clásico -> Valle -> ... -> Navidad -> AUTO. */
    fun next(choice: SeasonChoice): SeasonChoice {
        val all = SeasonChoice.entries
        return all[(all.indexOf(choice) + 1) % all.size]
    }
}
