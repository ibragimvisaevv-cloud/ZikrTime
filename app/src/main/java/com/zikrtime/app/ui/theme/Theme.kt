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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R

// Фирменные цвета: изумруд и золото тасбиха
val Emerald = Color(0xFF345B80)
val EmeraldDeep = Color(0xFF223E5B)
val EmeraldNight = Color(0xFF14283D)
val Gold = Color(0xFFD9B98C)
val GoldDeep = Color(0xFFAA8151)
val Cream = Color(0xFFF7F2EA)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9F6),
    onPrimaryContainer = Color(0xFF193550),
    secondary = Color(0xFF8A6414),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E7C1),
    onSecondaryContainer = Color(0xFF2B1E00),
    tertiary = Color(0xFF75658A),
    onTertiary = Color.White,
    background = Color(0xFFF6F3EE),
    onBackground = Color(0xFF192C41),
    surface = Color(0xFFF6F3EE),
    onSurface = Color(0xFF192C41),
    surfaceVariant = Color(0xFFE8E5E0),
    onSurfaceVariant = Color(0xFF566273),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFDF9),
    surfaceContainerHigh = Color(0xFFF1EEE8),
    surfaceContainerHighest = Color(0xFFE8E5E0),
    outline = Color(0xFF627582),
    outlineVariant = Color(0xFFDADDDC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAECDEC),
    onPrimary = Color(0xFF112D49),
    primaryContainer = Color(0xFF294664),
    onPrimaryContainer = Color(0xFFDCE9F6),
    secondary = Gold,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF4A3912),
    onSecondaryContainer = Color(0xFFF6E7C1),
    tertiary = Color(0xFFCCBCE2),
    onTertiary = Color(0xFF342640),
    background = Color(0xFF192C41),
    onBackground = Color(0xFFF2F5F3),
    surface = Color(0xFF192C41),
    onSurface = Color(0xFFF2F5F3),
    surfaceVariant = Color(0xFF24364B),
    onSurfaceVariant = Color(0xFFC0CBD0),
    surfaceContainerLowest = Color(0xFF101E2E),
    surfaceContainerLow = Color(0xFF1D2D40),
    surfaceContainer = Color(0xFF24364B),
    surfaceContainerHigh = Color(0xFF2B3E54),
    surfaceContainerHighest = Color(0xFF34495F),
    outline = Color(0xFF90A4B1),
    outlineVariant = Color(0xFF415666),
)

private val base = Typography()

// Заголовки плотнее и с чуть сжатым трекингом, основной текст — чуть крупнее и с воздухом между строк.
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.sp),
    bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 25.sp, letterSpacing = 0.2.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Шрифт для арабского текста (аяты, зикры) — Amiri, лицензия SIL OFL. */
val Amiri = FontFamily(Font(R.font.amiri))

val ArabicStyle = TextStyle(fontFamily = Amiri, fontSize = 30.sp, lineHeight = 52.sp)

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
                isAppearanceLightStatusBars = !dark
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
