package app.gifwidgets.gif

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GifReaderTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun decodeAll(reader: GifReader): List<IntArray> {
        val frames = ArrayList<IntArray>()
        reader.decode { _, _, canvas ->
            frames.add(canvas.copyOf())
            true
        }
        return frames
    }

    private fun expectReason(reason: GifException.Reason, block: () -> Unit) {
        try {
            block()
            fail("Expected GifException($reason)")
        } catch (e: GifException) {
            assertEquals(reason, e.reason)
        }
    }

    @Test
    fun signatureIsCheckedNotTheMimeType() {
        assertTrue(GifReader.hasGifSignature("GIF89a....".toByteArray()))
        assertTrue(GifReader.hasGifSignature("GIF87a....".toByteArray()))
        assertFalse(GifReader.hasGifSignature("GIF90a....".toByteArray()))
        assertFalse(GifReader.hasGifSignature("GIF8".toByteArray()))
        assertFalse(GifReader.hasGifSignature(ByteArray(0)))
    }

    @Test
    fun twoFramesInfoAndPixels() {
        val reader = GifReader.open(TestGifs.TWO_FRAMES)
        assertEquals(4, reader.info.width)
        assertEquals(4, reader.info.height)
        assertEquals(2, reader.info.frameCount)
        assertEquals(0, reader.info.loopCount)
        assertArrayEquals(intArrayOf(10, 20), reader.info.delaysCs)
        assertFalse(reader.info.usesTransparency)

        val frames = decodeAll(reader)
        assertEquals(2, frames.size)
        assertTrue(frames[0].all { it == red })
        assertTrue(frames[1].all { it == blue })
    }

    @Test
    fun decodeCanStopEarly() {
        var seen = 0
        GifReader.open(TestGifs.TWO_FRAMES).decode { _, _, _ ->
            seen++
            false
        }
        assertEquals(1, seen)
    }

    @Test
    fun interlacedRowsEndUpInTheRightPlace() {
        val reader = GifReader.open(TestGifs.INTERLACED)
        val canvas = decodeAll(reader).single()
        for (y in 0 until 16) {
            val g = y * 16
            val expected = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            for (x in 0 until 8) assertEquals("row $y col $x", expected, canvas[y * 8 + x])
        }
    }

    @Test
    fun interlacedRowOrderForHeight10() {
        val order = (0 until 10).map { GifReader.interlacedRow(it, 10) }
        assertEquals(listOf(0, 8, 4, 2, 6, 1, 3, 5, 7, 9), order)
    }

    @Test
    fun transparentIndexStaysTransparent() {
        val reader = GifReader.open(TestGifs.TRANSPARENT)
        assertTrue(reader.info.usesTransparency)
        val canvas = decodeAll(reader).single()
        for (y in 0 until 4) {
            assertEquals(0, canvas[y * 4 + 0])
            assertEquals(0, canvas[y * 4 + 1])
            assertEquals(red, canvas[y * 4 + 2])
            assertEquals(red, canvas[y * 4 + 3])
        }
    }

    @Test
    fun rejectsNonGif() {
        expectReason(GifException.Reason.NOT_GIF) { GifReader.open("PNG data here".toByteArray()) }
    }

    @Test
    fun rejectsTooManyFrames() {
        expectReason(GifException.Reason.TOO_MANY_FRAMES) {
            GifReader.open(TestGifs.TWO_FRAMES, GifLimits(maxFrames = 1))
        }
    }

    @Test
    fun rejectsFileOverTheSizeLimit() {
        expectReason(GifException.Reason.FILE_TOO_LARGE) {
            GifReader.open(TestGifs.TWO_FRAMES, GifLimits(maxFileBytes = 10))
        }
    }

    @Test
    fun rejectsHugeDimensionsFromTheHeaderAlone() {
        // "GIF89a", width 5000, height 10, no colour table, background 0, aspect 0.
        val header = "GIF89a".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf((5000 and 0xFF).toByte(), (5000 shr 8).toByte(), 10, 0, 0, 0, 0)
        expectReason(GifException.Reason.DIMENSIONS_TOO_BIG) { GifReader.open(header) }
    }

    @Test
    fun rejectsCanvasOverThePixelBudgetEvenIfEachSideIsAllowed() {
        expectReason(GifException.Reason.DIMENSIONS_TOO_BIG) {
            GifReader.open(TestGifs.TWO_FRAMES, GifLimits(maxCanvasPixels = 15))
        }
    }

    @Test
    fun truncatedHeaderIsRejectedWithoutCrashing() {
        expectReason(GifException.Reason.TRUNCATED) {
            GifReader.open(TestGifs.TWO_FRAMES.copyOf(8))
        }
    }

    @Test
    fun fileCutInsideTheSecondFrameKeepsTheFirst() {
        val cut = TestGifs.TWO_FRAMES.copyOf(TestGifs.TWO_FRAMES.size - 12)
        val reader = GifReader.open(cut)
        assertEquals(1, reader.info.frameCount)
        assertTrue(decodeAll(reader).single().all { it == red })
    }

    @Test
    fun headerOnlyWithNoFramesIsRejected() {
        // Valid 1x1 header with a trailer straight away.
        val bytes = "GIF89a".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(1, 0, 1, 0, 0, 0, 0, 0x3B)
        expectReason(GifException.Reason.NO_FRAMES) { GifReader.open(bytes) }
    }

    @Test
    fun randomGarbageNeverCrashesWithAnythingButGifException() {
        val rnd = java.util.Random(42)
        repeat(300) {
            val bytes = TestGifs.TWO_FRAMES.copyOf()
            // Corrupt a few random bytes after the signature.
            repeat(1 + rnd.nextInt(4)) { bytes[6 + rnd.nextInt(bytes.size - 6)] = rnd.nextInt(256).toByte() }
            try {
                val reader = GifReader.open(bytes)
                reader.decode { _, _, _ -> true }
            } catch (e: GifException) {
                // fine: rejected cleanly
            }
        }
    }
}
