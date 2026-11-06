package app.gifwidgets.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.gifwidgets.gif.GifException
import app.gifwidgets.gif.GifLimits
import app.gifwidgets.gif.GifReader
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Copies a GIF the user picked or shared into app-private storage, after checking it.
 * Call from a background thread (it does file I/O). Never throws for bad input.
 *
 * Order of checks (the file is untrusted):
 *  1. copy with a hard size cap (the MIME type and file name are never trusted)
 *  2. real signature "GIF87a"/"GIF89a"
 *  3. structure scan with dimension/frame/pixel limits, and decode of the first frame
 * On any failure the half-made folder is deleted.
 */
object GifImporter {

    fun import(
        context: Context,
        uri: Uri,
        fallbackName: String,
        limits: GifLimits = GifLimits(),
        /** The name the user typed in the "name this GIF" question; null = take it from the file name. */
        nameOverride: String? = null,
    ): ImportResult {
        val store = LibraryStore(context)
        val id = UUID.randomUUID().toString()
        val dir = store.dirFor(id)
        var keep = false
        try {
            if (!dir.mkdirs()) return ImportResult.Failure(FailureReason.CANNOT_READ)
            val part = File(dir, LibraryStore.ORIGINAL_FILE + ".part")

            val input = context.contentResolver.openInputStream(uri)
                ?: return ImportResult.Failure(FailureReason.CANNOT_READ)
            input.use { src ->
                part.outputStream().use { dst -> CappedCopy.copy(src, dst, limits.maxFileBytes) }
            }

            val head = ByteArray(6)
            val read = part.inputStream().use { it.read(head) }
            if (!GifReader.hasGifSignature(head, maxOf(read, 0))) {
                return ImportResult.Failure(FailureReason.NOT_GIF)
            }

            val reader = GifReader.open(part.readBytes(), limits)
            reader.decode { _, _, _ -> false } // proves the first frame decodes
            val info = reader.info

            val meta = AnimationMeta(
                id = id,
                name = AnimationNames.sanitize(nameOverride ?: displayName(context, uri), fallbackName),
                createdAt = System.currentTimeMillis(),
                srcWidth = info.width,
                srcHeight = info.height,
                frameCount = info.frameCount,
                fps = info.averageFps,
                hasAlpha = info.usesTransparency,
                bytes = part.length(),
            )
            AtomicFiles.moveInto(part, File(dir, LibraryStore.ORIGINAL_FILE))
            store.writeMeta(meta)
            keep = true
            return ImportResult.Success(meta)
        } catch (e: GifException) {
            return ImportResult.Failure(e.reason.toFailure())
        } catch (e: IOException) {
            return ImportResult.Failure(FailureReason.CANNOT_READ)
        } catch (e: SecurityException) {
            return ImportResult.Failure(FailureReason.CANNOT_READ)
        } catch (e: OutOfMemoryError) {
            return ImportResult.Failure(FailureReason.OUT_OF_MEMORY)
        } finally {
            if (!keep) dir.deleteRecursively()
        }
    }

    private fun GifException.Reason.toFailure(): FailureReason = when (this) {
        GifException.Reason.NOT_GIF -> FailureReason.NOT_GIF
        GifException.Reason.FILE_TOO_LARGE -> FailureReason.FILE_TOO_LARGE
        GifException.Reason.DIMENSIONS_TOO_BIG -> FailureReason.DIMENSIONS_TOO_BIG
        GifException.Reason.TOO_MANY_FRAMES -> FailureReason.TOO_MANY_FRAMES
        GifException.Reason.TRUNCATED,
        GifException.Reason.CORRUPT,
        GifException.Reason.NO_FRAMES -> FailureReason.CORRUPT
    }

    /** The file name the picker reports for [uri] (null if unknown). Cheap: one small cursor query. */
    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }
}
