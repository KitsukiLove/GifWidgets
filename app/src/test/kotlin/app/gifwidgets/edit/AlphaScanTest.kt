package app.gifwidgets.edit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaScanTest {
    @Test
    fun anOpaquePictureHasNoTransparency() {
        assertFalse(AlphaScan.anyTransparent(intArrayOf(0xFF112233.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())))
    }

    @Test
    fun oneSeeThroughPixelIsEnough() {
        assertTrue(AlphaScan.anyTransparent(intArrayOf(0xFF112233.toInt(), 0x00000000, 0xFF000000.toInt())))
        assertTrue(AlphaScan.anyTransparent(intArrayOf(0xFE112233.toInt())))
    }

    @Test
    fun anEmptyPictureHasNone() {
        assertFalse(AlphaScan.anyTransparent(IntArray(0)))
    }
}
