package app.gifwidgets.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.gifwidgets.R

// Pixelify Sans (text) and Jersey 10 (headings): the fonts of the KixKi Store page, both under the SIL Open
// Font License. The files in res/font are trimmed to Latin characters (about 27 KB each); any other character
// falls back to the system font. See THIRD_PARTY.md.
val PixelifySans = FontFamily(
    Font(R.font.pixelify_sans_regular, FontWeight.Normal),
    Font(R.font.pixelify_sans_medium, FontWeight.Medium),
    Font(R.font.pixelify_sans_bold, FontWeight.Bold),
)

val Jersey10 = FontFamily(Font(R.font.jersey_10, FontWeight.Normal))

private val base = Typography()

// Sizes follow the page: body 16, window titles 20, headings 24, buttons in bold. They are in sp, so the system's
// font size setting still scales them.
val GifTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Jersey10),
    displayMedium = base.displayMedium.copy(fontFamily = Jersey10),
    displaySmall = base.displaySmall.copy(fontFamily = Jersey10),
    headlineLarge = base.headlineLarge.copy(fontFamily = Jersey10),
    headlineMedium = base.headlineMedium.copy(fontFamily = Jersey10),
    headlineSmall = base.headlineSmall.copy(fontFamily = Jersey10, fontSize = 28.sp, lineHeight = 32.sp),
    titleLarge = base.titleLarge.copy(fontFamily = Jersey10, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    titleMedium = base.titleMedium.copy(fontFamily = PixelifySans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontFamily = PixelifySans, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = PixelifySans, fontSize = 17.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = PixelifySans, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontFamily = PixelifySans, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(fontFamily = PixelifySans, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.sp),
    labelMedium = base.labelMedium.copy(fontFamily = PixelifySans, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.sp),
    labelSmall = base.labelSmall.copy(fontFamily = PixelifySans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.sp),
)
