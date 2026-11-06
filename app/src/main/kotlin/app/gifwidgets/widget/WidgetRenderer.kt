package app.gifwidgets.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import app.gifwidgets.gif.GifKind
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.AtomicFiles
import app.gifwidgets.library.LibraryStore
import app.gifwidgets.memory.BitmapFormat
import app.gifwidgets.memory.ScreenMetrics
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * What the widget shows: bitmaps already drawn at the exact size, ready for RemoteViews.
 * Only what is needed is built: [frames] when the widget animates, [rest] when it is still (the other one is
 * empty / null), so pausing never decodes the whole animation.
 * [persist] (null when everything came from the cache) writes the cache; call it AFTER the widget was updated,
 * so writing the cache never delays what the user sees.
 */
class RenderedWidget(
    val frames: List<Bitmap>,
    val rest: Bitmap?,
    val intervalMs: Int,
    val plan: WidgetPlan,
    val persist: (() -> Unit)? = null,
)

/**
 * Renders a widget from the animation's saved WebP frames (frames/000.webp...) and caches the result in
 * filesDir/widgets/<id>/cache/, so the usual update (toggle, reboot, launcher refresh) only decodes small files.
 * The cache key changes when the widget size, memory level, animation or look changes. The cache may be partial
 * (only the resting frame, or only the animation frames): a missing file is simply a cache miss.
 * Call from a background thread, one render per widget at a time (WidgetUpdater holds the lock).
 */
object WidgetRenderer {

