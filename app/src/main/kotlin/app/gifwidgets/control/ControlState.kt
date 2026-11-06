package app.gifwidgets.control

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.service.quicksettings.TileService
import app.gifwidgets.library.AtomicFiles
import app.gifwidgets.widget.GifWidgetProvider
import app.gifwidgets.widget.TapAction
import app.gifwidgets.widget.WidgetActionReceiver
import app.gifwidgets.widget.WidgetSettings
import app.gifwidgets.widget.WidgetUpdater
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONException
import org.json.JSONObject

/** Reads and writes the app-wide settings file (filesDir/app_settings.json). Small, read on demand. */
class ControlStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "app_settings.json")

    fun load(): AppSettings {
        if (!file.isFile || file.length() > 8 * 1024L) return AppSettings()
        return try {
            val o = JSONObject(file.readText(Charsets.UTF_8))
            val map = HashMap<String, Any?>()
            for (k in o.keys()) map[k] = o.opt(k)
            AppSettings.fromMap(map)
        } catch (e: JSONException) {
            AppSettings()
        } catch (e: IOException) {
            AppSettings()
        }
    }

    fun save(settings: AppSettings) {
        file.parentFile?.mkdirs()
        AtomicFiles.writeBytes(file, JSONObject(settings.sanitized().toMap()).toString().toByteArray(Charsets.UTF_8))
    }
}

/** Glue between the pure rules (ControlLogic) and the phone: clock, battery saver, stored settings. */
object ControlState {

    fun powerSave(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

    /** All inputs for one widget, evaluated right now (lazy: nothing is kept running to keep this fresh). */
    fun inputs(
        context: Context,
        s: WidgetSettings,
        app: AppSettings = ControlStore(context).load(),
        now: LocalDateTime = LocalDateTime.now(),
    ): ControlInputs {
        val night = app.night
        return ControlInputs(
            masterEnabled = app.masterEnabled,
            manualSwitch = s.animating,
            nightActive = night.isNight(now),
            nightOverride = s.nightOverrideFor(night.windowKey(now)),
            powerSaveActive = powerSave(context),
            pauseOnBatterySaver = app.pauseOnBatterySaver,
            playOnceMode = s.tap == TapAction.PLAY_ONCE,
            // The end alarm can be lost (reboot, force stop): the clock decides, so it never plays forever.
            playingOnce = s.playingOnce && System.currentTimeMillis() < s.playUntilMs,
        )
    }

    fun isAnimating(context: Context, s: WidgetSettings): Boolean =
        ControlLogic.effective(inputs(context, s))
}

/** Things the UI, the tile and the widgets all do to the global state. */
object ControlActions {

    /** Flips the universal switch, re-renders every widget and refreshes the Quick Settings tile. */
    fun setMaster(context: Context, enabled: Boolean, sync: Boolean = false) {
        val app = context.applicationContext
        val store = ControlStore(app)
        synchronized(this) { store.save(store.load().copy(masterEnabled = enabled)) }
        afterChange(app, sync)
    }

    /** Flips the universal switch. Read and write happen under one lock, so two quick taps really flip it twice. */
    fun toggleMaster(context: Context, sync: Boolean = false) {
        val app = context.applicationContext
        val store = ControlStore(app)
        synchronized(this) { store.save(store.load().let { it.copy(masterEnabled = !it.masterEnabled) }) }
        afterChange(app, sync)
    }

    /**
     * After any global setting changed: re-arm the night alarm, re-render widgets, refresh the tile.
     * [sync] = true re-renders on the CALLING thread (which must not be the main thread). A broadcast receiver
     * uses it: if it finished before the helper thread did, the system could freeze the process mid-update.
     */
    fun afterChange(context: Context, sync: Boolean = false) {
        val app = context.applicationContext
        NightAlarm.arm(app)
        if (sync) WidgetUpdater.updateAll(app) else GifWidgetProvider.refreshAll(app)
        requestTileRefresh(app)
    }

    fun requestTileRefresh(context: Context) {
        try {
            TileService.requestListeningState(context, ComponentName(context, AnimationsTileService::class.java))
        } catch (e: RuntimeException) {
            // the tile is optional
        }
    }
}

/**
 * The night schedule's alarm: ONE inexact, non-waking alarm (type RTC, 15-minute window, no exact-alarm
 * permission) at the next start or end of the night window. It only exists to refresh the widgets near the
 * boundary; every update also re-evaluates the time itself, so a late alarm only delays the change.
 */
object NightAlarm {
    const val ACTION = "app.gifwidgets.action.NIGHT_BOUNDARY"
    private const val WINDOW_MS = 15 * 60 * 1000L

    fun arm(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pending(app)
        val settings = ControlStore(app).load()
        val next = settings.night.nextBoundary(LocalDateTime.now())
        try {
            if (next == null) {
                alarms.cancel(pending)
            } else {
                val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                // VERIFY on the Pixel 9: setWindow with RTC needs no permission and fires within the window.
                alarms.setWindow(AlarmManager.RTC, at, WINDOW_MS, pending)
            }
        } catch (e: SecurityException) {
            // Without the alarm the widgets still re-evaluate on every update, tap and app open.
        }
    }

    /** No widget is left: stop waking the app at the night boundaries. */
    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            alarms.cancel(pending(app))
        } catch (e: SecurityException) {
            // nothing to cancel
        }
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java)
            .setAction(ACTION)
            .setData(Uri.parse("gifwidgets://night/boundary"))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
