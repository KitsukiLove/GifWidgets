package app.gifwidgets.ui.theme

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.gifwidgets.audio.KixkiAudio
import app.gifwidgets.control.AppSettings
import app.gifwidgets.control.ControlStore
import app.gifwidgets.control.DesignStyle
import app.gifwidgets.control.SeasonChoice
import app.gifwidgets.control.SeasonLogic
import app.gifwidgets.control.ThemeMode
import app.gifwidgets.ui.design.LocalSceneClock
import app.gifwidgets.ui.design.LocalSeason
import app.gifwidgets.ui.design.STILL_TIME_MS
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app theme with the user's choices from Settings: light / dark / system, Pixel / Colina XP, the season of the
 * scenery, music, sounds and animated background.
 * Read once right away (a tiny file, so the first frame is already right) and again each time the screen starts.
 * The bottom bar and hero buttons change things through [LocalThemeController]; every change is saved as well.
 *
 * It also owns the scenery clock: it ticks ~8 times a second ONLY while a screen is started AND the animated
 * background is on AND battery saver is off AND the system's animations are not switched off. Otherwise the scenery
 * is still, and nothing runs.
 */
@Composable
fun AppTheme(lifecycle: Lifecycle, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember { ControlStore(context).load() }
    var settings by remember { mutableStateOf(initial) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var reduceMotion by remember { mutableStateOf(systemWantsStill(context)) }
    val clock = remember { mutableStateOf(STILL_TIME_MS) }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            settings = withContext(Dispatchers.IO) { ControlStore(context).load() }
            date = LocalDate.now()
            reduceMotion = systemWantsStill(context)
        }
    }

    val moves = settings.animatedBackground && !reduceMotion
    LaunchedEffect(lifecycle, moves) {
        if (!moves) {
            clock.value = STILL_TIME_MS
        } else {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    clock.value = SystemClock.uptimeMillis() + 100_000L
                    delay(125)
                }
            }
        }
    }

    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val season = SeasonLogic.resolve(settings.season, date.monthValue, date.dayOfMonth)

    LaunchedEffect(settings.musicOn, settings.soundOn, settings.style, season, dark) {
        KixkiAudio.init(context)
        KixkiAudio.configure(settings.musicOn, settings.soundOn, settings.style, season, dark)
    }

    fun update(change: (AppSettings) -> AppSettings) {
        settings = change(settings)
        scope.launch(Dispatchers.IO) {
            val store = ControlStore(context)
            store.save(change(store.load()))
        }
    }

    val controller = remember(settings, dark, season, moves) {
        ThemeController(
            mode = settings.theme,
            style = settings.style,
            dark = dark,
            setMode = { m -> update { it.copy(theme = m) } },
            setStyle = { st -> update { it.copy(style = st) } },
            seasonChoice = settings.season,
            season = season,
            setSeasonChoice = { c -> update { it.copy(season = c) } },
            musicOn = settings.musicOn,
            setMusic = { on -> update { it.copy(musicOn = on) } },
            soundOn = settings.soundOn,
            setSound = { on -> update { it.copy(soundOn = on) } },
            animatedBackground = settings.animatedBackground,
            sceneMoves = moves,
            setAnimatedBackground = { on -> update { it.copy(animatedBackground = on) } },
            welcomeSeen = settings.welcomeSeen,
            setWelcomeSeen = { seen -> update { it.copy(welcomeSeen = seen) } },
            tipsOn = settings.tipsOn,
            setTips = { on -> update { it.copy(tipsOn = on) } },
            reload = {
                scope.launch {
                    settings = withContext(Dispatchers.IO) { ControlStore(context).load() }
                }
            },
        )
    }
    CompositionLocalProvider(
        LocalThemeController provides controller,
        LocalSeason provides season,
        LocalSceneClock provides clock,
    ) {
        GifWidgetsTheme(darkTheme = dark, style = settings.style, content = content)
    }
}

/** Battery saver on, or the system's "remove animations" (animator scale 0): keep the scenery still. */
private fun systemWantsStill(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (power?.isPowerSaveMode == true) return true
    val scale = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: Exception) {
        1f
    }
    return scale == 0f
}
