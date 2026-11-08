package com.zikrtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.QuranSurah
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.theme.ArabicStyle

/** Reading uses the same semantic palette as the app, with a quiet text surface. */
internal data class QuranPaperColors(
    val canvas: Color, val paper: Color, val ink: Color, val muted: Color,
    val accent: Color, val rule: Color, val selection: Color,
)

@Composable
internal fun quranPaperColors(): QuranPaperColors {
    val scheme = MaterialTheme.colorScheme
    return QuranPaperColors(
        canvas = scheme.background, paper = scheme.surfaceContainerLow,
        ink = scheme.onSurface, muted = scheme.onSurfaceVariant,
        accent = scheme.primary, rule = scheme.outlineVariant,
        selection = scheme.primaryContainer,
    )
}

@Composable
internal fun QuranReadingTheme(content: @Composable () -> Unit) {
    val p = quranPaperColors()
    CompositionLocalProvider(LocalContentColor provides p.ink, content = content)
}

/** A quiet reading surface, without decorative frames near the Quranic signs. */
@Composable
internal fun QuranPageFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = quranPaperColors()
    Box(modifier.clip(MaterialTheme.shapes.medium).background(p.paper), content = content)
}

@Composable
internal fun QuranNumberSeal(number: String, modifier: Modifier = Modifier) {
    val p = quranPaperColors()
    Box(modifier.size(44.dp), contentAlignment = Alignment.CenterStart) {
        Text(number.padStart(2, '0'), color = p.muted,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun QuranSurahHeading(surah: QuranSurah, modifier: Modifier = Modifier, compact: Boolean = false) {
    val p = quranPaperColors()
    val ru = Lang.content(LocalContext.current) == "ru"
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(surah.nameArabic, color = p.ink, textAlign = TextAlign.Center,
            style = ArabicStyle.copy(fontSize = if (compact) 24.sp else 32.sp,
                lineHeight = 1.65.em, textDirection = TextDirection.Rtl))
        Text(if (ru) surah.nameRu else surah.nameLatin, color = p.accent,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center)
        if (!compact) Text(stringResource(R.string.quran_ayah_count, surah.ayahCount),
            color = p.muted, style = MaterialTheme.typography.bodySmall)
    }
}
