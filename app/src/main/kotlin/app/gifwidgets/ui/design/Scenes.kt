package app.gifwidgets.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import app.gifwidgets.control.Season
import app.gifwidgets.ui.theme.LocalGifDesign
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The living background of the KixKi Store page, ported to Canvas: a sky, a hill landscape per season (Valle,
// Primavera, Verano, Otoño, Invierno, Halloween, Navidad), in the PIXEL world (stepped blocks) and in the
// COLINA XP world (smooth hills). It moves a little (clouds, twinkling stars, falling snow/leaves/petals, birds,
// butterflies, fireflies, bats, shooting stars) and every motion is a pure function of the clock, so there is no
// state to keep. The clock only ticks while the screen is on, at ~8 frames per second, and not at all with
// battery saver or when the user turned animated backgrounds off (then the scene is simply still).

/** Which season the app is dressed in right now (the theme provides it). */
val LocalSeason = staticCompositionLocalOf { Season.CLASICA }

/** Milliseconds for the scenery. Reading it inside a draw block redraws only that Canvas. */
val LocalSceneClock = staticCompositionLocalOf<State<Long>> { StillClock }

const val STILL_TIME_MS = 41_000L

private object StillClock : State<Long> {
    override val value: Long = STILL_TIME_MS
}

internal class World(val season: Season, val xp: Boolean, val dark: Boolean) {
    /** Halloween is always a night scene for stars and moon, like the page. */
    val starsOn: Boolean get() = dark || season == Season.HALLOWEEN
}

/** Full-screen background: sky, hills at the bottom, little animals. Put it behind the screen's content. */
@Composable
fun SceneBackdrop(modifier: Modifier = Modifier) {
    val design = LocalGifDesign.current
    val season = LocalSeason.current
    val clock = LocalSceneClock.current
    val base = MaterialTheme.colorScheme.background
    val world = World(season, !design.isPixel, design.dark)
    // graphicsLayer: the redraw of this Canvas does not re-record the screen above it.
    Canvas(modifier.fillMaxSize().graphicsLayer { }) {
        drawRect(base)
        drawWorld(world, clock.value, size.width, size.height)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Entry point shared by the backdrop and the hero banner

internal fun DrawScope.drawWorld(w: World, t: Long, width: Float, height: Float) {
    if (w.xp) {
        drawXpWorld(w, t, width, height)
    } else if (w.season == Season.CLASICA) {
        drawClassicBackdrop(w, t, width, height)
    } else {
        drawPixelWorld(w, t, width, height)
    }
    drawParticles(w, t, width, height)
    drawCritters(w, t, width, height)
}

private fun c(argb: Int) = Color(argb)

private fun lerp(a: Color, b: Color, f: Float) = Color(
    a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f,
)

/** The page's sky: s1, s2 at 35 %, s3 at 65 %, s4. */
private fun skyAt(p: ScenePalette, f: Float): Color {
    val s1 = c(p.sky1); val s2 = c(p.sky2); val s3 = c(p.sky3); val s4 = c(p.sky4)
    return when {
        f < .35f -> lerp(s1, s2, f / .35f)
        f < .65f -> lerp(s2, s3, (f - .35f) / .30f)
        else -> lerp(s3, s4, min(1f, (f - .65f) / .35f))
    }
}

private fun hash01(a: Int, b: Int, c: Int): Float {
    var h = a * 374761393 + b * 668265263 + c * 2147483647
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216f
}

// ---------------------------------------------------------------------------------------------------------------
// PIXEL world

/** Draws rectangles on a 64-column grid with rounded edges, so neighbouring blocks never leave seams. */
private class Grid(val d: DrawScope, val u: Float, val ox: Float, val oy: Float) {
    fun r(x: Float, y: Float, w: Float, h: Float, color: Color) {
        val l = (ox + x * u).roundToInt()
        val t = (oy + y * u).roundToInt()
        val rr = (ox + (x + w) * u).roundToInt()
        val b = (oy + (y + h) * u).roundToInt()
        if (rr > l && b > t) d.drawRect(color, Offset(l.toFloat(), t.toFloat()), Size((rr - l).toFloat(), (b - t).toFloat()))
    }

    fun r(x: Int, y: Int, w: Int, h: Int, color: Color) = r(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), color)

    fun hill(y: Float, a: Float, p: Float, ph: Float, color: Color) {
        for (x in 0 until 64) r(x, (y + a * sin(x / p + ph)).roundToInt(), 1, 40, color)
    }

    fun disc(cx: Int, cy: Int, rad: Int, color: Color) {
        for (y in -rad..rad) {
            val w = floor(sqrt((rad * rad - y * y).toDouble())).toInt()
            r(cx - w, cy + y, 2 * w + 1, 1, color)
        }
    }
}

private val STARS = arrayOf(
    intArrayOf(4, 3), intArrayOf(12, 8), intArrayOf(20, 2), intArrayOf(27, 10), intArrayOf(35, 4), intArrayOf(44, 9),
    intArrayOf(52, 3), intArrayOf(58, 11), intArrayOf(8, 14), intArrayOf(40, 15), intArrayOf(61, 6), intArrayOf(31, 16),
)
private val TWINKLE_PERIOD = floatArrayOf(2.4f, 3.1f, 1.9f)
private val TWINKLE_DELAY = floatArrayOf(0f, .8f, 1.3f)

private fun DrawScope.drawPixelWorld(w: World, t: Long, width: Float, height: Float) {
    val pal = ScenePalettes.of(w.season, xp = false, night = w.dark)
    // sky in stepped bands
    val bands = 18
    for (i in 0 until bands) {
        val top = (height * i / bands).roundToInt()
        val bottom = (height * (i + 1) / bands).roundToInt()
        drawRect(skyAt(pal, (i + .5f) / bands), Offset(0f, top.toFloat()), Size(width, (bottom - top + 1).toFloat()))
    }
    val u = width / 64f
    val top = Grid(this, u, 0f, 0f)
    val bot = Grid(this, u, 0f, height - 40f * u)
    val a = c(pal.a); val tr = c(pal.t); val o = c(pal.o); val p = c(pal.p); val k = c(pal.k)
    val g1 = c(pal.g1); val g2 = c(pal.g2); val g3 = c(pal.g3)
    val secs = t / 1000f

    // stars (night or Halloween): three groups that blink out of step
    if (w.starsOn) {
        STARS.forEachIndexed { j, s ->
            val g = j % 3
            val phase = (((secs - TWINKLE_DELAY[g]) / TWINKLE_PERIOD[g]) % 1f + 1f) % 1f
            top.r(s[0], s[1], 1, 1, a.copy(alpha = if (phase < .5f) 1f else .25f))
        }
    }
    fun cloud(x: Int, y: Int) {
        if (w.season == Season.HALLOWEEN || w.season == Season.NAVIDAD) return
        val dur = 200 + (x * 7 + y * 13) % 140
        val offset = (x * 29 + y * 17) % 200
        val phase = (((secs + offset) / dur) % 1f)
        val dx = floor(phase * 92f) - 14f
        val col = c(pal.c).copy(alpha = if (w.dark) .45f else 1f)
        val cx = x + dx
        top.r(cx, (y + 1).toFloat(), 7f, 2f, col)
        top.r(cx + 1, y.toFloat(), 4f, 1f, col)
        top.r(cx + 3, (y - 1).toFloat(), 2f, 1f, col)
    }
    fun bat(x: Int, y: Int) {
        top.r(x, y, 1, 1, k); top.r(x + 4, y, 1, 1, k); top.r(x + 2, y, 1, 1, k); top.r(x + 1, y + 1, 3, 1, k)
    }
    fun pine(x: Int, y: Int, h: Int) {
        bot.r(x, y + h, 1, 2, tr)
        for (j in 0 until h) {
            val kk = j / 2
            bot.r(x - kk, y + j, 1 + 2 * kk, 1, p)
        }
        bot.r(x, y + 3, 1, 1, o); bot.r(x - 1, y + 7, 1, 1, o); bot.r(x + 1, y + 6, 1, 1, o)
    }
    fun pump(x: Int, y: Int) {
        bot.r(x, y + 1, 5, 3, o); bot.r(x + 1, y, 3, 1, o); bot.r(x + 2, y - 1, 1, 1, tr)
        bot.r(x + 1, y + 2, 1, 1, k); bot.r(x + 3, y + 2, 1, 1, k)
    }
    fun dead(x: Int, y: Int) {
        bot.r(x, y, 2, 14, k); bot.r(x - 4, y + 3, 4, 1, k); bot.r(x - 4, y + 1, 1, 3, k)
        bot.r(x + 2, y + 5, 5, 1, k); bot.r(x + 6, y + 2, 1, 4, k)
    }
    fun tree(x: Int, y: Int, r: Int, col: Color) {
        bot.r(x, y + r, 1, 6, tr); bot.disc(x, y, r, col)
    }
    fun flower(x: Int, y: Int, col: Color) {
        bot.r(x, y, 1, 1, col); bot.r(x, y + 1, 1, 1, p)
    }
    fun sunflower(x: Int, y: Int) {
        bot.r(x - 1, y, 3, 3, o); bot.r(x, y + 1, 1, 1, tr); bot.r(x, y + 3, 1, 4, p)
    }
    when (w.season) {
        Season.VALLE, Season.CLASICA -> {
            top.disc(33, 7, 4, a); cloud(6, 5); cloud(20, 12); cloud(44, 3)
            bot.hill(20f, 2.5f, 7f, 0f, g1); bot.hill(26f, 2f, 5f, 2f, g2); bot.hill(32f, 1.5f, 9f, 4f, g3)
            for (s in arrayOf(intArrayOf(6, 34), intArrayOf(13, 36), intArrayOf(22, 35), intArrayOf(30, 37), intArrayOf(41, 34), intArrayOf(50, 36), intArrayOf(58, 35))) bot.r(s[0], s[1], 1, 1, o)
        }
        Season.HALLOWEEN -> {
            top.disc(18, 9, 6, a); bat(26, 5); bat(33, 12); bat(40, 6)
            bot.hill(24f, 2f, 8f, 1f, g1); bot.hill(29f, 1.5f, 6f, 3f, g2); bot.hill(34f, 1f, 10f, 0f, g3)
            dead(32, 12); pump(8, 31); pump(15, 33); pump(24, 34); pump(38, 32)
        }
        Season.NAVIDAD -> {
            top.disc(18, 7, 3, a)
            bot.hill(26f, 2f, 9f, 0f, g1); pine(8, 18, 9); pine(17, 20, 8); pine(29, 15, 12); pine(39, 21, 7)
            bot.hill(31f, 1.5f, 6f, 2f, g2); bot.hill(35f, 1f, 8f, 4f, g3)
        }
        Season.PRIMAVERA -> {
            top.disc(50, 7, 4, a); cloud(8, 6); cloud(26, 12); cloud(36, 3)
            bot.hill(22f, 2f, 7f, 1f, g1); tree(14, 14, 4, o); tree(47, 16, 3, o)
            bot.hill(28f, 1.5f, 5f, 0f, g2); bot.hill(33f, 1.5f, 9f, 3f, g3)
            val fl = arrayOf(intArrayOf(6, 34, 0), intArrayOf(12, 31, 1), intArrayOf(20, 35, 0), intArrayOf(27, 33, 1), intArrayOf(36, 32, 0), intArrayOf(44, 34, 1), intArrayOf(52, 31, 0), intArrayOf(58, 35, 1))
            for (f in fl) flower(f[0], f[1], if (f[2] == 0) o else a)
        }
        Season.VERANO -> {
            top.disc(14, 8, 6, a); cloud(38, 5); cloud(50, 13)
            bot.hill(21f, 2f, 8f, 2f, g1); bot.hill(27f, 2f, 6f, 0f, g2); bot.hill(33f, 1.5f, 10f, 3f, g3)
            for (s in arrayOf(intArrayOf(7, 29), intArrayOf(19, 31), intArrayOf(33, 28), intArrayOf(47, 30), intArrayOf(58, 32))) sunflower(s[0], s[1])
        }
        Season.OTONO -> {
            top.disc(48, 8, 4, a); cloud(8, 6); cloud(26, 12)
            bot.hill(24f, 2f, 8f, 1f, g1); tree(12, 15, 5, p); tree(30, 18, 4, o); tree(52, 14, 5, p)
            bot.hill(30f, 1.5f, 6f, 3f, g2); bot.hill(35f, 1f, 8f, 0f, g3)
            for (s in arrayOf(intArrayOf(6, 36), intArrayOf(20, 34), intArrayOf(40, 36), intArrayOf(58, 34))) bot.r(s[0], s[1], 2, 1, o)
        }
        Season.INVIERNO -> {
            top.disc(48, 7, 3, a); cloud(10, 6); cloud(30, 12)
            bot.hill(24f, 2f, 8f, 1f, g1); dead(10, 16); dead(46, 18); pine(28, 19, 7)
            bot.hill(30f, 1.5f, 6f, 2f, g2); bot.hill(35f, 1f, 8f, 4f, g3)
        }
    }
}

/** The page's own default look (no season): lavender stripes, a faint grid, dithering and blinking dots. */
private fun DrawScope.drawClassicBackdrop(w: World, t: Long, width: Float, height: Float) {
    val bands = if (w.dark) {
        intArrayOf(0xFF0F0826.toInt(), 0xFF130A2E.toInt(), 0xFF170C37.toInt(), 0xFF1C0F41.toInt(), 0xFF21124C.toInt())
    } else {
        intArrayOf(0xFFE8DCFF.toInt(), 0xFFECDFFF.toInt(), 0xFFF1E4FF.toInt(), 0xFFF7E6FB.toInt(), 0xFFFDE9F0.toInt())
    }
    val dp = density
    val unit = (4 * dp).roundToInt().coerceAtLeast(1).toFloat()
    val bandH = height / 5f
    bands.forEachIndexed { i, col ->
        drawRect(c(col), Offset(0f, i * bandH), Size(width, bandH + 1f))
    }
    // dithering: a checker strip at the end of each stripe
    for (i in 1 until 5) {
        val y = (i * bandH / unit).roundToInt() * unit
        var x = 0f
        var flip = false
        while (x < width) {
            if (flip) drawRect(c(bands[i]), Offset(x, y - unit), Size(unit, unit)) else drawRect(c(bands[i - 1]), Offset(x, y), Size(unit, unit))
            x += unit
            flip = !flip
        }
    }
    // grid
    val gridColor = if (w.dark) Color(0x0FBEAAFF) else Color(0x125B3FC4)
    val step = 24 * dp
    var gx = 0f
    while (gx < width) { drawRect(gridColor, Offset(gx, 0f), Size(2 * dp, height)); gx += step }
    var gy = 0f
    while (gy < height) { drawRect(gridColor, Offset(0f, gy), Size(width, 2 * dp)); gy += step }
    // little blinking squares from a 96 px tile
    val blink = if (((t / 2500L) and 1L) == 0L) 1f else .4f
    val dot = (if (w.dark) Color(0x29BEAAFF) else Color(0x245B3FC4)).let { it.copy(alpha = it.alpha * blink) }
    val tile = 96 * dp
    val pts = arrayOf(intArrayOf(10, 14, 4), intArrayOf(58, 6, 4), intArrayOf(80, 52, 4), intArrayOf(30, 70, 4), intArrayOf(64, 84, 4), intArrayOf(44, 38, 2), intArrayOf(6, 50, 2))
    var ty = 0f
    while (ty < height) {
        var tx = 0f
        while (tx < width) {
            for (p in pts) drawRect(dot, Offset(tx + p[0] * dp, ty + p[1] * dp), Size(p[2] * dp, p[2] * dp))
            tx += tile
        }
        ty += tile
    }
}

// ---------------------------------------------------------------------------------------------------------------
// COLINA XP world

private val HL = arrayOf(
    floatArrayOf(0f, 24.5f, 9f, 21.5f, 20f, 20.4f, 32f, 21.6f, 44f, 22.8f, 54f, 23.4f, 64f, 21.4f),
    floatArrayOf(0f, 27f, 10f, 23f, 22f, 21.2f, 36f, 22.4f, 50f, 23.6f, 56f, 25.6f, 64f, 23.6f),
    floatArrayOf(0f, 29f, 7f, 20.5f, 18f, 14.8f, 30f, 15f, 42f, 15.2f, 53f, 20.6f, 64f, 25.4f),
    floatArrayOf(0f, 35f, 12f, 30.5f, 30f, 29.6f, 46f, 31.4f, 54f, 32.3f, 60f, 34f, 64f, 33.2f),
)

/** Height of the crest of hill layer [layer] at column [x] (bisection over the Bézier, like the page's `crest`). */
internal fun xpCrest(layer: Int, x: Float): Float {
    val l = HL[layer]
    var x0 = l[0]
    var y0 = l[1]
    val segments = (l.size - 2) / 6
    for (s in 0 until segments) {
        val i = 2 + s * 6
        val x1 = l[i]; val y1 = l[i + 1]; val x2 = l[i + 2]; val y2 = l[i + 3]; val x3 = l[i + 4]; val y3 = l[i + 5]
        if (x <= x3 || s == segments - 1) {
            var lo = 0.0
            var hi = 1.0
            repeat(18) {
                val tt = (lo + hi) / 2
                val u = 1 - tt
                val bx = u * u * u * x0 + 3 * u * u * tt * x1 + 3 * u * tt * tt * x2 + tt * tt * tt * x3
                if (bx < x) lo = tt else hi = tt
            }
            val tt = (lo + hi) / 2
            val u = 1 - tt
            return (u * u * u * y0 + 3 * u * u * tt * y1 + 3 * u * tt * tt * y2 + tt * tt * tt * y3).toFloat()
        }
        x0 = x3; y0 = y3
    }
    return l[1]
}

private fun hillPath(layer: Int): Path {
    val l = HL[layer]
    return Path().apply {
        moveTo(l[0], l[1])
        var i = 2
        while (i + 5 < l.size) {
            cubicTo(l[i], l[i + 1], l[i + 2], l[i + 3], l[i + 4], l[i + 5])
            i += 6
        }
        lineTo(64f, 40f)
        lineTo(0f, 40f)
        close()
    }
}

private val HILL_PATHS = Array(4) { hillPath(it) }

private class Glow(val period: Float, val phase: Float, val low: Float, val draw: DrawScope.(Float) -> Unit)

private fun DrawScope.drawXpWorld(w: World, t: Long, width: Float, height: Float) {
    val pal = ScenePalettes.of(w.season, xp = true, night = w.dark)
    val secs = t / 1000f
    // sky
    drawRect(
        Brush.verticalGradient(
            0f to c(pal.sky1), .35f to c(pal.sky2), .65f to c(pal.sky3), 1f to c(pal.sky4),
            startY = 0f, endY = height,
        ),
        size = Size(width, height),
    )
    val u = width / 64f
    val so = w.starsOn
    val a = c(pal.a)
    val season = if (w.season == Season.VALLE) Season.CLASICA else w.season

    // everything below is drawn in the page's 64 x (24 | 40) unit space
    withTransform({
        scale(u, u, Offset.Zero)
    }) {
        // stars
        if (so) {
            var seed = 7
            for (j in 0 until 52) {
                seed = (seed * 16807L % 2147483647L).toInt()
                val rx = seed / 2147483647f
                seed = (seed * 16807L % 2147483647L).toInt()
                val ry = seed / 2147483647f
                seed = (seed * 16807L % 2147483647L).toInt()
                val rr = seed / 2147483647f
                val tw = if ((secs / (2f + (j % 3)) + j * .37f) % 1f < .5f) 1f else .45f
                drawCircle(a.copy(alpha = tw), .06f + rr * .12f, Offset(rx * 64f, ry * 17f))
            }
        }
        // sun, moon and glow, per season
        fun sunAt(x: Float, y: Float, r: Float) {
            drawCircle(Brush.radialGradient(0f to a.copy(alpha = .95f), .3f to a.copy(alpha = .5f), 1f to a.copy(alpha = 0f), center = Offset(x, y), radius = r * 2.6f), r * 2.6f, Offset(x, y), alpha = if (w.dark) .6f else 1f)
            if (!so) drawCircle(a, r, Offset(x, y)) else drawCircle(a, r * .8f, Offset(x, y))
        }
        when (season) {
            Season.CLASICA -> if (so) drawCircle(a, 1.9f, Offset(44f, 4.6f))
            Season.PRIMAVERA -> sunAt(48f, 5.4f, 1.6f)
            Season.VERANO -> sunAt(50f, 6f, 2.3f)
            Season.OTONO -> sunAt(10f, 9f, 2.6f)
            Season.INVIERNO -> sunAt(14f, 6.6f, 1.5f)
            Season.HALLOWEEN -> {
                drawCircle(Brush.radialGradient(0f to a.copy(alpha = .95f), .3f to a.copy(alpha = .5f), 1f to a.copy(alpha = 0f), center = Offset(46f, 7.2f), radius = 8.5f * 2.6f), 8.5f, Offset(46f, 7.2f), alpha = .6f)
                drawCircle(a, 3.4f, Offset(46f, 7.2f))
                val crater = Color(0xFFE08A2A)
                drawCircle(crater.copy(alpha = .35f), .7f, Offset(44.7f, 6.2f))
                drawCircle(crater.copy(alpha = .3f), 1f, Offset(47.4f, 8.2f))
                drawCircle(crater.copy(alpha = .3f), .45f, Offset(46.6f, 5.4f))
            }
            Season.NAVIDAD -> {
                drawCircle(Brush.radialGradient(0f to a.copy(alpha = .95f), .3f to a.copy(alpha = .5f), 1f to a.copy(alpha = 0f), center = Offset(46f, 6f), radius = 6f * 2.6f), 6f, Offset(46f, 6f), alpha = .7f)
                drawCircle(a, 1.9f, Offset(46f, 6f))
            }
            else -> {}
        }
        // soft clouds drifting right
        val cloudAlpha = when {
            season == Season.HALLOWEEN -> .6f
            w.dark -> .5f
            else -> 1f
        }
        val cc = c(pal.c)
        val clouds = arrayOf(floatArrayOf(8f, 6f, 1.1f, 360f), floatArrayOf(30f, 11f, .8f, 510f), floatArrayOf(52f, 5f, 1.3f, 420f), floatArrayOf(18f, 15f, .7f, 570f))
        for (cl in clouds) {
            val travel = 64f + 24f
            val x = ((cl[0] + secs / cl[3] * travel) % travel) - 12f
            xpCloud(x, cl[1], cl[2], cc, cloudAlpha)
        }
    }
    // hills + decor
    withTransform({
        translate(0f, height - 40f * u)
        scale(u, u, Offset.Zero)
    }) {
        val t0 = c(pal.t); val o0 = c(pal.o)
        drawPath(HILL_PATHS[0], t0)
        val decor = ArrayList<Glow>()
        val back = Decor(this, season, w.dark, decor)
        back.back()
        drawPath(HILL_PATHS[1], o0)
        drawPath(
            HILL_PATHS[2],
            Brush.linearGradient(
                0f to c(pal.g1), .5f to c(pal.g2), 1f to c(pal.g3),
                start = Offset(0f, 14f), end = Offset(26f, 40f),
            ),
        )
        drawPath(
            HILL_PATHS[2],
            Brush.linearGradient(
                0f to c(pal.p).copy(alpha = .7f), .55f to c(pal.p).copy(alpha = .12f), 1f to c(pal.p).copy(alpha = 0f),
                start = Offset(0f, 14f), end = Offset(35f, 36f),
            ),
        )
        back.body(secs)
        // glowing things (pumpkin faces, windows, lights) are not dimmed at night
        for (g in decor) {
            val ph = ((secs / g.period + g.phase) % 1f)
            val tri = if (ph < .5f) ph * 2 else (1 - ph) * 2
            g.draw(this, g.low + (1 - g.low) * tri)
        }
        drawPath(
            HILL_PATHS[3],
            Brush.verticalGradient(0f to c(pal.g2), 1f to c(pal.g3), startY = 28f, endY = 40f),
        )
    }
}

private fun DrawScope.xpCloud(x: Float, y: Float, k: Float, color: Color, alpha: Float) {
    val parts = arrayOf(
        floatArrayOf(-5f, 1.2f, 3f), floatArrayOf(-2f, -.6f, 4.2f), floatArrayOf(2.4f, .1f, 3.6f),
        floatArrayOf(5.8f, 1.4f, 2.8f), floatArrayOf(-3f, 1.9f, 3f), floatArrayOf(1.2f, 2f, 3.2f),
    )
    for (p in parts) {
        val cx = x + p[0] * k
        val cy = y + p[1] * k
        val r = p[2] * k
        drawCircle(
            Brush.radialGradient(0f to color.copy(alpha = alpha), .55f to color.copy(alpha = alpha * .9f), 1f to color.copy(alpha = 0f), center = Offset(cx, cy), radius = r),
            r, Offset(cx, cy),
        )
    }
}

/** The trees, pumpkins, snowman and so on of the Colina XP seasons (the page's tRound, tPine, tBare... ported). */
private class Decor(val d: DrawScope, val season: Season, val dark: Boolean, val glows: MutableList<Glow>) {
    private fun dim(col: Color): Color = if (dark) Color(col.red * .62f, col.green * .62f, col.blue * .62f, col.alpha) else col
    private fun ry(x: Float) = xpCrest(2, x)

    private fun circle(x: Float, y: Float, r: Float, col: Color, alpha: Float = 1f) = d.drawCircle(dim(col), r, Offset(x, y), alpha = alpha)
    private fun rect(x: Float, y: Float, w: Float, h: Float, col: Color) = d.drawRect(dim(col), Offset(x, y), Size(w, h))
    private fun oval(x: Float, y: Float, rx: Float, ry: Float, col: Color, alpha: Float = 1f) =
        d.drawOval(dim(col), Offset(x - rx, y - ry), Size(rx * 2, ry * 2), alpha = alpha)

    private fun poly(pts: Array<FloatArray>, col: Color, alpha: Float = 1f) {
        val p = Path()
        p.moveTo(pts[0][0], pts[0][1])
        for (i in 1 until pts.size) p.lineTo(pts[i][0], pts[i][1])
        p.close()
        d.drawPath(p, dim(col), alpha = alpha)
    }

    private fun line(path: Path, col: Color, w: Float) = d.drawPath(path, dim(col), style = Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round))

    private fun round(x: Float, s0: Float, a: Color, b: Color, sprinkle: Color?, y: Float = ry(x) + .25f) {
        val s = s0 * 1.45f
        rect(x - .06f * s, y - 1 * s, .12f * s, 1.05f * s, Color(0xFF5B3D22))
        circle(x - .5f * s, y - 1.25f * s, .62f * s, b)
        circle(x + .5f * s, y - 1.3f * s, .6f * s, b)
        circle(x, y - 1.75f * s, .8f * s, b)
        circle(x - .18f * s, y - 1.95f * s, .5f * s, a)
        circle(x + .32f * s, y - 1.62f * s, .4f * s, a, .9f)
        if (sprinkle != null) {
            val q = arrayOf(floatArrayOf(-.5f, -1.3f), floatArrayOf(.1f, -2f), floatArrayOf(.55f, -1.45f), floatArrayOf(-.2f, -1.55f), floatArrayOf(.3f, -1.8f), floatArrayOf(-.6f, -1.1f))
            for (e in q) circle(x + e[0] * s, y + e[1] * s, .1f * s, sprinkle)
        }
    }

    private fun pine(x: Float, s0: Float, snow: Boolean, lights: Boolean, y: Float = ry(x) + .25f) {
        val s = s0 * 1.4f
        rect(x - .07f * s, y - .25f * s, .14f * s, .35f * s, Color(0xFF4A3220))
        val wd = floatArrayOf(.92f, .74f, .54f)
        val col = arrayOf(Color(0xFF1D6538), Color(0xFF287A45), Color(0xFF349150))
        val lc = arrayOf(Color(0xFFFF4D4D), Color(0xFFFFD23A), Color(0xFF4DD0FF), Color(0xFFFF8FD0))
        for (j in 0 until 3) {
            val yb = y - .2f * s - j * .58f * s
            val wj = wd[j] * s
            val ya = yb - 1 * s
            poly(arrayOf(floatArrayOf(x - wj, yb), floatArrayOf(x, ya), floatArrayOf(x + wj, yb)), col[j])
            if (snow) {
                poly(
                    arrayOf(
                        floatArrayOf(x, ya), floatArrayOf(x - wj * .52f, ya + .52f * s), floatArrayOf(x - wj * .2f, ya + .4f * s),
                        floatArrayOf(x, ya + .55f * s), floatArrayOf(x + wj * .24f, ya + .4f * s), floatArrayOf(x + wj * .52f, ya + .52f * s),
                    ),
                    Color.White, .95f,
                )
            }
            if (lights) {
                for (q in 0 until 3) {
                    val f = (q + .6f) / 3.2f
                    val lx = x + (if (q % 2 == 1) 1 else -1) * wj * (.2f + f * .55f) * (if (q == 1) .6f else 1f)
                    val ly = ya + (yb - ya) * (.35f + f * .5f)
                    val color = lc[(j + q) % 4]
                    val r = .075f * s
                    glows.add(Glow(1.8f, (j * 3 + q) * .37f, .35f) { al -> drawCircle(color, r, Offset(lx, ly), alpha = al) })
                }
            }
        }
        if (lights) {
            val ty = y - .2f * s - 2 * .58f * s - 1 * s
            glows.add(Glow(1.8f, 0f, .35f) { al -> drawCircle(Color(0xFFFFE27A), .13f * s, Offset(x, ty), alpha = al) })
        }
    }

    private fun bare(x: Float, s0: Float, col: Color, snow: Boolean, y: Float = ry(x) + .25f) {
        val s = s0 * 1.35f
        val main = Path().apply { moveTo(x, y); cubicTo(x, y - .8f * s, x - .04f * s, y - 1.3f * s, x - .08f * s, y - 2.1f * s) }
        line(main, col, .17f * s)
        fun br(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float, width: Float = .075f * s, c2: Color = col) {
            line(Path().apply { moveTo(x + x1 * s, y + y1 * s); quadraticTo(x + cx * s, y + cy * s, x + x2 * s, y + y2 * s) }, c2, width)
        }
        br(0f, -1.1f, -.5f, -1.3f, -.95f, -1.95f)
        br(-.02f, -1.6f, .35f, -1.8f, .75f, -2.4f)
        br(.02f, -.95f, .5f, -1.05f, .9f, -1.55f)
        br(-.04f, -.8f, -.4f, -.85f, -.8f, -1.15f)
        br(-.2f, -1.8f, -.25f, -2.1f, -.2f, -2.4f)
        if (snow) {
            br(-.01f, -1.2f, -.4f, -1.38f, -.82f, -1.88f, .04f * s, Color.White)
            br(-.01f, -1.7f, .3f, -1.85f, .65f, -2.28f, .04f * s, Color.White)
            br(.03f, -1.0f, .45f, -1.1f, .8f, -1.5f, .04f * s, Color.White)
        }
    }

    private fun pumpkin(x: Float, y: Float, s: Float) {
        glows.add(
            Glow(2.6f, x * .13f, .55f) { al ->
                drawCircle(Color(0xFFFFB44A), 1.5f * s, Offset(x, y - .45f * s), alpha = .35f * al)
                val face = Color(0xFFFFE066)
                val p1 = Path().apply { moveTo(x - .5f * s, y - .62f * s); lineTo(x - .2f * s, y - .62f * s); lineTo(x - .35f * s, y - .9f * s); close() }
                val p2 = Path().apply { moveTo(x + .2f * s, y - .62f * s); lineTo(x + .5f * s, y - .62f * s); lineTo(x + .35f * s, y - .9f * s); close() }
                val mouth = Path().apply {
                    moveTo(x - .5f * s, y - .28f * s); quadraticTo(x, y + .02f * s, x + .5f * s, y - .28f * s)
                    lineTo(x + .3f * s, y - .1f * s); lineTo(x + .15f * s, y - .28f * s); lineTo(x, y - .08f * s)
                    lineTo(x - .15f * s, y - .28f * s); lineTo(x - .3f * s, y - .1f * s); close()
                }
                drawPath(p1, face, alpha = al); drawPath(p2, face, alpha = al); drawPath(mouth, face, alpha = al)
            },
        )
        oval(x, y - .45f * s, .9f * s, .68f * s, Color(0xFFD9631A))
        oval(x - .42f * s, y - .45f * s, .5f * s, .66f * s, Color(0xFFEE8226))
        oval(x + .42f * s, y - .45f * s, .5f * s, .66f * s, Color(0xFFEE8226))
        oval(x, y - .45f * s, .32f * s, .68f * s, Color(0xFFF59A3A))
        rect(x - .07f * s, y - 1.28f * s, .14f * s, .3f * s, Color(0xFF4B6A2A))
    }

    private fun snowman(x: Float, y: Float, s0: Float) {
        val s = s0 * 1.3f
        val ink = Color(0xFF222A44)
        oval(x, y + .02f * s, .8f * s, .12f * s, Color(0xFF9FB4D6), .35f)
        circle(x, y - .55f * s, .55f * s, Color.White)
        circle(x, y - 1.28f * s, .4f * s, Color.White)
        circle(x, y - 1.82f * s, .3f * s, Color.White)
        rect(x - .2f * s, y - 2.3f * s, .4f * s, .34f * s, ink)
        rect(x - .34f * s, y - 2.0f * s, .68f * s, .06f * s, ink)
        circle(x - .1f * s, y - 1.86f * s, .04f * s, ink)
        circle(x + .1f * s, y - 1.86f * s, .04f * s, ink)
        poly(arrayOf(floatArrayOf(x, y - 1.78f * s), floatArrayOf(x + .3f * s, y - 1.74f * s), floatArrayOf(x, y - 1.7f * s)), Color(0xFFFF8A1E))
        rect(x - .32f * s, y - 1.58f * s, .64f * s, .12f * s, Color(0xFF6B3FE0))
        rect(x + .12f * s, y - 1.52f * s, .1f * s, .34f * s, Color(0xFF6B3FE0))
        circle(x, y - 1.2f * s, .04f * s, ink); circle(x, y - .95f * s, .04f * s, ink); circle(x, y - .7f * s, .04f * s, ink)
        line(Path().apply { moveTo(x - .38f * s, y - 1.2f * s); lineTo(x - .85f * s, y - 1.55f * s) }, Color(0xFF5B3D22), .05f * s)
        line(Path().apply { moveTo(x + .38f * s, y - 1.2f * s); lineTo(x + .85f * s, y - 1.5f * s) }, Color(0xFF5B3D22), .05f * s)
    }

    private fun cabin(x: Float, s0: Float, y: Float = ry(x) + .3f) {
        val s = s0 * 1.3f
        glows.add(
            Glow(3.4f, 0f, .75f) { al ->
                drawCircle(Color(0xFFFFB44A), .7f * s, Offset(x - .5f * s, y - .6f * s), alpha = .4f * al)
                drawRect(Color(0xFFFFD36E), Offset(x - .72f * s, y - .78f * s), Size(.42f * s, .4f * s), alpha = al)
                drawRect(Color(0xFFFFD36E), Offset(x + .28f * s, y - .78f * s), Size(.42f * s, .4f * s), alpha = al)
            },
        )
        rect(x + .42f * s, y - 1.95f * s, .26f * s, .6f * s, Color(0xFF7A5A4A))
        rect(x - 1.1f * s, y - 1.1f * s, 2.2f * s, 1.15f * s, Color(0xFF8A5A32))
        rect(x - 1.1f * s, y - .75f * s, 2.2f * s, .05f * s, Color(0xFF6B4425))
        rect(x - 1.1f * s, y - .45f * s, 2.2f * s, .05f * s, Color(0xFF6B4425))
        poly(arrayOf(floatArrayOf(x - 1.3f * s, y - 1.08f * s), floatArrayOf(x, y - 2.0f * s), floatArrayOf(x + 1.3f * s, y - 1.08f * s)), Color(0xFF7A2A2A))
        poly(
            arrayOf(
                floatArrayOf(x - 1.3f * s, y - 1.08f * s), floatArrayOf(x, y - 2.0f * s), floatArrayOf(x + 1.3f * s, y - 1.08f * s),
                floatArrayOf(x + 1.18f * s, y - 1.08f * s), floatArrayOf(x, y - 1.84f * s), floatArrayOf(x - 1.18f * s, y - 1.08f * s),
            ),
            Color.White, .95f,
        )
        rect(x - .14f * s, y - .68f * s, .3f * s, .7f * s, Color(0xFF4A2F18))
    }

    private fun stone(x: Float, s0: Float, y: Float = ry(x) + .35f) {
        val s = s0 * 1.25f
        val p = Path().apply {
            moveTo(x - .34f * s, y); lineTo(x - .34f * s, y - .7f * s); quadraticTo(x - .34f * s, y - 1.05f * s, x, y - 1.05f * s)
            quadraticTo(x + .34f * s, y - 1.05f * s, x + .34f * s, y - .7f * s); lineTo(x + .34f * s, y); close()
        }
        d.drawPath(p, dim(Color(0xFF3B3350)))
        line(Path().apply { moveTo(x, y - .85f * s); lineTo(x, y - .5f * s); moveTo(x - .14f * s, y - .7f * s); lineTo(x + .14f * s, y - .7f * s) }, Color(0xFF6A5F86), .05f * s)
    }

    private fun fence(x0: Float, n: Int, y0: Float, col: Color) {
        for (i in 0 until n) {
            val x = x0 + i * .9f
            rect(x, y0 - .9f, .12f, .9f, col)
            poly(arrayOf(floatArrayOf(x, y0 - .9f), floatArrayOf(x + .06f, y0 - 1.03f), floatArrayOf(x + .12f, y0 - .9f)), col)
        }
        rect(x0 - .05f, y0 - .65f, n * .9f, .08f, col)
        rect(x0 - .05f, y0 - .3f, n * .9f, .08f, col)
    }

    /** Small trees on the far hill (behind the middle hill). */
    fun back() {
        when (season) {
            Season.PRIMAVERA -> {
                round(2.5f, .7f, Color(0xFFFFC9DE), Color(0xFFF59AC0), Color(0xFFFFF3F8), xpCrest(0, 2.5f) + .2f)
                round(60.5f, .8f, Color(0xFFFFC9DE), Color(0xFFF59AC0), Color(0xFFFFF3F8), xpCrest(0, 60.5f) + .2f)
            }
            Season.VERANO -> {
                round(3f, .7f, Color(0xFF5FB842), Color(0xFF3A9232), null, xpCrest(0, 3f) + .2f)
                round(61f, .8f, Color(0xFF5FB842), Color(0xFF3A9232), null, xpCrest(0, 61f) + .2f)
            }
            Season.OTONO -> {
                round(2.5f, .7f, Color(0xFFF2A23A), Color(0xFFD9531E), null, xpCrest(0, 2.5f) + .2f)
                round(61f, .8f, Color(0xFFF6C04A), Color(0xFFE2802A), null, xpCrest(0, 61f) + .2f)
            }
            Season.INVIERNO -> {
                pine(2.5f, .7f, true, false, xpCrest(0, 2.5f) + .2f)
                pine(61f, .75f, true, false, xpCrest(0, 61f) + .2f)
            }
            Season.HALLOWEEN -> bare(3f, .9f, Color(0xFF120A1E), false, xpCrest(0, 3f) + .2f)
            Season.NAVIDAD -> {
                pine(2.5f, .7f, true, true, xpCrest(0, 2.5f) + .2f)
                pine(61f, .75f, true, true, xpCrest(0, 61f) + .2f)
            }
            else -> {}
        }
    }

    /** Trees and things on the main hill. */
    fun body(secs: Float) {
        when (season) {
            Season.PRIMAVERA -> {
                round(9f, .95f, Color(0xFFFFC9DE), Color(0xFFF59AC0), Color(0xFFFFF3F8))
                round(14.5f, .7f, Color(0xFFFFDCEA), Color(0xFFF7A8C8), Color.White)
                round(47f, 1.05f, Color(0xFFFFC9DE), Color(0xFFF59AC0), Color(0xFFFFF3F8))
                round(53f, .75f, Color(0xFFFFDCEA), Color(0xFFF7A8C8), Color.White)
                round(41f, .6f, Color(0xFFC9F09A), Color(0xFF8FD56A), null)
            }
            Season.VERANO -> {
                round(10f, 1f, Color(0xFF6CC04A), Color(0xFF3C9A34), null)
                round(15f, .7f, Color(0xFF7CCF55), Color(0xFF46A63A), null)
                round(44f, 1.15f, Color(0xFF6CC04A), Color(0xFF3C9A34), null)
                round(52f, .8f, Color(0xFF7CCF55), Color(0xFF46A63A), null)
                round(57f, .6f, Color(0xFF6CC04A), Color(0xFF3C9A34), null)
            }
            Season.OTONO -> {
                round(10.5f, 1.05f, Color(0xFFF2A23A), Color(0xFFD9531E), null)
                round(16f, .75f, Color(0xFFF6C04A), Color(0xFFE2802A), null)
                round(43f, 1.1f, Color(0xFFF4A83A), Color(0xFFC9461A), null)
                round(50f, .8f, Color(0xFFF6C04A), Color(0xFFE2802A), null)
                round(55f, .65f, Color(0xFFF2A23A), Color(0xFFD9531E), null)
                bare(34f, .8f, Color(0xFF4A3022), false)
            }
            Season.INVIERNO -> {
                pine(10f, 1.15f, true, false); pine(14.5f, .8f, true, false); pine(46f, 1.15f, true, false); pine(52f, .85f, true, false)
                bare(38f, .85f, Color(0xFF4A3A34), true)
                snowman(50f, ry(50f) + (31f - ry(50f)) * .34f, 1.1f)
            }
            Season.HALLOWEEN -> {
                bare(12f, 1.7f, Color(0xFF120A1E), false)
                stone(39.5f, .8f); stone(43f, .95f); stone(47f, .75f)
                fence(52f, 5, ry(54f) + .5f, Color(0xFF1A1026))
                pumpkin(20f, ry(20f) + (30f - ry(20f)) * .34f, .95f)
                pumpkin(28.5f, ry(28f) + (30f - ry(28f)) * .42f, .8f)
                pumpkin(36f, ry(36f) + (30f - ry(36f)) * .5f, 1.4f)
                pumpkin(51f, ry(51f) + (31f - ry(51f)) * .5f, 1.25f)
                pumpkin(59f, ry(59f) + (31f - ry(59f)) * .5f, 1.0f)
            }
            Season.NAVIDAD -> {
                pine(10f, 1.25f, true, true); pine(15.5f, .9f, true, true); pine(43.5f, 1.25f, true, true)
                pine(50f, 1.0f, true, true); pine(56f, .7f, true, true)
                cabin(29.5f, .8f)
                snowman(23.5f, ry(23f) + (31f - ry(23f)) * .4f, .9f)
            }
            else -> {}
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Falling things (snow, leaves, petals)

private fun DrawScope.drawParticles(w: World, t: Long, width: Float, height: Float) {
    val count = when (w.season) {
        Season.NAVIDAD -> 26
        Season.INVIERNO -> 40
        Season.OTONO -> 16
        Season.PRIMAVERA -> 12
        else -> 0
    }
    if (count == 0) return
    val colored = w.season == Season.OTONO || w.season == Season.PRIMAVERA
    val color = if (colored) c(ScenePalettes.of(w.season, w.xp, w.dark).o) else Color.White
    val secs = t / 1000f
    val dp = density
    for (j in 0 until count) {
        val left = ((j * 37 + 9) % 100) / 100f
        val dur = (9 + (j * 5) % 8).toFloat()
        val delay = ((j * 3) % 9).toFloat()
        val z = ((if (colored) 3 else 2) + j % 3) * dp
        val phase = ((secs + delay) / dur) % 1f
        val x = left * width + 24f * dp * phase
        val y = -12f * dp + phase * (height * 1.1f + 12f * dp)
        if (w.xp) {
            drawCircle(color.copy(alpha = .85f), z * .7f, Offset(x, y))
        } else {
            val xi = (x / z).toInt() * z
            val yi = (y / z).toInt() * z
            drawRect(color.copy(alpha = .85f), Offset(xi, yi), Size(z, z))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Little visitors: birds, butterflies, fireflies, bats and shooting stars

private inline fun lanes(t: Long, periodMs: Long, lifeMs: Long, laneCount: Int, kind: Int, chance: Float, block: (progress: Float, h1: Float, h2: Float, h3: Float, lane: Int) -> Unit) {
    val k0 = t / periodMs
    val back = (lifeMs / periodMs).toInt() + 1
    for (lane in 0 until laneCount) {
        for (b in 0..back) {
            val k = (k0 - b).toInt()
            if (k < 0) continue
            if (hash01(kind, lane, k) > chance) continue
            val start = k.toLong() * periodMs + (lane * periodMs / laneCount) + (hash01(kind + 50, lane, k) * periodMs * .6f).toLong()
            val progress = (t - start).toFloat() / lifeMs
            if (progress < 0f || progress > 1f) continue
            block(progress, hash01(kind + 1, lane, k), hash01(kind + 2, lane, k), hash01(kind + 3, lane, k), lane)
        }
    }
}

private fun DrawScope.drawCritters(w: World, t: Long, width: Float, height: Float) {
    if (t == STILL_TIME_MS) return // still scene: nobody walks by
    val dp = density
    val night = w.dark
    val s = w.season
    val halloween = s == Season.HALLOWEEN
    val winter = s == Season.INVIERNO || s == Season.NAVIDAD
    if (halloween) {
        // bats
        val ink = Color(0xFF140A24)
        lanes(t, 10_000, 16_000, 2, 11, .6f) { p, h1, _, _, _ ->
            val x = -40f * dp + p * (width + 90f * dp)
            val y = height * (.06f + h1 * .3f) + sin(p * 40f) * 4f * dp
            val cell = 3.3f * dp
            val up = ((t / 450L) and 1L) == 0L
            val cells = if (up) BAT_UP else BAT_DOWN
            for (cl in cells) drawRect(ink, Offset(x + cl[0] * cell, y + cl[1] * cell), Size(cell, cell))
        }
    } else if (!night && !winter) {
        val ink = Color(0xFF2B1A5E)
        lanes(t, 12_000, 28_000, 3, 21, .5f) { p, h1, h2, _, _ ->
            val n = 1 + (h2 * 3).toInt().coerceAtMost(2)
            for (j in 0 until n) {
                val x = -40f * dp + p * (width + 130f * dp) - j * 34f * dp
                val y = height * (.06f + h1 * .26f) + j * 3.5f * dp + sin(p * 60f + j) * 3f * dp
                val up = (((t / 260L) + j) and 1L) == 0L
                if (w.xp) {
                    val sp = 6f * dp
                    val tip = if (up) -3f * dp else 3f * dp
                    drawLine(ink, Offset(x - sp, y + tip), Offset(x, y), strokeWidth = 1.6f * dp)
                    drawLine(ink, Offset(x, y), Offset(x + sp, y + tip), strokeWidth = 1.6f * dp)
                } else {
                    val cell = 5f * dp
                    for (cl in if (up) BIRD_UP else BIRD_DOWN) drawRect(ink, Offset(x + cl[0] * cell, y + cl[1] * cell), Size(cell, cell))
                }
            }
        }
        if (s == Season.PRIMAVERA || s == Season.VERANO) {
            lanes(t, 9_000, 20_000, 2, 31, .7f) { p, h1, h2, h3, _ ->
                val x0 = width * (.08f + h1 * .45f)
                val y0 = height * (.3f + h2 * .35f)
                val seg = (p * 5f).coerceAtMost(4.999f)
                val i = seg.toInt()
                val f = seg - i
                val dx = floatArrayOf(0f, 8f, 16f, 26f, 34f, 44f)
                val dy = floatArrayOf(0f, -30f, 10f, -24f, 6f, -18f)
                val x = x0 + (dx[i] + (dx[i + 1] - dx[i]) * f) * width / 100f
                val y = y0 + (dy[i] + (dy[i + 1] - dy[i]) * f) * dp
                val wing = if (h3 < .5f) Color(0xFFFF9AD5) else Color(0xFF9AD5FF)
                val cell = 3f * dp
                val flap = if (((t / 200L) and 1L) == 0L) 1f else .6f
                drawRect(wing, Offset(x, y), Size(2 * cell, 2 * cell * flap))
                drawRect(wing, Offset(x + 3 * cell, y), Size(2 * cell, 2 * cell * flap))
                drawRect(wing, Offset(x, y + 3 * cell), Size(cell, cell))
                drawRect(wing, Offset(x + 4 * cell, y + 3 * cell), Size(cell, cell))
                drawRect(Color(0xFF2B1A5E), Offset(x + 2 * cell, y + cell), Size(cell, 3 * cell))
            }
        }
    }
    if (night && !winter && !halloween) {
        lanes(t, 6_000, 10_000, 3, 41, .7f) { p, h1, h2, _, _ ->
            val seg = (p * 4f).coerceAtMost(3.999f)
            val i = seg.toInt()
            val f = seg - i
            val dx = floatArrayOf(0f, 18f, -12f, 20f, 0f)
            val dy = floatArrayOf(0f, -22f, -40f, -58f, -80f)
            val x = width * h1 + (dx[i] + (dx[i + 1] - dx[i]) * f) * dp
            val y = height * (.45f + h2 * .45f) + (dy[i] + (dy[i + 1] - dy[i]) * f) * dp
            val glow = if (((t / 800L) and 1L) == 0L) 1f else .3f
            val fly = Color(0xFFFFF8A8)
            drawCircle(fly.copy(alpha = .22f * glow), 8f * dp, Offset(x, y))
            drawRect(fly.copy(alpha = glow), Offset(x - 2f * dp, y - 2f * dp), Size(4f * dp, 4f * dp))
        }
    }
    if (night || halloween) {
        lanes(t, 8_000, 1_200, 1, 51, .4f) { p, h1, h2, _, _ ->
            val x = width * (.05f + h1 * .45f) + p * 300f * dp
            val y = height * (.03f + h2 * .22f) + p * 150f * dp
            val a = 1f - p
            drawRect(Color.White.copy(alpha = a), Offset(x, y), Size(4f * dp, 4f * dp))
            for (i in 1..4) drawRect(Color.White.copy(alpha = a * (.9f - i * .2f).coerceAtLeast(.1f)), Offset(x - 6f * dp * i, y - 3f * dp * i), Size(3.5f * dp, 3.5f * dp))
        }
    }
}

private val BIRD_UP = arrayOf(intArrayOf(0, 0), intArrayOf(2, 1), intArrayOf(3, 2), intArrayOf(4, 1), intArrayOf(6, 0))
private val BIRD_DOWN = arrayOf(intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(3, 0), intArrayOf(4, 1), intArrayOf(5, 2), intArrayOf(6, 1))
private val BAT_UP = arrayOf(intArrayOf(0, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(2, 1), intArrayOf(3, 1), intArrayOf(3, 2), intArrayOf(4, 0), intArrayOf(4, 1), intArrayOf(4, 2), intArrayOf(5, 1), intArrayOf(5, 2), intArrayOf(6, 1), intArrayOf(7, 1), intArrayOf(8, 0), intArrayOf(8, 1))
private val BAT_DOWN = arrayOf(intArrayOf(0, 2), intArrayOf(0, 3), intArrayOf(1, 2), intArrayOf(2, 2), intArrayOf(3, 1), intArrayOf(3, 2), intArrayOf(4, 0), intArrayOf(4, 1), intArrayOf(4, 2), intArrayOf(5, 1), intArrayOf(5, 2), intArrayOf(6, 2), intArrayOf(7, 2), intArrayOf(8, 2), intArrayOf(8, 3))
