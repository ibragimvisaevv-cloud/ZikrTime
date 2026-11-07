package com.zikrtime.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.*
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.theme.ArabicStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun QuranScreen(initialSurah: Int = 0, initialAyah: Int = 1) {
    if (initialSurah == 0) QuranReadingTheme { QuranScreenContent(initialSurah, initialAyah) }
    else Box(Modifier.fillMaxSize().testTag("reading_target_${initialSurah}_${initialAyah}")) {
        QuranReadingTheme { QuranScreenContent(initialSurah, initialAyah) }
    }
}

@Composable
private fun QuranScreenContent(initialSurah: Int, initialAyah: Int) {
    StandardSystemBars()
    val context = LocalContext.current
    val nav = LocalNav.current
    val progress = remember { QuranProgress(context) }
    var book by remember { mutableStateOf<QuranBook?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var surahId by rememberSaveable { mutableIntStateOf(initialSurah) }
    var startAyah by rememberSaveable { mutableIntStateOf(initialAyah) }
    var sources by rememberSaveable { mutableStateOf(false) }
    var indexQuery by rememberSaveable { mutableStateOf("") }
    var indexTab by rememberSaveable { mutableIntStateOf(0) }
    val indexList = rememberLazyListState()
    var topicQuery by rememberSaveable { mutableStateOf("") }
    var topicId by rememberSaveable { mutableStateOf("") }
    val topicsList = rememberLazyListState()
    val topicDetailList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val closeTopic: () -> Unit = { focus.clearFocus(); topicId = "" }
    var scale by rememberReadingScale()
    LaunchedEffect(attempt) {
        failed = false
        try { book = QuranRepository.load(context) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    BackHandler(surahId != 0) { surahId = 0 }
    BackHandler(surahId == 0 && indexTab == 3 && topicId.isNotEmpty()) { closeTopic() }
    val content = book
    if (content != null && surahId != 0) {
        key(surahId) {
            QuranReader(content, surahId, startAyah, scale, { scale = it }, progress, { surahId = 0 },
                backLabel = if (indexTab == 3 && topicId.isNotEmpty()) R.string.qti_back_to_topic else R.string.quran_surahs)
        }
    } else Surface(color = quranPaperColors().canvas, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (indexTab == 3 && topicId.isNotEmpty()) closeTopic() else nav.back()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
                Text(stringResource(R.string.quran_title), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f), color = quranPaperColors().ink)
                IconButton(onClick = { sources = true }, modifier = Modifier.testTag("quran_sources")) {
                    Icon(Icons.Filled.Info, stringResource(R.string.quran_sources), tint = quranPaperColors().muted)
                }
            }
            when {
                content != null -> QuranIndex(content, progress, {
                    startAyah = it.ayah
                    surahId = it.surah
                    progress.save(it)
                }, Modifier.weight(1f), indexQuery, { indexQuery = it }, indexTab, {
                    focus.clearFocus()
                    indexTab = it
                }, if (indexTab != 3) indexList else if (topicId.isEmpty()) topicsList else topicDetailList,
                    topicQuery, { topicQuery = it }, topicId, {
                        focus.clearFocus()
                        topicId = it
                        scope.launch { topicDetailList.scrollToItem(0) }
                    }, closeTopic)
                failed -> Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.quran_load_error), textAlign = TextAlign.Center)
                    TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.quran_retry)) }
                }
                else -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
    if (sources) QuranSources { sources = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuranIndex(book: QuranBook, progress: QuranProgress, open: (QuranProgress.Location) -> Unit,
    modifier: Modifier, query: String, onQuery: (String) -> Unit, tab: Int, onTab: (Int) -> Unit,
    listState: LazyListState, topicQuery: String, onTopicQuery: (String) -> Unit,
    topicId: String, onTopic: (String) -> Unit, closeTopic: () -> Unit) {
    val context = LocalContext.current
    val ru = Lang.content(context) == "ru"
    val nav = LocalNav.current
    val selectedTopic = if (tab == 3) QuranTopics.find(topicId) else null
    val filteredTopics = if (tab == 3) QuranTopics.search(topicQuery, book) {
        context.getString(it.titleResource()) + " " + context.getString(it.descriptionResource())
    } else emptyList()
    val colors = quranPaperColors()
    var statistics by rememberSaveable { mutableStateOf(false) }
    val stop = remember(book) { progress.stop(book) }
    val notes = remember(book) { progress.notes(book) }
    val bookmarks = remember(book) { progress.bookmarks(book).sortedWith(compareBy({ it.surah }, { it.ayah })) }
    val bookmarksBySurah = remember(bookmarks) { bookmarks.groupingBy { it.surah }.eachCount() }
    val notesBySurah = remember(notes) { notes.groupingBy { it.surah }.eachCount() }
    val last = remember(book) { progress.last(book) }
    val filtered = remember(book, query) {
        val value = query.trim()
        book.surahs.filter { value.isEmpty() || it.id.toString() == value ||
            it.nameRu.contains(value, true) || it.nameLatin.contains(value, true) || it.nameArabic.contains(value) }
    }
    // A single scroll surface keeps the catalog reachable with large accessibility text.
    LazyColumn(modifier.fillMaxWidth().testTag(if (tab != 3) "quran_index_list" else
        if (selectedTopic == null) "quran_topics_list" else "quran_topics_detail_list"), state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        if (tab != 3) item("book_heading") {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                QuranOrnament(Modifier.width(104.dp).height(16.dp))
                Text(stringResource(R.string.qp_index_title),
                    style = MaterialTheme.typography.headlineMedium, color = colors.ink,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp), textAlign = TextAlign.Center)
                Text(stringResource(R.string.qp_surah_total, book.surahs.size),
                    style = MaterialTheme.typography.labelLarge, color = colors.accent,
                    modifier = Modifier.padding(bottom = 6.dp))
                Text(stringResource(R.string.qp_index_subtitle), style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted, textAlign = TextAlign.Center)
            }
        }
        if (selectedTopic == null) item("listen") {
            OutlinedCard(onClick = { nav.openQuranAudio(0) }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                .testTag("quran_audio_entry"), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = colors.paper), border = BorderStroke(1.dp, colors.rule)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.qaudio_title), style = MaterialTheme.typography.titleMedium, color = colors.accent)
                        Text(stringResource(R.string.qaudio_entry_detail), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.accent)
                }
            }
        }
        if (selectedTopic == null) item("sections") {
            FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(tab == 0, { onTab(0) }, label = { Text(stringResource(R.string.quran_surahs)) },
                    modifier = Modifier.testTag("quran_surahs"))
                FilterChip(tab == 1, { onTab(1) }, label = { Text(stringResource(R.string.quran_bookmarks) + " · " + bookmarks.size) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_quran_bookmark_saved), null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("quran_bookmarks"))
                FilterChip(tab == 2, { onTab(2) }, label = { Text(stringResource(R.string.qf_notes) + " · " + notes.size) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("quran_personal_notes"))
                FilterChip(tab == 3, { onTab(3) }, label = { Text(stringResource(R.string.qti_title)) },
                    modifier = Modifier.testTag("quran_topics"))
            }
        }
        if (tab == 0) {
            item("search") {
                OutlinedTextField(query, onQuery, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQuery("") }, modifier = Modifier.testTag("quran_search_clear")) {
                                Icon(Icons.Filled.Close, stringResource(R.string.qp_clear_search))
                            }
                        }
                    } else null,
                    label = { Text(stringResource(R.string.quran_search)) }, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("quran_search"))
            }
            if (query.isBlank() && last != null) item("continue") {
                QuranContinueCard(book, last, ru, R.string.quran_continue, "quran_continue", open)
                Spacer(Modifier.height(12.dp))
            }
            if (query.isBlank() && stop != null && stop != last) item("stop") {
                QuranContinueCard(book, stop, ru, R.string.qf_saved_stop, "quran_saved_stop", open, progress.note(stop))
                Spacer(Modifier.height(12.dp))
            }
        }
        if (tab != 3) item("statistics") {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { statistics = true }, modifier = Modifier.testTag("quran_statistics_open")) {
                    Text(stringResource(R.string.qf_statistics))
                }
            }
            HorizontalDivider(color = colors.rule)
        }
        when (tab) {
            3 -> quranTopicItems(book, selectedTopic, filteredTopics, topicQuery, onTopicQuery,
                onTopic, closeTopic, open)
            2 -> {
                if (notes.isEmpty()) item("no_notes") { QuranIndexEmpty(stringResource(R.string.qf_no_notes)) }
                items(notes, key = { "note_" + it.key }) { location ->
                    val surah = book.surahs.first { it.id == location.surah }
                    QuranSavedRow(if (ru) surah.nameRu else surah.nameLatin, location,
                        "quran_note_" + location.surah + "_" + location.ayah,
                        onClick = { open(location) }, note = progress.note(location), bookmarked = location in bookmarks)
                }
            }
            1 -> {
                if (bookmarks.isEmpty()) item("no_bookmarks") { QuranIndexEmpty(stringResource(R.string.quran_no_bookmarks)) }
                items(bookmarks, key = { "saved_" + it.key }) { location ->
                    val surah = book.surahs.first { it.id == location.surah }
                    QuranSavedRow(if (ru) surah.nameRu else surah.nameLatin, location,
                        "quran_saved_" + location.surah + "_" + location.ayah, onClick = { open(location) },
                        note = progress.note(location), bookmarked = true)
                }
            }
            else -> {
                if (filtered.isEmpty()) item("no_results") { QuranIndexEmpty(stringResource(R.string.quran_no_results)) }
                items(filtered, key = { "surah_" + it.id }) { surah ->
                    QuranSurahRow(surah, ru, bookmarksBySurah[surah.id] ?: 0, notesBySurah[surah.id] ?: 0) {
                        open(QuranProgress.Location(surah.id, 1))
                    }
                }
            }
        }
    }
    if (statistics) HorizonSheet(stringResource(R.string.qf_statistics), { statistics = false }) {
        QuranStatisticsCard(book, showTitle = false)
        PersonalProgressSetting()
    }
}

