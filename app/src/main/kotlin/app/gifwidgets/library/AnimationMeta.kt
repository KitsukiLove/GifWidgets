package app.gifwidgets.library

import app.gifwidgets.edit.EditorParams
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * What we remember about one imported animation (stored as meta.json next to original.gif).
 *
 * [srcWidth]/[srcHeight]/[frameCount]/[fps]/[bytes] describe the ORIGINAL gif.
 * After "Guardar optimizado" the out* fields describe the frames in frames/ (000.webp, ...),
 * and [params] holds the editor settings so the animation can be re-edited from original.gif.
 */
data class AnimationMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
    val srcWidth: Int,
    val srcHeight: Int,
    val frameCount: Int,
    val fps: Int,
    val hasAlpha: Boolean,
    val bytes: Long,
    val outWidth: Int = 0,
    val outHeight: Int = 0,
    /** 0 = not optimized yet. */
    val outFrames: Int = 0,
    val outFps: Int = 0,
    /** Total size of the files in frames/. */
    val framesBytes: Long = 0,
    /** When the optimized frames were last written (0 = never); used to refresh thumbnails. */
    val editedAt: Long = 0,
    val params: EditorParams? = null,
    /**
     * [hasAlpha] was measured on the saved pixels (see [AlphaScan]). Older entries only had the GIF's own
     * "transparent colour" flag, which many GIFs set without any see-through pixel; those are re-checked once.
     */
    val alphaChecked: Boolean = false,
    /** Pinned first in the library. */
    val favorite: Boolean = false,
    /** Name of the collection (folder) it is in; empty = none. */
    val collection: String = "",
) {
    val isOptimized: Boolean get() = outFrames > 0
    val displayWidth: Int get() = if (isOptimized) outWidth else srcWidth
    val displayHeight: Int get() = if (isOptimized) outHeight else srcHeight
    val displayFrames: Int get() = if (isOptimized) outFrames else frameCount
    val displayFps: Int get() = if (isOptimized) outFps else fps

    /** Disk space used by this animation (original + optimized frames). */
    val totalBytes: Long get() = bytes + framesBytes

    fun toJson(): String = JSONObject().apply {
        put("version", FORMAT_VERSION)
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("srcWidth", srcWidth)
        put("srcHeight", srcHeight)
        put("frameCount", frameCount)
        put("fps", fps)
        put("hasAlpha", hasAlpha)
        put("bytes", bytes)
        put("outWidth", outWidth)
        put("outHeight", outHeight)
        put("outFrames", outFrames)
        put("outFps", outFps)
        put("framesBytes", framesBytes)
        put("editedAt", editedAt)
        put("alphaChecked", alphaChecked)
        if (favorite) put("favorite", true)
        if (collection.isNotEmpty()) put("collection", collection)
        if (params != null) put("params", JSONObject(params.toMap()))
    }.toString()

    companion object {
        const val FORMAT_VERSION = 2
        private val UUID_REGEX =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        /** Ids become folder names, so only accept the UUID shape we generate ourselves. */
        fun isValidId(id: String): Boolean = UUID_REGEX.matches(id)

        /** Returns null if the text is not valid meta.json (never throws). Version 1 files still load. */
        fun fromJson(text: String): AnimationMeta? = try {
            val o = JSONObject(text)
            val id = o.getString("id")
            if (!isValidId(id)) {
                null
            } else {
                AnimationMeta(
                    id = id,
                    name = o.getString("name"),
                    createdAt = o.getLong("createdAt"),
                    srcWidth = o.getInt("srcWidth"),
                    srcHeight = o.getInt("srcHeight"),
                    frameCount = o.getInt("frameCount"),
                    fps = o.getInt("fps"),
                    hasAlpha = o.getBoolean("hasAlpha"),
                    bytes = o.getLong("bytes"),
                    outWidth = o.optInt("outWidth", 0),
                    outHeight = o.optInt("outHeight", 0),
                    outFrames = o.optInt("outFrames", 0),
                    outFps = o.optInt("outFps", 0),
                    framesBytes = o.optLong("framesBytes", 0),
                    editedAt = o.optLong("editedAt", 0),
                    params = o.optJSONObject("params")?.let { EditorParams.fromMap(jsonToMap(it)) },
                    alphaChecked = o.optBoolean("alphaChecked", false),
                    favorite = o.optBoolean("favorite", false),
                    collection = LibraryOrganizer.cleanCollection(o.optString("collection", "")),
                )
            }
        } catch (e: JSONException) {
            null
        }

        private fun jsonToMap(o: JSONObject): Map<String, Any?> {
            val m = HashMap<String, Any?>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = o.opt(k)
                m[k] = if (v is JSONArray) List(v.length()) { i -> v.opt(i) } else v
            }
            return m
        }
    }
}