    fun render(
        context: Context,
        appWidgetId: Int,
        settings: WidgetSettings,
        meta: AnimationMeta,
        widthPx: Int,
        heightPx: Int,
        usagePercent: Int,
        animated: Boolean,
    ): RenderedWidget {
        val files = frameFiles(context, meta)
        val screen = ScreenMetrics.physicalSizePx(context)
        val use565 = canUseRgb565(settings, meta)
        val format = if (use565) BitmapFormat.RGB_565 else BitmapFormat.ARGB_8888
        val config = if (use565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        // A "Máxima calidad" widget that is not flipping bitmaps (paused, or the service plays it) shows ONE still
        // image: full widget size, no memory fit.
        val fullRes = settings.highQuality && !animated
        val (bmpW, bmpH) = if (fullRes) widthPx to heightPx else bitmapSize(settings, meta, widthPx, heightPx)
        val plan = WidgetPlanner.plan(
            if (fullRes) 1 else files.size, meta.outFps, settings.speedPercent, bmpW, bmpH,
            screen.width, screen.height, usagePercent, format,
            smooth = isSmoothKind(meta),
        )
        val restIndex = (meta.params?.timeline?.restFrame ?: 0).coerceIn(0, files.lastIndex)
        val key = listOf(
            "v3", fullRes, meta.id, meta.editedAt, "${plan.width}x${plan.height}", plan.stride, usagePercent, use565,
            settings.visualKey(), restIndex,
        ).joinToString("|")
        val wantFrames = animated && plan.keptFrames > 1

        val cacheDir = WidgetStore(context).cacheDir(appWidgetId)
        readCache(cacheDir, key, wantFrames, plan.keptFrames, config)?.let { (frames, rest) ->
            return RenderedWidget(frames, rest, plan.intervalMs, plan)
        }

        val unit = context.resources.displayMetrics.density * plan.width / widthPx
        val natural = plan.width.toFloat() / widthPx

        fun draw(index: Int): Bitmap {
            val src = BitmapFactory.decodeFile(files[index].path) ?: throw IOException("Bad frame ${files[index].name}")
            try {
                return WidgetCompositor.compose(src, plan.width, plan.height, settings, unit, natural, config)
            } finally {
                src.recycle()
            }
        }

        val frames = ArrayList<Bitmap>(if (wantFrames) plan.keptFrames else 0)
        var rest: Bitmap? = null
        if (wantFrames) {
            frames.addAll(drawAll(plan.keptIndices, ::draw))
        } else {
            rest = draw(restIndex)
        }
        val persist = {
            writeCache(cacheDir, key, frames, rest)
        }
        return RenderedWidget(frames, rest, plan.intervalMs, plan, persist)
    }

    /**
     * Decodes and composes the frames on up to [PARALLEL] threads, in order. With 40+ frames this is two to three
     * times faster than one after the other (a resize or a first render no longer leaves the old image on screen
     * for seconds). At most [PARALLEL] decoded source frames are alive at once.
     */
    private fun drawAll(indices: List<Int>, draw: (Int) -> Bitmap): List<Bitmap> {
        val workers = minOf(PARALLEL, indices.size)
        if (workers <= 1) return indices.map(draw)
        val pool = Executors.newFixedThreadPool(workers)
        val futures = indices.map { index -> pool.submit<Bitmap> { draw(index) } }
        try {
            return futures.map { it.get() }
        } catch (e: ExecutionException) {
            throw e.cause ?: e // IOException / OutOfMemoryError as if it had happened on this thread
        } finally {
            futures.forEach { it.cancel(true) }
            pool.shutdownNow()
        }
    }

    /** Best effort: the cache is only a speed-up, so a failure here is not an error. */
    private fun writeCache(dir: File, key: String, frames: List<Bitmap>, rest: Bitmap?) {
        try {
            val keyFile = File(dir, KEY_FILE)
            val sameKey = keyFile.isFile && keyFile.readText(Charsets.UTF_8) == key
            if (!sameKey) {
                dir.deleteRecursively()
            }
            if (!dir.isDirectory && !dir.mkdirs()) return
            // The key goes last: a crash in the middle leaves files the key does not vouch for.
            if (!sameKey) keyFile.delete()
            for ((n, bmp) in frames.withIndex()) writeBitmap(File(dir, "f%03d.img".format(n)), bmp)
            if (rest != null) writeBitmap(File(dir, REST_FILE), rest)
            AtomicFiles.writeBytes(keyFile, key.toByteArray(Charsets.UTF_8))
        } catch (e: IOException) {
            // ignored on purpose
        }
    }

    /** One still frame for the configuration preview, at most [maxSide] px on its longest side. */
    fun renderPreview(
        context: Context,
        settings: WidgetSettings,
        meta: AnimationMeta,
        widthPx: Int,
        heightPx: Int,
        maxSide: Int = 640,
    ): Bitmap? {
        val files = try {
            frameFiles(context, meta)
        } catch (e: IOException) {
            return null
        }
        val restIndex = (meta.params?.timeline?.restFrame ?: 0).coerceIn(0, files.lastIndex)
        val k = minOf(1f, maxSide.toFloat() / maxOf(widthPx, heightPx))
        val w = maxOf(1, (widthPx * k).toInt())
        val h = maxOf(1, (heightPx * k).toInt())
        val src = BitmapFactory.decodeFile(files[restIndex].path) ?: return null
        return try {
            WidgetCompositor.compose(src, w, h, settings, context.resources.displayMetrics.density * w / widthPx, w.toFloat() / widthPx)
        } finally {
            src.recycle()
        }
    }

    /** A few small composed frames for the animated preview of the configuration screen. */
    class PreviewFrames(val frames: List<Bitmap>, val frameMs: Long)

    /**
     * The configuration preview as a short loop: at most [maxFrames] frames (evenly spread), each at most [maxSide] px,
     * drawn with the current look. Plays at the widget's speed. Null when the animation cannot be read.
     */
    fun renderPreviewFrames(
        context: Context,
        settings: WidgetSettings,
        meta: AnimationMeta,
        widthPx: Int,
        heightPx: Int,
        maxSide: Int = 360,
        maxFrames: Int = 16,
    ): PreviewFrames? {
        val files = try {
            frameFiles(context, meta)
        } catch (e: IOException) {
            return null
        }
        val stride = ((files.size + maxFrames - 1) / maxFrames).coerceAtLeast(1)
        val k = minOf(1f, maxSide.toFloat() / maxOf(widthPx, heightPx))
        val w = maxOf(1, (widthPx * k).toInt())
        val h = maxOf(1, (heightPx * k).toInt())
        val unit = context.resources.displayMetrics.density * w / widthPx
        val frames = ArrayList<Bitmap>()
        for (i in files.indices step stride) {
            val src = BitmapFactory.decodeFile(files[i].path) ?: return null
            try {
                frames.add(WidgetCompositor.compose(src, w, h, settings, unit, w.toFloat() / widthPx))
            } finally {
                src.recycle()
            }
        }
        val ms = (1000f / meta.outFps.coerceAtLeast(1) * stride * 100f / settings.speedPercent.coerceAtLeast(1)).toLong()
        return PreviewFrames(frames, ms.coerceIn(40L, 1000L))
    }

    /**
     * Size of the bitmaps for a widget of [widthPx] x [heightPx]. When the picture is drawn BIGGER than its stored
     * pixels (a 480 px GIF in a 1000 px widget) the extra pixels carry no detail but cost memory, and memory is what
     * limits how many frames fit: so the bitmap is made smaller and the launcher scales it up (it does that
     * smoothly). Not for crisp pixel art, which needs the enlargement done with hard edges here.
     */
    fun bitmapSize(settings: WidgetSettings, meta: AnimationMeta, widthPx: Int, heightPx: Int): Pair<Int, Int> {
        if (settings.sharp || meta.outWidth <= 0 || meta.outHeight <= 0) return widthPx to heightPx
        val drawnScale = ScaleMath.drawnScale(settings.scale, meta.outWidth, meta.outHeight, widthPx, heightPx, settings.zoomPercent)
        if (drawnScale <= 1f) return widthPx to heightPx
        return maxOf(16, (widthPx / drawnScale).roundToInt()) to maxOf(16, (heightPx / drawnScale).roundToInt())
    }

    /** Photos, banners and recordings: keep every frame and lose a little resolution first (see GifKind). */
    fun isSmoothKind(meta: AnimationMeta): Boolean = !GifKind.isPixelArt(meta.srcWidth, meta.srcHeight)

    fun canUseRgb565(settings: WidgetSettings, meta: AnimationMeta): Boolean =
        meta.params?.saveMemory == true && !meta.hasAlpha &&
            settings.cornerDp == 0 && settings.bgOpacity == 100 && settings.opacity == 100

    private fun frameFiles(context: Context, meta: AnimationMeta): List<File> {
        val dir = LibraryStore(context).framesDir(meta.id)
        val files = dir.listFiles { f -> f.name.endsWith(".webp") }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) throw IOException("No frames for ${meta.id}")
        return files
    }

