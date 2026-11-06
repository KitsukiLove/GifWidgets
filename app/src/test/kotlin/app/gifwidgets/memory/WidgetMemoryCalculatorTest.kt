package app.gifwidgets.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JUnit tests: WidgetMemoryCalculator has no android.* imports.
 * Run with: ./gradlew :app:testDebugUnitTest
 */
class WidgetMemoryCalculatorTest {

    // Pixel 9 screen from the brief.
    private val w = 1080
    private val h = 2424
    private val pixel9Max = 12_566_016L // 6 * 1080 * 2424 * 80 / 100

    // --- limits and budget -------------------------------------------------

    @Test
    fun `platform limit is 6 x width x height, 15707520 on a Pixel 9`() {
        assertEquals(15_707_520L, WidgetMemoryCalculator.platformLimitBytes(w, h))
    }

    @Test
    fun `platform limit does not overflow Int arithmetic on a large screen`() {
        // 6 * 20000 * 20000 = 2.4e9 > Int.MAX_VALUE
        assertEquals(2_400_000_000L, WidgetMemoryCalculator.platformLimitBytes(20_000, 20_000))
    }

    @Test
    fun `platform limit rejects non-positive dimensions`() {
        assertThrows(IllegalArgumentException::class.java) {
            WidgetMemoryCalculator.platformLimitBytes(0, h)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WidgetMemoryCalculator.platformLimitBytes(w, -1)
        }
    }

    @Test
    fun `default budget is exactly 80 percent of the platform limit`() {
        assertEquals(pixel9Max, WidgetMemoryCalculator.maxBudgetBytes(w, h))
    }

    @Test
    fun `custom usage percent is respected and 100 gives the full limit`() {
        assertEquals(30_000L, WidgetMemoryCalculator.maxBudgetBytes(100, 100, usagePercent = 50))
        assertEquals(60_000L, WidgetMemoryCalculator.maxBudgetBytes(100, 100, usagePercent = 100))
    }

