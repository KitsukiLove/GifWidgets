package app.gifwidgets.audio

import app.gifwidgets.control.DesignStyle
import app.gifwidgets.control.Season
import kotlin.math.max
import kotlin.math.roundToInt

// The music of the KixKi Store page, ported: 30 short generated pieces (Pixel world = soft chiptune, Colina XP =
// early-2000s chillout), one per season and per day/night. Nothing is stored or downloaded: the notes are composed
// from a fixed seed and the sound is synthesised on the phone (see Synth.kt). Pure Kotlin, no Android.

/** One drum hit: eighth-note step 0..7 of the bar and its velocity. */
class Hit(val step: Int, val vel: Float)

/** Drum pattern: kick (k), rim (r), hat (h), shaker (sh), sleigh bells (sl). */
class Drums(
    val kick: List<Hit> = emptyList(),
    val rim: List<Hit> = emptyList(),
    val hat: List<Hit> = emptyList(),
    val shaker: List<Hit> = emptyList(),
    val bells: List<Hit> = emptyList(),
)

class Chord(val root: Int, val quality: String)

/** A bass note: eighth-note step, interval over the root, length in eighths. */
class BassNote(val step: Int, val interval: Int, val length: Int)

class Piece(
    val key: String,
    val bpm: Int,
    val root: Int,
    val scale: String,
    val prog: List<Chord>,
    val prog2: List<Chord>?,
    val lead: String,
    val leadVol: Double,
    val density: Int,
    val sustain: Double,
    val pad: Double,
    val bass: String?,
    val bassVol: Double,
    val bassPattern: List<BassNote>,
    val arp: String?,
    val arpVol: Double,
    val arpPattern: List<Int>,
    val arpOctave: Int,
    val drums: Drums?,
    val swing: Double,
    val delay: Double,
    val reverb: Double,
    val seed: Int,
    val low: Int,
    val high: Int,
    val sparkle: Double,
    val lowpass: Int,
)

object MusicPieces {

    // ----- theory tables -----
    val CHORDS: Map<String, IntArray> = mapOf(
        "t" to intArrayOf(0, 4, 7), "n" to intArrayOf(0, 3, 7), "M" to intArrayOf(0, 4, 7, 11),
        "m" to intArrayOf(0, 3, 7, 10), "d" to intArrayOf(0, 4, 7, 10), "M9" to intArrayOf(0, 4, 7, 11, 14),
        "m9" to intArrayOf(0, 3, 7, 10, 14), "s" to intArrayOf(0, 5, 7, 10), "M6" to intArrayOf(0, 4, 7, 9),
    )
    val SCALES: Map<String, IntArray> = mapOf(
        "maj" to intArrayOf(0, 2, 4, 5, 7, 9, 11), "pen" to intArrayOf(0, 2, 4, 7, 9),
        "lyd" to intArrayOf(0, 2, 4, 6, 7, 9, 11), "dor" to intArrayOf(0, 2, 3, 5, 7, 9, 10),
        "min" to intArrayOf(0, 2, 3, 5, 7, 8, 10), "pmi" to intArrayOf(0, 3, 5, 7, 10),
        "har" to intArrayOf(0, 2, 3, 5, 7, 8, 11),
    )

