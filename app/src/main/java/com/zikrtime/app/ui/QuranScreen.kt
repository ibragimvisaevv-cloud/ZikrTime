package com.zikrtime.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.*
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.theme.ArabicStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun QuranScreen() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val progress = remember { QuranProgress(context) }
    var book by remember { mutableStateOf<QuranBook?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var surahId by rememberSaveable { mutableIntStateOf(0) }
    var startAyah by rememberSaveable { mutableIntStateOf(1) }
    var sources by rememberSaveable { mutableStateOf(false) }
    var scale by rememberReadingScale()
    LaunchedEffect(attempt) {
        failed = false
        try { book = QuranRepository.load(context) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    BackHandler(surahId != 0) { surahId = 0 }
    val content = book
    if (content != null && surahId != 0) {
        key(surahId) { QuranReader(content, surahId, startAyah, scale, { scale = it }, progress, { surahId = 0 }) }
    } else ScreenScaffold(stringResource(R.string.quran_title), onBack = { nav.back() },
        scrollable = false, showHeading = false,
        actions = {
            IconButton(onClick = { sources = true }, modifier = Modifier.testTag("quran_sources")) {
                Icon(Icons.Filled.Info, stringResource(R.string.quran_sources))
            }
        }) {
        when {
            content != null -> QuranIndex(content, progress, {
                startAyah = it.ayah; surahId = it.surah; progress.save(it)
            }, Modifier.weight(1f))
            failed -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.quran_load_error))
                TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.quran_retry)) }
            }
            else -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
    if (sources) QuranSources { sources = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuranIndex(book: QuranBook, progress: QuranProgress, open: (QuranProgress.Location) -> Unit, modifier: Modifier) {
    val ru = Lang.content(LocalContext.current) == "ru"
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val showBookmarks = tab == 1
    var statistics by remember { mutableStateOf(false) }
    val stop = remember(book) { progress.stop(book) }
    val notes = remember(book) { progress.notes(book) }
    val bookmarks = remember(book) { progress.bookmarks(book).sortedWith(compareBy({ it.surah }, { it.ayah })) }
    val last = remember(book) { progress.last(book) }
    val filtered = remember(book, query) {
        book.surahs.filter { query.isBlank() || it.id.toString() == query.trim() ||
            it.nameRu.contains(query.trim(), true) || it.nameLatin.contains(query.trim(), true) || it.nameArabic.contains(query.trim()) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, label = { Text(stringResource(R.string.quran_surahs)) }, modifier = Modifier.testTag("quran_surahs"))
            FilterChip(showBookmarks, { tab = 1 }, label = { Text(stringResource(R.string.quran_bookmarks)) }, modifier = Modifier.testTag("quran_bookmarks"))
            FilterChip(tab == 2, { tab = 2 }, label = { Text(stringResource(R.string.qf_notes)) }, modifier = Modifier.testTag("quran_personal_notes"))
        }
        if (tab == 0) OutlinedTextField(query, { query = it }, singleLine = true,
            label = { Text(stringResource(R.string.quran_search)) }, modifier = Modifier.fillMaxWidth().testTag("quran_search"))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            item("statistics") {
                OutlinedButton(onClick = { statistics = true }, modifier = Modifier.fillMaxWidth().testTag("quran_statistics_open")) {
                    Text(stringResource(R.string.qf_statistics))
                }
            }
            if (stop != null && tab != 1) item("stop") {
                ZCard(onClick = { open(stop) }, modifier = Modifier.testTag("quran_saved_stop")) {
                    Text(stringResource(R.string.qf_saved_stop), style = MaterialTheme.typography.titleMedium)
                    Text(stop.key + " · " + book.surahs.first { it.id == stop.surah }.let { if (ru) it.nameRu else it.nameLatin })
                    if (progress.note(stop).isNotBlank()) Text(progress.note(stop), maxLines = 3)
                }
            }
            if (tab == 0 && last != null) item("continue") {
                ZCard(modifier = Modifier.testTag("quran_continue"), onClick = { open(last) }) {
                    Text(stringResource(R.string.quran_continue), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    val surah = book.surahs.first { it.id == last.surah }
                    Text("${if (ru) surah.nameRu else surah.nameLatin} · ${last.surah}:${last.ayah}", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (tab == 2) {
                if (notes.isEmpty()) item { Text(stringResource(R.string.qf_no_notes)) }
                items(notes, key = { "note_" + it.key }) { location ->
                    ZCard(onClick = { open(location) }, modifier = Modifier.testTag("quran_note_" + location.surah + "_" + location.ayah)) {
                        Text(location.key, style = MaterialTheme.typography.titleMedium)
                        Text(progress.note(location), maxLines = 4)
                    }
                }
            } else if (showBookmarks) {
                if (bookmarks.isEmpty()) item { Text(stringResource(R.string.quran_no_bookmarks), modifier = Modifier.padding(vertical = 24.dp)) }
                items(bookmarks, key = { it.key }) { location ->
                    val surah = book.surahs.first { it.id == location.surah }
                    ZCard(onClick = { open(location) }, modifier = Modifier.testTag("quran_saved_${location.surah}_${location.ayah}")) {
                        Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleMedium)
                        Text("${location.surah}:${location.ayah}", color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                if (filtered.isEmpty()) item { Text(stringResource(R.string.quran_no_results)) }
                items(filtered, key = { it.id }) { surah ->
                    ZCard(onClick = { open(QuranProgress.Location(surah.id, 1)) }, modifier = Modifier.testTag("quran_surah_${surah.id}")) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(surah.id.toString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            Column(Modifier.weight(1f)) {
                                Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.quran_ayah_count, surah.ayahCount), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(surah.nameArabic, style = ArabicStyle.copy(fontSize = 25.sp, textDirection = TextDirection.Rtl), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    if (statistics) HorizonSheet(stringResource(R.string.qf_statistics), { statistics = false }) {
        PersonalProgressSetting()
        QuranStatisticsCard(book)
    }
}

@Composable
internal fun QuranSources(close: () -> Unit) {
    val uri = LocalUriHandler.current
    HorizonSheet(stringResource(R.string.quran_sources), close) {
        Text(stringResource(R.string.quran_source_arabic), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { runCatching { uri.openUri("https://tanzil.net/docs/Quran_Text") } }) { Text("Tanzil") }
        Text(stringResource(R.string.quran_source_translation), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { runCatching { uri.openUri("https://quranenc.com/en/browse/russian_rwwad/1") } }) { Text("QuranEnc") }
        TextButton(onClick = close, modifier = Modifier.testTag("quran_sources_close")) { Text(stringResource(R.string.quran_close)) }
    }
}
