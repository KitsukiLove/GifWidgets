package app.gifwidgets.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

// A small software synthesiser for the pieces of MusicPieces: oscillators, envelopes, one-pole and biquad filters,
// a feedback delay and a tiny reverb. It renders one bar (4 beats) at a time as 16-bit mono, so a player can
// stream it. Pure Kotlin (no Android): the same code is used by the unit tests.

/** The page's seeded random generator (a mulberry32 variant): same seed, same melody. */
class SeededRng(seed: Int) {
    private var s = seed

    fun next(): Double {
        s += 1831565813
        var t = (s xor (s ushr 15)) * (1 or s)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

internal fun midiToHz(m: Double): Double = 440.0 * 2.0.pow((m - 69.0) / 12.0)

/** RBJ biquad (band-pass / high-pass), for the noisy drum sounds. */
internal class Biquad(type: Char, freq: Double, q: Double, sr: Int) {
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    init {
        val w0 = 2 * PI * min(freq, sr * 0.45) / sr
        val alpha = sin(w0) / (2 * q)
        val cs = cos(w0)
        val a0 = 1 + alpha
        if (type == 'h') { // high-pass
            b0 = (1 + cs) / 2 / a0
            b1 = -(1 + cs) / a0
            b2 = (1 + cs) / 2 / a0
        } else { // band-pass (constant 0 dB peak)
            b0 = alpha / a0
            b1 = 0.0
            b2 = -alpha / a0
        }
        a1 = -2 * cs / a0
        a2 = (1 - alpha) / a0
    }

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

internal fun onePole(cutoff: Double, sr: Int): Double = 1.0 - exp(-2.0 * PI * cutoff / sr)

/**
 * Renders [piece] bar by bar. [nextBar] returns exactly one bar of audio (the tails of the previous notes are
 * already mixed in), so call it again and again for an endless stream.
 */
class SynthRenderer(val piece: Piece, val sampleRate: Int = 22050) {
    private val sr = sampleRate
    private val barSamples = (240.0 / piece.bpm * sr).roundToInt()
    private val tailSamples = (4.5 * sr).toInt()
    private var dry = FloatArray(barSamples + tailSamples)
    private var carry = FloatArray(tailSamples)
    private var bar = 0
    private var phrase: List<Note>? = null
    private var rnd = SeededRng(piece.seed * 7919)
    private var jitter = SeededRng(piece.seed * 31 + 5)

    // effects state
    private val delayLen = max(1, (60.0 / piece.bpm * .75 * sr).roundToInt())
    private val delayBuf = FloatArray(delayLen)
    private var delayPos = 0
    private var delayLp = 0.0
    private val delayLpA = onePole(2400.0, sr)
    private val combLens = doubleArrayOf(0.0297, 0.0371, 0.0411, 0.0437).map { (it * sr).toInt() }
    private val combBufs = combLens.map { FloatArray(it) }
    private val combPos = IntArray(4)
    private val combLp = DoubleArray(4)
    private val apLens = intArrayOf((0.005 * sr).toInt(), (0.0017 * sr).toInt())
    private val apBufs = apLens.map { FloatArray(it) }
    private val apPos = IntArray(2)
    private val masterA = onePole(piece.lowpass.toDouble(), sr)
    private var m1 = 0.0
    private var m2 = 0.0

    class Note(val k: Int, var note: Int, val idx: Int)

    val samplesPerBar: Int get() = barSamples

    fun nextBar(): ShortArray {
        java.util.Arrays.fill(dry, 0f)
        System.arraycopy(carry, 0, dry, 0, tailSamples)
        scheduleBar(bar)
        // keep what rings past this bar for the next one
        System.arraycopy(dry, barSamples, carry, 0, tailSamples)
        val out = ShortArray(barSamples)
        for (i in 0 until barSamples) out[i] = toShort(effects(dry[i].toDouble()))
        bar++
        return out
    }

    // ----- effects chain: dry + delay + reverb, then a warm low-pass and a soft limiter -----

    private fun effects(x: Double): Double {
        // feedback delay (dotted eighth) with a dark repeat
        val d = delayBuf[delayPos].toDouble()
        delayLp += (d - delayLp) * delayLpA
        delayBuf[delayPos] = (x * piece.delay + delayLp * .38).toFloat()
        delayPos = (delayPos + 1) % delayLen
        // small reverb: four damped combs and two all-passes
        val send = (x + delayLp * .5) * .25
        var wet = 0.0
        for (i in 0 until 4) {
            val buf = combBufs[i]
            val y = buf[combPos[i]].toDouble()
            combLp[i] += (y - combLp[i]) * .35
            buf[combPos[i]] = (send + combLp[i] * .78).toFloat()
            combPos[i] = (combPos[i] + 1) % buf.size
            wet += y
        }
        wet *= .5
        for (i in 0 until 2) {
            val buf = apBufs[i]
            val z = buf[apPos[i]].toDouble()
            val v = wet + z * .5
            buf[apPos[i]] = v.toFloat()
            apPos[i] = (apPos[i] + 1) % buf.size
            wet = z - v * .5
        }
        val mixed = x + delayLp + wet * piece.reverb * 2.2
        // two-pole low-pass (the page's warm filter)
        m1 += (mixed - m1) * masterA
        m2 += (m1 - m2) * masterA
        return tanh(m2 * 1.25) * .8
    }

    private fun toShort(x: Double): Short = (x * .75 * 32767.0).coerceIn(-32767.0, 32767.0).toInt().toShort()

    // ----- one bar of the arrangement (port of the page's `bar()`) -----

    private fun scheduleBar(b: Int) {
        val p = piece
        val sp = 60.0 / p.bpm
        val e = sp / 2
        val s = b % 16
        val sec = s shr 2
        val pr = if (sec == 2 && p.prog2 != null) p.prog2 else p.prog
        val ch = pr[s and 3]
        val rt = p.root + ch.root
        val iv = MusicPieces.CHORDS.getValue(ch.quality)
        val intro = b < 4
        fun at(step: Int): Double =
            step * e + (if (step and 1 == 1) e * p.swing * .33 else 0.0) + (jitter.next() - .5) * .012
        fun vr(): Double = .86 + jitter.next() * .28

        if (p.pad > 0) {
            val freqs = iv.map {
                var n = rt + it
                while (n < 54) n += 12
                while (n >= 72) n -= 12
                midiToHz(n.toDouble())
            }
            pad(freqs, 0.0, sp * 4, p.pad * 1.5)
        }
        if (p.bass != null) {
            var r = rt
            while (r >= 53) r -= 12
            while (r < 41) r += 12
            for (bn in p.bassPattern) {
                val f = midiToHz((r + bn.interval).toDouble())
                val t = at(bn.step)
                val d = bn.length * e * 1.1
                val v = p.bassVol * vr()
                if (p.bass == "sub") sub(f, t, d, v) else tri(f, t, d, v)
            }
        }
        if (p.arp != null && !intro) {
            val tn = iv.take(4).map {
                var n = rt + it + p.arpOctave
                while (n < 57) n += 12
                while (n >= 79) n -= 12
                n
            }
            p.arpPattern.forEachIndexed { st, a ->
                if (a >= 0) {
                    val f = midiToHz(tn[a % tn.size].toDouble())
                    val d = e * (if (p.arp == "bell") 5.0 else 1.6)
                    voice(p.arp, f, at(st), d, p.arpVol * vr())
                }
            }
        }
        p.drums?.let { dr ->
            for (h in dr.kick) kick(at(h.step), h.vel * vr())
            for (h in dr.rim) rim(at(h.step), h.vel * vr())
            for (h in dr.hat) hat(at(h.step), h.vel * vr())
            for (h in dr.shaker) shaker(at(h.step), h.vel * vr())
            for (h in dr.bells) sleigh(at(h.step), h.vel * vr())
        }
        if (!intro) {
            if (s and 1 == 0) phrase = makePhrase(s, pr)
            phrase?.let { ph ->
                ph.forEachIndexed { i, note ->
                    if ((note.k shr 3) != (s and 1)) return@forEachIndexed
                    val nx = ph.getOrNull(i + 1)
                    val l = min(if (nx != null) nx.k - note.k else 6, 5)
                    voice(p.lead, midiToHz(note.note.toDouble()), at(note.k and 7), max(.25, l * e * p.sustain), p.leadVol * 1.45 * vr())
                }
            }
            if (p.sparkle > 0 && jitter.next() < p.sparkle) {
                val tn = iv[(jitter.next() * iv.size).toInt()]
                var n = rt + tn + 24
                while (n > 96) n -= 12
                val st = intArrayOf(2, 4, 6)[(jitter.next() * 3).toInt()]
                bell(midiToHz(n.toDouble()), at(st), sp * 3, p.leadVol * .7)
            }
        }
    }

    private fun voice(name: String, f: Double, t: Double, d: Double, v: Double) {
        when (name) {
            "ep" -> ep(f, t, d, v)
            "bell" -> bell(f, t, d, v)
            "pluck" -> pluck(f, t, d, v)
            "vib" -> vibrato(f, t, d, v)
            else -> pulse(f, t, d, v)
        }
    }

    /** The page's melody generator: a seeded walk over the scale that leans on the chord notes. */
    private fun makePhrase(s: Int, pr: List<Chord>): List<Note> {
        val p = piece
        val sec = s shr 2
        val pair = (s shr 1) and 1
        val salt = intArrayOf(0, if (pair == 1) 30 else 0, 10, if (pair == 1) 40 else 0)[sec]
        val r = SeededRng(p.seed * 977 + pair + salt)
        val sc = MusicPieces.SCALES.getValue(p.scale)
        val pool = ArrayList<Int>()
        for (n in p.low..p.high) if (sc.contains((((n - p.root) % 12) + 12) % 12)) pool.add(n)
        val patterns = MusicPieces.RHYTHMS[min(2, p.density)]
        val pat = patterns[(r.next() * patterns.size).toInt()]
        val out = ArrayList<Note>()
        val ca = pr[s and 3]
        val cb = pr[(s + 1) and 3]
        var idx = (pool.size * .45).toInt()
        var last = -1
        for (k in 0 until 16) {
            if (pat[k] == 0) continue
            val ch = if (k < 8) ca else cb
            val pcs = MusicPieces.CHORDS.getValue(ch.quality).map { (p.root + ch.root + it) % 12 }
            if (k % 4 == 0) {
                var best = idx
                var bd = 99
                for (j in pool.indices) {
                    val d = abs(j - idx) + (if (pcs.contains(pool[j] % 12)) 0 else 5) + (if (j == last) 2 else 0)
                    if (d < bd) { bd = d; best = j }
                }
                idx = best
            } else {
                idx += intArrayOf(-2, -1, -1, 1, 1, 2)[(r.next() * 6).toInt()]
                if (idx > pool.size * .8) idx -= 2
                if (idx < pool.size * .1) idx += 2
                idx = max(0, min(pool.size - 1, idx))
            }
            last = idx
            out.add(Note(k, pool[idx], idx))
        }
        if (pair == 1 && out.isNotEmpty()) {
            val lastIdx = out[out.size - 1].idx
            var b = lastIdx
            var bd = 99
            pool.forEachIndexed { j, n ->
                val d = abs(j - lastIdx) + (if ((n - p.root) % 12 == 0) 0 else 3)
                if (d < bd) { bd = d; b = j }
            }
            out[out.size - 1].note = pool[b]
        }
        return out
    }

    // ----- low level: write a decaying note into the dry buffer -----

    /** Index of second [t] of this bar in the dry buffer. */
    private fun at0(t: Double): Int = max(0, (t * sr).toInt())

    private inline fun render(t: Double, seconds: Double, body: (i: Int, pos: Int, time: Double) -> Float) {
        val start = at0(t)
        val n = min((seconds * sr).toInt(), dry.size - start)
        for (i in 0 until n) dry[start + i] += body(i, start + i, i.toDouble() / sr)
    }

    /** Page envelope: linear attack [a], then an exponential fall to 0.0001 over [d]; returns a per-sample generator. */
    private class Env(a: Double, private val d: Double, private val v: Double, sr: Int) {
        private val attackSamples = max(1.0, a * sr)
        private val decayPerSample: Double = if (v <= 0.0001) 0.0 else (0.0001 / v).pow(1.0 / max(1.0, d * sr))
        private var level = v
        private var n = 0
        fun next(): Double {
            val out = if (n < attackSamples) v * (n / attackSamples) else { level *= decayPerSample; level }
            n++
            return out
        }
    }

    private fun sine(f: Double, t: Double, a: Double, d: Double, v: Double) {
        val env = Env(a, d, v, sr)
        val w = 2 * PI * f / sr
        var ph = 0.0
        render(t, a + d + .02) { _, _, _ -> val s = sin(ph) * env.next(); ph += w; s.toFloat() }
    }

    private fun ep(f: Double, t: Double, d: Double, v: Double) {
        sine(f, t, .006, d, v)
        sine(f * 2, t, .006, d * .5, v * .26)
        sine(f * 4, t, .006, d * .22, v * .06)
        sine(f * 7, t, .006, .1, v * .03)
    }

    private fun bell(f: Double, t: Double, d: Double, v: Double) {
        sine(f, t, .004, d, v)
        sine(f * 2.76, t, .004, d * .55, v * .38)
        sine(f * 5.4, t, .004, d * .3, v * .16)
        sine(f * 8.9, t, .004, d * .16, v * .06)
    }

    private fun pluck(f: Double, t: Double, d: Double, v: Double) {
        val env = Env(.004, d, v, sr)
        val w = f / sr
        var ph = 0.0
        var y = 0.0
        val total = d * .6 * sr
        render(t, .004 + d + .02) { i, _, _ ->
            val tri = 4 * abs(ph - floorD(ph + .5)) - 1 // triangle in -1..1
            ph += w
            val fc = 3200.0 * (500.0 / 3200.0).pow(min(1.0, i / total))
            y += (tri - y) * onePole(fc, sr)
            (y * env.next()).toFloat()
        }
    }

    private fun floorD(x: Double): Double = kotlin.math.floor(x)

    private fun pulse(f: Double, t: Double, d: Double, v: Double) {
        val env = Env(.012, d, v, sr)
        var ph = 0.0
        var y = 0.0
        var lfo = 0.0
        val lfoW = 2 * PI * 5.3 / sr
        val a = onePole(2400.0, sr)
        val ramp = min(d * .7, .5) * sr
        render(t, .012 + d + .06) { i, _, _ ->
            val cents = 8.0 * min(1.0, i / ramp) * sin(lfo)
            lfo += lfoW
            ph += f * 2.0.pow(cents / 1200.0) / sr
            ph -= floorD(ph)
            val raw = if (ph < .25) 1.0 else -1.0
            y += (raw - y) * a
            (y * .6 * env.next()).toFloat()
        }
    }

    private fun vibrato(f: Double, t: Double, d: Double, v: Double) {
        val env = Env(.18, d, v * 1.1, sr)
        val env2 = Env(.2, d, v * .18, sr)
        var ph = 0.0
        var ph2 = 0.0
        var lfo = 0.0
        val lfoW = 2 * PI * 5.6 / sr
        val ramp = min((d + .2) * .7, .5) * sr
        render(t, .2 + d + .06) { i, _, _ ->
            val k = 2.0.pow(24.0 * min(1.0, i / ramp) * sin(lfo) / 1200.0)
            lfo += lfoW
            ph += 2 * PI * f * k / sr
            ph2 += 2 * PI * f * 2 / sr
            (sin(ph) * env.next() + sin(ph2) * env2.next()).toFloat()
        }
    }

    private fun pad(freqs: List<Double>, t: Double, d: Double, v: Double) {
        val each = v / freqs.size
        val a = onePole(1000.0, sr)
        for (f in freqs) {
            for (cents in doubleArrayOf(-6.0, 6.0)) {
                val w = f * 2.0.pow(cents / 1200.0) / sr
                var ph = 0.0
                var y1 = 0.0
                var y2 = 0.0
                val total = d + 1.0
                val up = .7 * sr
                val hold = d * sr
                val down = .9 * sr
                render(t, total) { i, _, _ ->
                    ph += w
                    ph -= floorD(ph)
                    val saw = 2 * ph - 1
                    y1 += (saw - y1) * a
                    y2 += (y1 - y2) * a
                    val g = when {
                        i < up -> each * (i / up)
                        i < hold -> each
                        else -> each * max(0.0, 1.0 - (i - hold) / down)
                    }
                    (y2 * g).toFloat()
                }
            }
        }
    }

    private fun sub(f: Double, t: Double, d: Double, v: Double) {
        sine(f, t, .015, d, v)
        triangle(f * 2, t, .01, d * .5, v * .5)
    }

    private fun tri(f: Double, t: Double, d: Double, v: Double) = triangle(f, t, .008, d, v)

    private fun triangle(f: Double, t: Double, a: Double, d: Double, v: Double) {
        val env = Env(a, d, v, sr)
        val w = f / sr
        var ph = 0.0
        render(t, a + d + .02) { _, _, _ ->
            val s = 4 * abs(ph - floorD(ph + .5)) - 1
            ph += w
            (s * env.next()).toFloat()
        }
    }

    private fun kick(t: Double, v: Double) {
        val env = Env(.002, .24, v, sr)
        var ph = 0.0
        render(t, .3) { i, _, time ->
            val f = 130.0 * (46.0 / 130.0).pow(min(1.0, time / .12))
            ph += 2 * PI * f / sr
            (sin(ph) * env.next()).toFloat()
        }
    }

    private fun noise(t: Double, d: Double, v: Double, filter: Biquad, a: Double = .002) {
        val env = Env(a, d, v, sr)
        render(t, d + .05) { _, _, _ -> (filter.process(jitter.next() * 2 - 1) * env.next()).toFloat() }
    }

    private fun hat(t: Double, v: Double) = noise(t, .045, v * .18, Biquad('h', 7500.0, .5, sr))
    private fun shaker(t: Double, v: Double) = noise(t, .08, v * .2, Biquad('b', 5200.0, 1.2, sr), .012)

    private fun rim(t: Double, v: Double) {
        noise(t, .06, v * .2, Biquad('b', 1900.0, 2.2, sr))
        sine(330.0, t, .002, .06, v * .1)
    }

    private fun sleigh(t: Double, v: Double) {
        for (f in doubleArrayOf(3300.0, 4100.0, 5200.0, 6100.0)) sine(f * (1 + jitter.next() * .01), t, .003, .28, v * .05)
        noise(t, .1, v * .08, Biquad('h', 6000.0, .6, sr), .004)
    }
}
