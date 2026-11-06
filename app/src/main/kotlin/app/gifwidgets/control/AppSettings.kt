package app.gifwidgets.control

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Look of the app screens: flat pixel windows, or the rounded "Colina XP" windows. Same palette, other chrome. */
enum class DesignStyle { PIXEL, XP }

/** App-wide settings (one file, filesDir/app_settings.json). Pure data. */
data class AppSettings(
    /** The universal animation switch. */
    val masterEnabled: Boolean = true,
    val nightEnabled: Boolean = false,
    val nightStartMin: Int = 23 * 60,
    val nightEndMin: Int = 7 * 60,
    val pauseOnBatterySaver: Boolean = true,
    /** Stage 6: default speed of a new edit (5..25 fps, 12 by default). */
    val defaultFps: Int = 20,
    /** Stage 6: "Píxel nítido" default for the editor and for new widgets. */
    val sharpPixels: Boolean = true,
    /** Stage 6: false = original.gif is deleted after a successful "Guardar optimizado" (saves space, no re-editing). */
    val keepOriginals: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Experimental "play once" tap action is only offered when this is on. */
    val playOnceEnabled: Boolean = false,
    /** Design stage: Pixel (default) or Colina XP. Last field, so older positional constructors keep working. */
    val style: DesignStyle = DesignStyle.PIXEL,
    /** Background world: follows the calendar by default, like the page's "Auto". */
    val season: SeasonChoice = SeasonChoice.AUTO,
    /** Generated background music (only while the app is on screen). Off until the user turns it on. */
    val musicOn: Boolean = false,
    /** Little tap sounds of buttons and the sheep. */
    val soundOn: Boolean = true,
    /** Clouds, snow, birds... in the background. Always still when battery saver is on or system animations are off. */
    val animatedBackground: Boolean = true,
    /** Kixki's welcome window has been shown (it appears only once). */
    val welcomeSeen: Boolean = false,
    /** The "Consejo" strip with Kixki's tips. The strip's X turns it off; Settings turns it on again. */
    val tipsOn: Boolean = true,
    /** When the user last exported a backup (epoch millis; 0 = never). */
    val lastBackupMs: Long = 0L,
    /** The "back up your GIFs" reminder is hidden until this moment ("Más tarde"). */
    val backupSnoozeUntilMs: Long = 0L,
    /** Library layout: false = cards, true = compact list. */
    val compactList: Boolean = false,
) {
    fun sanitized() = copy(
        defaultFps = defaultFps.coerceIn(5, 25),
        nightStartMin = nightStartMin.coerceIn(0, 1439),
        nightEndMin = nightEndMin.coerceIn(0, 1439),
    )

    val night: NightSchedule get() = NightSchedule(nightEnabled, nightStartMin, nightEndMin)

    fun toMap(): Map<String, Any> = linkedMapOf(
        "master" to masterEnabled,
        "nightEnabled" to nightEnabled,
        "nightStart" to nightStartMin,
        "nightEnd" to nightEndMin,
        "batterySaver" to pauseOnBatterySaver,
        "defaultFps" to defaultFps,
        "sharp" to sharpPixels,
        "keepOriginals" to keepOriginals,
        "theme" to theme.name,
        "playOnce" to playOnceEnabled,
        "style" to style.name,
        "season" to season.name,
        "music" to musicOn,
        "sound" to soundOn,
        "animBg" to animatedBackground,
        "welcomeSeen" to welcomeSeen,
        "tips" to tipsOn,
        "lastBackup" to lastBackupMs,
        "backupSnooze" to backupSnoozeUntilMs,
        "compactList" to compactList,
    )

    companion object {
        fun fromMap(m: Map<String, Any?>): AppSettings {
            val d = AppSettings()
            fun int(k: String, def: Int) = (m[k] as? Number)?.toInt() ?: def
            fun bool(k: String, def: Boolean) = m[k] as? Boolean ?: def
            return AppSettings(
                masterEnabled = bool("master", d.masterEnabled),
                nightEnabled = bool("nightEnabled", d.nightEnabled),
                nightStartMin = int("nightStart", d.nightStartMin),
                nightEndMin = int("nightEnd", d.nightEndMin),
                pauseOnBatterySaver = bool("batterySaver", d.pauseOnBatterySaver),
                defaultFps = int("defaultFps", d.defaultFps),
                sharpPixels = bool("sharp", d.sharpPixels),
                keepOriginals = bool("keepOriginals", d.keepOriginals),
                theme = ThemeMode.entries.firstOrNull { it.name == m["theme"] } ?: d.theme,
                playOnceEnabled = bool("playOnce", d.playOnceEnabled),
                style = DesignStyle.entries.firstOrNull { it.name == m["style"] } ?: d.style,
                season = SeasonChoice.entries.firstOrNull { it.name == m["season"] } ?: d.season,
                musicOn = bool("music", d.musicOn),
                soundOn = bool("sound", d.soundOn),
                animatedBackground = bool("animBg", d.animatedBackground),
                welcomeSeen = bool("welcomeSeen", d.welcomeSeen),
                tipsOn = bool("tips", d.tipsOn),
                lastBackupMs = (m["lastBackup"] as? Number)?.toLong() ?: 0L,
                backupSnoozeUntilMs = (m["backupSnooze"] as? Number)?.toLong() ?: 0L,
                compactList = bool("compactList", d.compactList),
            ).sanitized()
        }
    }
}
