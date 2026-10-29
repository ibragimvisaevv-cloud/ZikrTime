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

// Warm Architecture: matte olive, terracotta and illuminated plaster. Legacy names keep callers consistent.
val Emerald = Color(0xFF3D5037)
val EmeraldDeep = Color(0xFF2C3D29)
val EmeraldNight = Color(0xFF1E2B1E)
val Gold = Color(0xFFD9B77B)
val GoldDeep = Color(0xFF956B3E)
val Cream = Color(0xFFFFF3DF)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3CF),
    onPrimaryContainer = Color(0xFF273620),
    secondary = Color(0xFFA54E35),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6D7C6),
    onSecondaryContainer = Color(0xFF402014),
    tertiary = Color(0xFF82613E),
    onTertiary = Color.White,
    background = Color(0xFFF9F0E0),
    onBackground = Color(0xFF30271F),
    surface = Color(0xFFF9F0E0),
    onSurface = Color(0xFF30271F),
    surfaceVariant = Color(0xFFEBDFCD),
    onSurfaceVariant = Color(0xFF5F503F),
    surfaceContainerLowest = Color(0xFFFFF8EE),
    surfaceContainerLow = Color(0xFFFFF8EE),
    surfaceContainer = Color(0xFFFFF8EE),
    surfaceContainerHigh = Color(0xFFF0E5D3),
    surfaceContainerHighest = Color(0xFFEBDFCD),
    outline = Color(0xFF897768),
    outlineVariant = Color(0xFFDDCFB9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCCBA5),
    onPrimary = Color(0xFF23301E),
    primaryContainer = Color(0xFF3D4C35),
    onPrimaryContainer = Color(0xFFDCE3CF),
    secondary = Gold,
    onSecondary = Color(0xFF382818),
    secondaryContainer = Color(0xFF633C2C),
    onSecondaryContainer = Color(0xFFF6D7C6),
    tertiary = Color(0xFFDDC39B),
    onTertiary = Color(0xFF382A18),
    background = Color(0xFF27221C),
    onBackground = Color(0xFFF6ECD9),
    surface = Color(0xFF27221C),
    onSurface = Color(0xFFF6ECD9),
    surfaceVariant = Color(0xFF342D25),
    onSurfaceVariant = Color(0xFFD2C2AD),
    surfaceContainerLowest = Color(0xFF211D18),
    surfaceContainerLow = Color(0xFF2B251F),
    surfaceContainer = Color(0xFF342D25),
    surfaceContainerHigh = Color(0xFF40372D),
    surfaceContainerHighest = Color(0xFF4B4135),
    outline = Color(0xFFA99580),
    outlineVariant = Color(0xFF5A4B3D),
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
