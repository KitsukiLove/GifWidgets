package app.gifwidgets.memory

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Thin Android adapter that feeds [WidgetMemoryCalculator] with the real
 * display size. Kept apart from the calculator so that one stays pure.
 */
object ScreenMetrics {

    data class SizePx(val width: Int, val height: Int)

    /**
     * Full physical display size in pixels for the current rotation (the
     * product width x height, which is what the budget uses, does not depend
     * on rotation).
     *
     * VERIFY: that the platform's own widget memory cap is computed from this
     * same display size (it may use Display or DisplayMetrics of another
     * context); compare the figure with what the launcher actually accepts in
     * stage 4.
     */
    fun physicalSizePx(context: Context): SizePx {
        val windowManager = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            SizePx(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            SizePx(metrics.widthPixels, metrics.heightPixels)
        }
    }
}
