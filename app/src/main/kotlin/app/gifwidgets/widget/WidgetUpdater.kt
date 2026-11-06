package app.gifwidgets.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import app.gifwidgets.MainActivity
import app.gifwidgets.R
import app.gifwidgets.control.ControlState
import app.gifwidgets.control.ControlStore
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.LibraryStore
import app.gifwidgets.memory.WidgetShare
import app.gifwidgets.util.runGuarded
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Builds the RemoteViews and pushes a FULL update for one widget. The launcher moves the animation
 * (ViewFlipper); we never update per frame. Call from a background thread.
 */
object WidgetUpdater {
    /** Memory levels tried in order when the system refuses the bitmaps (percent of the platform cap). */
    private const val MIN_USAGE = 8

    /** One lock per widget id: updates of the same widget never overlap (they share files and would clobber each other). */
    private val locks = ConcurrentHashMap<Int, Any>()

    private fun lockFor(appWidgetId: Int): Any = locks.getOrPut(appWidgetId) { Any() }

    /** Runs [block] while holding the widget's lock (reentrant), for read-modify-write of its settings. */
    fun <T> locked(appWidgetId: Int, block: () -> T): T = synchronized(lockFor(appWidgetId), block)

    fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        synchronized(lockFor(appWidgetId)) { updateLocked(context, manager, appWidgetId) }
        // Widgets in "Máxima calidad" mode are played by a service: let it re-check what it has to play.
        HighQualityService.requestSync()
    }

    /**
     * Quick update while a widget is being resized: only the resting frame at the new size, as a still picture.
     * The full (animated) update follows once the resizing has stopped. Does nothing if the widget is not
     * animating (the full update is then just as cheap) or cannot show anything yet.
     */
    fun updateQuick(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        synchronized(lockFor(appWidgetId)) {
            val store = WidgetStore(context)
            if (!store.hasSettings(appWidgetId)) return
            val settings = store.loadSettings(appWidgetId)
            if (!ControlState.isAnimating(context, settings)) return
            val meta = settings.animationId
                ?.takeIf { AnimationMeta.isValidId(it) }
                ?.let { LibraryStore(context).read(it) }
                ?.takeIf { it.isOptimized } ?: return
            val (widthPx, heightPx) = sizePx(context, manager, appWidgetId)
            try {
                val rendered = WidgetRenderer.render(
                    context, appWidgetId, settings, meta, widthPx, heightPx, store.readUsage(appWidgetId), false,
                )
                manager.updateAppWidget(appWidgetId, build(context, appWidgetId, settings, meta, rendered, false, false))
            } catch (e: RuntimeException) {
                // best effort: the full update that follows decides what the widget shows
            } catch (e: OutOfMemoryError) {
            } catch (e: java.io.IOException) {
            }
        }
    }

    /** The widget was removed: forget its files. Waits for an update that is running right now. */
    fun forget(context: Context, appWidgetId: Int) {
        synchronized(lockFor(appWidgetId)) {
            WidgetStore(context).delete(appWidgetId)
            WidgetActionReceiver.cancelPlayEnd(context, appWidgetId)
        }
        locks.remove(appWidgetId)
        HighQualityService.halt(appWidgetId)
        HighQualityService.requestSync()
    }

    /** How many placed widgets show (or rotate through) this animation. */
    fun countUsing(context: Context, animationId: String): Int {
        val store = WidgetStore(context)
        return allIds(context, AppWidgetManager.getInstance(context)).count { id ->
            store.hasSettings(id) && store.loadSettings(id).let { it.animationId == animationId || animationId in it.rotation }
        }
    }

    /** Deletes the folders of widgets that no longer exist (an update that raced a removal can leave one behind). */
    fun cleanOrphans(context: Context) {
        val root = File(context.applicationContext.filesDir, "widgets")
        val dirs = root.listFiles { f -> f.isDirectory } ?: return
        val live = allIds(context, AppWidgetManager.getInstance(context)).map { it.toString() }.toSet()
        // Never clean when the system reports no widget at all (it may simply not be ready yet, e.g. right after boot).
        if (live.isEmpty()) return
        val tooNew = System.currentTimeMillis() - 10 * 60_000L
        for (d in dirs) {
            val id = d.name.toIntOrNull() ?: continue
            if (d.name in live || d.lastModified() > tooNew) continue
            synchronized(lockFor(id)) { d.deleteRecursively() }
            locks.remove(id)
        }
    }

    private fun updateLocked(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val store = WidgetStore(context)
        // A widget placed without the configuration screen has no settings yet: set it up by itself.
        val settings = if (store.hasSettings(appWidgetId)) {
            store.loadSettings(appWidgetId)
        } else {
            autoSetup(context, store, appWidgetId)
        }
        if (settings == null) {
            HighQualityService.halt(appWidgetId)
            manager.updateAppWidget(appWidgetId, placeholder(context, appWidgetId))
            return
        }
        val meta = settings.animationId
            ?.takeIf { AnimationMeta.isValidId(it) }
            ?.let { LibraryStore(context).read(it) }
            ?.takeIf { it.isOptimized }
        if (meta == null) {
            HighQualityService.halt(appWidgetId)
            manager.updateAppWidget(appWidgetId, placeholder(context, appWidgetId))
            return
        }
        val (widthPx, heightPx) = sizePx(context, manager, appWidgetId)
        // Decided once: the renderer builds only what this state needs, and build() must agree with it.
        val animating = ControlState.isAnimating(context, settings)
        // "Máxima calidad": the service plays the frames, this update only draws the still image it starts from.
        // If the system does not let us start the service now, the widget uses the normal mode.
        val hq = settings.highQuality && animating && HighQualityService.start(context)
        if (!hq) HighQualityService.halt(appWidgetId) // no stale frame may land after the still image below
        val wantAnimated = animating && !hq
        // Animated widgets share the launcher's memory: with many of them each one gets a smaller share.
        val share = if (wantAnimated) WidgetShare.usagePercent(animatedOthers(context, manager, appWidgetId) + 1) else 100
        val limit = minOf(store.readUsageFor(appWidgetId, share), share)
        var usage = limit
        while (true) {
            try {
                val rendered = WidgetRenderer.render(context, appWidgetId, settings, meta, widthPx, heightPx, usage, wantAnimated)
                manager.updateAppWidget(appWidgetId, build(context, appWidgetId, settings, meta, rendered, wantAnimated, hq))
                // Remembered together with the share it was reached under (see UsageFile).
                store.writeUsage(appWidgetId, usage, share)
                // The widget is already on screen: now the slow part (cache files), never blocking what the user sees.
                runGuarded { rendered.persist?.invoke() }
                return
            } catch (e: RuntimeException) {
                // The system refused the update (for example too much bitmap memory): retry with fewer pixels.
                usage = retryUsage(usage) ?: break
            } catch (e: OutOfMemoryError) {
                usage = retryUsage(usage) ?: break
            } catch (e: java.io.IOException) {
                break
            }
        }
        manager.updateAppWidget(appWidgetId, placeholder(context, appWidgetId))
    }

    /**
     * First setup of a widget that was added without opening the configuration (Android 12+
     * configuration_optional): the animation chosen with "Añadir a pantalla de inicio" if there is a recent one,
     * otherwise the newest optimized animation, with the default look. Returns null when the library has no
     * optimized animation yet; nothing is saved then, so the next update tries again.
     */
    private fun autoSetup(context: Context, store: WidgetStore, appWidgetId: Int): WidgetSettings? {
        val animationId = WidgetPinner.peekPending(context)
            ?: LibraryStore(context).list().firstOrNull { it.isOptimized }?.id
            ?: return null
        val globalSharp = ControlStore(context).load().sharpPixels
        val meta = LibraryStore(context).read(animationId)
        val settings = if (meta != null) {
            WidgetSettings.forAnimation(meta, globalSharp)
        } else {
            WidgetSettings(animationId = animationId, sharp = globalSharp)
        }
        store.saveSettings(appWidgetId, settings)
        store.writeUsage(appWidgetId, WidgetStore.DEFAULT_USAGE)
        WidgetPinner.clearPending(context)
        return settings
    }

    /** Ids of the widgets that flip bitmaps right now. Cached for a few seconds: updateAll asks once per widget. */
    @Volatile private var animatedCache: Pair<Long, Set<Int>>? = null

    private fun animatedIds(context: Context, manager: AppWidgetManager): Set<Int> {
        val now = System.currentTimeMillis()
        animatedCache?.let { (at, ids) -> if (now - at < 3_000L) return ids }
        val store = WidgetStore(context)
        val ids = allIds(context, manager).filter { id ->
            store.hasSettings(id) && store.loadSettings(id).let { !it.highQuality && ControlState.isAnimating(context, it) }
        }.toSet()
        animatedCache = now to ids
        return ids
    }

    /** How many OTHER widgets flip bitmaps right now (the one being updated counts itself). */
    private fun animatedOthers(context: Context, manager: AppWidgetManager, appWidgetId: Int): Int =
        (animatedIds(context, manager) - appWidgetId).size

    /** For the Settings screen: how many widgets animate by flipping bitmaps right now. */
    fun animatedWidgetCount(context: Context): Int {
        animatedCache = null
        return animatedIds(context, AppWidgetManager.getInstance(context)).size
    }

    /** Ids of the animations used by any placed widget (shown, or in its "next animation" cycle). */
    fun usedAnimationIds(context: Context): Set<String> {
        val store = WidgetStore(context)
        val used = HashSet<String>()
        for (id in allIds(context, AppWidgetManager.getInstance(context))) {
            if (!store.hasSettings(id)) continue
            val s = store.loadSettings(id)
            s.animationId?.let { used.add(it) }
            used.addAll(s.rotation)
        }
        return used
    }

    private fun retryUsage(usage: Int): Int? {
        val next = (usage * 0.6).toInt()
        return if (next < MIN_USAGE) null else next
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        animatedCache = null // the states may just have changed (master switch, night, battery saver)
        // One broken widget must not stop the others.
        for (id in allIds(context, manager)) runGuarded { update(context, manager, id) }
    }

    /** Every placed widget of every size variant (2x2, 4x2, 4x4). */
    fun allIds(context: Context, manager: AppWidgetManager): IntArray =
        PROVIDERS.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }.toIntArray()

    private val PROVIDERS = listOf(
        GifWidgetProvider::class.java,
        GifWidgetProvider2x2::class.java,
        GifWidgetProvider4x4::class.java,
    )

    /** Widget size in pixels, using the shape (portrait or landscape) that matches how the phone is held now. */
    fun sizePx(context: Context, manager: AppWidgetManager, appWidgetId: Int): Pair<Int, Int> {
        val o = manager.getAppWidgetOptions(appWidgetId)
        val d = context.resources.displayMetrics.density
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        var sizes: List<Pair<Float, Float>> = emptyList()
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val list: List<SizeF>? = o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            sizes = list.orEmpty().map { it.width to it.height }
        }
        val dp = WidgetSizing.pick(
            sizes,
            o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
            o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
            o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
            o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
            landscape,
        ) ?: (DEFAULT_WIDTH_DP.toFloat() to DEFAULT_HEIGHT_DP.toFloat())
        return maxOf(16, (dp.first * d).roundToInt()) to maxOf(16, (dp.second * d).roundToInt())
    }

    private fun build(
        context: Context,
        appWidgetId: Int,
        settings: WidgetSettings,
        meta: AnimationMeta,
        r: RenderedWidget,
        wantAnimated: Boolean,
        /** Played by [HighQualityService]: a still layout (the service sends the frames) that looks "playing". */
        hq: Boolean,
    ): RemoteViews {
        val pkg = context.packageName
        val flipping = wantAnimated && r.frames.size > 1
        val animated = flipping || hq
        val views = RemoteViews(pkg, if (flipping) R.layout.widget_animated else R.layout.widget_static)
        if (flipping) {
            views.removeAllViews(R.id.widget_flipper)
            for (bmp in r.frames) {
                val child = RemoteViews(pkg, R.layout.widget_frame)
                child.setImageViewBitmap(R.id.frame_image, bmp)
                views.addView(R.id.widget_flipper, child)
            }
            // VERIFY: ViewFlipper.setFlipInterval is a remotable method (documented in the platform source).
            views.setInt(R.id.widget_flipper, "setFlipInterval", r.intervalMs)
        } else {
            // rest is always built when the widget is still (see WidgetRenderer); the fallback is only for safety.
            views.setImageViewBitmap(R.id.widget_image, r.rest ?: r.frames.firstOrNull())
        }
        views.setContentDescription(R.id.widget_root, meta.name)
        bindButtons(context, views, appWidgetId, settings, animated)
        val tapIntent = when (settings.tap) {
            TapAction.TOGGLE -> WidgetActionReceiver.pendingFor(context, WidgetActionReceiver.ACTION_TOGGLE, appWidgetId)
            TapAction.TOGGLE_ALL -> WidgetActionReceiver.pendingFor(context, WidgetActionReceiver.ACTION_TOGGLE_ALL, appWidgetId)
            TapAction.NEXT_ANIMATION -> WidgetActionReceiver.pendingFor(context, WidgetActionReceiver.ACTION_NEXT, appWidgetId)
            TapAction.PLAY_ONCE -> WidgetActionReceiver.pendingFor(context, WidgetActionReceiver.ACTION_PLAY, appWidgetId)
            TapAction.OPEN_APP -> openAppPending(context)
            TapAction.NONE -> null
        }
        if (tapIntent != null) views.setOnClickPendingIntent(R.id.widget_root, tapIntent)
        return views
    }

    private fun placeholder(context: Context, appWidgetId: Int): RemoteViews {
        // A clean "empty" card in the app's look (see layout/widget_empty.xml), not a test picture.
        val views = RemoteViews(context.packageName, R.layout.widget_empty)
        views.setContentDescription(R.id.widget_root, context.getString(R.string.widget_placeholder_description))
        // Tapping an empty widget opens its configuration, where "Elegir GIF" is.
        views.setOnClickPendingIntent(R.id.widget_root, configurePending(context, appWidgetId))
        return views
    }

    private fun configurePending(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetConfigureActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // One PendingIntent per widget (extras do not make them different, the request code does).
        return PendingIntent.getActivity(
            context, appWidgetId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private val BUTTON_IDS = intArrayOf(R.id.widget_btn_tl, R.id.widget_btn_tr, R.id.widget_btn_bl, R.id.widget_btn_br)

    private fun bindButtons(context: Context, views: RemoteViews, appWidgetId: Int, s: WidgetSettings, animated: Boolean) {
        val selected = when (s.buttonCorner) {
            ButtonCorner.TOP_START -> R.id.widget_btn_tl
            ButtonCorner.TOP_END -> R.id.widget_btn_tr
            ButtonCorner.BOTTOM_START -> R.id.widget_btn_bl
            ButtonCorner.BOTTOM_END -> R.id.widget_btn_br
        }
        val pending = WidgetActionReceiver.pendingFor(context, WidgetActionReceiver.ACTION_TOGGLE, appWidgetId)
        val label = context.getString(if (animated) R.string.widget_button_pause else R.string.widget_button_play)
        for (id in BUTTON_IDS) {
            if (s.showButton && s.tap != TapAction.PLAY_ONCE && id == selected) {
                views.setViewVisibility(id, View.VISIBLE)
                views.setImageViewResource(id, if (animated) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                views.setContentDescription(id, label)
                views.setOnClickPendingIntent(id, pending)
            } else {
                views.setViewVisibility(id, View.GONE)
            }
        }
    }

    private fun openAppPending(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // Same as widget_info.xml minWidth/minHeight, used until the launcher reports a real size.
    private const val DEFAULT_WIDTH_DP = 250
    private const val DEFAULT_HEIGHT_DP = 110
}
