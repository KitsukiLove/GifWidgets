package app.gifwidgets.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFitTest {
    private val budget = 6L * 1080 * 2424 * 80 / 100 // Pixel 9, 80 %

    @Test
    fun alreadyFittingParamsAreUnchanged() {
        val p = EditorParams(timeline = TimelineParams(fps = 10), image = ImageParams(maxSide = 200))
        val d = IntArray(20) { 10 }
        assertEquals(p, AutoFit.fit(p, d, 1080, 498, 4, budget))
    }

    @Test
    fun bannerIsReducedUntilItFits() {
        val d = IntArray(96) { 4 }
        val p = EditorParams(timeline = TimelineParams(fps = 12))
        val out = AutoFit.fit(p, d, 1080, 498, 4, budget)
        assertTrue(AutoFit.usedBytes(d, 1080, 498, out, 4) <= budget)
        // frames are thinned to the floor before the size is touched
        assertEquals(AutoFit.MIN_FRAMES, Timeline.plan(d, out.timeline).frameCount)
        assertTrue(out.image.maxSide in 1..1079)
    }

    @Test
    fun cheapBannerKeepsFramesWhenOnlyFpsNeedsLowering() {
        val d = IntArray(96) { 4 }
        val p = EditorParams(timeline = TimelineParams(fps = 25), image = ImageParams(maxSide = 400))
        val out = AutoFit.fit(p, d, 1080, 498, 4, budget)
        assertTrue(AutoFit.usedBytes(d, 1080, 498, out, 4) <= budget)
        assertEquals(0, if (out.image.maxSide == 400) 0 else 1) // size untouched
    }

    @Test
    fun rgb565HalvesTheEstimateOnlyWithoutAlpha() {
        val on = EditorParams(saveMemory = true)
        assertEquals(2, AutoFit.bytesPerPixel(on, sourceHasAlpha = false))
        assertEquals(4, AutoFit.bytesPerPixel(on, sourceHasAlpha = true))
        assertEquals(4, AutoFit.bytesPerPixel(on.copy(background = BackgroundParams(BackgroundMode.BLACK_TO_ALPHA)), false))
        assertEquals(4, AutoFit.bytesPerPixel(EditorParams(), false))
    }

    @Test
    fun manualCropShrinksTheFrameSize() {
        val img = ImageParams(manualCrop = CropRect(0, 0, 100, 50))
        assertEquals(100 to 50, AutoFit.frameSize(1080, 498, img))
        assertEquals(1080 to 498, AutoFit.frameSize(1080, 498, img.copy(autoCrop = true)))
    }

    @Test
    fun restFrameMapsFromSourceFrame() {
        val plan = Timeline.plan(IntArray(10) { 10 }, TimelineParams(fps = 5)) // 5 output frames: 0,2,4,6,8
        assertEquals(2, Timeline.outputIndexFor(plan, 4))
        assertEquals(3, Timeline.outputIndexFor(plan, 7)) // closest is 6
    }
}
