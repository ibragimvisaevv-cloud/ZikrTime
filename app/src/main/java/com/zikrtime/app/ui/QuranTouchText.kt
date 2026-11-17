package com.zikrtime.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import com.zikrtime.app.R
import com.zikrtime.app.audio.isQuranSpokenToken
import com.zikrtime.app.data.QuranTextRange
import com.zikrtime.app.data.QuranVisibleText

/** Layout coordinates are transient. Stored notes and progress only contain surah/ayah IDs. */
internal class QuranTextGeometry {
    class Entry(val ranges: List<QuranTextRange>, val words: Map<Int, Map<Int, IntRange>>) {
        var layout: TextLayoutResult? = null
        var coordinates: LayoutCoordinates? = null
    }
    val entries = mutableMapOf<Int, Entry>()
    var viewport: LayoutCoordinates? = null

    fun visibleText(completeLines: Boolean = true): List<QuranVisibleText> {
        val view = viewport?.takeIf { it.isAttached } ?: return emptyList()
        val bounds = view.boundsInRoot()
        return entries.values.flatMap { entry ->
            val coordinates = entry.coordinates?.takeIf { it.isAttached } ?: return@flatMap emptyList()
            val layout = entry.layout ?: return@flatMap emptyList()
            val origin = coordinates.localToRoot(Offset.Zero).y
            (0 until layout.lineCount).flatMap { line ->
                val top = origin + layout.getLineTop(line)
                val bottom = origin + layout.getLineBottom(line)
                val visible = if (completeLines) top >= bounds.top - .5f && bottom <= bounds.bottom + .5f
                    else bottom > bounds.top && top < bounds.bottom
                if (!visible) emptyList() else entry.ranges.mapNotNull { range ->
                    val start = maxOf(range.start, layout.getLineStart(line))
                    val end = minOf(range.arabicEnd, layout.getLineEnd(line))
                    if (start < end) QuranVisibleText(range.ayah, start - range.start, end - range.start) else null
                }
            }
        }
    }

    /** Keep both lines in view if they fit. Oversized pairs follow the recited word. */
    fun scrollToPassage(ayah: Int, first: Int, last: Int, spokenOffset: Int): Float? {
        val bounds = viewport?.takeIf { it.isAttached }?.boundsInRoot() ?: return null
        val entry = entries.values.firstOrNull { block -> block.ranges.any { it.ayah == ayah } } ?: return null
        val coordinates = entry.coordinates?.takeIf { it.isAttached } ?: return null
        val layout = entry.layout ?: return null
        val range = entry.ranges.first { it.ayah == ayah }
        fun lineAt(offset: Int) = layout.getLineForOffset((range.start + offset).coerceIn(range.start, range.arabicEnd))
        val origin = coordinates.localToRoot(Offset.Zero).y
        var top = origin + layout.getLineTop(lineAt(first))
        var bottom = origin + layout.getLineBottom(lineAt(last))
        if (bottom - top > bounds.height) {
            val line = lineAt(spokenOffset)
            top = origin + layout.getLineTop(line)
            bottom = origin + layout.getLineBottom(line)
        }
        if (top < bounds.top - 1f || bottom - top > bounds.height) return top - bounds.top
        return if (bottom > bounds.bottom + 1f) bottom - bounds.bottom else 0f
    }

    fun firstAyah(): Int? = visibleText(false).minOfOrNull { it.ayah }

    fun wordCenter(ayah: Int, word: Int): Offset? {
        val view = viewport?.takeIf { it.isAttached } ?: return null
        val entry = entries.values.firstOrNull { block -> block.ranges.any { it.ayah == ayah } } ?: return null
        val coordinates = entry.coordinates?.takeIf { it.isAttached } ?: return null
        val layout = entry.layout ?: return null
        val verse = entry.ranges.first { it.ayah == ayah }
        val text = layout.layoutInput.text.text
        val token = entry.words[ayah]?.get(word) ?: return null
        val letters = token.map { verse.start + it }.filter { text[it].isLetter() }
        val center = letters.getOrNull(letters.size / 2) ?: verse.start + token.first
        val offsets = (listOf(center) + letters).distinct()
        for (offset in offsets) {
            val box = layout.getBoundingBox(offset)
            if (box.width > 1f && view.boundsInRoot().contains(coordinates.localToRoot(box.center)))
                return coordinates.localToWindow(box.center)
        }
        return null
    }
}

/** Read by the debug-only Android fixture to tap an actual laid-out glyph. */
internal object QuranWordProbe {
    var surah = 0
    var geometry: QuranTextGeometry? = null
    fun point(surah: Int, ayah: Int, word: Int): Offset? =
        if (this.surah == surah) geometry?.wordCenter(ayah, word) else null
}

private val tokenPattern = Regex("\\S+")
private data class QuranTextHit(val ayah: Int, val word: Int?)

