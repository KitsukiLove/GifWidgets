package app.gifwidgets.audio

import app.gifwidgets.control.Season
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The page's little interface sounds, ported: a soft "blip" on a note of the season's scale (it never repeats the
 * same note twice in a row) and Kixki's "beee". Pure Kotlin: returns 16-bit mono samples.
 */
object SfxSynth {
    const val SAMPLE_RATE = 22050

    private class Voice(val scale: IntArray, val triangle: Boolean, val bell: Boolean, val low: Boolean)

    private val PENTA = intArrayOf(0, 2, 4, 7, 9)

    private fun voiceFor(season: Season): Voice = when (season) {
        Season.CLASICA -> Voice(PENTA, false, false, false)
        Season.VALLE, Season.PRIMAVERA, Season.VERANO -> Voice(PENTA, true, false, false)
        Season.OTONO -> Voice(intArrayOf(0, 3, 5, 7, 10), false, false, false)
        Season.INVIERNO, Season.NAVIDAD -> Voice(PENTA, false, true, false)
        Season.HALLOWEEN -> Voice(intArrayOf(0, 3, 5, 6, 7, 10), true, false, true)
    }

    /** The scale note (MIDI) nearest to [requestedHz]; [avoid] is the last note played (to not repeat it). */
    fun noteFor(season: Season, requestedHz: Double, avoid: Int): Int {
        val v = voiceFor(season)
        val m = (69 + 12 * ln(requestedHz / 440.0) / ln(2.0)).roundToInt()
        val r = ((m % 12) + 12) % 12
        val base = m - r
        var best = 0
        for (j in v.scale.indices) if (abs(v.scale[j] - r) < abs(v.scale[best] - r)) best = j
        var note = base + v.scale[best]
        if (note == avoid) {
            best = max(0, min(v.scale.size - 1, best + (if (best > 0) -1 else 1)))
            note = base + v.scale[best]
        }
        return note
    }

    /** A blip on MIDI [note]. [volume] 0..1 already includes night / music ducking. */
    fun beep(season: Season, note: Int, lengthSec: Double, night: Boolean, volume: Double): ShortArray {
        val v = voiceFor(season)
        val sr = SAMPLE_RATE
        val fr = midiToHz((if (v.low) note - 12 else note).toDouble())
        val len = max(lengthSec, .1) + .1
        val n = ((len + .02) * sr).toInt()
        val out = ShortArray(n)
        var ph1 = 0.0
        var ph2 = 0.0
        val lp = onePole(if (night) 1700.0 else 2600.0, sr)
        var y = 0.0
        val hMul = if (v.bell) 2.76 else 2.0
        val hGain = if (v.bell) .14 else .22
        val decay = (0.0001 / .075).pow(1.0 / (len * sr))
        var level = .075
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val glide = 1.0 - .015 * exp(-t / .012)
            ph1 += fr * glide / sr
            ph2 += fr * hMul / sr
            val wave1 = if (v.triangle) tri(ph1) else sin(2 * PI * ph1)
            val wave2 = if (v.bell) sin(2 * PI * ph2) else tri(ph2)
            val attack = if (t < .008) t / .008 * .075 else { level *= decay; level }
            val s = (wave1 + wave2 * hGain) * attack
            y += (s - y) * lp
            out[i] = (y * volume * 3.2 * 32767.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return out
    }

    /** Kixki's bleat. [rnd] gives 0..1 so the pitch varies a little. */
    fun baa(night: Boolean, halloween: Boolean, rnd: Double, volume: Double): ShortArray {
        val sr = SAMPLE_RATE
        val base = (360 + rnd * 80) * (if (night) .88 else 1.0) * (if (halloween) .82 else 1.0)
        val n = (.62 * sr).toInt()
        val out = ShortArray(n)
        val bp1 = Biquad('b', 900.0, 2.2, sr)
        val bp2 = Biquad('b', 1700.0, 3.0, sr)
        var ph = 0.0
        val decay = (0.0001 / .085).pow(1.0 / (.55 * sr))
        var level = .085
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val f = (if (t < .5) base * (1.15 - .3 * (t / .5)) else base * .85) + 22.0 * sin(2 * PI * 28.0 * t)
            ph += f / sr
            ph -= floor(ph)
            val saw = 2 * ph - 1
            val env = if (t < .06) t / .06 * .085 else { level *= decay; level }
            val s = (bp1.process(saw) + bp2.process(saw)) * env
            out[i] = (s * volume * 5.0 * 32767.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return out
    }

    private fun tri(ph: Double): Double = 4 * abs(ph - floor(ph + .5)) - 1
}
