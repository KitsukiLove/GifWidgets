package app.gifwidgets.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import app.gifwidgets.control.DesignStyle

/**
 * App-wide theme: the KixKi Store palette (light and dark), its pixel fonts and one of two window styles
 * (Pixel or Colina XP). No dynamic colour (Material You) any more: the look is the page's.
 *
 * NOT used by the widget itself — RemoteViews can't host Compose.
 */
@Composable
fun GifWidgetsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    style: DesignStyle = DesignStyle.PIXEL,
    content: @Composable () -> Unit,
) {
    val design = remember(style, darkTheme) { gifDesign(style, darkTheme) }
    val shapes = remember(style) { gifShapes(style) }
    CompositionLocalProvider(LocalGifDesign provides design) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = GifTypography,
            shapes = shapes,
            content = content,
        )
    }
}
