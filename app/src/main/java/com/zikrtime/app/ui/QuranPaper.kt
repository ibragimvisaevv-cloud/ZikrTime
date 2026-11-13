package com.zikrtime.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.zikrtime.app.ui.theme.LocalDarkTheme

/** Reader-only colours. The surrounding app keeps its existing emerald theme. */
internal data class QuranPaperColors(
    val canvas: Color, val paper: Color, val ink: Color, val muted: Color,
    val accent: Color, val rule: Color, val selection: Color,
)

private val DayPaper = QuranPaperColors(
    canvas = Color(0xFFECE5D8), paper = Color(0xFFFCF7EB), ink = Color(0xFF292C30),
    muted = Color(0xFF6C6559), accent = Color(0xFF80602B), rule = Color(0xFFD5C6A8),
    selection = Color(0xFFE9DAB9),
)
private val NightPaper = QuranPaperColors(
    canvas = Color(0xFF11171D), paper = Color(0xFF1A222B), ink = Color(0xFFF0E6D1),
    muted = Color(0xFFB7AFA0), accent = Color(0xFFD8B876), rule = Color(0xFF4E4D48),
    selection = Color(0xFF4C4534),
)

@Composable
internal fun quranPaperColors(): QuranPaperColors = if (LocalDarkTheme.current) NightPaper else DayPaper

@Composable
internal fun QuranReadingTheme(content: @Composable () -> Unit) {
    val p = quranPaperColors()
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme.copy(
        primary = p.accent, onPrimary = if (dark) NightPaper.canvas else DayPaper.paper,
        primaryContainer = p.selection, onPrimaryContainer = p.ink,
        secondary = p.accent, onSecondary = if (dark) NightPaper.canvas else DayPaper.paper,
        secondaryContainer = p.selection, onSecondaryContainer = p.ink,
        tertiary = p.accent, onTertiary = if (dark) NightPaper.canvas else DayPaper.paper,
        background = p.canvas, onBackground = p.ink,
        surface = p.paper, onSurface = p.ink,
        surfaceVariant = p.canvas, onSurfaceVariant = p.muted,
        surfaceContainerLowest = p.canvas, surfaceContainerLow = p.paper,
        surfaceContainer = p.paper, surfaceContainerHigh = p.canvas,
        surfaceContainerHighest = p.selection, surfaceTint = Color.Transparent,
        outline = p.muted, outlineVariant = p.rule,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides p.ink, content = content)
    }
}

/** Ornament lives in the margins. It never adds characters to the source text. */
@Composable
internal fun QuranPageFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = quranPaperColors()
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(p.paper).drawBehind {
        val outer = 5.dp.toPx()
        val inner = 9.dp.toPx()
        if (size.width > inner * 2 && size.height > inner * 2) {
            drawRect(p.rule, Offset(outer, outer), Size(size.width - outer * 2, size.height - outer * 2),
                style = Stroke(1.dp.toPx()))
            drawRect(p.rule.copy(alpha = .55f), Offset(inner, inner),
                Size(size.width - inner * 2, size.height - inner * 2), style = Stroke(.5.dp.toPx()))
            val length = 14.dp.toPx()
            for (right in listOf(false, true)) for (bottom in listOf(false, true)) {
                val x = if (right) size.width - outer else outer
                val y = if (bottom) size.height - outer else outer
                val dx = if (right) -length else length
                val dy = if (bottom) -length else length
                drawLine(p.accent, Offset(x, y), Offset(x + dx, y), 1.5.dp.toPx())
                drawLine(p.accent, Offset(x, y), Offset(x, y + dy), 1.5.dp.toPx())
            }
        }
    }, content = content)
}

@Composable
internal fun QuranOrnament(modifier: Modifier = Modifier) {
    val p = quranPaperColors()
    Canvas(modifier.size(width = 124.dp, height = 16.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = 5.dp.toPx()
        drawLine(p.rule, Offset(0f, cy), Offset(cx - r * 3, cy), 1.dp.toPx())
        drawLine(p.rule, Offset(cx + r * 3, cy), Offset(size.width, cy), 1.dp.toPx())
        val diamond = Path().apply {
            moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close()
        }
        drawPath(diamond, p.accent, style = Stroke(1.dp.toPx()))
        drawCircle(p.accent, 1.5.dp.toPx(), Offset(cx - r * 2, cy))
        drawCircle(p.accent, 1.5.dp.toPx(), Offset(cx + r * 2, cy))
    }
}

@Composable
internal fun QuranNumberSeal(number: String, modifier: Modifier = Modifier) {
    val p = quranPaperColors()
    Box(modifier.size(44.dp).drawBehind {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = size.minDimension * .46f
        val path = Path()
        repeat(16) { point ->
            val angle = Math.PI * point / 8 - Math.PI / 2
            val r = radius * if (point % 2 == 0) 1f else .77f
            val x = cx + (kotlin.math.cos(angle) * r).toFloat()
            val y = cy + (kotlin.math.sin(angle) * r).toFloat()
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, p.accent.copy(alpha = .07f))
        drawPath(path, p.accent.copy(alpha = .7f), style = Stroke(.8.dp.toPx()))
    }, contentAlignment = Alignment.Center) {
        Text(number, color = p.accent, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun QuranSurahHeading(surah: QuranSurah, modifier: Modifier = Modifier, compact: Boolean = false) {
    val p = quranPaperColors()
    val ru = Lang.content(LocalContext.current) == "ru"
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!compact) QuranOrnament()
        Text(surah.nameArabic, color = p.ink, textAlign = TextAlign.Center,
            style = ArabicStyle.copy(fontSize = if (compact) 24.sp else 32.sp,
                lineHeight = 1.65.em, textDirection = TextDirection.Rtl))
        Text(if (ru) surah.nameRu else surah.nameLatin, color = p.accent,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            textAlign = TextAlign.Center)
        if (!compact) Text(stringResource(R.string.quran_ayah_count, surah.ayahCount),
            color = p.muted, style = MaterialTheme.typography.bodySmall)
    }
}