@Composable
internal fun QuranTouchText(text: String, ranges: List<QuranTextRange>,
    geometry: QuranTextGeometry, style: TextStyle, tag: String, modifier: Modifier = Modifier,
    choose: (Int, Int?, Boolean) -> Unit, savedAyahs: Set<Int> = emptySet(), focusedAyah: Int? = null,
    playingAyah: Int? = null, focusRanges: List<IntRange> = emptyList(), ayahWithoutWords: Int? = null,
    unmappedTap: IntRange? = null) {
    val key = ranges.first().ayah
    val entry = remember(geometry, text, ranges) {
        val words = ranges.associate { verse ->
            val tokens = tokenPattern.findAll(text.substring(verse.start, verse.arabicEnd))
            verse.ayah to tokens.mapIndexedNotNull { index, match ->
                if (isQuranSpokenToken(match.value)) index + 1 to match.range else null
            }.toMap()
        }
        QuranTextGeometry.Entry(ranges, words)
    }
    DisposableEffect(geometry, entry) {
        geometry.entries[key] = entry
        onDispose { if (geometry.entries[key] === entry) geometry.entries.remove(key) }
    }
    val onChoose by rememberUpdatedState(choose)
    val haptic = LocalHapticFeedback.current
    val colors = quranPaperColors()
    val soundingRange = ranges.firstOrNull { it.ayah == playingAyah }
    val soundingLabel = soundingRange?.let { stringResource(R.string.ql_reading_now, it.ayah.toString()) }
    val focus = ranges.firstOrNull { it.ayah == focusedAyah }
    val words = if (focus != null) focusRanges else emptyList()
    val annotated = remember(text, ranges, colors, savedAyahs, focus, words, ayahWithoutWords, unmappedTap) {
        AnnotatedString.Builder(text).apply {
            focus?.let { addStyle(SpanStyle(background = colors.selection), it.start, it.end) }
            ranges.forEach { range ->
                if (range.arabicEnd < range.end) {
                    addStyle(SpanStyle(color = colors.accent), range.arabicEnd, range.end)
                    if (range.ayah == ayahWithoutWords) {
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                            range.arabicEnd, range.end)
                    } else if (range.ayah in savedAyahs || range.ayah == focusedAyah) {
                        addStyle(SpanStyle(textDecoration = TextDecoration.Underline), range.arabicEnd, range.end)
                    }
                }
            }
            // Layer the more prominent two-word cursor over the single softly marked ayah.
            focus?.let { verse ->
                if (unmappedTap != null && unmappedTap.last < verse.arabicEnd - verse.start)
                    addStyle(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline),
                        verse.start + unmappedTap.first, verse.start + unmappedTap.last + 1)
                words.forEach { word ->
                    if (word.first >= 0 && word.last < verse.arabicEnd - verse.start)
                        addStyle(SpanStyle(color = colors.paper, background = colors.accent),
                            verse.start + word.first, verse.start + word.last + 1)
                }
            }
        }.toAnnotatedString()
    }
    val accessibility = ranges.map { range ->
        CustomAccessibilityAction(stringResource(R.string.qt_ayah_actions, range.ayah)) {
            onChoose(range.ayah, null, true); true
        }
    }
    fun hit(position: Offset): QuranTextHit? {
        val layout = entry.layout ?: return null
        val line = layout.getLineForVerticalPosition(position.y)
        if (position.y < layout.getLineTop(line) || position.y > layout.getLineBottom(line) ||
            position.x < layout.getLineLeft(line) || position.x > layout.getLineRight(line)) return null
        // getOffsetForPosition returns the nearest caret, including the far edge of an RTL
        // glyph. Prefer the character whose laid-out box actually contains the finger.
        val caret = layout.getOffsetForPosition(position)
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        if (lineEnd <= lineStart) return null
        val offset = listOf(caret, caret - 1).firstOrNull {
            it in lineStart until lineEnd && layout.getBoundingBox(it).contains(position)
        } ?: caret.coerceIn(lineStart, lineEnd - 1)
        val verse = ranges.lastOrNull { it.start <= offset } ?: return null
        val local = offset - verse.start
        val word = if (offset < verse.arabicEnd)
            entry.words[verse.ayah]?.entries?.firstOrNull { local in it.value }?.key
            else null
        return QuranTextHit(verse.ayah, word)
    }
    Text(annotated, style = style,
        textAlign = if (style.textAlign == TextAlign.Unspecified) TextAlign.Right else style.textAlign, color = colors.ink,
        onTextLayout = { entry.layout = it },
        modifier = modifier.testTag(tag).onGloballyPositioned { entry.coordinates = it }
            .semantics {
                customActions = accessibility
                if (soundingLabel != null) stateDescription = soundingLabel
            }
            .pointerInput(entry) {
                detectTapGestures(onTap = { hit(it)?.let { target -> onChoose(target.ayah, target.word, false) } },
                    onLongPress = { hit(it)?.let { target ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChoose(target.ayah, target.word, true)
                    } })
            })
}