    /** Frames when [wantFrames], else only the resting frame. Null = cache miss (wrong key, or a file is missing). */
    private fun readCache(
        dir: File,
        key: String,
        wantFrames: Boolean,
        count: Int,
        config: Bitmap.Config,
    ): Pair<List<Bitmap>, Bitmap?>? {
        val keyFile = File(dir, KEY_FILE)
        try {
            if (!keyFile.isFile || keyFile.readText(Charsets.UTF_8) != key) return null
        } catch (e: IOException) {
            return null
        }
        if (!wantFrames) {
            val rest = readBitmap(File(dir, REST_FILE), config) ?: return null
            return emptyList<Bitmap>() to rest
        }
        val frames = ArrayList<Bitmap>(count)
        for (n in 0 until count) {
            frames.add(readBitmap(File(dir, "f%03d.img".format(n)), config) ?: return null)
        }
        return frames to null
    }

    /**
     * The cache holds the raw pixels (size header + bytes): writing and reading them takes a few milliseconds.
     * It used to be lossless WebP, whose encoding took seconds for a long animation and was done while the
     * widget's update lock was held, so every later tap or resize waited for it.
     */
    private fun writeBitmap(file: File, bmp: Bitmap) {
        val buffer = ByteBuffer.allocate(bmp.byteCount)
        bmp.copyPixelsToBuffer(buffer)
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeInt(bmp.width)
            out.writeInt(bmp.height)
            out.write(buffer.array())
        }
    }

    /** Null when the file is missing, from an older format or cut short: a cache miss, never an error. */
    private fun readBitmap(file: File, config: Bitmap.Config): Bitmap? {
        val bytesPerPixel = if (config == Bitmap.Config.RGB_565) 2 else 4
        try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val w = input.readInt()
                val h = input.readInt()
                if (w !in 1..8192 || h !in 1..8192) return null
                if (file.length() != 8L + w.toLong() * h * bytesPerPixel) return null
                val bmp = Bitmap.createBitmap(w, h, config)
                val bytes = ByteArray(bmp.byteCount)
                input.readFully(bytes)
                bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
                return bmp
            }
        } catch (e: IOException) {
            return null
        }
    }

    private val PARALLEL = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
    private const val KEY_FILE = "key.txt"
    private const val REST_FILE = "rest.img"
}