    @Test
    fun `usage percent outside 1 to 100 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WidgetMemoryCalculator.maxBudgetBytes(100, 100, usagePercent = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WidgetMemoryCalculator.maxBudgetBytes(100, 100, usagePercent = 101)
        }
    }

    @Test
    fun `FrameSize bytes for ARGB_8888 and RGB_565`() {
        assertEquals(400L, FrameSize(10, 10, BitmapFormat.ARGB_8888).bytes)
        assertEquals(200L, FrameSize(10, 10, BitmapFormat.RGB_565).bytes)
    }

    @Test
    fun `FrameSize rejects non-positive dimensions`() {
        assertThrows(IllegalArgumentException::class.java) { FrameSize(0, 10) }
        assertThrows(IllegalArgumentException::class.java) { FrameSize(10, 0) }
    }

    @Test
    fun `budgetFor sums every frame and reports under budget`() {
        // screen 100x100 -> max 48_000
        val budget = WidgetMemoryCalculator.budgetFor(100, 100, listOf(FrameSize(100, 100)))
        assertFalse(budget.isOverBudget)
        assertEquals(48_000L, budget.maxBytes)
        assertEquals(40_000L, budget.usedBytes)
        assertEquals(8_000L, budget.remainingBytes)
    }

    @Test
    fun `budgetFor reports over budget and clamps remaining to zero`() {
        val frames = listOf(FrameSize(100, 100), FrameSize(100, 100))
        val budget = WidgetMemoryCalculator.budgetFor(100, 100, frames)
        assertTrue(budget.isOverBudget)
        assertEquals(0L, budget.remainingBytes)
    }

    @Test
    fun `budgetFor allows mixed frame formats`() {
        val frames = listOf(FrameSize(100, 100), FrameSize(100, 100, BitmapFormat.RGB_565))
        assertEquals(60_000L, WidgetMemoryCalculator.budgetFor(100, 100, frames).usedBytes)
    }

    @Test
    fun `exactly at the budget is not over budget`() {
        val budget = WidgetMemoryCalculator.budgetFor(100, 100, listOf(FrameSize(100, 120)))
        assertEquals(48_000L, budget.usedBytes)
        assertFalse(budget.isOverBudget)
    }

    // --- keptFrameCount ----------------------------------------------------

    @Test
    fun `keptFrameCount counts indices 0, N, 2N`() {
        assertEquals(96, WidgetMemoryCalculator.keptFrameCount(96, 1))
        assertEquals(48, WidgetMemoryCalculator.keptFrameCount(96, 2))
        assertEquals(32, WidgetMemoryCalculator.keptFrameCount(96, 3))
        assertEquals(5, WidgetMemoryCalculator.keptFrameCount(96, 20)) // 0,20,40,60,80
        assertEquals(1, WidgetMemoryCalculator.keptFrameCount(96, 500))
        assertEquals(0, WidgetMemoryCalculator.keptFrameCount(0, 3))
    }

    // --- planAutoFit: step 1 (drop frames) ---------------------------------

    @Test
    fun `plan changes nothing when everything already fits`() {
        val plan = WidgetMemoryCalculator.planAutoFit(w, h, 10, FrameSize(100, 100))
        assertEquals(1, plan.frameStride)
        assertEquals(10, plan.keptFrames)
        assertEquals(100, plan.widthPx)
        assertEquals(100, plan.heightPx)
        assertTrue(plan.fits)
        assertFalse(plan.framesReduced)
        assertFalse(plan.sizeReduced)
        assertEquals(400_000L, plan.usedBytes)
    }

    @Test
    fun `plan drops frames first and leaves the size alone`() {
        // 100x100 ARGB = 40_000 bytes. Screen 200x200 -> max 192_000 -> 4 frames fit.
        val plan = WidgetMemoryCalculator.planAutoFit(
            200, 200, frameCount = 12, frame = FrameSize(100, 100), minKeptFrames = 1,
        )
        // stride 3 -> 4 frames = 160_000 fits; stride 2 -> 6 frames = 240_000 does not.
        assertEquals(3, plan.frameStride)
        assertEquals(4, plan.keptFrames)
        assertEquals(100, plan.widthPx)
        assertEquals(100, plan.heightPx)
        assertTrue(plan.framesReduced)
        assertFalse(plan.sizeReduced)
        assertTrue(plan.fits)
    }

    @Test
    fun `sample banner 1080x498 x 96 frames with no floor needs stride 20 (5 frames)`() {
        val plan = WidgetMemoryCalculator.planAutoFit(
            w, h, 96, FrameSize(1080, 498), minKeptFrames = 1,
        )
        assertEquals(20, plan.frameStride)
        assertEquals(5, plan.keptFrames)
        assertEquals(1080, plan.widthPx)
        assertEquals(498, plan.heightPx)
        assertEquals(5L * 1080 * 498 * 4, plan.usedBytes)
        assertFalse(plan.sizeReduced)
        // Stride 19 keeps 6 frames, which must NOT fit (proves 20 is minimal).
        val sixFrames = WidgetMemoryCalculator.keptFrameCount(96, 19)
        assertEquals(6, sixFrames)
        assertTrue(sixFrames * FrameSize(1080, 498).bytes > pixel9Max)
    }

    // --- planAutoFit: step 2 (reduce size) ----------------------------------

    @Test
    fun `sample banner with the default floor keeps 12 frames and shrinks the size`() {
        val plan = WidgetMemoryCalculator.planAutoFit(w, h, 96, FrameSize(1080, 498))
        assertEquals(8, plan.frameStride) // ceil(96/8) = 12 frames
        assertEquals(12, plan.keptFrames)
        assertTrue(plan.sizeReduced)
        assertTrue(plan.framesReduced)
        assertTrue(plan.fits)
        assertTrue(plan.usedBytes <= pixel9Max)
        assertEquals(12L * plan.widthPx * plan.heightPx * 4, plan.usedBytes)
        // Aspect ratio preserved to within one pixel.
        val expectedHeight = plan.widthPx * 498.0 / 1080.0
        assertTrue(Math.abs(plan.heightPx - expectedHeight) <= 1.5)
        // Not shrunk more than necessary: one more pixel of width and height would not fit.
        assertTrue(12L * (plan.widthPx + 1) * (plan.heightPx + 1) * 4 > pixel9Max)
    }

    @Test
    fun `a single frame larger than the budget shrinks and keeps stride 1`() {
        // 3000x3000 ARGB = 36_000_000 bytes > 12_566_016.
        val plan = WidgetMemoryCalculator.planAutoFit(w, h, 1, FrameSize(3000, 3000))
        assertEquals(1, plan.frameStride)
        assertEquals(1, plan.keptFrames)
        assertTrue(plan.sizeReduced)
        assertFalse(plan.framesReduced)
        assertTrue(plan.fits)
        assertTrue(plan.usedBytes <= pixel9Max)
        assertEquals(plan.widthPx, plan.heightPx) // square stays square
    }

    @Test
    fun `size is only reduced after frames hit the floor, never before`() {
        // 40 frames of 100x100 (40_000 each) on a 200x200 screen (max 192_000):
        // floor 4 can be met by thinning alone (stride 10 -> 4 frames = 160_000).
        val thinOnly = WidgetMemoryCalculator.planAutoFit(
            200, 200, 40, FrameSize(100, 100), minKeptFrames = 4,
        )
        assertFalse(thinOnly.sizeReduced)
        // Same frames, floor 10: thinning stops at stride 4 (10 frames), then the size shrinks.
        val thenShrink = WidgetMemoryCalculator.planAutoFit(
            200, 200, 40, FrameSize(100, 100), minKeptFrames = 10,
        )
        assertEquals(4, thenShrink.frameStride)
        assertEquals(10, thenShrink.keptFrames)
        assertTrue(thenShrink.sizeReduced)
        assertTrue(thenShrink.usedBytes <= 192_000L)
    }

    @Test
    fun `RGB_565 halves the cost per frame and keeps more frames`() {
        val argb = WidgetMemoryCalculator.planAutoFit(
            w, h, 96, FrameSize(1080, 498, BitmapFormat.ARGB_8888), minKeptFrames = 1,
        )
        val rgb = WidgetMemoryCalculator.planAutoFit(
            w, h, 96, FrameSize(1080, 498, BitmapFormat.RGB_565), minKeptFrames = 1,
        )
        assertEquals(5, argb.keptFrames)
        assertEquals(11, rgb.keptFrames) // stride 9 -> ceil(96/9) = 11 frames of 1_075_680 bytes
        assertEquals(9, rgb.frameStride)
        assertTrue(rgb.fits)
    }

    @Test
    fun `minKeptFrames larger than the frame count is clamped to the frame count`() {
        val plan = WidgetMemoryCalculator.planAutoFit(
            w, h, 3, FrameSize(1080, 2424), minKeptFrames = 50,
        )
        // One 1080x2424 ARGB frame is 10_471_680 bytes, so 3 cannot fit. The floor
        // is clamped to 3, so thinning is not allowed and the size shrinks.
        assertEquals(1, plan.frameStride)
        assertEquals(3, plan.keptFrames)
        assertTrue(plan.sizeReduced)
        assertTrue(plan.fits)
    }

    @Test
    fun `plan reports fits false when even 1x1 frames cannot fit`() {
        // Screen 1x1 -> max = 6 * 80 / 100 = 4 bytes. Two 1x1 ARGB frames = 8 bytes, floor 2.
        val plan = WidgetMemoryCalculator.planAutoFit(
            1, 1, 2, FrameSize(1, 1), minKeptFrames = 2,
        )
        assertFalse(plan.fits)
        assertEquals(1, plan.widthPx)
        assertEquals(1, plan.heightPx)
        assertEquals(8L, plan.usedBytes)
    }

    @Test
    fun `plan rejects a frame count below 1 and a floor below 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            WidgetMemoryCalculator.planAutoFit(w, h, 0, FrameSize(10, 10))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WidgetMemoryCalculator.planAutoFit(w, h, 5, FrameSize(10, 10), minKeptFrames = 0)
        }
    }

    @Test
    fun `invariants hold over a grid of inputs`() {
        val sizes = listOf(
            FrameSize(1, 1), FrameSize(37, 91), FrameSize(360, 166), FrameSize(1080, 498),
            FrameSize(1080, 2424), FrameSize(4096, 4096), FrameSize(4096, 17, BitmapFormat.RGB_565),
        )
        val counts = listOf(1, 2, 7, 12, 13, 96, 400)
        val floors = listOf(1, 5, 12, 24, 1000)
        for (size in sizes) for (count in counts) for (floor in floors) {
            val plan = WidgetMemoryCalculator.planAutoFit(w, h, count, size, minKeptFrames = floor)
            val label = "size=$size count=$count floor=$floor plan=$plan"
            assertTrue(label, plan.fits)
            assertTrue(label, plan.usedBytes <= plan.maxBytes)
            assertEquals(label, WidgetMemoryCalculator.keptFrameCount(count, plan.frameStride), plan.keptFrames)
            assertTrue(label, plan.keptFrames >= minOf(count, floor))
            assertEquals(
                label,
                plan.keptFrames.toLong() * plan.widthPx * plan.heightPx * size.format.bytesPerPixel,
                plan.usedBytes,
            )
            assertTrue(label, plan.widthPx in 1..size.widthPx && plan.heightPx in 1..size.heightPx)
            assertEquals(label, plan.sizeReduced, plan.widthPx != size.widthPx || plan.heightPx != size.heightPx)
        }
    }
}
