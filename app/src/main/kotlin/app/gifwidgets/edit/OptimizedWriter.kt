package app.gifwidgets.edit

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import app.gifwidgets.gif.GifException
import app.gifwidgets.gif.GifReader
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.LibraryStore
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class SaveFailure { CANNOT_READ, CORRUPT, OUT_OF_MEMORY, ENCODE_FAILED }

sealed interface SaveResult {
    /** [beforeBytes] = original.gif, [afterBytes] = the new frames/ folder. */
    data class Success(val meta: AnimationMeta, val beforeBytes: Long, val afterBytes: Long) : SaveResult
    data object Cancelled : SaveResult
    data class Failure(val reason: SaveFailure) : SaveResult
}

/**
 * "Guardar optimizado": re-reads original.gif (so editing never accumulates loss), applies background,
 * crop and scaling to every frame that the timeline needs, and writes one WebP per output frame.
 *
 * Call from a background thread. Memory: the GIF is decoded one frame at a time; at most
 * [WORKERS] processed frames are in flight besides the decoder canvas.
 * Everything is written to frames.part/ and swapped in at the end, so a failure or cancel
 * leaves the previous result untouched.
 */
object OptimizedWriter {
    /** 2..4 frame encoders at once: more cores make importing several GIFs noticeably faster, memory stays bounded. */
    private val WORKERS = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    // VERIFY: for WEBP_LOSSLESS the quality value is the compression effort (0 fast ... 100 slow and smallest).
    private const val LOSSLESS_EFFORT = 75

    /** Output fps of the "Máxima calidad" set, next to its frames. */
    const val HQ_FPS_FILE = "fps.txt"

    fun save(
        context: Context,
        meta: AnimationMeta,
        params: EditorParams,
        onProgress: (done: Int, total: Int) -> Unit,
        isCancelled: () -> Boolean,
        /** true = write the "Máxima calidad" set (frames_hq/ plus fps.txt) and leave the animation's meta untouched. */
        highQuality: Boolean = false,
    ): SaveResult {
        val store = LibraryStore(context)
        val dir = store.dirFor(meta.id)
        val targetName = if (highQuality) LibraryStore.HQ_FRAMES_DIR else LibraryStore.FRAMES_DIR
        val part = File(dir, "$targetName.part")
        val framesDir = File(dir, targetName)
        val original = File(dir, LibraryStore.ORIGINAL_FILE)
        try {
            part.deleteRecursively()
            if (!part.mkdirs()) return SaveResult.Failure(SaveFailure.CANNOT_READ)

            val reader = GifReader.open(original.readBytes())
            val info = reader.info
            val w = info.width
            val h = info.height
            val plan = Timeline.plan(info.delaysCs, params.timeline)
            val needed = plan.neededSources.toSortedSet()
            val bg = params.background.sanitized()
            val lossy = !params.lossless && bg.mode == BackgroundMode.KEEP && !info.usesTransparency

            val total = needed.size * (if (params.image.autoCrop) 2 else 1) + 1
            val done = AtomicInteger(0)
            onProgress(0, total)

            // Pass 1 (only for auto-crop): bounding box of every non-transparent pixel of the kept frames.
            var autoBounds: CropRect? = null
            if (params.image.autoCrop) {
                val lock = Any()
                runPass(reader, needed, isCancelled) { _, frame ->
                    BackgroundRemover.apply(frame, w, h, bg)
                    val b = ImageMath.opaqueBounds(frame, w, h)
                    synchronized(lock) { autoBounds = ImageMath.union(autoBounds, b) }
                    onProgress(done.incrementAndGet(), total)
                }
                if (isCancelled()) return cancel(part)
                if (autoBounds == null) autoBounds = CropRect(0, 0, w, h) // fully transparent: keep everything
            }
            val crop = FrameProcessor.effectiveCrop(params.image, autoBounds)

            // Pass 2: process and encode each needed source frame once.
            val outW = AtomicInteger(0)
            val outH = AtomicInteger(0)
            // Real transparency of what is saved (the GIF's own flag is set by many GIFs that have none).
            val realAlpha = AtomicBoolean(false)
            runPass(reader, needed, isCancelled) { index, frame ->
                BackgroundRemover.apply(frame, w, h, bg)
                val pf = FrameProcessor.cropAndScale(frame, w, h, crop, params.image)
                outW.set(pf.width)
                outH.set(pf.height)
                if (!realAlpha.get() && AlphaScan.anyTransparent(pf.pixels)) realAlpha.set(true)
                val bmp = Bitmap.createBitmap(pf.pixels, pf.width, pf.height, Bitmap.Config.ARGB_8888)
                try {
                    File(part, "src_%03d.webp".format(index)).outputStream().use { out ->
                        if (!bmp.compress(webpFormat(lossy), if (lossy) params.quality else LOSSLESS_EFFORT, out)) {
                            throw IOException("WebP encoding failed")
                        }
                    }
                } finally {
                    bmp.recycle()
                }
                onProgress(done.incrementAndGet(), total)
            }
            if (isCancelled()) return cancel(part)

            // Assemble the output sequence (a source frame may appear several times) as 000.webp, 001.webp, ...
            var afterBytes = 0L
            for ((k, src) in plan.sourceIndices.withIndex()) {
                val from = File(part, "src_%03d.webp".format(src))
                val to = File(part, "%03d.webp".format(k))
                Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
                afterBytes += to.length()
            }
            for (index in needed) File(part, "src_%03d.webp".format(index)).delete()
            if (isCancelled()) return cancel(part)

            if (highQuality) File(part, HQ_FPS_FILE).writeText(plan.fps.toString(), Charsets.UTF_8)

            // Swap in the new frames.
            val old = File(dir, "$targetName.old")
            old.deleteRecursively()
            if (framesDir.exists() && !framesDir.renameTo(old)) throw IOException("Cannot replace frames")
            if (!part.renameTo(framesDir)) throw IOException("Cannot move frames into place")
            old.deleteRecursively()
            if (highQuality) {
                onProgress(total, total)
                return SaveResult.Success(meta, original.length(), afterBytes)
            }
            File(dir, "thumbs").deleteRecursively() // thumbnails are rebuilt from the new frames

            val updated = meta.copy(
                hasAlpha = realAlpha.get(),
                alphaChecked = true,
                outWidth = outW.get(),
                outHeight = outH.get(),
                outFrames = plan.frameCount,
                outFps = plan.fps,
                framesBytes = afterBytes,
                editedAt = System.currentTimeMillis(),
                params = params,
            )
            store.writeMeta(updated)
            onProgress(total, total)
            return SaveResult.Success(updated, original.length(), afterBytes)
        } catch (e: GifException) {
            part.deleteRecursively()
            return SaveResult.Failure(SaveFailure.CORRUPT)
        } catch (e: OutOfMemoryError) {
            part.deleteRecursively()
            return SaveResult.Failure(SaveFailure.OUT_OF_MEMORY)
        } catch (e: PassFailure) {
            part.deleteRecursively()
            return SaveResult.Failure(if (e.cause is OutOfMemoryError) SaveFailure.OUT_OF_MEMORY else SaveFailure.ENCODE_FAILED)
        } catch (e: IOException) {
            part.deleteRecursively()
            return SaveResult.Failure(SaveFailure.CANNOT_READ)
        } catch (e: InterruptedException) {
            part.deleteRecursively()
            Thread.currentThread().interrupt()
            return SaveResult.Cancelled
        }
    }

