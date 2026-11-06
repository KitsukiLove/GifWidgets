package app.gifwidgets.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gifwidgets.control.DesignStyle
import app.gifwidgets.control.Season
import app.gifwidgets.control.SeasonChoice
import app.gifwidgets.control.ThemeMode

/**
 * The "chrome" of the app on top of the Material colours: frame, hard shadow, title bars, button shape.
 * Two styles share one palette: PIXEL (flat notched windows with a hard shadow) and XP (rounded windows).
 * Colours come from the variables of the KixKi Store page.
 */
@Immutable
class GifDesign(
    val style: DesignStyle,
    val dark: Boolean,
    /** Frame colour (the page's --line). */
    val outline: Color,
    /** Hard drop shadow (the page's --sh). */
    val shadow: Color,
    val ink: Color,
    val accent: Color,
    val pink: Color,
    val titleStart: Color,
    val titleEnd: Color,
    val titleInk: Color,
    val borderWidth: Dp,
    val shadowOffset: Dp,
    val buttonShape: Shape,
    val cardShape: Shape,
) {
    val isPixel: Boolean get() = style == DesignStyle.PIXEL
}

fun gifDesign(style: DesignStyle, dark: Boolean): GifDesign {
    val pixel = style == DesignStyle.PIXEL
    return GifDesign(
        style = style,
        dark = dark,
        outline = if (dark) Color(0xFF6A52D8) else Color(0xFF3B2480),
        shadow = if (dark) Color(0xFF0C0620) else Color(0xFFBBA6F0),
        ink = if (dark) Color(0xFFF6F0FF) else Color(0xFF2A1650),
        accent = Color(0xFFFFD36E),
        pink = Color(0xFFFF8FC7),
        titleStart = if (dark) Color(0xFF2E1D73) else Color(0xFF3B2480),
        titleEnd = if (dark) Color(0xFF5B3FC0) else Color(0xFF7B52F0),
        titleInk = if (dark) Color(0xFFFFF7E6) else Color(0xFFFFFFFF),
        borderWidth = if (pixel) 4.dp else 3.dp,
        shadowOffset = if (pixel) 5.dp else 0.dp,
        buttonShape = if (pixel) PixelShape(3.dp) else RoundedCornerShape(8.dp),
        cardShape = if (pixel) PixelShape(4.dp) else RoundedCornerShape(10.dp),
    )
}

fun gifShapes(style: DesignStyle): Shapes =
    if (style == DesignStyle.PIXEL) {
        Shapes(
            extraSmall = PixelShape(2.dp),
            small = PixelShape(3.dp),
            medium = PixelShape(4.dp),
            large = PixelShape(4.dp),
            extraLarge = PixelShape(6.dp),
        )
    } else {
        Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(10.dp),
            large = RoundedCornerShape(12.dp),
            extraLarge = RoundedCornerShape(14.dp),
        )
    }

val LocalGifDesign = staticCompositionLocalOf { gifDesign(DesignStyle.PIXEL, false) }

/**
 * What the bottom bar, the hero buttons and Settings need to change the look at once (every change is also saved
 * in the settings). [reload] re-reads the settings file after a screen saved something by itself.
 */
@Immutable
class ThemeController(
    val mode: ThemeMode,
    val style: DesignStyle,
    val dark: Boolean,
    val setMode: (ThemeMode) -> Unit,
    val setStyle: (DesignStyle) -> Unit,
    /** What the user chose (Auto, Clásico, Valle...) and the season actually shown right now. */
    val seasonChoice: SeasonChoice = SeasonChoice.AUTO,
    val season: Season = Season.CLASICA,
    val setSeasonChoice: (SeasonChoice) -> Unit = {},
    val musicOn: Boolean = false,
    val setMusic: (Boolean) -> Unit = {},
    val soundOn: Boolean = true,
    val setSound: (Boolean) -> Unit = {},
    val animatedBackground: Boolean = true,
    /** True when the scenery really moves (the setting is on, battery saver is off, system animations are on). */
    val sceneMoves: Boolean = false,
    val setAnimatedBackground: (Boolean) -> Unit = {},
    /** Kixki's one-time welcome and the "Consejo" strip. The defaults show nothing when there is no controller. */
    val welcomeSeen: Boolean = true,
    val setWelcomeSeen: (Boolean) -> Unit = {},
    val tipsOn: Boolean = false,
    val setTips: (Boolean) -> Unit = {},
    val reload: () -> Unit = {},
)

val LocalThemeController = staticCompositionLocalOf<ThemeController?> { null }
