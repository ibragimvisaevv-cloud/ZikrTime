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
    background = Color(0xFFF8F5EE),
    onBackground = Color(0xFF1B1C18),
    surface = Color(0xFFF8F5EE),
    onSurface = Color(0xFF1B1C18),
    surfaceVariant = Color(0xFFE9E3D6),
    onSurfaceVariant = Color(0xFF4F4C44),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF2EDE2),
    surfaceContainerHigh = Color(0xFFECE6D9),
    surfaceContainerHighest = Color(0xFFE5DECF),
    outline = Color(0xFF7E7A70),
    outlineVariant = Color(0xFFDCD5C6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86D5B9),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF115443),
    onPrimaryContainer = Color(0xFFD3EBE1),
    secondary = Gold,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF4A3912),
    onSecondaryContainer = Color(0xFFF6E7C1),
    tertiary = Color(0xFFA6CCE0),
    onTertiary = Color(0xFF0B3443),
    background = Color(0xFF0B1210),
    onBackground = Color(0xFFE2E5E1),
    surface = Color(0xFF0B1210),
    onSurface = Color(0xFFE2E5E1),
    surfaceVariant = Color(0xFF29322E),
    onSurfaceVariant = Color(0xFFBDC7C2),
    surfaceContainerLowest = Color(0xFF070D0B),
    surfaceContainerLow = Color(0xFF131C19),
    surfaceContainer = Color(0xFF17211E),
    surfaceContainerHigh = Color(0xFF212B28),
    surfaceContainerHighest = Color(0xFF2B3532),
    outline = Color(0xFF88918D),
    outlineVariant = Color(0xFF2E3835),
)

private val base = Typography()

private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
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
fun ZikrTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
