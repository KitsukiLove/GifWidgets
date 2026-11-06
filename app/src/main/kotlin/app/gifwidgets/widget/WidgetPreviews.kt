package app.gifwidgets.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import app.gifwidgets.R
import app.gifwidgets.control.ControlStore
import app.gifwidgets.library.AtomicFiles
import app.gifwidgets.library.LibraryStore
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Widget-picker previews.
 * - Android 15+: setWidgetPreview with the first (resting) frame of the newest optimized animation, drawn as
 *   the widget would look, for the home screen and the lock-screen widget page. The system allows only about two calls per hour per provider, so this only
 *   calls it when the newest animation (or its last edit) changed, and waits before retrying after a refusal.
 * - Android 12-14: previewLayout and, for older launchers, previewImage (static placeholders from widget_info*.xml).
 * Call [refresh] from a background thread; it is cheap when nothing changed.
 */
object WidgetPreviews {
    private const val STATE_FILE = "preview_state.txt"
    private const val RETRY_MS = 10 * 60_000L
    private val lock = Any()

    private class Target(val provider: Class<out GifWidgetProvider>, val widthDp: Int, val heightDp: Int)

    // Same default sizes as the widget_info*.xml files (minWidth x minHeight).
    private val TARGETS = listOf(
        Target(GifWidgetProvider::class.java, 250, 110),
        Target(GifWidgetProvider2x2::class.java, 110, 110),
        Target(GifWidgetProvider4x4::class.java, 250, 250),
    )

    private class Entry(val done: String?, val attemptAt: Long)

    fun refresh(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        synchronized(lock) {
            try {
                refreshGenerated(context.applicationContext)
            } catch (e: RuntimeException) {
                // never crash the app because of a preview
            } catch (e: OutOfMemoryError) {
                // same
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun refreshGenerated(app: Context) {
        val newest = LibraryStore(app).list().firstOrNull { it.isOptimized }
        // The ":hk" tag = the preview was published for home screen AND lock screen, so a preview set by an
        // older version (home screen only) is published again once.
        val signature = PreviewPolicy.signature(newest?.id, newest?.editedAt ?: 0L)
            .let { if (it == PreviewPolicy.NONE) it else "$it:hk" }
        val state = readState(app)
        val now = System.currentTimeMillis()
        val manager = AppWidgetManager.getInstance(app)
        val category = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN or AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
        val density = app.resources.displayMetrics.density
        var changed = false

        for (target in TARGETS) {
            val key = target.provider.simpleName
            val previous = state[key]
            if (!PreviewPolicy.shouldAttempt(previous?.done, previous?.attemptAt ?: 0L, signature, now, RETRY_MS)) continue
            val component = ComponentName(app, target.provider)
            val ok = try {
                if (newest == null) {
                    manager.removeWidgetPreview(component, category) // no-op if none was set
                    true
                } else {
                    val settings = WidgetSettings.forAnimation(newest, ControlStore(app).load().sharpPixels)
                    val bitmap = WidgetRenderer.renderPreview(
                        app, settings, newest,
                        (target.widthDp * density).roundToInt(), (target.heightDp * density).roundToInt(),
                    )
                    if (bitmap == null) {
                        false
                    } else {
                        val views = RemoteViews(app.packageName, R.layout.widget_static)
                        views.setImageViewBitmap(R.id.widget_image, bitmap)
                        // true = set, false = rate-limited (documented). We try again after RETRY_MS.
                        manager.setWidgetPreview(component, category, views)
                    }
                }
            } catch (e: RuntimeException) {
                false
            } catch (e: OutOfMemoryError) {
                false
            }
            state[key] = Entry(done = if (ok) signature else previous?.done, attemptAt = now)
            changed = true
        }
        if (changed) writeState(app, state)
    }

    // One line per provider: "<name>|<done signature or ->|<last attempt millis>"
    private fun readState(app: Context): MutableMap<String, Entry> {
        val map = LinkedHashMap<String, Entry>()
        val file = File(app.filesDir, STATE_FILE)
        if (!file.isFile || file.length() > 4096) return map
        val lines = try {
            file.readLines(Charsets.UTF_8)
        } catch (e: IOException) {
            return map
        }
        for (line in lines) {
            val p = line.split('|')
            if (p.size != 3) continue
            val at = p[2].toLongOrNull() ?: continue
            map[p[0]] = Entry(done = p[1].takeIf { it != "-" }, attemptAt = at)
        }
        return map
    }

    private fun writeState(app: Context, state: Map<String, Entry>) {
        val text = state.entries.joinToString("\n") { (k, v) -> "$k|${v.done ?: "-"}|${v.attemptAt}" }
        try {
            AtomicFiles.writeBytes(File(app.filesDir, STATE_FILE), text.toByteArray(Charsets.UTF_8))
        } catch (e: IOException) {
            // worst case: one extra (rate-limited) call next time
        }
    }
}
