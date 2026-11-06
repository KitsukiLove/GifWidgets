package app.gifwidgets.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gifwidgets.ui.theme.LocalGifDesign

/** One button of the bottom bar: a pixel icon (with its two colours) and a short label. */
@Immutable
class BarItem(
    val icon: List<String>,
    val label: String,
    val selected: Boolean,
    val mainColor: Color,
    val secondColor: Color,
    val onClick: () -> Unit,
)

/**
 * The bottom navigation of the KixKi Store mobile page (Inicio / Buscar / Favoritos / Tema / Más), here with the
 * app's own actions. Flat with a thick top line in the Pixel style, a glossy purple "taskbar" in Colina XP.
 */
@Composable
fun GifBottomBar(items: List<BarItem>, modifier: Modifier = Modifier) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val background = if (design.isPixel) {
        Modifier.background(scheme.surface)
    } else {
        Modifier.background(Brush.verticalGradient(listOf(design.titleEnd, design.titleStart)))
    }
    Column(modifier = modifier.fillMaxWidth().then(background)) {
        Box(Modifier.fillMaxWidth().height(design.borderWidth).background(if (design.isPixel) design.outline else design.titleInk.copy(alpha = 0.35f)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            items.forEach { item -> BarButton(item, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BarButton(item: BarItem, modifier: Modifier) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val labelColor = if (design.isPixel) scheme.onSurface else design.titleInk
    val iconOutline = if (design.isPixel) scheme.onSurface else design.titleInk
    val highlight = when {
        !item.selected -> Color.Transparent
        design.isPixel -> scheme.primaryContainer
        else -> Color.White.copy(alpha = 0.22f)
    }
    val shape = if (design.isPixel) design.buttonShape else RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(highlight, shape)
            .clickable(role = Role.Button, onClick = item.onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelIcon(
            icon = item.icon,
            outline = iconOutline,
            main = item.mainColor,
            second = item.secondColor,
            size = 24.dp,
        )
        Text(
            text = item.label,
            color = labelColor,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
