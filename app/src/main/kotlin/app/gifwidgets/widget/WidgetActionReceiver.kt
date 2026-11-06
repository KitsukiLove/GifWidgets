package app.gifwidgets.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import app.gifwidgets.R
import app.gifwidgets.control.Block
import app.gifwidgets.control.ControlActions
import app.gifwidgets.control.ControlLogic
import app.gifwidgets.control.ControlState
import app.gifwidgets.control.ControlStore
import app.gifwidgets.control.NightAlarm
import app.gifwidgets.control.Rotation
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.LibraryStore
import app.gifwidgets.util.runGuarded
import java.time.LocalDateTime

/**
 * Everything the widgets and the system send to the app while it is not running: the pause button and tap
 * actions, the night-boundary alarm, the end of a "play once" loop and (best effort) battery-saver changes.
 * Not exported: only our own explicit PendingIntents reach it (and the system's power-save broadcast).
 * Each action is short work on a helper thread under goAsync().
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext
        val id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val needsId = action in ID_ACTIONS
        if (needsId && id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        Thread {
            try {
                runGuarded { handle(app, action, id) } // an exception escaping this thread would kill the app
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handle(app: Context, action: String, id: Int) {
        val manager = AppWidgetManager.getInstance(app)
        when (action) {
            ACTION_TOGGLE -> toggle(app, manager, id)
            // sync = true: re-render on THIS thread, so the receiver is not finished before the widgets are updated.
            ACTION_TOGGLE_ALL -> ControlActions.toggleMaster(app, sync = true)
            ACTION_NEXT -> next(app, manager, id)
            ACTION_PIN_DONE -> pinDone(app, manager, id)
            ACTION_PLAY -> playOnce(app, manager, id)
            ACTION_PLAY_END -> WidgetUpdater.locked(id) {
                val store = WidgetStore(app)
                if (store.hasSettings(id)) {
                    store.saveSettings(id, store.loadSettings(id).copy(playingOnce = false, playUntilMs = 0L))
                    WidgetUpdater.update(app, manager, id)
                }
            }
            RotationAlarm.ACTION -> RotationAlarm.rotateDue(app)
            NightAlarm.ACTION,
            // The night window is in local time: a new time zone or a clock change moves its boundaries.
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                NightAlarm.arm(app)
                RotationAlarm.arm(app)
                WidgetUpdater.updateAll(app)
            }
            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> WidgetUpdater.updateAll(app)
        }
    }

    private fun toggle(app: Context, manager: AppWidgetManager, id: Int) = WidgetUpdater.locked(id) {
        val store = WidgetStore(app)
        // A widget that was removed (or never set up) has no settings: do not recreate its folder.
        if (!store.hasSettings(id)) return@locked
        val s = store.loadSettings(id)
        val app0 = ControlStore(app).load()
        val now = LocalDateTime.now()
        val inputs = ControlState.inputs(app, s, app0, now)
        val r = ControlLogic.toggle(inputs)
        if (r.block != Block.NONE) {
            toast(app, if (r.block == Block.MASTER_OFF) R.string.toast_master_off else R.string.toast_battery_saver)
            return@locked
        }
        val key = app0.night.windowKey(now)
        store.saveSettings(
            id,
            if (inputs.nightActive) s.copy(nightOverride = r.nightOverride, nightOverrideKey = key)
            else s.copy(animating = r.manualSwitch),
        )
        WidgetUpdater.update(app, manager, id)
    }

    private fun next(app: Context, manager: AppWidgetManager, id: Int) = WidgetUpdater.locked(id) {
        val store = WidgetStore(app)
        if (!store.hasSettings(id)) return@locked
        val s = store.loadSettings(id)
        val library = LibraryStore(app)
        val target = Rotation.next(s.rotation, s.animationId) { candidate ->
            AnimationMeta.isValidId(candidate) && library.read(candidate)?.isOptimized == true
        } ?: return@locked
        store.saveSettings(id, s.copy(animationId = target))
        WidgetUpdater.update(app, manager, id)
    }

    /**
     * The launcher accepted "Añadir a pantalla de inicio". If the configuration screen has not saved anything for
     * this widget yet, give it the animation the user picked in the library. Settings that already exist win.
     */
    private fun pinDone(app: Context, manager: AppWidgetManager, id: Int) = WidgetUpdater.locked(id) {
        val store = WidgetStore(app)
        val animationId = WidgetPinner.peekPending(app)
        if (animationId != null && !store.hasSettings(id)) {
            val globalSharp = ControlStore(app).load().sharpPixels
            val meta = LibraryStore(app).read(animationId)
            store.saveSettings(
                id,
                if (meta != null) {
                    WidgetSettings.forAnimation(meta, globalSharp)
                } else {
                    WidgetSettings(animationId = animationId, sharp = globalSharp)
                },
            )
            store.writeUsage(id, WidgetStore.DEFAULT_USAGE)
            WidgetUpdater.update(app, manager, id)
        }
        WidgetPinner.clearPending(app)
    }

    /** EXPERIMENTAL: moves for one loop, then an inexact alarm puts the resting frame back. */
    private fun playOnce(app: Context, manager: AppWidgetManager, id: Int) = WidgetUpdater.locked(id) {
        val store = WidgetStore(app)
        if (!store.hasSettings(id)) return@locked
        val s = store.loadSettings(id)
        val inputs = ControlState.inputs(app, s)
        val block = ControlLogic.blockedBy(inputs)
        if (block != Block.NONE) {
            toast(app, if (block == Block.MASTER_OFF) R.string.toast_master_off else R.string.toast_battery_saver)
            return@locked
        }
        val meta = s.animationId?.takeIf { AnimationMeta.isValidId(it) }?.let { LibraryStore(app).read(it) }
        val loopMs = if (meta != null && meta.outFps > 0) {
            (meta.outFrames * 1000.0 / (meta.outFps * s.speedPercent / 100.0)).toLong()
        } else {
            3000L
        }.coerceIn(500L, 60_000L)
        // The clock decides how long it plays (the end alarm below can be lost on a reboot or a force stop).
        store.saveSettings(id, s.copy(playingOnce = true, playUntilMs = System.currentTimeMillis() + loopMs))
        WidgetUpdater.update(app, manager, id)
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@locked
        try {
            // set(): inexact on purpose (no exact-alarm permission); it may end a little late.
            alarms.set(AlarmManager.RTC, System.currentTimeMillis() + loopMs, pendingFor(app, ACTION_PLAY_END, id))
        } catch (e: SecurityException) {
            // leave it playing; the next tap or update resets it
        }
    }

    private fun toast(app: Context, res: Int) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(app, res, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val ACTION_TOGGLE = "app.gifwidgets.action.TOGGLE"
        const val ACTION_TOGGLE_ALL = "app.gifwidgets.action.TOGGLE_ALL"
        const val ACTION_NEXT = "app.gifwidgets.action.NEXT"
        const val ACTION_PLAY = "app.gifwidgets.action.PLAY_ONCE"
        const val ACTION_PLAY_END = "app.gifwidgets.action.PLAY_ONCE_END"
        const val ACTION_PIN_DONE = "app.gifwidgets.action.PIN_DONE"
        const val EXTRA_WIDGET_ID = AppWidgetManager.EXTRA_APPWIDGET_ID
        private val ID_ACTIONS = setOf(ACTION_TOGGLE, ACTION_NEXT, ACTION_PLAY, ACTION_PLAY_END, ACTION_PIN_DONE)

        /** Cancels the "play once" end alarm of a widget (it was removed). */
        fun cancelPlayEnd(context: Context, id: Int) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            try {
                alarms.cancel(pendingFor(context, ACTION_PLAY_END, id))
            } catch (e: SecurityException) {
                // nothing to cancel
            }
        }

        /** Explicit, immutable PendingIntent to this (non-exported) receiver. [id] is 0 for global actions. */
        fun pendingFor(context: Context, action: String, id: Int): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("gifwidgets://widget/$id/${action.substringAfterLast('.')}"))
                .putExtra(EXTRA_WIDGET_ID, id)
            return PendingIntent.getBroadcast(
                context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
