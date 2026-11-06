package app.gifwidgets.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gifwidgets.R
import app.gifwidgets.ui.theme.LocalGifDesign
import app.gifwidgets.ui.theme.PixelShape
import kotlin.math.roundToInt

// The logo of the KixKi Store page, drawn with pixels: "GIF" big, "WIDGETS" smaller beside it, each letter with a
// pink shadow and a lilac one behind it. When the scenery moves, a little wave runs through the letters.

private val GLYPHS: Map<Char, Array<String>> = mapOf(
    'G' to arrayOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."),
    'I' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"),
    'F' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
    'W' to arrayOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"),
    'D' to arrayOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
    'E' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
    'T' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
    'S' to arrayOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
)

private const val GLYPH_W = 5
private const val GLYPH_H = 7

private fun DrawScope.drawWord(word: String, x0: Float, y0: Float, cell: Float, first: Int, t: Long, color: Color, dx: Float, dy: Float) {
    val moving = t != STILL_TIME_MS
    val amp = cell
    word.forEachIndexed { k, ch ->
        val rows = GLYPHS[ch] ?: return@forEachIndexed
        val step = if (moving) ((t / 110L).toInt() - (first + k)).mod(16) else 99
        val lift = when (step) {
            0 -> -amp
            1 -> -amp / 2f
            else -> 0f
        }
        val left = x0 + k * (GLYPH_W + 1) * cell
        for (r in 0 until GLYPH_H) {
            var c = 0
            while (c < GLYPH_W) {
                if (rows[r][c] == '#') {
                    var run = 1
                    while (c + run < GLYPH_W && rows[r][c + run] == '#') run++
                    val l = (left + c * cell + dx).roundToInt().toFloat()
                    val tp = (y0 + r * cell + lift + dy).roundToInt().toFloat()
                    val rr = (left + (c + run) * cell + dx).roundToInt().toFloat()
                    val bb = (y0 + (r + 1) * cell + lift + dy).roundToInt().toFloat()
                    drawRect(color, Offset(l, tp), Size(rr - l, bb - tp))
                    c += run
                } else {
                    c++
                }
            }
        }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier, baseCell: Dp = 6.dp) {
    val design = LocalGifDesign.current
    // Grows a little with the system font size (so it keeps up with the text beside it), never more than 20 %.
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.2f)
    val big = baseCell * scale
    val clock = LocalSceneClock.current
    val name = stringResource(R.string.app_name)
    val small = big / 2
    val gap = big * 2
    val bigW = big * (3 * (GLYPH_W + 1) - 1)
    val smallW = small * (7 * (GLYPH_W + 1) - 1)
    val pad = 8.dp
    val w = bigW + gap + smallW + pad
    val h = big * GLYPH_H + pad + 4.dp
    val yellow = design.accent
    Canvas(
        modifier
            .size(w, h)
            .semantics { contentDescription = name },
    ) {
        val t = clock.value
        val bc = big.toPx()
        val sc = small.toPx()
        val xBig = 0f
        val xSmall = bigW.toPx() + gap.toPx()
        val ySmall = 4.dp.toPx() + (GLYPH_H * bc - GLYPH_H * sc)
        val yBig = 4.dp.toPx()
        val o1 = bc / 2f
        val o2 = bc
        // far shadow (lilac), then pink, then the letters
        drawWord("GIF", xBig, yBig, bc, 0, t, design.shadow, o2, o2)
        drawWord("WIDGETS", xSmall, ySmall, sc, 3, t, design.shadow, o2 * .66f, o2 * .66f)
        drawWord("GIF", xBig, yBig, bc, 0, t, design.pink, o1, o1)
        drawWord("WIDGETS", xSmall, ySmall, sc, 3, t, design.pink, o1 * .66f, o1 * .66f)
        drawWord("GIF", xBig, yBig, bc, 0, t, design.ink, 0f, 0f)
        drawWord("WIDGETS", xSmall, ySmall, sc, 3, t, design.ink, 0f, 0f)
        // little yellow sparks that blink
        val on = t == STILL_TIME_MS || (t / 600L) % 2L == 0L
        val spark = 3.dp.toPx()
        drawRect(yellow, Offset(-1f, 0f), Size(spark, spark))
        if (on) drawRect(yellow, Offset(xSmall + smallW.toPx() - spark, 0f), Size(spark, spark))
        if (!on || t == STILL_TIME_MS) drawRect(yellow, Offset(xBig + bigW.toPx() + 2.dp.toPx(), yBig - 2.dp.toPx()), Size(spark, spark))
    }
}

/** The logo and the small framed tagline under it. */
@Composable
fun LogoBlock(modifier: Modifier = Modifier) {
    val design = LocalGifDesign.current
    val shape = PixelShape(3.dp)
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Wordmark()
        Text(
            text = stringResource(R.string.main_tagline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .hardShadow(design.shadow, 3.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(2.dp, design.outline, shape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
