package app.gifwidgets.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import app.gifwidgets.control.NightAlarm
import app.gifwidgets.util.runGuarded
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The widget provider. updatePeriodMillis is 0 and nothing here keeps running: every call does its short
 * job on a helper thread under goAsync() and ends. The animation itself is moved by the launcher (ViewFlipper).
 * Every helper thread body is wrapped in [runGuarded]: an exception escaping a bare thread would kill the app.
 */
open class GifWidgetProvider : AppWidgetProvider() {

    /** The first widget on the screen (or after a reboot): make sure the night alarm exists. */
    override fun onEnabled(context: Context) {
        NightAlarm.arm(context.applicationContext)
        RotationAlarm.arm(context.applicationContext)
    }

    /** The last widget of this provider is gone: when no widget of any size is left, stop the night alarm. */
    override fun onDisabled(context: Context) {
        val app = context.applicationContext
        runGuarded {
            if (WidgetUpdater.allIds(app, AppWidgetManager.getInstance(app)).isEmpty()) {
                NightAlarm.cancel(app)
                RotationAlarm.cancel(app)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                runGuarded { RotationAlarm.arm(app) }
                runGuarded { NightAlarm.arm(app) } // re-armed after reboot / app update too (alarms do not survive a reboot)
                for (id in appWidgetIds) runGuarded { WidgetUpdater.update(app, appWidgetManager, id) }
            } finally {
                pending.finish()
            }
        }.start()
    }

    /**
     * The launcher sends several of these while the user drags a resize handle: wait 150 ms for a quick still picture and
     * 500 ms for the full render; only the last burst re-renders (VERIFY: that the launcher really sends bursts; harmless if it does not).
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val app = context.applicationContext
        val token = counters.getOrPut(appWidgetId) { AtomicInteger() }.incrementAndGet()
        val pending = goAsync()
        Thread {
            try {
                // First a still picture at the new size (one frame: fast), so the launcher does not keep
                // stretching the old bitmaps; then, once the user has let go, the full animated render.
                Thread.sleep(QUICK_MS)
                if (counters[appWidgetId]?.get() == token) {
                    runGuarded { WidgetUpdater.updateQuick(app, appWidgetManager, appWidgetId) }
                }
                Thread.sleep(DEBOUNCE_MS - QUICK_MS)
                if (counters[appWidgetId]?.get() == token) {
                    runGuarded { WidgetUpdater.update(app, appWidgetManager, appWidgetId) }
                }
            } catch (e: InterruptedException) {
                // give up quietly
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext
        for (id in appWidgetIds) counters.remove(id)
        // Deleting folders is disk work: not on the main thread.
        val pending = goAsync()
        Thread {
            try {
                for (id in appWidgetIds) runGuarded { WidgetUpdater.forget(app, id) }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private const val QUICK_MS = 150L
        private const val DEBOUNCE_MS = 500L
        private val counters = ConcurrentHashMap<Int, AtomicInteger>()

        /** Re-renders every placed widget (for example after an animation was edited). Starts its own short thread. */
        fun refreshAll(context: Context) {
            val app = context.applicationContext
            Thread { runGuarded { WidgetUpdater.updateAll(app) } }.start()
        }
    }
}

/** Same widget, smaller default size (2x2 cells). Settings are per widget id, so nothing else differs. */
class GifWidgetProvider2x2 : GifWidgetProvider()

/** Same widget, bigger default size (4x4 cells). */
class GifWidgetProvider4x4 : GifWidgetProvider()
