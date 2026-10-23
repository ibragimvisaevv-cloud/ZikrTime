package com.zikrtime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Изумрудный и золото тасбиха
val Emerald = Color(0xFF0F5C4A)
val EmeraldDeep = Color(0xFF0A4537)
val Gold = Color(0xFFE2B659)
val Cream = Color(0xFFFFF4DC)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8DD),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF8A6414),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E3B8),
    onSecondaryContainer = Color(0xFF2B1E00),
    tertiary = Color(0xFF3F6374),
    onTertiary = Color.White,
    background = Color(0xFFFBF8F1),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFBF8F1),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFECE6DA),
    onSurfaceVariant = Color(0xFF4D4A42),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1E6),
    surfaceContainer = Color(0xFFF1EBDF),
    surfaceContainerHigh = Color(0xFFEBE4D7),
    surfaceContainerHighest = Color(0xFFE5DED0),
    outline = Color(0xFF7E7A70),
    outlineVariant = Color(0xFFD0C9BC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD3B6),
    onPrimary = Color(0xFF003829),
    primaryContainer = Emerald,
    onPrimaryContainer = Color(0xFFCDE8DD),
    secondary = Gold,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFF5E3B8),
    tertiary = Color(0xFFA6CCE0),
    onTertiary = Color(0xFF0B3443),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFE3E6E2),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFE3E6E2),
    surfaceVariant = Color(0xFF2A322F),
    onSurfaceVariant = Color(0xFFBFC8C3),
    surfaceContainerLowest = Color(0xFF09100E),
    surfaceContainerLow = Color(0xFF151D1A),
    surfaceContainer = Color(0xFF19221F),
    surfaceContainerHigh = Color(0xFF232C29),
    surfaceContainerHighest = Color(0xFF2E3734),
    outline = Color(0xFF89928E),
    outlineVariant = Color(0xFF3F4945),
)

private val base = Typography()

private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

/** Стиль для арабского текста аятов. */
val ArabicStyle = TextStyle(fontSize = 26.sp, lineHeight = 44.sp)

@Composable
fun ZikrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
