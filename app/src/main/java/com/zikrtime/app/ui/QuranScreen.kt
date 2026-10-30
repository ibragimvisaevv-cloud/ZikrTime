package com.zikrtime.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
    val compactHeader = LocalConfiguration.current.let { it.screenWidthDp < 360 || it.fontScale > 1.3f }
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
    ScreenScaffold(stringResource(R.string.quran_title),
        onBack = { if (surahId != 0) surahId = 0 else nav.back() },
        scrollable = false, showHeading = false,
        actions = {
            if (surahId != 0) ReadingTools(scale) { scale = it }
            if (compactHeader) {
                IconButton(onClick = { sources = true }, modifier = Modifier.testTag("quran_sources")) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.quran_sources), tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                TextButton(onClick = { sources = true }, modifier = Modifier.testTag("quran_sources")) {
                    Text(stringResource(R.string.quran_sources))
                }
            }
        }) {
        val content = book
        when {
            content != null -> {
                val open: (QuranProgress.Location) -> Unit = { startAyah = it.ayah; surahId = it.surah; progress.save(it) }
                if (surahId == 0) QuranIndex(content, progress, open, Modifier.weight(1f))
                else key(surahId) {
                    QuranReader(content, surahId, startAyah, scale, progress, { surahId = 0 }, Modifier.weight(1f))
                }
            }
            failed -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.quran_load_error))
                TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.quran_retry)) }
            }
            else -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
    if (sources) QuranSources { sources = false }
}

@Composable
private fun QuranIndex(book: QuranBook, progress: QuranProgress, open: (QuranProgress.Location) -> Unit, modifier: Modifier) {
    val ru = Lang.content(LocalContext.current) == "ru"
    var query by rememberSaveable { mutableStateOf("") }
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    val bookmarks = remember(book) { progress.bookmarks(book).sortedWith(compareBy({ it.surah }, { it.ayah })) }
    val last = remember(book) { progress.last(book) }
    val filtered = remember(book, query) {
        book.surahs.filter { query.isBlank() || it.id.toString() == query.trim() ||
            it.nameRu.contains(query.trim(), true) || it.nameLatin.contains(query.trim(), true) || it.nameArabic.contains(query.trim()) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!showBookmarks, { showBookmarks = false }, label = { Text(stringResource(R.string.quran_surahs)) }, modifier = Modifier.testTag("quran_surahs"))
            FilterChip(showBookmarks, { showBookmarks = true }, label = { Text(stringResource(R.string.quran_bookmarks)) }, modifier = Modifier.testTag("quran_bookmarks"))
        }
        if (!showBookmarks) OutlinedTextField(query, { query = it }, singleLine = true,
            label = { Text(stringResource(R.string.quran_search)) }, modifier = Modifier.fillMaxWidth().testTag("quran_search"))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (!showBookmarks && last != null) item("continue") {
                ZCard(modifier = Modifier.testTag("quran_continue"), onClick = { open(last) }) {
                    Text(stringResource(R.string.quran_continue), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    val surah = book.surahs.first { it.id == last.surah }
                    Text("${if (ru) surah.nameRu else surah.nameLatin} · ${last.surah}:${last.ayah}", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (showBookmarks) {
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
}

@Composable
private fun QuranReader(book: QuranBook, surahId: Int, firstAyah: Int, scale: Float,
    progress: QuranProgress, showIndex: () -> Unit, modifier: Modifier) {
    val ru = Lang.content(LocalContext.current) == "ru"
    val surah = remember(book, surahId) { book.surahs.first { it.id == surahId } }
    val verses = remember(book, surahId) { book.verses(surahId) }
    val list = rememberLazyListState((firstAyah - 1).coerceIn(verses.indices))
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var bookmarks by remember { mutableStateOf(progress.bookmarks(book)) }
    var jump by rememberSaveable { mutableStateOf(firstAyah.toString()) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(list, surahId) {
        snapshotFlow { list.isScrollInProgress to list.firstVisibleItemIndex }.distinctUntilChanged().collect { (scrolling, index) ->
            if (scrolling) progress.save(QuranProgress.Location(surahId, index + 1))
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.quran_ayah_count, surah.ayahCount), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = showIndex, modifier = Modifier.testTag("quran_surahs")) { Text(stringResource(R.string.quran_surahs)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(jump, { jump = it; invalid = false }, singleLine = true, isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.quran_ayah)) }, modifier = Modifier.weight(1f).testTag("quran_ayah_input"))
            FilledTonalButton(onClick = {
                val number = jump.toIntOrNull()
                if (number == null || number !in 1..surah.ayahCount) invalid = true
                else scope.launch {
                    focus.clearFocus()
                    list.scrollToItem(number - 1)
                    // A short final verse may share the viewport with the preceding verse.
                    progress.save(QuranProgress.Location(surahId, number))
                }
            }, modifier = Modifier.testTag("quran_go")) { Text(stringResource(R.string.quran_go)) }
        }
        if (invalid) Text(stringResource(R.string.quran_jump_error, surah.ayahCount), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("quran_jump_error"))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(verses, key = { it.number }) { ayah ->
                val location = QuranProgress.Location(surahId, ayah.number)
                var notes by rememberSaveable { mutableStateOf(false) }
                ZCard(modifier = Modifier.testTag("quran_ayah_${ayah.number}")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$surahId:${ayah.number}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { bookmarks = progress.toggle(location, book) }, modifier = Modifier.testTag("quran_bookmark_${ayah.number}")) {
                            Text(stringResource(if (location in bookmarks) R.string.quran_saved else R.string.quran_save))
                        }
                    }
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text(ayah.arabic, modifier = Modifier.fillMaxWidth().testTag("quran_arabic_${ayah.number}"),
                                style = ArabicStyle.copy(fontSize = (30 * scale).sp, lineHeight = (54 * scale).sp, textDirection = TextDirection.Rtl),
                                textAlign = TextAlign.Right, color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(stringResource(R.string.quran_translation_label), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary)
                            Text(ayah.translation, modifier = Modifier.testTag("quran_translation_${ayah.number}"),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = (18 * scale).sp, lineHeight = (30 * scale).sp))
                        }
                    }
                    if (ayah.footnotes.isNotBlank()) {
                        TextButton(onClick = { notes = !notes }) { Text(stringResource(R.string.quran_notes)) }
                        if (notes) SelectionContainer {
                            Text(ayah.footnotes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranSources(close: () -> Unit) {
    val uri = LocalUriHandler.current
    HorizonSheet(stringResource(R.string.quran_sources), close) {
        Text(stringResource(R.string.quran_source_arabic), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { runCatching { uri.openUri("https://tanzil.net/docs/Quran_Text") } }) { Text("Tanzil") }
        Text(stringResource(R.string.quran_source_translation), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { runCatching { uri.openUri("https://quranenc.com/en/browse/russian_rwwad/1") } }) { Text("QuranEnc") }
        TextButton(onClick = close, modifier = Modifier.testTag("quran_sources_close")) { Text(stringResource(R.string.quran_close)) }
    }
}
