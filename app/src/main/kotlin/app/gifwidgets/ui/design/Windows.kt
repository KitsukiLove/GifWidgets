package app.gifwidgets.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gifwidgets.R
import app.gifwidgets.audio.KixkiAudio
import app.gifwidgets.ui.theme.LocalGifDesign

/**
 * A screen shown as a "program window" like the pages of KixKi Store ("Ajustes.exe"): a gradient title bar with a
 * close button, and the content below on the lavender background. The title bar also paints the area behind the
 * status bar, and the content keeps clear of the navigation bar.
 */
@Composable
fun ScreenWindow(
    title: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val design = LocalGifDesign.current
    Box(modifier = Modifier.fillMaxSize()) {
        SceneBackdrop()
        Column(modifier = Modifier.fillMaxSize()) {
            WindowTitleBar(title = title, onClose = onClose, actions = actions)
            Box(Modifier.fillMaxWidth().height(design.borderWidth).background(design.outline))
            Box(modifier = Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
                content()
            }
        }
    }
}

@Composable
fun WindowTitleBar(
    title: String,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val design = LocalGifDesign.current
    val bar = Brush.horizontalGradient(listOf(design.titleStart, design.titleEnd))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bar)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 48.dp)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = design.titleInk,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
        if (onClose != null) WindowCloseButton(onClose)
    }
}

/** The window's close button. [description] is what a screen reader says: "Cerrar", or "Borrar" on a GIF card. */
@Composable
internal fun WindowCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = stringResource(R.string.win_close),
    touch: Dp = 48.dp,
    faceSize: Dp = 28.dp,
) {
    val design = LocalGifDesign.current
    // Pixel: a white square with a dark X. XP: a small red rounded square with a white X.
    val shape = if (design.isPixel) RectangleShape else RoundedCornerShape(6.dp)
    val face = if (design.isPixel) Color(0xFFFFFAF0) else Color(0xFFE8574D)
    val ink = if (design.isPixel) Color(0xFF2A1650) else Color.White
    Box(
        modifier = modifier
            .size(touch)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button) {
                KixkiAudio.blip(440.0, .06)
                onClose()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(faceSize)
                .clip(shape)
                .background(face, shape)
                .border(2.dp, design.titleInk.copy(alpha = 0.9f), shape),
            contentAlignment = Alignment.Center,
        ) {
            PixelIcon(icon = PixelIcons.CLOSE, outline = ink, main = ink, second = ink, size = 18.dp)
        }
    }
}
