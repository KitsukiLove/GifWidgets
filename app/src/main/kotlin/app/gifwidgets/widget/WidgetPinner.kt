package app.gifwidgets.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import app.gifwidgets.R
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.AtomicFiles
import app.gifwidgets.library.LibraryStore
import java.io.File
import java.io.IOException

/**
 * "Añadir a pantalla de inicio": asks the launcher to pin a widget (requestPinAppWidget) and remembers the
 * chosen animation as a pending assignment, so the configuration of the new widget preselects it.
 * Nothing runs in the background: the launcher answers, then WidgetConfigureActivity / WidgetActionReceiver
 * do their short job. Call [pin] from a background thread.
 */
object WidgetPinner {

    /** The three default sizes; each one is its own provider (the user can still resize afterwards). */
    enum class Size(val provider: Class<out GifWidgetProvider>, val labelRes: Int) {
        S4X2(GifWidgetProvider::class.java, R.string.widget_label_4x2),
        S2X2(GifWidgetProvider2x2::class.java, R.string.widget_label_2x2),
        S4X4(GifWidgetProvider4x4::class.java, R.string.widget_label_4x4),
    }

    enum class Result { REQUESTED, NOT_SUPPORTED, NOT_READY, FAILED }

    private const val PENDING_FILE = "pending_assignment.txt"
    private const val REQUEST_PIN = 4001

    fun pin(context: Context, animationId: String, size: Size): Result {
        val app = context.applicationContext
        val ready = AnimationMeta.isValidId(animationId) && LibraryStore(app).read(animationId)?.isOptimized == true
        if (!ready) return Result.NOT_READY
        val manager = AppWidgetManager.getInstance(app)
        if (!manager.isRequestPinAppWidgetSupported) return Result.NOT_SUPPORTED
        savePending(app, animationId)
        val accepted = try {
            manager.requestPinAppWidget(ComponentName(app, size.provider), null, successCallback(app))
        } catch (e: RuntimeException) {
            false
        }
        if (!accepted) {
            clearPending(app)
            return Result.FAILED
        }
        return Result.REQUESTED
    }

    /**
     * Sent by the launcher once the user accepts. It goes to our own non-exported receiver (explicit intent).
     * VERIFY: the system can only add EXTRA_APPWIDGET_ID to a mutable PendingIntent. Explicit target, so mutable is safe here.
     * If the id never arrives, the configuration screen still applies the pending animation by itself.
     */
    private fun successCallback(app: Context): PendingIntent {
        val intent = Intent(app, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_PIN_DONE)
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(app, REQUEST_PIN, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }

    private fun pendingFile(app: Context) = File(app.filesDir, PENDING_FILE)

    private fun savePending(app: Context, animationId: String) {
        try {
            val text = PendingAssignmentLogic.encode(animationId, System.currentTimeMillis())
            AtomicFiles.writeBytes(pendingFile(app), text.toByteArray(Charsets.UTF_8))
        } catch (e: IOException) {
            // not critical: the widget then opens its configuration with the first animation
        }
    }

    /** The pending animation if it is recent, still in the library and optimized; null otherwise. Does not delete it. */
    fun peekPending(context: Context): String? {
        val app = context.applicationContext
        val file = pendingFile(app)
        if (!file.isFile || file.length() > 512) return null
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            return null
        }
        val id = PendingAssignmentLogic.decode(text, System.currentTimeMillis())
        if (id == null) {
            file.delete() // expired or broken
            return null
        }
        return id.takeIf { AnimationMeta.isValidId(it) && LibraryStore(app).read(it)?.isOptimized == true }
    }

    fun clearPending(context: Context) {
        pendingFile(context.applicationContext).delete()
    }
}
