package app.gifwidgets.edit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMathTest {
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()

    @Test
    fun boundsOfOpaquePixels() {
        val w = 5
        val px = IntArray(20)
        px[1 * w + 2] = white
        px[2 * w + 3] = white
        assertEquals(CropRect(2, 1, 4, 3), ImageMath.opaqueBounds(px, w, 4))
        assertEquals(null, ImageMath.opaqueBounds(IntArray(4), 2, 2))
    }

    @Test
    fun unionOfRects() {
        assertEquals(CropRect(0, 1, 5, 6), ImageMath.union(CropRect(0, 2, 3, 6), CropRect(1, 1, 5, 4)))
        assertEquals(CropRect(1, 1, 2, 2), ImageMath.union(null, CropRect(1, 1, 2, 2)))
    }

    @Test
    fun cropCopiesTheRectangle() {
        val px = IntArray(12) { it } // 4x3
        assertArrayEquals(intArrayOf(5, 6, 9, 10), ImageMath.crop(px, 4, CropRect(1, 1, 3, 3)))
    }

    @Test
    fun clampRectStaysInside() {
        assertEquals(CropRect(0, 0, 4, 3), ImageMath.clampRect(CropRect(-5, -5, 99, 99), 4, 3))
        assertEquals(CropRect(3, 2, 4, 3), ImageMath.clampRect(CropRect(3, 2, 3, 2), 4, 3))
    }

    @Test
    fun scaledSizeNeverEnlarges() {
        assertEquals(1080 to 498, ImageMath.scaledSize(1080, 498, 0))
        assertEquals(1080 to 498, ImageMath.scaledSize(1080, 498, 2000))
        assertEquals(540 to 249, ImageMath.scaledSize(1080, 498, 540))
        assertEquals(1 to 100, ImageMath.scaledSize(1, 1000, 100))
    }

    @Test
    fun nearestKeepsHardEdges() {
        // 4x1: red red white white -> 2x1: red white
        val out = ImageMath.scaleNearest(intArrayOf(red, red, white, white), 4, 1, 2, 1)
        assertArrayEquals(intArrayOf(red, white), out)
    }

    @Test
    fun areaAveragesAndKeepsColourOfTransparentNeighbours() {
        val out = ImageMath.scaleArea(intArrayOf(red, 0, 0, 0), 2, 2, 1, 1)
        // 1/4 coverage -> alpha 64 (63.75), colour stays pure red (no dark bleed)
        assertEquals(64, out[0] ushr 24)
        assertEquals(0xFF, (out[0] shr 16) and 0xFF)
        assertEquals(0, out[0] and 0xFF)
    }

    @Test
    fun areaOfUniformImageIsUniform() {
        val out = ImageMath.scaleArea(IntArray(9 * 7) { red }, 9, 7, 4, 3)
        assertTrue(out.all { it == red })
    }
}
