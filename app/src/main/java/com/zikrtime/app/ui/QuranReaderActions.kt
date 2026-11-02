package com.zikrtime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R

/** Selection controls reserve their own space: they never cover a line of Quran text. */
@Composable
internal fun QuranSelectionBar(
    surah: Int, ayah: Int, saved: Boolean, clear: () -> Unit, note: () -> Unit,
    bookmark: () -> Unit, copy: () -> Unit, actions: () -> Unit, compact: Boolean,
) {
    val colors = quranPaperColors()
    val reference = stringResource(R.string.qp_selected_ayah, surah, ayah)
    val noteLabel = stringResource(R.string.qt_note)
    val bookmarkLabel = stringResource(if (saved) R.string.qt_remove_bookmark else R.string.qt_save_place)
    val copyLabel = stringResource(R.string.qt_copy)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("quran_selection"),
        shape = RoundedCornerShape(16.dp), color = colors.paper,
        border = BorderStroke(1.dp, colors.rule),
    ) {
        if (compact) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = actions, modifier = Modifier.weight(1f).testTag("quran_selection_actions"),
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("$surah:$ayah", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { contentDescription = reference })
                }
                IconButton(onClick = note, modifier = Modifier.testTag("quran_selection_note")) {
                    Icon(Icons.Filled.Edit, noteLabel)
                }
                IconButton(onClick = bookmark, modifier = Modifier.testTag("quran_selection_bookmark")) {
                    Icon(painterResource(if (saved) R.drawable.ic_quran_bookmark_saved else R.drawable.ic_quran_bookmark), bookmarkLabel)
                }
                IconButton(onClick = copy, modifier = Modifier.testTag("quran_selection_copy").semantics { contentDescription = copyLabel }) {
                    QuranCopyGlyph()
                }
                IconButton(onClick = clear, modifier = Modifier.testTag("quran_clear_selection")) {
                    Icon(Icons.Filled.Close, stringResource(R.string.qp_clear_selection))
                }
            }
        } else {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = actions, modifier = Modifier.weight(1f).testTag("quran_selection_actions")) {
                        Text(reference, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = clear, modifier = Modifier.testTag("quran_clear_selection")) {
                        Icon(Icons.Filled.Close, stringResource(R.string.qp_clear_selection))
                    }
                }
                HorizontalDivider(color = colors.rule.copy(alpha = .7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = note, modifier = Modifier.weight(1f).testTag("quran_selection_note"),
                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text(noteLabel, maxLines = 1) }
                    TextButton(onClick = bookmark, modifier = Modifier.weight(1f).testTag("quran_selection_bookmark")
                        .semantics { contentDescription = bookmarkLabel }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Icon(painterResource(if (saved) R.drawable.ic_quran_bookmark_saved else R.drawable.ic_quran_bookmark),
                            null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.qp_bookmark_short), maxLines = 1)
                    }
                    TextButton(onClick = copy, modifier = Modifier.weight(1f).testTag("quran_selection_copy"),
                        contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(stringResource(R.string.qp_copy_short), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranCopyGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(1.7f * unit)
        drawRect(color, Offset(8 * unit, 7 * unit), Size(11 * unit, 14 * unit), style = stroke)
        drawLine(color, Offset(5 * unit, 17 * unit), Offset(5 * unit, 3 * unit), strokeWidth = stroke.width)
        drawLine(color, Offset(5 * unit, 3 * unit), Offset(15 * unit, 3 * unit), strokeWidth = stroke.width)
    }
}
