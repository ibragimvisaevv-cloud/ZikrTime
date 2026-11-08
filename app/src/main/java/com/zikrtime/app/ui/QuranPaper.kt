package com.zikrtime.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    canvas = Color(0xFFECE9E0), paper = Color(0xFFFAF7EF), ink = Color(0xFF243D34),
    muted = Color(0xFF626B60), accent = Color(0xFF506B56), rule = Color(0xFFCBCDBC),
    selection = Color(0xFFDCE5D8),
)
private val NightPaper = QuranPaperColors(
    canvas = Color(0xFF0E2520), paper = Color(0xFF192F28), ink = Color(0xFFF1EBDD),
    muted = Color(0xFFBAC8BC), accent = Color(0xFFD1BA8C), rule = Color(0xFF40584A),
    selection = Color(0xFF354B3E),
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

/** A real existing scene anchors the Quran section; none of its decoration is text. */
@Composable
internal fun QuranSceneSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF17382D))) {
        Image(painterResource(R.drawable.focus_courtyard), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(
            Color(0xF00D2A21), Color(0xC0143027), Color(0x30162E25)))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color.Transparent, Color(0x70112B22)))))
        CompositionLocalProvider(LocalContentColor provides Color(0xFFF5F0E3)) { content() }
    }
}

/** Ornament lives in the margins. It never adds characters to the source text. */
@Composable
internal fun QuranPageFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = quranPaperColors()
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(p.paper).drawBehind {
        val outer = 5.dp.toPx()
        val inner = 9.dp.toPx()
        if (size.width > inner * 2 && size.height > inner * 2) {
            drawRect(p.rule.copy(alpha = .65f), Offset(outer, outer), Size(size.width - outer * 2, size.height - outer * 2),
                style = Stroke(.75.dp.toPx()))
            drawRect(p.rule.copy(alpha = .3f), Offset(inner, inner),
                Size(size.width - inner * 2, size.height - inner * 2), style = Stroke(.5.dp.toPx()))
            val length = 14.dp.toPx()
            for (right in listOf(false, true)) for (bottom in listOf(false, true)) {
                val x = if (right) size.width - outer else outer
                val y = if (bottom) size.height - outer else outer
                val dx = if (right) -length else length
                val dy = if (bottom) -length else length
                drawLine(p.accent.copy(alpha = .65f), Offset(x, y), Offset(x + dx, y), 1.dp.toPx())
                drawLine(p.accent.copy(alpha = .65f), Offset(x, y), Offset(x, y + dy), 1.dp.toPx())
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
