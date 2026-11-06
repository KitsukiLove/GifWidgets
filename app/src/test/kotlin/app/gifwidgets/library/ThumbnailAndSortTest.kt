package app.gifwidgets.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailAndSortTest {

    @Test
    fun smallAnimationKeepsEveryFrame() {
        val p = ThumbnailPlan.plan(frameCount = 10, srcWidth = 100, srcHeight = 50)
        assertEquals(1, p.stride)
        assertEquals(10, p.frames)
        assertEquals(100, p.width)
        assertEquals(50, p.height)
    }

    @Test
    fun bannerOf96FramesKeepsOneOfEveryFour() {
        val p = ThumbnailPlan.plan(frameCount = 96, srcWidth = 1080, srcHeight = 498)
        assertEquals(4, p.stride)
        assertEquals(24, p.frames)
        assertEquals(192, p.width)
        assertEquals(89, p.height)
    }

    @Test
    fun framesNeverExceedTheMaximum() {
        for (n in 1..400) {
            val p = ThumbnailPlan.plan(n, 64, 64)
            assertTrue(p.frames <= ThumbnailPlan.MAX_FRAMES)
            assertTrue(p.frames >= 1)
        }
    }

    @Test
    fun frameMillisFollowsFpsAndStride() {
        assertEquals(160L, ThumbnailPlan.frameMillis(fps = 25, stride = 4))
        assertEquals(33L, ThumbnailPlan.frameMillis(fps = 60, stride = 1)) // clamped from 16 ms
        assertEquals(1000L, ThumbnailPlan.frameMillis(fps = 1, stride = 10)) // clamped from 10 s
    }

    private fun meta(name: String, createdAt: Long, bytes: Long) = AnimationMeta(
        id = "00000000-0000-0000-0000-000000000000", name = name, createdAt = createdAt,
        srcWidth = 1, srcHeight = 1, frameCount = 1, fps = 10, hasAlpha = false, bytes = bytes,
    )

    @Test
    fun sortingOrders() {
        val a = meta("beta", 1, 300)
        val b = meta("Alpha", 3, 100)
        val c = meta("gamma", 2, 200)
        val all = listOf(a, b, c)
        assertEquals(listOf("Alpha", "gamma", "beta"), AnimationSort.sort(all, SortOrder.NEWEST).map { it.name })
        assertEquals(listOf("Alpha", "beta", "gamma"), AnimationSort.sort(all, SortOrder.NAME).map { it.name })
        assertEquals(listOf("beta", "gamma", "Alpha"), AnimationSort.sort(all, SortOrder.SIZE).map { it.name })
    }
}