    private fun cancel(part: File): SaveResult {
        part.deleteRecursively()
        return SaveResult.Cancelled
    }

    private class PassFailure(cause: Throwable) : Exception(cause)

    @Suppress("DEPRECATION")
    private fun webpFormat(lossy: Boolean): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) {
            if (lossy) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            Bitmap.CompressFormat.WEBP // VERIFY: API 26-29 has only this constant (lossy at quality < 100)
        }

    /**
     * Decodes the GIF once, in order, and hands each needed frame (a private copy) to [task] on one of
     * [WORKERS] threads. At most [WORKERS] copies exist at a time, so memory stays bounded.
     */
    private fun runPass(
        reader: GifReader,
        needed: Set<Int>,
        isCancelled: () -> Boolean,
        task: (index: Int, frame: IntArray) -> Unit,
    ) {
        val pool = Executors.newFixedThreadPool(WORKERS)
        val permits = Semaphore(WORKERS)
        val failure = AtomicReference<Throwable?>(null)
        try {
            reader.decode { index, _, canvas ->
                if (isCancelled() || failure.get() != null) return@decode false
                if (index !in needed) return@decode true
                permits.acquire()
                val copy = try {
                    canvas.copyOf()
                } catch (e: OutOfMemoryError) {
                    permits.release()
                    failure.set(e)
                    return@decode false
                }
                pool.execute {
                    try {
                        if (!isCancelled() && failure.get() == null) task(index, copy)
                    } catch (t: Throwable) {
                        failure.compareAndSet(null, t)
                    } finally {
                        permits.release()
                    }
                }
                true
            }
            permits.acquire(WORKERS) // wait for the running tasks
            permits.release(WORKERS)
        } finally {
            pool.shutdown()
        }
        failure.get()?.let { throw PassFailure(it) }
    }
}
