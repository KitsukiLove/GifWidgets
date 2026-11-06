package app.gifwidgets.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighQualityParamsTest {
    @Test
    fun `keeps the users edits and raises speed and size`() {
        val base = EditorParams(
            timeline = TimelineParams(trimStart = 2, reverse = true, fps = 6),
            image = ImageParams(maxSide = 240, sharpPixels = false),
        )
        val p = HighQualityParams.from(base, 480, 270, nativeFps = 20, hasAlpha = false)
        assertEquals(2, p.timeline.trimStart)
        assertTrue(p.timeline.reverse)
        assertEquals(20, p.timeline.fps) // the GIF's own speed, not the 6 fps it was stored at
        assertEquals(HighQualityParams.MAX_SIDE, p.image.maxSide)
        assertFalse(p.image.sharpPixels)
    }

    @Test
    fun `speed is capped and never below what the user saved`() {
        assertEquals(30, HighQualityParams.from(null, 480, 270, 50, false).timeline.fps)
        val base = EditorParams(timeline = TimelineParams(fps = 25))
        assertEquals(25, HighQualityParams.from(base, 480, 270, 10, false).timeline.fps)
    }

    @Test
    fun `photos are lossy, pixel art and transparency are lossless`() {
        assertFalse(HighQualityParams.from(null, 480, 270, 15, hasAlpha = false).lossless)
        assertTrue(HighQualityParams.from(null, 128, 128, 15, hasAlpha = false).lossless)
        assertTrue(HighQualityParams.from(null, 480, 270, 15, hasAlpha = true).lossless)
    }
}
