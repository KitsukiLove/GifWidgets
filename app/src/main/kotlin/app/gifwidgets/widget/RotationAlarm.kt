package app.gifwidgets.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.gifwidgets.control.Rotation
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.LibraryStore

/**
 * Automatic rotation of the animation of a widget. ONE inexact, non-waking alarm (RTC, like the night one) for all
 * widgets: it is set for the earliest due time, and when it rings every due widget moves to its next animation and
 * the alarm is set again. Without widgets that rotate by themselves there is no alarm at all. A late or lost alarm
 * only delays a change: opening the app, a widget update or a reboot arm it again, and [rotateDue] looks at the clock.
 */
object RotationAlarm {
    const val ACTION = "app.gifwidgets.action.AUTO_ROTATE"
    private const val WINDOW_MS = 15 * 60 * 1000L

    private fun entries(app: Context): List<AutoRotate.Entry> {
        val store = WidgetStore(app)
        return WidgetUpdater.allIds(app, AppWidgetManager.getInstance(app)).toList().mapNotNull { id ->
            if (store.hasSettings(id)) AutoRotate.entryOf(id, store.loadSettings(id)) else null
        }
    }

    /** Sets (or cancels) the single alarm from the current settings of every widget. */
    fun arm(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pending(app)
        val at = AutoRotate.nextWake(entries(app), System.currentTimeMillis())
        try {
            if (at == null) alarms.cancel(pending) else alarms.setWindow(AlarmManager.RTC, at, WINDOW_MS, pending)
        } catch (e: SecurityException) {
            // Without the alarm the rotation still happens whenever the app or a widget updates.
        }
    }

    /** Moves every due widget to its next animation, then arms the alarm again. Call from a background thread. */
    fun rotateDue(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val manager = AppWidgetManager.getInstance(app)
        val store = WidgetStore(app)
        val library = LibraryStore(app)
        for (id in AutoRotate.due(entries(app), now)) {
            WidgetUpdater.locked(id) {
                if (!store.hasSettings(id)) return@locked
                val s = store.loadSettings(id)
                if (!s.autoRotates) return@locked
                val target = Rotation.next(s.rotation, s.animationId) { candidate ->
                    AnimationMeta.isValidId(candidate) && library.read(candidate)?.isOptimized == true
                }
                // Even with nothing to change the time is stored, so a broken list is not retried every minute.
                store.saveSettings(id, s.copy(animationId = target ?: s.animationId, rotatedAtMs = now))
                if (target != null) WidgetUpdater.update(app, manager, id)
            }
        }
        arm(app)
    }

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
            .setData(Uri.parse("gifwidgets://rotation/auto"))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
