package com.zikrtime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R

// Emerald actions, warm stone surfaces and restrained brass accents.
val Emerald = Color(0xFF235D4C)
val EmeraldDeep = Color(0xFF173E34)
val EmeraldNight = Color(0xFF151C19)
val Gold = Color(0xFFD6BD8E)
val GoldDeep = Color(0xFF796039)
val Cream = Color(0xFFF8F5EF)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEBE3),
    onPrimaryContainer = Color(0xFF174536),
    secondary = GoldDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E5CF),
    onSecondaryContainer = Color(0xFF4C3A20),
    tertiary = Color(0xFF526875),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1EAF0),
    onTertiaryContainer = Color(0xFF233C49),
    background = Cream,
    onBackground = Color(0xFF242923),
    surface = Cream,
    onSurface = Color(0xFF242923),
    surfaceVariant = Color(0xFFECE9E1),
    onSurfaceVariant = Color(0xFF62675E),
    surfaceContainerLowest = Color(0xFFFFFEFA),
    surfaceContainerLow = Color(0xFFFFFEFA),
    surfaceContainer = Color(0xFFF3F0E9),
    surfaceContainerHigh = Color(0xFFECE9E1),
    surfaceContainerHighest = Color(0xFFE5E2D9),
    surfaceTint = Emerald,
    inverseSurface = Color(0xFF293128),
    inverseOnSurface = Color(0xFFF6F3EB),
    inversePrimary = Color(0xFFACD3BC),
    outline = Color(0xFF7B8075),
    outlineVariant = Color(0xFFDCDCD1),
    error = Color(0xFFA43831),
    onError = Color.White,
    errorContainer = Color(0xFFFBE5E0),
    onErrorContainer = Color(0xFF71251F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFACD3BC),
    onPrimary = Color(0xFF193E2D),
    primaryContainer = Color(0xFF2B483B),
    onPrimaryContainer = Color(0xFFD9ECE0),
    secondary = Gold,
    onSecondary = Color(0xFF3B2E18),
    secondaryContainer = Color(0xFF463E2E),
    onSecondaryContainer = Color(0xFFF0E2C6),
    tertiary = Color(0xFFB6CCD8),
    onTertiary = Color(0xFF253C49),
    tertiaryContainer = Color(0xFF344651),
    onTertiaryContainer = Color(0xFFDBEAF2),
    background = Color(0xFF151815),
    onBackground = Color(0xFFF1EFE7),
    surface = Color(0xFF151815),
    onSurface = Color(0xFFF1EFE7),
    surfaceVariant = Color(0xFF343832),
    onSurfaceVariant = Color(0xFFC0C4BA),
    surfaceContainerLowest = Color(0xFF111411),
    surfaceContainerLow = Color(0xFF1D211C),
    surfaceContainer = Color(0xFF252923),
    surfaceContainerHigh = Color(0xFF2D312A),
    surfaceContainerHighest = Color(0xFF363B32),
    surfaceTint = Color(0xFFACD3BC),
    inverseSurface = Color(0xFFF1EFE7),
    inverseOnSurface = Color(0xFF242923),
    inversePrimary = Emerald,
    outline = Color(0xFF92988B),
    outlineVariant = Color(0xFF42483D),
    error = Color(0xFFF0B6AC),
    onError = Color(0xFF5B201B),
    errorContainer = Color(0xFF50302A),
    onErrorContainer = Color(0xFFFFDAD2),
)

private val base = Typography()

// Заголовки плотнее и с чуть сжатым трекингом, основной текст — чуть крупнее и с воздухом между строк.
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.sp),
    bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Normal, lineHeight = 25.sp, letterSpacing = 0.1.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Шрифт для арабского текста (аяты, зикры) — Amiri, лицензия SIL OFL. */
val Amiri = FontFamily(Font(R.font.amiri))

// Amiri's stacked Quranic signs need more room than the Latin UI font.
// Relative leading also follows the reader's text-size control and system scale.
val ArabicStyle = TextStyle(
    fontFamily = Amiri,
    fontSize = 30.sp,
    lineHeight = 2.25.em,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/** Цифры одинаковой ширины — время не «прыгает». */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Тёмная ли сейчас тема — для мелких отличий в оформлении. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ZikrTheme(natural: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (com.zikrtime.app.data.Store.appearance) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val activity = view.context as? android.app.Activity
        if (activity != null) {
            androidx.core.view.WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark, com.zikrtime.app.ui.LocalNatural provides natural) {
        val baseColors = if (dark) DarkColors else LightColors
        val colors = baseColors
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes) {
            // Цвет текста по умолчанию — светлый в тёмной теме, тёмный в светлой (раньше его давал Surface).
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colors.onSurface,
                content = content,
            )
        }
    }
}
