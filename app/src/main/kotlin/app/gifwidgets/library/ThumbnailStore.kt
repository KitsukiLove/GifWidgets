package app.gifwidgets.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.gifwidgets.gif.GifException
import app.gifwidgets.gif.GifReader
import java.io.File
import java.io.IOException

/** Frames of a library thumbnail, small Bitmaps ready to draw, plus how long each stays on screen. */
class ThumbnailFrames(val bitmaps: List<Bitmap>, val frameMillis: Long)

/**
 * Small animated previews for the library, cached as PNGs in <animation>/thumbs/.
 * Made on demand the first time an animation is shown, then reused. They come from the optimized
 * frames (the frames/ folder) when the animation has been saved from the editor, otherwise from original.gif.
 * The editor deletes thumbs/ when it saves. Call from a background thread. One generation at a time.
 */
object ThumbnailStore {
    const val DIR_NAME = "thumbs"
    private const val DONE_MARKER = "done"
    private val lock = Any()

    fun load(context: Context, meta: AnimationMeta): ThumbnailFrames? = synchronized(lock) {
        val store = LibraryStore(context)
        val animDir = store.dirFor(meta.id)
        val dir = File(animDir, DIR_NAME)
        val plan = ThumbnailPlan.plan(meta.displayFrames, meta.displayWidth, meta.displayHeight)
        try {
            if (!File(dir, DONE_MARKER).exists()) {
                if (meta.isOptimized) {
                    generateFromFrames(store.framesDir(meta.id), dir, plan)
                } else {
                    generateFromGif(File(animDir, LibraryStore.ORIGINAL_FILE), dir, plan)
                }
            }
            val files = dir.listFiles { f -> f.name.endsWith(".png") }?.sortedBy { it.name }
                ?: return null
            val bitmaps = files.mapNotNull { BitmapFactory.decodeFile(it.path) }
            if (bitmaps.isEmpty()) {
                dir.deleteRecursively()
                null
            } else {
                ThumbnailFrames(bitmaps, ThumbnailPlan.frameMillis(meta.displayFps, plan.stride))
            }
        } catch (e: GifException) {
            dir.deleteRecursively(); null
        } catch (e: IOException) {
            dir.deleteRecursively(); null
        } catch (e: OutOfMemoryError) {
            dir.deleteRecursively(); null
        }
    }

    /** Just the first frame (a still thumbnail for pickers): the same files as [load], but only one is decoded. */
    fun loadFirst(context: Context, meta: AnimationMeta): Bitmap? = synchronized(lock) {
        val store = LibraryStore(context)
        val animDir = store.dirFor(meta.id)
        val dir = File(animDir, DIR_NAME)
        val plan = ThumbnailPlan.plan(meta.displayFrames, meta.displayWidth, meta.displayHeight)
        try {
            if (!File(dir, DONE_MARKER).exists()) {
                if (meta.isOptimized) {
                    generateFromFrames(store.framesDir(meta.id), dir, plan)
                } else {
                    generateFromGif(File(animDir, LibraryStore.ORIGINAL_FILE), dir, plan)
                }
            }
            val first = dir.listFiles { f -> f.name.endsWith(".png") }?.minByOrNull { it.name } ?: return null
            BitmapFactory.decodeFile(first.path)
        } catch (e: GifException) {
            dir.deleteRecursively(); null
        } catch (e: IOException) {
            dir.deleteRecursively(); null
        } catch (e: OutOfMemoryError) {
            dir.deleteRecursively(); null
        }
    }

    private fun writeThumb(full: Bitmap, dir: File, number: Int, plan: ThumbnailPlan) {
        val small = if (plan.width == full.width && plan.height == full.height) {
            full
        } else {
            Bitmap.createScaledBitmap(full, plan.width, plan.height, true)
        }
        File(dir, "%03d.png".format(number)).outputStream().use {
            small.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        if (small !== full) small.recycle()
    }

    private fun generateFromGif(original: File, dir: File, plan: ThumbnailPlan) {
        dir.deleteRecursively()
        if (!dir.mkdirs()) throw IOException("Cannot create $dir")
        val reader = GifReader.open(original.readBytes())
        val w = reader.info.width
        val h = reader.info.height
        var written = 0
        reader.decode { index, _, canvas ->
            if (index % plan.stride == 0) {
                val full = Bitmap.createBitmap(canvas, w, h, Bitmap.Config.ARGB_8888)
                writeThumb(full, dir, written, plan)
                full.recycle()
                written++
            }
            true
        }
        File(dir, DONE_MARKER).writeText("ok")
    }

    private fun generateFromFrames(framesDir: File, dir: File, plan: ThumbnailPlan) {
        dir.deleteRecursively()
        if (!dir.mkdirs()) throw IOException("Cannot create $dir")
        val files = framesDir.listFiles { f -> f.name.endsWith(".webp") }?.sortedBy { it.name }
            ?: throw IOException("No frames")
        var written = 0
        for ((i, f) in files.withIndex()) {
            if (i % plan.stride != 0) continue
            val full = BitmapFactory.decodeFile(f.path) ?: throw IOException("Bad frame ${f.name}")
            writeThumb(full, dir, written, plan)
            full.recycle()
            written++
        }
        File(dir, DONE_MARKER).writeText("ok")
    }
}
