package app.gifwidgets.audio

import app.gifwidgets.control.DesignStyle
import app.gifwidgets.control.Season
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthTest {
    @Test
    fun everySeasonStyleAndTimeOfDayHasAPiece() {
        for (style in DesignStyle.entries) for (season in Season.entries) for (night in listOf(false, true)) {
            MusicPieces.pick(style, season, night)
        }
        assertTrue(MusicPieces.all().isNotEmpty())
    }

    @Test
    fun barsAreAudibleNeverClipAndAreDeterministic() {
        for (piece in MusicPieces.all()) {
            val a = SynthRenderer(piece)
            val b = SynthRenderer(piece)
            var peak = 0
            var sumSq = 0.0
            var n = 0
            repeat(8) {
                val x = a.nextBar()
                assertTrue(piece.key, x.contentEquals(b.nextBar()))
                for (v in x) {
                    peak = maxOf(peak, abs(v.toInt()))
                    sumSq += v.toDouble() * v
                    n++
                }
            }
            val rms = sqrt(sumSq / n) / 32767.0
            assertTrue("${piece.key} clips", peak < 32000)
            assertTrue("${piece.key} too quiet", rms > 0.01)
            assertTrue("${piece.key} too loud", rms < 0.35)
        }
    }

    @Test
    fun littleSoundsHaveSamplesAndStayInRange() {
        for (season in Season.entries) {
            val note = SfxSynth.noteFor(season, 660.0, -1)
            val beep = SfxSynth.beep(season, note, .07, false, 1.0)
            assertTrue(beep.isNotEmpty())
            assertTrue(beep.all { abs(it.toInt()) < 32000 })
        }
        val baa = SfxSynth.baa(false, false, .5, 1.0)
        assertTrue(baa.size > SfxSynth.SAMPLE_RATE / 10)
        assertEquals(true, baa.any { it.toInt() != 0 })
    }
}
