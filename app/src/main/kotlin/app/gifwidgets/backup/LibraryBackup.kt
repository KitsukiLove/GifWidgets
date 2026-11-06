package app.gifwidgets.backup

import android.content.Context
import android.graphics.BitmapFactory
import app.gifwidgets.edit.Timeline
import app.gifwidgets.gif.GifLimits
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.AnimationNames
import app.gifwidgets.library.LibraryStore
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipFile
import org.json.JSONException
import org.json.JSONObject

class ImportItem(val id: String, val name: String, val frames: Int, val bytes: Long)

/** A ZIP that was copied to the cache and checked; nothing is in the library yet. */
class ImportPreview(val zipFile: File, val items: List<ImportItem>)

/**
 * Export / import of the library as one ZIP (format [FORMAT]). The ZIP is untrusted: names, counts and sizes are
 * checked by [LibraryZipReader], and each animation is built in a ".part" folder and renamed into place only when
 * complete, so a failed import never leaves a half animation in the library.
 */
object LibraryBackup {
    const val FORMAT = 1
    private const val MAX_META_BYTES = 64 * 1024L

    /** Writes the chosen animations (those that exist) to [out]. Returns how many were written. */
    fun export(context: Context, ids: List<String>, out: OutputStream): Int {
        val store = LibraryStore(context)
        val items = ids.filter { AnimationMeta.isValidId(it) && store.read(it) != null }
            .map { ZipItem(it, store.dirFor(it)) }
        val names = org.json.JSONArray()
        for (item in items) {
            val m = store.read(item.id) ?: continue
            names.put(JSONObject().put("id", m.id).put("name", m.name))
        }
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("app", context.packageName)
            .put("createdAt", System.currentTimeMillis())
            .put("animations", names)
            .toString()
        LibraryZipWriter.write(out, manifest, items)
        return items.size
    }

    /** Copies [input] to the cache (with a size cap) and validates it. Throws [ZipFormatException] or IOException. */
    fun prepareImport(context: Context, input: InputStream, limits: ZipLimits = ZipLimits()): ImportPreview {
        val dir = File(context.cacheDir, "import").also { it.deleteRecursively(); it.mkdirs() }
        val file = File(dir, "library.zip")
        try {
            var total = 0L
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > limits.maxTotalBytes) throw ZipFormatException("file too large")
                    out.write(buf, 0, n)
                }
            }
            ZipFile(file).use { zip ->
                val scan = LibraryZipReader.scan(zip, limits)
                val manifest = try {
                    JSONObject(LibraryZipReader.readManifest(zip, limits))
                } catch (e: JSONException) {
                    throw ZipFormatException("bad manifest")
                }
                if (manifest.optInt("format", -1) != FORMAT) throw ZipFormatException("unsupported format")
                val items = ArrayList<ImportItem>()
                for ((id, entries) in scan.entriesById) {
                    val metaEntry = entries.firstOrNull { it.second.kind == ZipPaths.Kind.META }?.first ?: continue
                    val meta = AnimationMeta.fromJson(LibraryZipReader.readText(zip, metaEntry, MAX_META_BYTES)) ?: continue
                    if (meta.id != id) continue
                    items.add(ImportItem(id, meta.name, meta.displayFrames, entries.sumOf { maxOf(0L, it.first.size) }))
                }
                if (items.isEmpty()) throw ZipFormatException("no animations")
                return ImportPreview(file, items)
            }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    /** Imports the chosen ids. Returns (imported, failed). Animations whose id already exists get a new id. */
    fun commitImport(context: Context, preview: ImportPreview, ids: Set<String>, limits: ZipLimits = ZipLimits()): Pair<Int, Int> {
        val store = LibraryStore(context)
        var ok = 0
        var failed = 0
        val budget = LibraryZipReader.Budget(limits.maxTotalBytes)
        ZipFile(preview.zipFile).use { zip ->
            val scan = LibraryZipReader.scan(zip, limits)
            for (id in ids) {
                val entries = scan.entriesById[id]
                if (entries == null) {
                    failed++
                    continue
                }
                val newId = if (store.exists(id)) UUID.randomUUID().toString() else id
                var staging: File? = null
                try {
                    staging = store.newStagingDir(newId)
                    LibraryZipReader.extract(zip, entries, staging, budget, limits)
                    val metaFile = File(staging, LibraryStore.META_FILE)
                    val meta = AnimationMeta.fromJson(metaFile.readText(Charsets.UTF_8)) ?: throw ZipFormatException("bad meta")
                    val frames = File(staging, LibraryStore.FRAMES_DIR).listFiles { f -> f.name.endsWith(".webp") }?.size ?: 0
                    val hasOriginal = File(staging, LibraryStore.ORIGINAL_FILE).isFile
                    if (meta.isOptimized && frames != meta.outFrames) throw ZipFormatException("frames missing")
                    if (meta.outFrames > Timeline.MAX_OUTPUT_FRAMES) throw ZipFormatException("too many frames")
                    if (meta.isOptimized) checkFrameSizes(File(staging, LibraryStore.FRAMES_DIR))
                    if (!meta.isOptimized && !hasOriginal) throw ZipFormatException("nothing to show")
                    val fixed = meta.copy(id = newId, name = AnimationNames.sanitize(meta.name, meta.name))
                    app.gifwidgets.library.AtomicFiles.writeBytes(metaFile, fixed.toJson().toByteArray(Charsets.UTF_8))
                    if (!store.commitStaging(staging, newId)) throw IOException("rename failed")
                    ok++
                } catch (e: IOException) {
                    staging?.deleteRecursively()
                    failed++
                } catch (e: RuntimeException) {
                    staging?.deleteRecursively()
                    failed++
                }
            }
        }
        return ok to failed
    }

    /**
     * The frames come from an untrusted file: read only their headers and refuse sizes no animation of ours
     * can have (a tiny WebP can declare a huge picture and exhaust memory when the widget decodes it).
     */
    private fun checkFrameSizes(dir: File, limits: GifLimits = GifLimits()) {
        val files = dir.listFiles { f -> f.name.endsWith(".webp") } ?: return
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        for (f in files) {
            BitmapFactory.decodeFile(f.path, options)
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0 || w > limits.maxSide || h > limits.maxSide || w.toLong() * h > limits.maxCanvasPixels) {
                throw ZipFormatException("bad frame size")
            }
        }
    }

    fun discard(context: Context) {
        File(context.cacheDir, "import").deleteRecursively()
    }
}
