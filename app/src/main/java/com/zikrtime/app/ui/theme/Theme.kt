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

// Living Horizon: deep emerald, sage glass and warm ivory. Legacy names keep callers consistent.
val Emerald = Color(0xFF124C40)
val EmeraldDeep = Color(0xFF0A372F)
val EmeraldNight = Color(0xFF071F1B)
val Gold = Color(0xFFD9BE87)
val GoldDeep = Color(0xFF856838)
val Cream = Color(0xFFFAF7EE)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E8DB),
    onPrimaryContainer = Color(0xFF11372C),
    secondary = Color(0xFF80623C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFE4CB),
    onSecondaryContainer = Color(0xFF382D1A),
    tertiary = Color(0xFF58776A),
    onTertiary = Color.White,
    background = Color(0xFFF5F5ED),
    onBackground = Color(0xFF102D26),
    surface = Color(0xFFF5F5ED),
    onSurface = Color(0xFF102D26),
    surfaceVariant = Color(0xFFE1E9DF),
    onSurfaceVariant = Color(0xFF4C6258),
    surfaceContainerLowest = Color(0xFFFCFCF5),
    surfaceContainerLow = Color(0xFFFCFCF5),
    surfaceContainer = Color(0xFFFCFCF5),
    surfaceContainerHigh = Color(0xFFEBF0E6),
    surfaceContainerHighest = Color(0xFFE1E9DF),
    outline = Color(0xFF71867B),
    outlineVariant = Color(0xFFCCD9CE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9D5B9),
    onPrimary = Color(0xFF0B3027),
    primaryContainer = Color(0xFF1D5143),
    onPrimaryContainer = Color(0xFFD8E8DB),
    secondary = Gold,
    onSecondary = Color(0xFF302716),
    secondaryContainer = Color(0xFF50462F),
    onSecondaryContainer = Color(0xFFEFE4CB),
    tertiary = Color(0xFFBAD5C5),
    onTertiary = Color(0xFF1E352A),
    background = Color(0xFF091F1B),
    onBackground = Color(0xFFF0F4E9),
    surface = Color(0xFF091F1B),
    onSurface = Color(0xFFF0F4E9),
    surfaceVariant = Color(0xFF17332B),
    onSurfaceVariant = Color(0xFFB8CBBE),
    surfaceContainerLowest = Color(0xFF071A16),
    surfaceContainerLow = Color(0xFF102A23),
    surfaceContainer = Color(0xFF17332B),
    surfaceContainerHigh = Color(0xFF234136),
    surfaceContainerHighest = Color(0xFF2E4C40),
    outline = Color(0xFF90AA98),
    outlineVariant = Color(0xFF355346),
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
