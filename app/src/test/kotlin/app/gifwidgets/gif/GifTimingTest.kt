package app.gifwidgets.gif

import org.junit.Assert.assertEquals
import org.junit.Test

class GifTimingTest {
    @Test
    fun zeroAndOneCentisecondDelaysBecomeTen() {
        assertEquals(10, GifTiming.normalizeDelayCs(0))
        assertEquals(10, GifTiming.normalizeDelayCs(1))
        assertEquals(2, GifTiming.normalizeDelayCs(2))
        assertEquals(40, GifTiming.normalizeDelayCs(40))
    }

    @Test
    fun fpsFromDelays() {
        assertEquals(25, GifTiming.averageFps(IntArray(96) { 4 })) // 40 ms = 25 fps
        assertEquals(10, GifTiming.averageFps(intArrayOf(10, 10, 10)))
        assertEquals(7, GifTiming.averageFps(intArrayOf(10, 20))) // 2 frames / 0.3 s = 6.67
    }

    @Test
    fun fpsIsClamped() {
        assertEquals(60, GifTiming.averageFps(IntArray(5) { 1 })) // 100 fps raw -> 60
        assertEquals(1, GifTiming.averageFps(intArrayOf(1000))) // 0.1 fps raw -> 1
    }

    @Test
    fun emptyDelaysFallBack() {
        assertEquals(GifTiming.FALLBACK_FPS, GifTiming.averageFps(IntArray(0)))
    }
}
