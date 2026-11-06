package app.gifwidgets.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRemoverTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()

    private fun alpha(c: Int) = c ushr 24

    @Test
    fun blackBecomesTransparent() {
        val px = intArrayOf(black)
        BackgroundRemover.apply(px, 1, 1, BackgroundParams(mode = BackgroundMode.BLACK_TO_ALPHA))
        assertEquals(0, alpha(px[0]))
    }

    @Test
    fun whiteStaysOpaqueWhite() {
        val px = intArrayOf(white)
        BackgroundRemover.apply(px, 1, 1, BackgroundParams(mode = BackgroundMode.BLACK_TO_ALPHA))
        assertEquals(white, px[0])
    }

    @Test
    fun midColourIsRecoveredOverBlackWithinOne() {
        // every colour composited over black must come back within 1 unit
        for (r in 0..255 step 15) for (g in 0..255 step 15) for (b in 0..255 step 15) {
            val src = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val px = intArrayOf(src)
            BackgroundRemover.apply(px, 1, 1, BackgroundParams(mode = BackgroundMode.BLACK_TO_ALPHA))
            val a = alpha(px[0])
            val rr = ((px[0] shr 16) and 0xFF) * a / 255.0
            val gg = ((px[0] shr 8) and 0xFF) * a / 255.0
            val bb = (px[0] and 0xFF) * a / 255.0
            assertTrue("r=$r g=$g b=$b -> $rr", Math.abs(rr - r) <= 1.0 && Math.abs(gg - g) <= 1.0 && Math.abs(bb - b) <= 1.0)
        }
    }

    @Test
    fun blackThresholdAndGain() {
        val px = intArrayOf((0xFF shl 24) or (40 shl 16) or (40 shl 8) or 40, white)
        BackgroundRemover.apply(px, 2, 1, BackgroundParams(mode = BackgroundMode.BLACK_TO_ALPHA, blackThreshold = 40))
        assertEquals(0, alpha(px[0]))
        assertEquals(255, alpha(px[1]))
        val g = intArrayOf((0xFF shl 24) or (100 shl 16) or (100 shl 8) or 100)
        BackgroundRemover.apply(g, 1, 1, BackgroundParams(mode = BackgroundMode.BLACK_TO_ALPHA, blackGain = 200))
        assertEquals(200, alpha(g[0]))
    }

    /** 5x3 image: black everywhere except a white pixel at (2,1) surrounded by a black pixel that is NOT on the border. */
    private fun ring(): IntArray {
        // 5x5: border black, ring of red at distance 1, black hole in the middle
        val w = 5
        val px = IntArray(25) { black }
        for (y in 1..3) for (x in 1..3) px[y * w + x] = red
        px[2 * w + 2] = black
        return px
    }

    @Test
    fun contiguousKeepsEnclosedHoleGlobalRemovesIt() {
        val c = ring()
        BackgroundRemover.apply(c, 5, 5, BackgroundParams(mode = BackgroundMode.COLOR_KEY, tolerance = 0, contiguous = true))
        assertEquals(black, c[12]) // enclosed black stays
        assertEquals(0, c[0])      // border black removed
        val g = ring()
        BackgroundRemover.apply(g, 5, 5, BackgroundParams(mode = BackgroundMode.COLOR_KEY, tolerance = 0, contiguous = false))
        assertEquals(0, g[12])
        assertEquals(red, g[6])
    }

    @Test
    fun toleranceZeroIsExactAndHundredRemovesAll() {
        val near = (0xFF shl 24) or 0x010101
        val a = intArrayOf(near, black)
        BackgroundRemover.apply(a, 2, 1, BackgroundParams(mode = BackgroundMode.COLOR_KEY, tolerance = 0, contiguous = false))
        assertEquals(near, a[0])
        assertEquals(0, a[1])
        val b = intArrayOf(white, red)
        BackgroundRemover.apply(b, 2, 1, BackgroundParams(mode = BackgroundMode.COLOR_KEY, tolerance = 100))
        assertEquals(0, b[0]); assertEquals(0, b[1])
    }

    @Test
    fun toleranceRemovesNearColours() {
        val near = (0xFF shl 24) or 0x0A0A0A
        val a = intArrayOf(near, red)
        BackgroundRemover.apply(a, 2, 1, BackgroundParams(mode = BackgroundMode.COLOR_KEY, tolerance = 10, contiguous = false))
        assertEquals(0, a[0])
        assertEquals(red, a[1])
    }

    @Test
    fun edgeSoftenFeathersKeptEdge() {
        // 6x1: black, black, red, red, red, red -> red at distance 1 and 2 from the removed region
        val px = intArrayOf(black, black, red, red, red, red)
        BackgroundRemover.apply(px, 6, 1, BackgroundParams(mode = BackgroundMode.COLOR_KEY, tolerance = 0, edgeSoften = 2))
        assertEquals(0, px[1])
        assertEquals(85, alpha(px[2]))   // 255 * 1/3
        assertEquals(170, alpha(px[3]))  // 255 * 2/3
        assertEquals(255, alpha(px[4]))
    }

    @Test
    fun defringeTakesInteriorColour() {
        val darkRed = 0xFF550000.toInt()
        // black, edge pixel of dark red (halo), then two bright red pixels
        val px = intArrayOf(black, darkRed, red, red)
        BackgroundRemover.apply(px, 4, 1, BackgroundParams(mode = BackgroundMode.COLOR_KEY, tolerance = 0, defringe = true))
        assertEquals(0, px[0])
        assertEquals(red, px[1])
    }

    @Test
    fun keepModeDoesNothing() {
        val px = intArrayOf(black, white, red)
        val copy = px.copyOf()
        BackgroundRemover.apply(px, 3, 1, BackgroundParams())
        assertTrue(px.contentEquals(copy))
    }
}
