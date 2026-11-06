package app.gifwidgets.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gifwidgets.audio.KixkiAudio
import app.gifwidgets.ui.theme.LocalGifDesign
import app.gifwidgets.ui.theme.PixelShape
import kotlin.math.roundToInt

// Kixki is a small, quiet guide now: the sprite appears in the welcome window and in the "Consejo" strip. It does
// not hop, bleat or react to taps. The big banner of the first design (HeroScene) was removed.

/** Kixki the sheep, standing still. One sprite pixel is [cell] wide (the sprite is 20 x 15 pixels). */
@Composable
fun KixkiMini(cell: Dp, modifier: Modifier = Modifier) {
    val dark = LocalGifDesign.current.dark
    Canvas(modifier.size(cell * SheepSprite.WIDTH, cell * SheepSprite.HEIGHT)) {
        val c = cell.toPx().roundToInt().coerceAtLeast(1).toFloat()
        for ((color, rects) in SheepSprite.parts(dark)) {
            val tint = Color(color)
            var i = 0
            while (i + 3 < rects.size) {
                drawRect(
                    tint,
                    Offset(rects[i] * c, rects[i + 1] * c),
                    Size(rects[i + 2] * c, rects[i + 3] * c),
                )
                i += 4
            }
        }
    }
}

/** A 44 dp square button of the top bar (theme, music, season), like the page's `.tg`. */
@Composable
fun ToolButton(icon: List<String>, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = if (design.isPixel) PixelShape(3.dp) else RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = description }
            .hardShadow(design.shadow, 3.dp, shape)
            .clip(shape)
            .background(scheme.surface, shape)
            .border(3.dp, design.outline, shape)
            .clickable(role = Role.Button) {
                KixkiAudio.blip(880.0, .08)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(icon = icon, outline = design.ink, main = design.accent, second = design.pink, size = 26.dp)
    }
}
