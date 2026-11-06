package app.gifwidgets.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickFitTest {
    private val sw = 1080
    private val sh = 2424

    private fun delays(frames: Int, cs: Int) = IntArray(frames) { cs }

    @Test
    fun `a small smooth photo keeps its own speed up to the cap`() {
        val c = QuickFit.choose(delays(20, 5), 400, 300, nativeFps = 20, hasAlpha = false, sw, sh)
        assertEquals(QuickFit.MAX_FPS, c.fps)
        assertFalse(c.lossless) // photo without transparency: lossy WebP, fast
        assertEquals(0, c.maxSide) // not bigger than the cap: no resizing
        assertFalse(c.pixelArt)
    }

    @Test
    fun `speed is capped and never below 5`() {
        assertEquals(QuickFit.MAX_FPS, QuickFit.choose(delays(20, 2), 400, 300, 50, false, sw, sh).fps)
        assertEquals(5, QuickFit.choose(delays(10, 50), 400, 300, 2, false, sw, sh).fps)
    }

    @Test
    fun `a huge GIF is limited to 960 px on the long side`() {
        val c = QuickFit.choose(delays(10, 5), 1920, 1080, 20, false, sw, sh)
        assertTrue(c.maxSide in 1..QuickFit.MAX_SIDE)
    }

    @Test
    fun `a long smooth GIF loses resolution first and keeps its fps`() {
        // 40 frames of 480x270 do not fit at full size; smooth keeps all of them, a little smaller.
        val c = QuickFit.choose(delays(40, 5), 480, 270, 20, false, sw, sh)
        assertEquals(QuickFit.MAX_FPS, c.fps)
        assertTrue(c.maxSide in 240..479)
    }

    @Test
    fun `an extremely long GIF lowers the fps (keeps the duration) at half size`() {
        val c = QuickFit.choose(delays(200, 5), 480, 270, 20, false, sw, sh)
        assertTrue(c.fps < QuickFit.MAX_FPS)
        assertEquals(240, c.maxSide)
    }

    @Test
    fun `pixel art keeps its sharp size and thins frames, as before`() {
        // 256x256 x 4 B = 262 KB per frame; 100 frames do not fit in 12.5 MB: frames go down to the floor first.
        val c = QuickFit.choose(delays(100, 5), 256, 256, 20, true, sw, sh)
        assertTrue(c.pixelArt)
        assertTrue(c.lossless)
        assertTrue(c.fps < QuickFit.MAX_FPS)
        assertEquals(0, c.maxSide)
    }
}
