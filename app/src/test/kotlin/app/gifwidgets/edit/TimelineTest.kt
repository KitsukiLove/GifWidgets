package app.gifwidgets.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {
    private fun delays(n: Int, cs: Int) = IntArray(n) { cs }

    @Test
    fun sameFpsKeepsEveryFrame() {
        val p = Timeline.plan(delays(10, 4), TimelineParams(fps = 25))
        assertEquals((0..9).toList(), p.sourceIndices)
    }

    @Test
    fun halfFpsDropsEveryOtherFrame() {
        val p = Timeline.plan(delays(10, 4), TimelineParams(fps = 12))
        // 10 frames * 40 ms = 400 ms -> 5 output frames at 12 fps (round(4.8))
        assertEquals(5, p.frameCount)
        assertEquals(listOf(0, 2, 4, 6, 8), p.sourceIndices)
    }

    @Test
    fun bannerOf96At25FpsGivesAbout46At12Fps() {
        val p = Timeline.plan(delays(96, 4), TimelineParams(fps = 12))
        assertEquals(46, p.frameCount)
        assertEquals(0, p.sourceIndices.first())
    }

    @Test
    fun slowSourceRepeatsFrames() {
        val p = Timeline.plan(delays(2, 20), TimelineParams(fps = 10)) // 2 frames * 200 ms
        assertEquals(listOf(0, 0, 1, 1), p.sourceIndices)
    }

    @Test
    fun trimAndDelete() {
        val p = Timeline.plan(delays(10, 10), TimelineParams(trimStart = 2, trimEnd = 6, deleted = setOf(4), fps = 10))
        assertEquals(listOf(2, 3, 5, 6), p.sourceIndices)
    }

    @Test
    fun reverseAndPingPong() {
        val r = Timeline.plan(delays(4, 10), TimelineParams(reverse = true, fps = 10))
        assertEquals(listOf(3, 2, 1, 0), r.sourceIndices)
        val pp = Timeline.plan(delays(4, 10), TimelineParams(pingPong = true, fps = 10))
        assertEquals(listOf(0, 1, 2, 3, 2, 1), pp.sourceIndices)
    }

    @Test
    fun keepOneOfNPreservesDuration() {
        val p = Timeline.plan(delays(8, 10), TimelineParams(keepOneOfN = 2, fps = 10))
        // 4 groups of 200 ms -> each kept frame lasts 2 output frames
        assertEquals(listOf(0, 0, 2, 2, 4, 4, 6, 6), p.sourceIndices)
    }

    @Test
    fun variableDelaysBecomeConstant() {
        val p = Timeline.plan(intArrayOf(10, 30), TimelineParams(fps = 10)) // 100 ms + 300 ms
        assertEquals(listOf(0, 1, 1, 1), p.sourceIndices)
    }

    @Test
    fun everythingDeletedKeepsOneFrame() {
        val p = Timeline.plan(delays(3, 10), TimelineParams(deleted = setOf(0, 1, 2), fps = 10))
        assertEquals(1, p.frameCount)
    }

    @Test
    fun restFrameIsClamped() {
        val p = Timeline.plan(delays(3, 10), TimelineParams(fps = 10, restFrame = 99))
        assertEquals(2, p.restFrame)
    }

    @Test
    fun outputIsCappedAt400FramesByLoweringFps() {
        val p = Timeline.plan(delays(400, 10), TimelineParams(fps = 25)) // 40 s * 25 fps = 1000 frames
        assertEquals(10, p.fps) // 400 frames / 40 s
        assertEquals(400, p.frameCount)
    }
}
