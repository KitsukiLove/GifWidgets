package app.gifwidgets.library

import android.content.Context
import app.gifwidgets.edit.HighQualityParams
import app.gifwidgets.edit.OptimizedWriter
import app.gifwidgets.edit.SaveResult
import java.io.File
import java.io.IOException

/**
 * The frames the "Máxima calidad" service plays: re-made from original.gif with the animation's own edits, at full
 * speed and size (see [HighQualityParams]). Without the original (deleted in Settings) the animation's normal
 * frames are played instead: still frame by frame, but only as good as they were saved.
 * Call [prepare] from a background thread.
 */
object HighQualityFrames {
    private const val STAMP_FILE = "stamp.txt"

    class FrameSet(val files: List<File>, val fps: Int, val fromOriginal: Boolean)

    enum class Result { READY, NO_ORIGINAL, FAILED }

    /** The set to play right now (the high quality one if it is up to date, else the normal frames), or null. */
    fun load(context: Context, meta: AnimationMeta): FrameSet? {
        val store = LibraryStore(context)
        val hq = store.hqFramesDir(meta.id)
        if (isUpToDate(hq, meta)) {
            val files = webpFiles(hq)
            val fps = try {
                File(hq, OptimizedWriter.HQ_FPS_FILE).readText(Charsets.UTF_8).trim().toInt()
            } catch (e: IOException) {
                0
            } catch (e: NumberFormatException) {
                0
            }
            if (files.isNotEmpty() && fps > 0) return FrameSet(files, fps, true)
        }
        val files = webpFiles(store.framesDir(meta.id))
        return if (files.isEmpty()) null else FrameSet(files, meta.outFps.coerceAtLeast(1), false)
    }

    fun isReady(context: Context, meta: AnimationMeta): Boolean =
        isUpToDate(LibraryStore(context).hqFramesDir(meta.id), meta)

    /** Makes the high quality set if it is missing or older than the animation's last save. */
    fun prepare(
        context: Context,
        meta: AnimationMeta,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result {
        val app = context.applicationContext
        val store = LibraryStore(app)
        if (isUpToDate(store.hqFramesDir(meta.id), meta)) return Result.READY
        if (!store.hasOriginal(meta.id)) return Result.NO_ORIGINAL
        val params = HighQualityParams.from(meta.params, meta.srcWidth, meta.srcHeight, meta.fps, meta.hasAlpha)
        return when (
            OptimizedWriter.save(
                app, meta, params,
                onProgress = { done, total -> onProgress(if (total > 0) done.toFloat() / total else 0f) },
                isCancelled = isCancelled,
                highQuality = true,
            )
        ) {
            is SaveResult.Success -> {
                try {
                    File(store.hqFramesDir(meta.id), STAMP_FILE).writeText(meta.editedAt.toString(), Charsets.UTF_8)
                    Result.READY
                } catch (e: IOException) {
                    Result.FAILED
                }
            }
            else -> Result.FAILED
        }
    }

    private fun isUpToDate(dir: File, meta: AnimationMeta): Boolean =
        try {
            dir.isDirectory && File(dir, STAMP_FILE).readText(Charsets.UTF_8).trim() == meta.editedAt.toString()
        } catch (e: IOException) {
            false
        }

    private fun webpFiles(dir: File): List<File> =
        dir.listFiles { f -> f.name.endsWith(".webp") }?.sortedBy { it.name }.orEmpty()
}
