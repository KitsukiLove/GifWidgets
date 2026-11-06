package app.gifwidgets.widget

import android.content.Context
import app.gifwidgets.library.AtomicFiles
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Per-widget files: filesDir/widgets/<appWidgetId>/{settings.json, usage.txt, cache/}. Deleted in onDeleted. */
class WidgetStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "widgets")

    fun dirFor(appWidgetId: Int): File = File(root, appWidgetId.toString())
    fun cacheDir(appWidgetId: Int): File = File(dirFor(appWidgetId), "cache")

    fun loadSettings(appWidgetId: Int): WidgetSettings {
        val file = File(dirFor(appWidgetId), SETTINGS_FILE)
        if (!file.isFile || file.length() > MAX_BYTES) return WidgetSettings()
        return try {
            val o = JSONObject(file.readText(Charsets.UTF_8))
            val map = HashMap<String, Any?>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = o.opt(k)
                map[k] = if (v is JSONArray) null else v
            }
            WidgetSettings.fromMap(map)
        } catch (e: JSONException) {
            WidgetSettings()
        } catch (e: IOException) {
            WidgetSettings()
        }
    }

    fun hasSettings(appWidgetId: Int): Boolean = File(dirFor(appWidgetId), SETTINGS_FILE).isFile

    fun saveSettings(appWidgetId: Int, settings: WidgetSettings) {
        val dir = dirFor(appWidgetId)
        dir.mkdirs()
        AtomicFiles.writeBytes(
            File(dir, SETTINGS_FILE),
            JSONObject(settings.sanitized().toMap()).toString().toByteArray(Charsets.UTF_8),
        )
    }

    private fun readUsageValue(appWidgetId: Int): UsageFile.Value? =
        try {
            UsageFile.parse(File(dirFor(appWidgetId), USAGE_FILE).takeIf { it.isFile }?.readText())
        } catch (e: IOException) {
            null
        }

    /** The memory level (percent of the platform cap) that last worked for this widget. */
    fun readUsage(appWidgetId: Int): Int =
        (readUsageValue(appWidgetId)?.percent ?: DEFAULT_USAGE).coerceIn(5, 100)

    /** The level to start from under [share]: the stored one only if it was reached under the same share. */
    fun readUsageFor(appWidgetId: Int, share: Int): Int =
        UsageFile.levelFor(readUsageValue(appWidgetId), share, DEFAULT_USAGE).coerceIn(5, 100)

    fun writeUsage(appWidgetId: Int, percent: Int, share: Int = 100) {
        val dir = dirFor(appWidgetId)
        dir.mkdirs()
        try {
            File(dir, USAGE_FILE).writeText(UsageFile.format(percent, share))
        } catch (e: IOException) {
            // not critical
        }
    }

    fun delete(appWidgetId: Int) {
        dirFor(appWidgetId).deleteRecursively()
    }

    companion object {
        const val SETTINGS_FILE = "settings.json"
        private const val USAGE_FILE = "usage.txt"
        private const val MAX_BYTES = 64 * 1024L
        const val DEFAULT_USAGE = 80
    }
}
