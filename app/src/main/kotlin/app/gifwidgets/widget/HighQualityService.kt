package app.gifwidgets.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.widget.RemoteViews
import app.gifwidgets.MainActivity
import app.gifwidgets.R
import app.gifwidgets.control.ControlState
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.HighQualityFrames
import app.gifwidgets.library.LibraryStore
import app.gifwidgets.util.runGuarded
import java.util.concurrent.ConcurrentHashMap

/**
 * "Máxima calidad": plays the animations of the widgets that ask for it, frame by frame, from a foreground
 * service. Each frame is drawn at the widget's full size and sent as a partial update of the widget, so nothing
 * is limited by the launcher's bitmap memory (every frame, full resolution), like other GIF widget apps do.
 *
 * The price, shown to the user: a notification while it runs and more battery. To keep that as low as it can be:
 *  - it runs only while at least one widget in this mode is animating (pause, night, battery saver and the
 *    universal switch stop it: every widget update calls [requestSync] / [halt]);
 *  - it pauses completely while the screen is off.
 *
 * Android 12+ forbids starting a foreground service from the background; [start] returns false then and the
 * widget simply uses the normal mode (limited memory) until the app or the widget is touched again.
 */
class HighQualityService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private val players = ConcurrentHashMap<Int, Player>()
    @Volatile private var screenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            handler.post { sync() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("gifwidgets-hq").also { it.start() }
        handler = Handler(thread.looper)
        instance = this
        screenOn = (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive != false
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // Platform call on purpose (system broadcasts; the app declares no dynamic-receiver permission).
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNow()
        handler.post { sync() }
        return START_NOT_STICKY // never restarted from the background by the system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // not registered
        }
        for (p in players.values) p.requestStop()
        players.clear()
        thread.quitSafely()
        super.onDestroy()
    }

    private fun startForegroundNow() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.hq_channel_name), NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseAll = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_widget_pause),
            getString(R.string.hq_notification_pause),
            WidgetActionReceiver.pendingFor(this, WidgetActionReceiver.ACTION_TOGGLE_ALL, 0),
        ).build()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_animation)
            .setContentTitle(getString(R.string.hq_notification_title))
            .setContentText(getString(R.string.hq_notification_text))
            .setContentIntent(open)
            .addAction(pauseAll)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Makes the running players match the widgets that want one right now. Runs on the service's own thread. */
    private fun sync() {
        runGuarded {
            val app = applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val store = WidgetStore(app)
            val library = LibraryStore(app)
            val wanted = HashMap<Int, Spec>()
            var anyActive = false // in this mode and animating, even if the screen is off right now
            for (id in WidgetUpdater.allIds(app, manager)) {
                if (!store.hasSettings(id)) continue
                val settings = store.loadSettings(id)
                if (!settings.highQuality || !ControlState.isAnimating(app, settings)) continue
                val meta = settings.animationId
                    ?.takeIf { AnimationMeta.isValidId(it) }
                    ?.let { library.read(it) }
                    ?.takeIf { it.isOptimized } ?: continue
                anyActive = true
                val (w, h) = WidgetUpdater.sizePx(app, manager, id)
                wanted[id] = Spec(settings, meta, w, h)
            }
            if (!screenOn) wanted.clear()
            for ((id, player) in players.entries.toList()) {
                val spec = wanted[id]
                if (spec == null || spec.key != player.spec.key) {
                    player.requestStop()
                    players.remove(id, player)
                }
            }
            for ((id, spec) in wanted) {
                if (!players.containsKey(id)) players[id] = Player(app, manager, id, spec).also { it.start() }
            }
            if (!anyActive) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun halt(id: Int) {
        players.remove(id)?.requestStop()
    }

    private class Spec(val settings: WidgetSettings, val meta: AnimationMeta, val widthPx: Int, val heightPx: Int) {
        val key = "${meta.id}|${meta.editedAt}|$widthPx|$heightPx|${settings.visualKey()}|${settings.speedPercent}"
    }

    /** One thread per widget: decode a frame, draw it at the widget's size, send it, wait for the next one. */
    private class Player(
        private val app: Context,
        private val manager: AppWidgetManager,
        private val id: Int,
        val spec: Spec,
    ) : Thread("gifwidgets-hq-$id") {
        private val pushLock = Any()
        @Volatile private var stopped = false

        /** After this returns, no more frames of this player reach the widget. */
        fun requestStop() {
            synchronized(pushLock) { stopped = true }
            interrupt()
        }

        override fun run() {
            runGuarded { play() }
        }

        private fun play() {
            val meta = spec.meta
            // Missing or older than the animation's last save: make it now (needs original.gif; else normal frames).
            if (!HighQualityFrames.isReady(app, meta)) HighQualityFrames.prepare(app, meta, isCancelled = { stopped })
            if (stopped) return
            val set = HighQualityFrames.load(app, meta) ?: return
            val density = app.resources.displayMetrics.density
            val interval = (1000.0 * 100.0 / (set.fps * spec.settings.speedPercent))
                .toLong().coerceAtLeast(MIN_FRAME_MS)
            var index = 0
            var next = SystemClock.uptimeMillis()
            var failures = 0
            while (!stopped) {
                val src = BitmapFactory.decodeFile(set.files[index].path)
                if (src != null) {
                    val bmp = WidgetCompositor.compose(src, spec.widthPx, spec.heightPx, spec.settings, density)
                    src.recycle()
                    val views = RemoteViews(app.packageName, R.layout.widget_static)
                    views.setImageViewBitmap(R.id.widget_image, bmp)
                    try {
                        synchronized(pushLock) {
                            if (stopped) return
                            manager.partiallyUpdateAppWidget(id, views)
                        }
                        failures = 0
                    } catch (e: RuntimeException) {
                        // e.g. the launcher process died: give up after a few tries, the widget keeps its still image
                        if (++failures >= MAX_FAILURES) return
                    } finally {
                        bmp.recycle() // the system has copied it by the time the call returns
                    }
                }
                index = (index + 1) % set.files.size
                next += interval
                val wait = next - SystemClock.uptimeMillis()
                if (wait > 0) {
                    try {
                        sleep(wait)
                    } catch (e: InterruptedException) {
                        return
                    }
                } else if (wait < -interval * 5) {
                    next = SystemClock.uptimeMillis() // too slow: do not try to catch up on a backlog
                }
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "hq_playback"
        private const val NOTIFICATION_ID = 1
        private const val MIN_FRAME_MS = 33L // at most about 30 frames per second
        private const val MAX_FAILURES = 5

        @Volatile private var instance: HighQualityService? = null

        /**
         * Starts the service (or tells the running one to re-check the widgets). False when the system does not
         * allow it right now (background start): the caller then keeps using the normal mode.
         */
        fun start(context: Context): Boolean =
            try {
                val app = context.applicationContext
                app.startForegroundService(Intent(app, HighQualityService::class.java))
                true
            } catch (e: RuntimeException) {
                false
            }

        /** A widget changed: the running service re-checks which widgets need a player. Does nothing if not running. */
        fun requestSync() {
            instance?.let { s -> s.handler.post { s.sync() } }
        }

        /** Stops the player of one widget right now (before the widget is redrawn as a still image). */
        fun halt(appWidgetId: Int) {
            instance?.halt(appWidgetId)
        }
    }
}
