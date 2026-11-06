package app.gifwidgets.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [WidgetMemoryCalculator.planSmoothFit]: every frame first, a little less resolution next, thinning last. */
class SmoothFitTest {
    private val w = 1080
    private val h = 2424

    @Test
    fun `everything that fits is left alone`() {
        // 24 x 480x270 ARGB = 12.4 MB <= 12.57 MB
        val plan = WidgetMemoryCalculator.planSmoothFit(w, h, 24, FrameSize(480, 270))
        assertEquals(1, plan.frameStride)
        assertEquals(24, plan.keptFrames)
        assertEquals(480, plan.widthPx)
        assertFalse(plan.sizeReduced)
        assertTrue(plan.fits)
    }

    @Test
    fun `keeps every frame and shrinks a little before dropping any`() {
        // 40 frames do not fit at full size; planAutoFit would show 20 frames, planSmoothFit shows all 40.
        val size = FrameSize(480, 270)
        val smooth = WidgetMemoryCalculator.planSmoothFit(w, h, 40, size)
        assertEquals(1, smooth.frameStride)
        assertEquals(40, smooth.keptFrames)
        assertTrue(smooth.sizeReduced)
        assertTrue(smooth.fits)
        assertTrue(smooth.widthPx >= 240) // never below half of the linear size
        val auto = WidgetMemoryCalculator.planAutoFit(w, h, 40, size)
        assertTrue(auto.keptFrames < smooth.keptFrames)
    }

    @Test
    fun `drops frames only when the minimum size is not enough`() {
        // 100 frames: half the linear size (240x135) x 100 = 12.96 MB > 12.57 MB, so every second frame goes.
        val plan = WidgetMemoryCalculator.planSmoothFit(w, h, 100, FrameSize(480, 270))
        assertEquals(240, plan.widthPx)
        assertEquals(135, plan.heightPx)
        assertEquals(2, plan.frameStride)
        assertEquals(50, plan.keptFrames)
        assertTrue(plan.fits)
        assertTrue(plan.framesReduced)
    }

    @Test
    fun `a single frame always fits and invariants hold over a grid`() {
        for (side in listOf(64, 200, 480, 1000, 2000)) {
            for (count in listOf(1, 2, 12, 60, 150, 400)) {
                val size = FrameSize(side, side * 9 / 16 + 1)
                val plan = WidgetMemoryCalculator.planSmoothFit(w, h, count, size)
                val label = "size=$size count=$count plan=$plan"
                assertTrue(label, plan.fits)
                assertTrue(label, plan.usedBytes <= plan.maxBytes)
                assertEquals(label, WidgetMemoryCalculator.keptFrameCount(count, plan.frameStride), plan.keptFrames)
                assertTrue(label, plan.widthPx in 1..size.widthPx && plan.heightPx in 1..size.heightPx)
            }
        }
    }
}
