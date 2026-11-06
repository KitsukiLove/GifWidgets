package app.gifwidgets.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.gifwidgets.ui.theme.LocalGifDesign
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Drop-in replacements for the Material progress bars, with the same names and parameters the screens use (a screen
// only changes its import). Pixel look: framed bar made of steps, ring of squares. Colours come from the Material
// theme, so light / dark / both styles keep working. (Chips, check boxes, radio buttons, tabs and dialogs: Choices.kt.)

/** A progress bar made of 4 dp steps, with a frame. [progress] is read while drawing, so only the bar redraws. */
@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val frame = LocalGifDesign.current.outline
    Canvas(modifier.height(12.dp)) {
        drawBar(frame, trackColor)
        val step = 4.dp.toPx()
        val inner = size.width - 4.dp.toPx()
        val fraction = progress().coerceIn(0f, 1f)
        val w = ((inner * fraction) / step).toInt() * step
        if (w > 0f) drawRect(color, Offset(2.dp.toPx(), 2.dp.toPx()), Size(w, size.height - 4.dp.toPx()))
    }
}

/** The same bar when nobody knows how long it takes: a block runs back and forth. */
@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val frame = LocalGifDesign.current.outline
    val transition = rememberInfiniteTransition()
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
    )
    Canvas(modifier.height(12.dp)) {
        drawBar(frame, trackColor)
        val step = 4.dp.toPx()
        val inner = size.width - 4.dp.toPx()
        val block = (inner * .28f / step).toInt() * step
        val left = ((inner - block) * x / step).toInt() * step
        drawRect(color, Offset(2.dp.toPx() + left, 2.dp.toPx()), Size(block, size.height - 4.dp.toPx()))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBar(frame: Color, track: Color) {
    drawRect(frame, Offset.Zero, size)
    drawRect(track, Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()))
}

/** A ring of eight little squares; a bright one runs round with a fading tail. */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition()
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
    )
    Canvas(modifier.size(40.dp)) {
        val head = turn.toInt() % 8
        val s = 7.dp.toPx()
        val r = size.minDimension / 2f - s / 2f
        for (i in 0 until 8) {
            val angle = i * PI / 4 - PI / 2
            val cx = size.width / 2f + (r * cos(angle)).toFloat()
            val cy = size.height / 2f + (r * sin(angle)).toFloat()
            val behind = (head - i + 8) % 8
            val a = (1f - behind * .12f).coerceAtLeast(.18f)
            val x = (cx - s / 2f).roundToInt().toFloat()
            val y = (cy - s / 2f).roundToInt().toFloat()
            drawRect(color.copy(alpha = a), Offset(x, y), Size(s, s))
        }
    }
}
