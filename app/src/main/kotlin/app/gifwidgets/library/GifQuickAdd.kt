package app.gifwidgets.library

import android.content.Context
import android.net.Uri
import app.gifwidgets.control.ControlStore
import app.gifwidgets.edit.EditorParams
import app.gifwidgets.edit.EditorSession
import app.gifwidgets.edit.ImageParams
import app.gifwidgets.edit.OptimizedWriter
import app.gifwidgets.edit.QuickFit
import app.gifwidgets.edit.SaveFailure
import app.gifwidgets.edit.SaveResult
import app.gifwidgets.edit.TimelineParams
import app.gifwidgets.gif.GifException
import app.gifwidgets.gif.GifReader
import app.gifwidgets.memory.ScreenMetrics
import java.io.File
import java.io.IOException

/**
 * "Elegir GIF" from the widget configuration: import a GIF and save it optimized in one go, with the same
 * automatic choices (speed, size, sharp or smooth by kind of GIF: see [QuickFit]) and the memory fit,
 * so the widget can use it right away. All or nothing: if the second step fails, the imported copy is deleted.
 * Call from a background thread. Never throws for bad input.
 */
object GifQuickAdd {

    sealed interface Result {
        data class Success(val meta: AnimationMeta) : Result
        data class ImportFailed(val reason: FailureReason) : Result
        data class SaveFailed(val reason: SaveFailure) : Result
    }

    /** [onProgress] gets 0f..1f while the frames are written (the import step itself has no progress). */
    fun add(
        context: Context,
        uri: Uri,
        fallbackName: String,
        onProgress: (Float) -> Unit = {},
        nameOverride: String? = null,
    ): Result {
        val app = context.applicationContext
        val imported = GifImporter.import(app, uri, fallbackName, nameOverride = nameOverride)
        val meta = when (imported) {
            is ImportResult.Failure -> return Result.ImportFailed(imported.reason)
            is ImportResult.Success -> imported.meta
        }
        val store = LibraryStore(app)
        fun fail(reason: SaveFailure): Result {
            store.delete(meta.id)
            return Result.SaveFailed(reason)
        }
        try {
            val appSettings = ControlStore(app).load()
            val session = EditorSession(GifReader.open(File(store.dirFor(meta.id), LibraryStore.ORIGINAL_FILE).readBytes()))
            val screen = ScreenMetrics.physicalSizePx(app)
            // Automatic: the GIF's own speed (up to 20 fps), and size / frames chosen by the kind of GIF (QuickFit).
            val choice = QuickFit.choose(
                session.delaysCs, session.width, session.height, meta.fps, session.usesTransparency,
                screen.width, screen.height,
            )
            val params = EditorParams(
                timeline = TimelineParams(fps = choice.fps),
                image = ImageParams(maxSide = choice.maxSide, sharpPixels = appSettings.sharpPixels && choice.pixelArt),
                lossless = choice.lossless,
                quality = 85,
            )

            return when (
                val saved = OptimizedWriter.save(
                    app, meta, params,
                    onProgress = { done, total -> onProgress(if (total > 0) done.toFloat() / total else 0f) },
                    isCancelled = { false },
                )
            ) {
                is SaveResult.Success -> {
                    if (!appSettings.keepOriginals) store.deleteOriginal(meta.id)
                    Result.Success(saved.meta)
                }
                is SaveResult.Failure -> fail(saved.reason)
                SaveResult.Cancelled -> fail(SaveFailure.CANNOT_READ) // cannot happen: isCancelled is always false
            }
        } catch (e: GifException) {
            return fail(SaveFailure.CORRUPT)
        } catch (e: IOException) {
            return fail(SaveFailure.CANNOT_READ)
        } catch (e: OutOfMemoryError) {
            return fail(SaveFailure.OUT_OF_MEMORY)
        }
    }
}
