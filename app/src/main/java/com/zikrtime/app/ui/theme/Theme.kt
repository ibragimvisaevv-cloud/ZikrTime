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

// Rhythm and pause: clear ink, neutral surfaces and a restrained warm secondary.
// Compatibility names remain for old consumers; visual values use the new system.
val Emerald = Color(0xFF3157C8)
val EmeraldDeep = Color(0xFF223E91)
val EmeraldNight = Color(0xFF14171D)
val Gold = Color(0xFFE7AB96)
val GoldDeep = Color(0xFFA4523B)
val Cream = Color(0xFFF5F6F8)

internal val LightColors = lightColorScheme(
    primary = Emerald, onPrimary = Color.White,
    primaryContainer = Color(0xFFE5EBFF), onPrimaryContainer = Color(0xFF233E91),
    secondary = GoldDeep, onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7E6DF), onSecondaryContainer = Color(0xFF743723),
    tertiary = Color(0xFF49636B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEBF0), onTertiaryContainer = Color(0xFF28464E),
    background = Cream, onBackground = Color(0xFF20242D),
    surface = Cream, onSurface = Color(0xFF20242D),
    surfaceVariant = Color(0xFFE9EBF0), onSurfaceVariant = Color(0xFF5C626F),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF1F5), surfaceContainerHigh = Color(0xFFE9EBF0),
    surfaceContainerHighest = Color(0xFFE1E4EB), surfaceTint = Emerald,
    inverseSurface = Color(0xFF252A34), inverseOnSurface = Color(0xFFF0F2F6),
    inversePrimary = Color(0xFFB5C5FF),
    outline = Color(0xFF777E8D), outlineVariant = Color(0xFFD8DCE5),
    error = Color(0xFFB0323A), onError = Color.White,
    errorContainer = Color(0xFFFBE4E5), onErrorContainer = Color(0xFF7A2028),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFB5C5FF), onPrimary = Color(0xFF182E71),
    primaryContainer = Color(0xFF293A65), onPrimaryContainer = Color(0xFFDFE6FF),
    secondary = Gold, onSecondary = Color(0xFF49271C),
    secondaryContainer = Color(0xFF50362E), onSecondaryContainer = Color(0xFFFFDDCE),
    tertiary = Color(0xFFB1CDD5), onTertiary = Color(0xFF1D3941),
    tertiaryContainer = Color(0xFF304B53), onTertiaryContainer = Color(0xFFD2ECF3),
    background = Color(0xFF14171D), onBackground = Color(0xFFF0F2F6),
    surface = Color(0xFF14171D), onSurface = Color(0xFFF0F2F6),
    surfaceVariant = Color(0xFF343A47), onSurfaceVariant = Color(0xFFB9C0CD),
    surfaceContainerLowest = Color(0xFF0F1217), surfaceContainerLow = Color(0xFF1C2028),
    surfaceContainer = Color(0xFF252A34), surfaceContainerHigh = Color(0xFF2D3340),
    surfaceContainerHighest = Color(0xFF373E4C), surfaceTint = Color(0xFFB5C5FF),
    inverseSurface = Color(0xFFF0F2F6), inverseOnSurface = Color(0xFF20242D),
    inversePrimary = Emerald, outline = Color(0xFF8B93A4), outlineVariant = Color(0xFF3C4454),
    error = Color(0xFFFFB2B7), onError = Color(0xFF5D1821),
    errorContainer = Color(0xFF592D34), onErrorContainer = Color(0xFFFFDADF),
)

private val base = Typography()
private val AppTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp),
    displayMedium = base.displayMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, letterSpacing = (-1.5).sp),
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-.7).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-.5).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-.3).sp),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = .1.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = .1.sp),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = .1.sp),
)
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
