package app.gifwidgets

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.StrictMode
import app.gifwidgets.audio.KixkiAudio

/**
 * Two small jobs:
 * 1. In a debug build, switch StrictMode on so that disk or network work on the main thread and leaked
 *    resources show up in Logcat (filter "StrictMode"). It only logs, never crashes, and does nothing in release.
 * 2. Tell [KixkiAudio] whether any screen of the app is on screen, so the (optional) music only plays while the
 *    app is visible. No service, no permission, nothing runs in the background.
 */
class GifWidgetsApp : Application() {
    private var started = 0

    override fun onCreate() {
        super.onCreate()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        KixkiAudio.init(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                KixkiAudio.onForeground(true)
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) KixkiAudio.onForeground(false)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
