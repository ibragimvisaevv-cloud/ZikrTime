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
import com.zikrtime.app.R
import com.zikrtime.app.data.QuranTextRange
import com.zikrtime.app.data.QuranVisibleText

/** Layout coordinates are transient. Stored notes and progress only contain surah/ayah IDs. */
internal class QuranTextGeometry {
    class Entry(val ranges: List<QuranTextRange>) {
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

    /** A source-relative offset locates the word's line in both layout modes. */
    fun scrollToAyahStart(ayah: Int, offset: Int = 0): Float? {
        val bounds = viewport?.takeIf { it.isAttached }?.boundsInRoot() ?: return null
        val entry = entries.values.firstOrNull { block -> block.ranges.any { it.ayah == ayah } } ?: return null
        val coordinates = entry.coordinates?.takeIf { it.isAttached } ?: return null
        val layout = entry.layout ?: return null
        val range = entry.ranges.first { it.ayah == ayah }
        val line = layout.getLineForOffset((range.start + offset).coerceIn(range.start, range.arabicEnd))
        val origin = coordinates.localToRoot(Offset.Zero).y
        val top = origin + layout.getLineTop(line)
        val bottom = origin + layout.getLineBottom(line)
        return if (top >= bounds.top - 1 && bottom <= bounds.bottom + 1) 0f else top - bounds.top
    }

    fun firstAyah(): Int? = visibleText(false).minOfOrNull { it.ayah }
}

@Composable
internal fun QuranTouchText(text: String, ranges: List<QuranTextRange>, selected: Int,
    geometry: QuranTextGeometry, style: TextStyle, tag: String, modifier: Modifier = Modifier,
    choose: (Int, Boolean) -> Unit, savedAyahs: Set<Int> = emptySet(), playingAyah: Int? = null,
    playingWordRange: IntRange? = null) {
    val key = ranges.first().ayah
    val entry = remember(geometry, ranges) { QuranTextGeometry.Entry(ranges) }
    DisposableEffect(geometry, entry) {
        geometry.entries[key] = entry
        onDispose { if (geometry.entries[key] === entry) geometry.entries.remove(key) }
    }
    val onChoose by rememberUpdatedState(choose)
    val haptic = LocalHapticFeedback.current
    val colors = quranPaperColors()
    val soundingRange = ranges.firstOrNull { it.ayah == playingAyah }
    val soundingLabel = soundingRange?.let { stringResource(R.string.ql_reading_now, it.ayah.toString()) }
    val word = if (soundingRange != null) playingWordRange else null
    val annotated = remember(text, ranges, selected, colors, savedAyahs, soundingRange, word) {
        AnnotatedString.Builder(text).apply {
            ranges.forEach { range ->
                if (range.ayah in savedAyahs) {
                    addStyle(SpanStyle(background = colors.selection.copy(alpha = .38f)), range.start, range.end)
                    // Only the existing UI verse marker is decorated; source glyphs/offsets stay exact.
                    if (range.arabicEnd < range.end) addStyle(SpanStyle(textDecoration = TextDecoration.Underline),
                        range.arabicEnd, range.end)
                }
                if (range.arabicEnd < range.end) addStyle(SpanStyle(color = colors.accent), range.arabicEnd, range.end)
            }
            ranges.firstOrNull { it.ayah == selected }?.let {
                addStyle(SpanStyle(background = colors.selection), it.start, it.end)
            }
            soundingRange?.let {
                if (word != null && word.first >= 0 && word.last < it.arabicEnd - it.start) {
                    addStyle(SpanStyle(color = colors.paper, background = colors.accent),
                        it.start + word.first, it.start + word.last + 1)
                } else {
                    addStyle(SpanStyle(color = colors.accent), it.start, it.arabicEnd)
                }
            }
        }.toAnnotatedString()
    }
    val accessibility = ranges.map { range ->
        CustomAccessibilityAction(stringResource(R.string.qt_ayah_actions, range.ayah)) {
            onChoose(range.ayah, true); true
        }
    }
    fun hit(position: Offset): Int? {
        val layout = entry.layout ?: return null
        val line = layout.getLineForVerticalPosition(position.y)
        if (position.y < layout.getLineTop(line) || position.y > layout.getLineBottom(line) ||
            position.x < layout.getLineLeft(line) || position.x > layout.getLineRight(line)) return null
        val offset = layout.getOffsetForPosition(position)
        return ranges.firstOrNull { offset >= it.start && offset < it.end }?.ayah
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
                detectTapGestures(onTap = { hit(it)?.let { ayah -> onChoose(ayah, false) } },
                    onLongPress = { hit(it)?.let { ayah ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChoose(ayah, true)
                    } })
            })
}