    /** Melody rhythms: 16 positions (two bars of eighths), three densities. */
    val RHYTHMS: List<List<IntArray>> = listOf(
        listOf(
            intArrayOf(1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0),
        ),
        listOf(
            intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0),
            intArrayOf(1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 0, 0),
            intArrayOf(1, 0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0),
        ),
        listOf(
            intArrayOf(1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 0),
            intArrayOf(1, 1, 0, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 0),
        ),
    )

    private fun h(step: Int, vel: Double = 1.0) = Hit(step, vel.toFloat())

    private val DR_PX = Drums(
        kick = listOf(h(0), h(4)), rim = listOf(h(2, .5), h(6, .5)),
        hat = listOf(h(1, .6), h(3, .6), h(5, .6), h(7, .6)),
    )
    private val DR_PXS = Drums(kick = listOf(h(0)), rim = listOf(h(6, .45)), hat = listOf(h(2, .5), h(6, .5)))
    private val SH_ALL = listOf(h(0, .5), h(1, .8), h(2, .5), h(3, .8), h(4, .5), h(5, .8), h(6, .5), h(7, .8))
    private val DR_CHILL = Drums(kick = listOf(h(0), h(5, .8)), rim = listOf(h(4, .7)), shaker = SH_ALL)
    private val DR_CHILLS = Drums(
        kick = listOf(h(0)), rim = listOf(h(4, .5)),
        shaker = listOf(h(1, .7), h(3, .7), h(5, .7), h(7, .7)),
    )
    private val DR_BOSSA = Drums(
        kick = listOf(h(0), h(3, .8), h(6, .8)), rim = listOf(h(2, .6), h(5, .6), h(7, .5)),
        shaker = listOf(h(0, .5), h(1, .7), h(2, .5), h(3, .7), h(4, .5), h(5, .7), h(6, .5), h(7, .7)),
    )
    private val DR_BELL = Drums(kick = listOf(h(0)), bells = listOf(h(0, .7), h(2, .5), h(4, .7), h(6, .5)))
    private val DR_BELLS = Drums(bells = listOf(h(0, .5), h(4, .5)))
    private val DR_HEART = Drums(kick = listOf(h(0), h(1, .55)))

    // ----- the page's piece tables, as "overrides over a base" -----
    private fun c(vararg p: Pair<Int, String>): List<Chord> = p.map { Chord(it.first, it.second) }

    private val BASE: Map<String, Any?> = mapOf(
        "sc" to "maj", "lead" to "pulse", "lv" to .3, "den" to 1, "sus" to 1.3, "pad" to 0.0, "bass" to "tri", "bv" to .2,
        "arp" to null, "av" to .12, "ap" to listOf(0, 1, 2, 3, 2, 1, 2, 1), "dr" to null, "sw" to 0.0, "dl" to .2,
        "rv" to .25, "seed" to 1, "key" to 60, "bpm" to 100, "lo" to 64, "hi" to 84, "ao" to 12, "sp" to 0.0, "lp" to 6000,
    )

    private val PXD: Map<String, Map<String, Any?>> = mapOf(
        "classic" to mapOf("bpm" to 102, "key" to 60, "sc" to "pen", "prog" to c(0 to "t", 7 to "t", 9 to "n", 5 to "t"), "prog2" to c(9 to "n", 5 to "t", 0 to "t", 7 to "d"), "lead" to "pulse", "arp" to "pluck", "pad" to .04, "dr" to DR_PX, "seed" to 3, "den" to 1),
        "valle" to mapOf("bpm" to 96, "key" to 62, "sc" to "maj", "prog" to c(0 to "t", 5 to "t", 7 to "t", 9 to "n"), "prog2" to c(5 to "t", 7 to "t", 4 to "n", 9 to "n"), "lead" to "pluck", "lv" to .34, "arp" to "pluck", "pad" to .05, "dr" to DR_PXS, "seed" to 7, "den" to 1),
        "primavera" to mapOf("bpm" to 112, "key" to 67, "sc" to "lyd", "prog" to c(0 to "t", 4 to "n", 5 to "t", 7 to "t"), "prog2" to c(9 to "n", 5 to "t", 0 to "t", 7 to "t"), "lead" to "bell", "lv" to .3, "arp" to "pluck", "pad" to .04, "dr" to DR_PX, "seed" to 11, "den" to 2, "sp" to .3),
        "verano" to mapOf("bpm" to 116, "key" to 65, "sc" to "maj", "prog" to c(0 to "t", 5 to "t", 7 to "d", 0 to "t"), "prog2" to c(2 to "n", 7 to "d", 0 to "t", 5 to "t"), "lead" to "pulse", "lv" to .28, "arp" to "pluck", "pad" to .03, "dr" to DR_PX, "seed" to 13, "den" to 2, "ap" to listOf(0, 2, 1, 3, 0, 2, 1, 3)),
        "otono" to mapOf("bpm" to 88, "key" to 57, "sc" to "dor", "prog" to c(0 to "n", 5 to "t", 3 to "t", 7 to "d"), "prog2" to c(0 to "n", 10 to "t", 5 to "t", 7 to "d"), "lead" to "pluck", "lv" to .36, "arp" to "pluck", "pad" to .05, "dr" to DR_PXS, "seed" to 17, "den" to 1),
        "invierno" to mapOf("bpm" to 76, "key" to 64, "sc" to "maj", "prog" to c(0 to "t", 9 to "n", 5 to "t", 7 to "t"), "prog2" to c(5 to "t", 0 to "t", 2 to "n", 7 to "t"), "lead" to "bell", "lv" to .34, "arp" to "bell", "av" to .1, "pad" to .07, "dr" to null, "seed" to 19, "den" to 0, "sus" to 2.0, "ap" to listOf(0, -1, 2, -1, 1, -1, 3, -1)),
        "halloween" to mapOf("bpm" to 86, "key" to 57, "sc" to "har", "prog" to c(0 to "n", 8 to "t", 7 to "d", 0 to "n"), "prog2" to c(5 to "n", 3 to "t", 7 to "d", 0 to "n"), "lead" to "vib", "lv" to .26, "arp" to "pluck", "pad" to .05, "dr" to DR_HEART, "seed" to 23, "den" to 0, "sus" to 2.0, "ap" to listOf(0, -1, 1, -1, 2, -1, 1, -1)),
        "navidad" to mapOf("bpm" to 100, "key" to 60, "sc" to "maj", "prog" to c(0 to "t", 9 to "n", 2 to "n", 7 to "d"), "prog2" to c(5 to "t", 7 to "d", 0 to "t", 0 to "t"), "lead" to "bell", "lv" to .32, "arp" to "bell", "av" to .11, "pad" to .04, "dr" to DR_BELL, "seed" to 29, "den" to 1, "sp" to .35),
    )

    private val PXN: Map<String, Map<String, Any?>> = mapOf(
        "classic" to mapOf("bpm" to 78, "key" to 57, "sc" to "pmi", "prog" to c(0 to "n", 8 to "t", 3 to "t", 10 to "t"), "prog2" to c(5 to "n", 3 to "t", 10 to "t", 7 to "d"), "lead" to "ep", "lv" to .3, "pad" to .07, "dr" to DR_PXS, "ap" to listOf(0, -1, 1, -1, 2, -1, 1, -1), "seed" to 5),
        "valle" to mapOf("bpm" to 72, "key" to 62, "sc" to "pen", "prog" to c(0 to "t", 7 to "t", 9 to "n", 5 to "t"), "prog2" to c(5 to "t", 0 to "t", 7 to "t", 4 to "n"), "lead" to "bell", "lv" to .3, "pad" to .08, "dr" to null, "ap" to listOf(0, -1, 2, -1, 1, -1, 3, -1), "seed" to 8, "den" to 0),
        "primavera" to mapOf("bpm" to 80, "key" to 67, "sc" to "pen", "prog" to c(0 to "t", 4 to "n", 5 to "t", 7 to "t"), "lead" to "ep", "lv" to .3, "pad" to .07, "dr" to null, "ap" to listOf(0, -1, 2, -1, 1, -1, 3, -1), "seed" to 12, "den" to 0, "sp" to .4),
        "verano" to mapOf("bpm" to 84, "key" to 65, "sc" to "pen", "prog" to c(0 to "t", 5 to "t", 2 to "n", 7 to "d"), "lead" to "ep", "lv" to .3, "pad" to .07, "dr" to DR_CHILLS, "seed" to 14, "den" to 1),
        "otono" to mapOf("bpm" to 68, "key" to 57, "sc" to "pmi", "prog" to c(0 to "n", 5 to "t", 3 to "t", 7 to "d"), "lead" to "ep", "lv" to .32, "pad" to .08, "dr" to null, "ap" to listOf(0, -1, 1, -1, 2, -1, 1, -1), "seed" to 18, "den" to 0),
        "invierno" to mapOf("bpm" to 60, "key" to 64, "sc" to "pen", "prog" to c(0 to "t", 9 to "n", 5 to "t", 7 to "t"), "lead" to "bell", "lv" to .32, "pad" to .09, "dr" to null, "arp" to null, "seed" to 20, "den" to 0, "sus" to 2.4, "sp" to .5),
        "halloween" to mapOf("bpm" to 72, "key" to 57, "sc" to "har", "prog" to c(0 to "n", 8 to "t", 7 to "d", 0 to "n"), "lead" to "vib", "lv" to .26, "pad" to .08, "dr" to DR_HEART, "arp" to null, "seed" to 24, "den" to 0, "sus" to 2.4, "sp" to .4),
        "navidad" to mapOf("bpm" to 80, "key" to 60, "sc" to "maj", "prog" to c(0 to "t", 9 to "n", 2 to "n", 7 to "d"), "lead" to "bell", "lv" to .3, "pad" to .07, "dr" to DR_BELLS, "arp" to "bell", "av" to .09, "ap" to listOf(0, -1, -1, -1, 2, -1, -1, -1), "seed" to 30, "den" to 0, "sp" to .4),
    )

    private val XB: Map<String, Any?> = mapOf(
        "lead" to "ep", "lv" to .3, "pad" to .07, "bass" to "sub", "bv" to .22, "arp" to null, "sc" to "maj", "dr" to DR_CHILL,
        "sw" to .35, "dl" to .28, "rv" to .4, "sp" to .45, "sus" to 1.5, "lp" to 4600, "lo" to 62, "hi" to 86,
    )

    private val XPD: Map<String, Map<String, Any?>> = mapOf(
        "clasica" to mapOf("bpm" to 84, "key" to 60, "prog" to c(0 to "M9", 9 to "m9", 5 to "M9", 7 to "d"), "prog2" to c(2 to "m9", 7 to "d", 4 to "m", 9 to "m9"), "seed" to 31, "den" to 1),
        "primavera" to mapOf("bpm" to 92, "key" to 62, "sc" to "lyd", "prog" to c(0 to "M9", 4 to "m", 5 to "M9", 7 to "M6"), "prog2" to c(9 to "m", 5 to "M9", 0 to "M9", 7 to "M6"), "seed" to 32, "den" to 1, "sp" to .6),
        "verano" to mapOf("bpm" to 100, "key" to 65, "dr" to DR_BOSSA, "prog" to c(0 to "M9", 5 to "M9", 2 to "m9", 7 to "d"), "prog2" to c(9 to "m9", 2 to "m9", 7 to "d", 0 to "M9"), "seed" to 33, "den" to 1, "sw" to .2),
        "otono" to mapOf("bpm" to 76, "key" to 57, "sc" to "dor", "prog" to c(0 to "m9", 5 to "M9", 3 to "M9", 7 to "d"), "prog2" to c(0 to "m9", 10 to "M9", 5 to "M9", 7 to "d"), "seed" to 34, "den" to 1, "dr" to DR_CHILLS),
        "invierno" to mapOf("bpm" to 66, "key" to 60, "prog" to c(0 to "M9", 4 to "m", 5 to "M9", 9 to "m9"), "prog2" to c(5 to "M9", 7 to "d", 9 to "m9", 0 to "M9"), "seed" to 35, "den" to 0, "dr" to null, "pad" to .1, "sus" to 2.4, "sp" to .6, "lead" to "bell"),
        "halloween" to mapOf("bpm" to 74, "key" to 57, "sc" to "har", "prog" to c(0 to "m", 8 to "M", 7 to "d", 0 to "m"), "prog2" to c(5 to "m", 3 to "M", 7 to "d", 0 to "m"), "seed" to 36, "den" to 0, "dr" to DR_HEART, "lead" to "vib", "lv" to .24, "pad" to .09, "sus" to 2.4, "sp" to .5),
        "navidad" to mapOf("bpm" to 88, "key" to 60, "prog" to c(0 to "M", 9 to "m", 2 to "m", 7 to "d"), "prog2" to c(5 to "M", 7 to "d", 4 to "m", 0 to "M"), "seed" to 37, "den" to 1, "dr" to DR_BELL, "lead" to "bell", "lv" to .32, "sp" to .5),
    )

    private fun merge(vararg maps: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (m in maps) out.putAll(m)
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun build(key: String, m: Map<String, Any?>): Piece {
        fun num(k: String): Double = (m[k] as Number).toDouble()
        fun int(k: String): Int = (m[k] as Number).toInt()
        val prog = m["prog"] as List<Chord>
        val bass = m["bass"] as String?
        return Piece(
            key = key,
            bpm = int("bpm"), root = int("key"), scale = m["sc"] as String,
            prog = prog, prog2 = m["prog2"] as List<Chord>?,
            lead = m["lead"] as String, leadVol = num("lv"), density = int("den"), sustain = num("sus"),
            pad = num("pad"), bass = bass, bassVol = num("bv"),
            bassPattern = listOf(BassNote(0, 0, 3), BassNote(3, 0, 2), BassNote(6, 7, 2)),
            arp = m["arp"] as String?, arpVol = num("av"), arpPattern = m["ap"] as List<Int>, arpOctave = int("ao"),
            drums = m["dr"] as Drums?, swing = num("sw"), delay = num("dl"), reverb = num("rv"), seed = int("seed"),
            low = int("lo"), high = int("hi"), sparkle = num("sp"), lowpass = int("lp"),
        )
    }

    private val PX: Map<String, Pair<Piece, Piece>> = PXD.keys.associateWith { k ->
        val day = build("px-$k-day", merge(BASE, PXD.getValue(k)))
        val night = build("px-$k-night", merge(BASE, PXD.getValue(k), PXN.getValue(k)))
        day to night
    }

    private val XP: Map<String, Pair<Piece, Piece>> = XPD.keys.associateWith { k ->
        val dayMap = merge(BASE, XB, XPD.getValue(k))
        val day = build("xp-$k-day", dayMap)
        val dr = dayMap["dr"] as Drums?
        val nightDr = when {
            dr != null && (dr === DR_CHILL || dr === DR_BOSSA) -> DR_CHILLS
            dr === DR_BELL -> DR_BELLS
            else -> dr
        }
        val dayKey = (dayMap["key"] as Number).toInt()
        val nightMap = merge(
            BASE, XB, XPD.getValue(k),
            mapOf(
                "bpm" to ((dayMap["bpm"] as Number).toDouble() * .82).roundToInt(),
                "dr" to nightDr,
                "lv" to (dayMap["lv"] as Number).toDouble() * .9,
                "pad" to (dayMap["pad"] as Number).toDouble() * 1.25,
                "lp" to 3800,
                "den" to max(0, (dayMap["den"] as Number).toInt() - 1),
                "seed" to (dayMap["seed"] as Number).toInt() + 50,
                "key" to dayKey - (if (dayKey > 62) 5 else 0),
            ),
        )
        day to build("xp-$k-night", nightMap)
    }

    private fun pixelKey(season: Season): String = when (season) {
        Season.CLASICA -> "classic"
        Season.VALLE -> "valle"
        Season.PRIMAVERA -> "primavera"
        Season.VERANO -> "verano"
        Season.OTONO -> "otono"
        Season.INVIERNO -> "invierno"
        Season.HALLOWEEN -> "halloween"
        Season.NAVIDAD -> "navidad"
    }

    private fun xpKey(season: Season): String = when (season) {
        Season.CLASICA, Season.VALLE -> "clasica"
        else -> pixelKey(season)
    }

    /** The piece for the current world, season and day/night (the page picks it the same way). */
    fun pick(style: DesignStyle, season: Season, night: Boolean): Piece {
        val pair = if (style == DesignStyle.XP) XP.getValue(xpKey(season)) else PX.getValue(pixelKey(season))
        return if (night) pair.second else pair.first
    }

    /** Every piece, for tests. */
    fun all(): List<Piece> = PX.values.flatMap { listOf(it.first, it.second) } + XP.values.flatMap { listOf(it.first, it.second) }
}