@Composable
private fun QuranContinueCard(book: QuranBook, location: QuranProgress.Location, ru: Boolean,
    label: Int, tag: String, open: (QuranProgress.Location) -> Unit, note: String = "") {
    val colors = quranPaperColors()
    val surah = book.surahs.first { it.id == location.surah }
    Surface(onClick = { open(location) }, modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(18.dp), color = colors.paper, border = BorderStroke(1.dp, colors.rule)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(label), color = colors.accent, style = MaterialTheme.typography.labelLarge)
                Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleLarge, color = colors.ink)
                Text(stringResource(R.string.quran_ayah) + " " + location.ayah + "  \u00b7  " + location.key,
                    color = colors.muted, style = MaterialTheme.typography.bodySmall)
                if (note.isNotBlank()) Text(note, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = colors.muted, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun QuranSurahRow(surah: QuranSurah, ru: Boolean, bookmarkCount: Int, noteCount: Int, open: () -> Unit) {
    val colors = quranPaperColors()
    val largeText = LocalConfiguration.current.fontScale > 1.25f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp || largeText
        Row(Modifier.fillMaxWidth().testTag("quran_surah_" + surah.id)
            .clickable(role = Role.Button, onClick = open).semantics(mergeDescendants = true) {}.padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            QuranNumberSeal(surah.id.toString(), Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text(stringResource(R.string.quran_ayah_count, surah.ayahCount),
                    style = MaterialTheme.typography.bodySmall, color = colors.muted)
                if (bookmarkCount > 0 || noteCount > 0) QuranIndexSavedBadges(bookmarkCount, noteCount)
                if (compact) Text(surah.nameArabic,
                    style = ArabicStyle.copy(fontSize = 23.sp, textDirection = TextDirection.Rtl),
                    color = colors.accent, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
            }
            if (!compact) Text(surah.nameArabic,
                style = ArabicStyle.copy(fontSize = 25.sp, textDirection = TextDirection.Rtl),
                color = colors.accent, modifier = Modifier.widthIn(max = 126.dp), textAlign = TextAlign.Right)
        }
    }
    HorizontalDivider(color = colors.rule.copy(alpha = .6f))
}

@Composable
private fun QuranSavedRow(title: String, location: QuranProgress.Location, tag: String,
    onClick: () -> Unit, note: String = "", bookmarked: Boolean = false) {
    val colors = quranPaperColors()
    Column(Modifier.fillMaxWidth().testTag(tag).clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) {}.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
        Text(stringResource(R.string.quran_ayah) + " " + location.ayah + "  \u00b7  " + location.key,
            style = MaterialTheme.typography.labelMedium, color = colors.accent)
        QuranIndexSavedBadges(if (bookmarked) 1 else 0, if (note.isNotBlank()) 1 else 0, showCount = false)
        if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodyLarge, color = colors.muted,
            maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(color = colors.rule.copy(alpha = .6f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuranIndexSavedBadges(bookmarkCount: Int, noteCount: Int, showCount: Boolean = true) {
    val colors = quranPaperColors()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (bookmarkCount > 0) Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(R.drawable.ic_quran_bookmark_saved), null, Modifier.size(14.dp), tint = colors.accent)
            Text(stringResource(R.string.quran_bookmarks) + if (showCount) " · $bookmarkCount" else "",
                style = MaterialTheme.typography.labelSmall, color = colors.accent)
        }
        if (noteCount > 0) Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Edit, null, Modifier.size(14.dp), tint = colors.accent)
            Text(stringResource(R.string.qf_notes) + if (showCount) " · $noteCount" else "",
                style = MaterialTheme.typography.labelSmall, color = colors.accent)
        }
    }
}

@Composable
private fun QuranIndexEmpty(message: String) {
    val colors = quranPaperColors()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuranOrnament(Modifier.width(80.dp).height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = colors.muted, textAlign = TextAlign.Center)
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
