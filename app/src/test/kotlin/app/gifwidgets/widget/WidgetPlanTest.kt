package app.gifwidgets.widget

import app.gifwidgets.memory.BitmapFormat
import app.gifwidgets.memory.WidgetMemoryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPlanTest {
    private fun plan(frames: Int, fps: Int, speed: Int, w: Int, h: Int, usage: Int = 80, fmt: BitmapFormat = BitmapFormat.ARGB_8888) =
        WidgetPlanner.plan(frames, fps, speed, w, h, 1080, 2424, usage, fmt)

    @Test
    fun smallWidgetKeepsEveryFrame() {
        val p = plan(46, 12, 100, 300, 140)
        assertEquals(46, p.keptFrames)
        assertEquals(1, p.stride)
        assertEquals(83, p.intervalMs) // 1000 / 12
        assertFalse(p.framesReduced)
    }

    @Test
    fun bigBannerWidgetStaysInsideTheBudget() {
        val p = plan(46, 12, 100, 1000, 470)
        val used = p.keptFrames.toLong() * p.width * p.height * 4
        assertTrue(used <= WidgetMemoryCalculator.maxBudgetBytes(1080, 2424))
        assertTrue(p.keptFrames >= 12)
        // thinning frames keeps the animation speed: interval grows with the stride
        assertEquals(Math.round(1000.0 * p.stride / 12).toInt(), p.intervalMs)
    }

    @Test
    fun speedMultiplierChangesTheInterval() {
        assertEquals(167, plan(20, 12, 50, 100, 100).intervalMs)
        assertEquals(42, plan(20, 12, 200, 100, 100).intervalMs)
    }

    @Test
    fun intervalIsClamped() {
        assertEquals(WidgetPlanner.MIN_INTERVAL_MS, plan(20, 60, 300, 50, 50).intervalMs)
    }

    @Test
    fun rgb565AllowsMoreFrames() {
        val a = plan(100, 12, 100, 900, 900)
        val b = plan(100, 12, 100, 900, 900, fmt = BitmapFormat.RGB_565)
        assertTrue(b.keptFrames >= a.keptFrames)
    }

    @Test
    fun lowerUsageKeepsFewerBytes() {
        val hi = plan(100, 12, 100, 900, 900, usage = 80)
        val lo = plan(100, 12, 100, 900, 900, usage = 20)
        assertTrue(lo.keptFrames.toLong() * lo.width * lo.height <= hi.keptFrames.toLong() * hi.width * hi.height)
    }

    @Test
    fun scaleMathFitFillCenter() {
        val fit = ScaleMath.destRect(ScaleMode.FIT, 200, 100, 0f, 0f, 100f, 100f, 1f)
        assertEquals(listOf(0f, 25f, 100f, 75f), fit.toList())
        val fill = ScaleMath.destRect(ScaleMode.FILL, 200, 100, 0f, 0f, 100f, 100f, 1f)
        assertEquals(listOf(-50f, 0f, 150f, 100f), fill.toList())
        val center = ScaleMath.destRect(ScaleMode.CENTER, 20, 10, 0f, 0f, 100f, 100f, 2f)
        assertEquals(listOf(30f, 40f, 70f, 60f), center.toList())
    }

    @Test
    fun settingsRoundTripAndClamp() {
        val s = WidgetSettings("abc", false, 150, ScaleMode.FILL, 20, 3, 0xFF112233.toInt(), 0xFF445566.toInt(), 60, 8, 120, 80, false, false, ButtonCorner.TOP_START, TapAction.OPEN_APP)
        assertEquals(s, WidgetSettings.fromMap(s.toMap()))
        val g = WidgetSettings.fromMap(mapOf("speed" to 9999, "corner" to -3, "scale" to "NOPE", "opacity" to 0))
        assertEquals(300, g.speedPercent)
        assertEquals(0, g.cornerDp)
        assertEquals(ScaleMode.FIT, g.scale)
        assertEquals(10, g.opacity)
        assertEquals(WidgetSettings(), WidgetSettings.fromMap(emptyMap()))
    }

    @Test
    fun smoothPlanKeepsMoreFramesThanTheSharpOne() {
        // 46 frames in a 600x280 banner: the sharp plan thins, the smooth one keeps every frame and shrinks a little
        val sharp = WidgetPlanner.plan(46, 12, 100, 600, 280, 1080, 2424, 80, BitmapFormat.ARGB_8888, smooth = false)
        val smooth = WidgetPlanner.plan(46, 12, 100, 600, 280, 1080, 2424, 80, BitmapFormat.ARGB_8888, smooth = true)
        assertTrue(smooth.keptFrames > sharp.keptFrames)
        assertEquals(1, smooth.stride)
        assertEquals(83, smooth.intervalMs)
        val used = smooth.keptFrames.toLong() * smooth.width * smooth.height * 4
        assertTrue(used <= WidgetMemoryCalculator.maxBudgetBytes(1080, 2424))
    }
}
