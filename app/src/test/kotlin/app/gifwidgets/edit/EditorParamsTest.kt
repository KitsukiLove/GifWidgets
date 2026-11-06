package app.gifwidgets.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorParamsTest {
    @Test
    fun roundTripThroughMap() {
        val p = EditorParams(
            background = BackgroundParams(BackgroundMode.COLOR_KEY, 0xFF112233.toInt(), 33, false, 2, true, 5, 150),
            timeline = TimelineParams(2, 50, setOf(7, 3), true, true, 3, 15, 4),
            image = ImageParams(true, CropRect(1, 2, 30, 40), 512, false),
            lossless = false,
            quality = 85,
        )
        assertEquals(p, EditorParams.fromMap(p.toMap()))
    }

    @Test
    fun emptyMapGivesDefaults() {
        assertEquals(EditorParams(), EditorParams.fromMap(emptyMap()))
    }

    @Test
    fun garbageValuesAreClamped() {
        val p = EditorParams.fromMap(
            mapOf("bgTolerance" to 999, "bgSoften" to -4, "tFps" to 0, "tKeepN" to 0, "iMaxSide" to 99999, "bgMode" to "NOPE", "quality" to "x"),
        )
        assertEquals(100, p.background.tolerance)
        assertEquals(0, p.background.edgeSoften)
        assertEquals(1, p.timeline.fps)
        assertEquals(1, p.timeline.keepOneOfN)
        assertEquals(4096, p.image.maxSide)
        assertEquals(BackgroundMode.KEEP, p.background.mode)
        assertEquals(90, p.quality)
    }

    @Test
    fun frameProcessorCropsThenScales() {
        val w = 8
        val h = 4
        val px = IntArray(w * h) { 0xFFFF0000.toInt() }
        val out = FrameProcessor.cropAndScale(px, w, h, CropRect(0, 0, 4, 4), ImageParams(maxSide = 2))
        assertEquals(2, out.width)
        assertEquals(2, out.height)
    }
}
