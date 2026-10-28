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
val Emerald = Color(0xFF0F5C4A)
val EmeraldDeep = Color(0xFF083C30)
val EmeraldNight = Color(0xFF04231C)
val Gold = Color(0xFFE2B659)
val GoldDeep = Color(0xFFB98A2E)
val Cream = Color(0xFFFFF6E0)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EBE1),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF8A6414),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E7C1),
    onSecondaryContainer = Color(0xFF2B1E00),
    tertiary = Color(0xFF3F6374),
    onTertiary = Color.White,
    background = Color(0xFFE6EDF1),
    onBackground = Color(0xFF101E2B),
    surface = Color(0xFFE6EDF1),
    onSurface = Color(0xFF101E2B),
    surfaceVariant = Color(0xFFDCE6EB),
    onSurfaceVariant = Color(0xFF435664),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF6F8F9),
    surfaceContainerHigh = Color(0xFFE8EFF3),
    surfaceContainerHighest = Color(0xFFDCE6EB),
    outline = Color(0xFF627582),
    outlineVariant = Color(0xFFB9C9D3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DCBB9),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF115443),
    onPrimaryContainer = Color(0xFFD3EBE1),
    secondary = Gold,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF4A3912),
    onSecondaryContainer = Color(0xFFF6E7C1),
    tertiary = Color(0xFFA6CCE0),
    onTertiary = Color(0xFF0B3443),
    background = Color(0xFF101E2B),
    onBackground = Color(0xFFF2F5F3),
    surface = Color(0xFF101E2B),
    onSurface = Color(0xFFF2F5F3),
    surfaceVariant = Color(0xFF263A49),
    onSurfaceVariant = Color(0xFFC0CBD0),
    surfaceContainerLowest = Color(0xFF0C1721),
    surfaceContainerLow = Color(0xFF1B2C39),
    surfaceContainer = Color(0xFF263A49),
    surfaceContainerHigh = Color(0xFF2C4151),
    surfaceContainerHighest = Color(0xFF344B5D),
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
        val colors = if (!natural) baseColors else baseColors.copy(
            background = if (dark) Color(0xFF101B29) else Color(0xFFE4EAF0),
            surface = if (dark) Color(0xFF192635) else Color(0xFFF1F4F7),
            surfaceContainer = if (dark) Color(0xFF213143) else Color(0xFFD9E3EB),
            surfaceContainerHigh = if (dark) Color(0xFF293A4C) else Color(0xFFD3DEE8),
            onSurfaceVariant = if (dark) Color(0xFFC3CEDA) else Color(0xFF47596A),
            primary = if (dark) Color(0xFFA3CBB9) else Color(0xFF315E50),
            primaryContainer = if (dark) Color(0xFF304D49) else Color(0xFFCCDED6),
        )
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes) {
            // Цвет текста по умолчанию — светлый в тёмной теме, тёмный в светлой (раньше его давал Surface).
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colors.onSurface,
                content = content,
            )
        }
    }
}
