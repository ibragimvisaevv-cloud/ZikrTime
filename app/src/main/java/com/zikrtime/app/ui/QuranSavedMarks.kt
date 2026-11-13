package com.zikrtime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R

/** User annotations are UI metadata, never appended to the Quran source string. */
internal data class QuranSavedMark(val ayah: Int, val bookmarked: Boolean, val note: String)

@Composable
internal fun QuranSavedEntry(count: Int, open: () -> Unit) {
    val p = quranPaperColors()
    val description = stringResource(R.string.qm_in_surah, count)
    Surface(onClick = open, modifier = Modifier.fillMaxWidth().testTag("quran_saved_open")
        .semantics { contentDescription = description }, shape = RoundedCornerShape(12.dp),
        color = p.paper, border = BorderStroke(1.dp, p.rule)) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_quran_bookmark_saved), null, tint = p.accent, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.qm_saved_here), Modifier.weight(1f),
                color = p.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), color = p.accent, style = MaterialTheme.typography.titleSmall)
            Text("›", color = p.accent, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun markDescription(surah: Int, mark: QuranSavedMark): String = buildList {
    add(stringResource(R.string.qp_selected_ayah, surah, mark.ayah))
    if (mark.bookmarked) add(stringResource(R.string.qp_bookmark_short))
    if (mark.note.isNotEmpty()) add(stringResource(R.string.qt_note))
}.joinToString(" · ")

@Composable
private fun QuranSavedIcons(mark: QuranSavedMark) {
    val p = quranPaperColors()
    if (mark.bookmarked) Icon(painterResource(R.drawable.ic_quran_bookmark_saved), null,
        tint = p.accent, modifier = Modifier.size(16.dp))
    if (mark.note.isNotEmpty()) Icon(Icons.Filled.Edit, null, tint = p.accent, modifier = Modifier.size(16.dp))
}

/** A separate chip identifies the exact ayah and both saved kinds without relying on colour. */
@Composable
internal fun QuranSavedIndicator(surah: Int, mark: QuranSavedMark, open: () -> Unit) {
    val p = quranPaperColors()
    val description = markDescription(surah, mark)
    OutlinedButton(onClick = open, modifier = Modifier.testTag("quran_saved_indicator_" + mark.ayah)
        .semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        border = BorderStroke(1.dp, p.accent.copy(alpha = .5f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = p.selection.copy(alpha = .38f))) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            QuranSavedIcons(mark)
            Text("$surah:${mark.ayah}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun QuranSavedNotePreview(mark: QuranSavedMark, open: () -> Unit) {
    val p = quranPaperColors()
    Surface(onClick = open, modifier = Modifier.fillMaxWidth().testTag("quran_saved_note_preview_" + mark.ayah),
        shape = RoundedCornerShape(12.dp), color = p.selection.copy(alpha = .38f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp), tint = p.accent)
                Text(stringResource(R.string.qm_your_note), style = MaterialTheme.typography.labelMedium, color = p.accent)
            }
            Text(mark.note, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = p.ink)
        }
    }
}

@Composable
internal fun QuranSavedSheet(surah: Int, name: String, marks: List<QuranSavedMark>, close: () -> Unit,
    jump: (Int) -> Unit, edit: (Int) -> Unit) {
    val p = quranPaperColors()
    HorizonSheet(stringResource(R.string.qm_saved_here), close) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = p.muted)
        Column(Modifier.fillMaxWidth().testTag("quran_saved_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (marks.isEmpty()) Text(stringResource(R.string.qm_empty),
                style = MaterialTheme.typography.bodyLarge, color = p.muted)
            marks.forEach { mark ->
                val description = markDescription(surah, mark)
                Surface(shape = RoundedCornerShape(16.dp), color = p.paper, border = BorderStroke(1.dp, p.rule)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            QuranSavedIcons(mark)
                            Text(description, style = MaterialTheme.typography.labelLarge, color = p.accent)
                        }
                        if (mark.note.isNotEmpty()) Text(mark.note, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            color = p.ink, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { jump(mark.ayah) }, modifier = Modifier.weight(1f)
                                .testTag("quran_saved_jump_" + mark.ayah), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(stringResource(R.string.qm_go_to_ayah))
                            }
                            if (mark.note.isNotEmpty()) TextButton(onClick = { edit(mark.ayah) }, modifier = Modifier.weight(1f)
                                .testTag("quran_saved_edit_" + mark.ayah), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(stringResource(R.string.qm_open_note))
                            }
                        }
                    }
                }
            }
        }
    }
}
