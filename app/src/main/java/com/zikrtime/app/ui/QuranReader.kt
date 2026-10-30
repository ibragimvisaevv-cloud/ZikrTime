package com.zikrtime.app.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.zikrtime.app.R
import com.zikrtime.app.data.*
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.theme.ArabicStyle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun QuranReader(book: QuranBook, surahId: Int, firstAyah: Int, scale: Float,
    onScale: (Float) -> Unit, progress: QuranProgress, showIndex: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.readerActivity()
    val configuration = LocalConfiguration.current
    val ru = Lang.content(context) == "ru"
    val surah = remember(book, surahId) { book.surahs.first { it.id == surahId } }
    val verses = remember(book, surahId) { book.verses(surahId) }
    var blockAnchor by rememberSaveable { mutableIntStateOf(firstAyah) }
    val blocks = remember(verses, blockAnchor) { QuranReading.blocks(verses, blockAnchor) }
    var wholeSurah by rememberSaveable { mutableStateOf(progress.wholeSurah) }
    val verseList = rememberLazyListState((firstAyah - 1).coerceIn(verses.indices))
    val wholeList = rememberLazyListState(QuranReading.blockIndex(blocks, firstAyah))
    val list = if (wholeSurah) wholeList else verseList
    val currentAyah by remember(wholeSurah, blocks, list) { derivedStateOf {
        if (wholeSurah) blocks[list.firstVisibleItemIndex.coerceIn(blocks.indices)].firstAyah else list.firstVisibleItemIndex + 1
    } }
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var bookmarks by remember { mutableStateOf(progress.bookmarks(book)) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf("") }
    var keepAwake by rememberSaveable { mutableStateOf(progress.keepAwake) }
    val originalOrientation = rememberSaveable { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    var orientation by rememberSaveable { mutableIntStateOf(-100) }
    val session = rememberQuranSession(progress, sheet.isEmpty())
    val openSheet: (String) -> Unit = { session.tick(false); sheet = it }
    BackHandler(fullscreen && sheet.isEmpty()) { fullscreen = false }

    DisposableEffect(activity, fullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(activity, orientation) { if (orientation != -100) activity?.requestedOrientation = orientation }
    DisposableEffect(activity) {
        onDispose { if (activity?.isChangingConfigurations == false) activity.requestedOrientation = originalOrientation }
    }
    DisposableEffect(view, keepAwake) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = previous }
    }
    LaunchedEffect(list, surahId, wholeSurah, blocks) {
        snapshotFlow { list.isScrollInProgress to list.firstVisibleItemIndex }.distinctUntilChanged().collect { (scrolling, index) ->
            if (scrolling) progress.save(QuranProgress.Location(surahId,
                if (wholeSurah) blocks[index.coerceIn(blocks.indices)].firstAyah else index + 1))
        }
    }
    val jumpTo: (Int) -> Unit = { number ->
        scope.launch {
            focus.clearFocus()
            if (wholeSurah) {
                blockAnchor = number
                wholeList.requestScrollToItem(QuranReading.blockIndex(QuranReading.blocks(verses, number), number))
            } else verseList.scrollToItem(number - 1)
            progress.save(QuranProgress.Location(surahId, number))
            sheet = ""
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(if (fullscreen) WindowInsets.displayCutout else WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().padding(horizontal = if (fullscreen) 8.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!fullscreen) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = showIndex, modifier = Modifier.testTag("quran_surahs")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.quran_surahs))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleMedium)
                        Text(surahId.toString() + ":" + currentAyah + " · " + surah.ayahCount, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { fullscreen = true }, modifier = Modifier.testTag("quran_fullscreen")) {
                        Text("⛶", fontSize = 28.sp, modifier = Modifier.semanticsDescription(stringResource(R.string.qf_fullscreen)))
                    }
                    IconButton(onClick = { openSheet("tools") }, modifier = Modifier.testTag("quran_tools")) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.qf_tools))
                    }
                }
                FilledTonalButton(onClick = {
                    val number = currentAyah
                    if (!wholeSurah) {
                        blockAnchor = number
                        wholeList.requestScrollToItem(QuranReading.blockIndex(QuranReading.blocks(verses, number), number))
                    } else verseList.requestScrollToItem(number - 1)
                    wholeSurah = !wholeSurah
                    progress.wholeSurah = wholeSurah
                    progress.save(QuranProgress.Location(surahId, number))
                }, modifier = Modifier.fillMaxWidth().testTag("quran_read_mode")) {
                    Text(stringResource(if (wholeSurah) R.string.quran_read_with_translation else R.string.quran_read_whole))
                }
                if (session.timer.remaining > 0 || session.timer.paused) TextButton(
                    onClick = { openSheet("timer") }, modifier = Modifier.testTag("quran_timer_status")) {
                    Text(stringResource(if (session.timer.paused) R.string.qf_timer_paused else R.string.qf_timer_remaining, readingClock(session.timer.remaining)))
                }
            }
        if (wholeSurah) {
            // One uninterrupted reading surface; no cards, translations or next-page controls.
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .testTag("quran_whole_surah"),
                state = wholeList,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 90.dp),
            ) {
                items(blocks, key = { it.firstAyah }) { block ->
                    SelectionContainer {
                        Text(block.text,
                            modifier = Modifier.fillMaxWidth().testTag("quran_whole_" + block.firstAyah),
                            style = ArabicStyle.copy(fontSize = (30 * scale).sp, textDirection = TextDirection.Rtl),
                            textAlign = TextAlign.Right, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else LazyColumn(Modifier.fillMaxWidth().weight(1f), state = verseList, verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)) {
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
                                style = ArabicStyle.copy(fontSize = (30 * scale).sp, textDirection = TextDirection.Rtl),
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
        if (fullscreen) Surface(tonalElevation = 6.dp, shadowElevation = 4.dp, shape = RoundedCornerShape(28.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { fullscreen = false }, modifier = Modifier.testTag("quran_exit_fullscreen")) {
                    Text(stringResource(R.string.qf_controls))
                }
                IconButton(onClick = { openSheet("tools") }, modifier = Modifier.testTag("quran_tools")) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.qf_tools))
                }
            }
        }
    }
    when (sheet) {
        "tools" -> HorizonSheet(stringResource(R.string.qf_tools), { sheet = "" }) {
            Text(surahId.toString() + ":" + currentAyah, style = MaterialTheme.typography.titleLarge)
            ReaderAction(R.string.qf_jump, "quran_jump_open") { sheet = "jump" }
            ReaderAction(R.string.qf_stop, "quran_stop_open") { sheet = "stop" }
            ReaderAction(R.string.qf_mark_read, "quran_mark_open") { sheet = "mark" }
            ReaderAction(R.string.qf_timer, "quran_timer_open") { sheet = "timer" }
            ReaderAction(R.string.qf_statistics, "quran_statistics_open") { sheet = "statistics" }
            ReaderAction(R.string.qf_rotate, "quran_rotate") {
                orientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                sheet = ""
            }
            ReaderAction(R.string.reader_text_size, "reader_options") { sheet = "size" }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.qf_keep_awake), Modifier.weight(1f))
                Switch(keepAwake, { keepAwake = it; progress.keepAwake = it }, Modifier.testTag("quran_keep_awake"))
            }
            FeedbackButton("quran_" + surahId + "_" + currentAyah)
            ReaderAction(R.string.quran_sources, "quran_sources") { sheet = "sources" }
        }
        "jump" -> QuranJumpSheet(surah.ayahCount, currentAyah, { sheet = "" }, jumpTo)
        "stop" -> QuranStopSheet(book, surahId, currentAyah, progress) { sheet = "" }
        "mark" -> QuranMarkSheet(surahId, currentAyah, surah.ayahCount) { sheet = "" }
        "timer" -> QuranTimerSheet(progress, session) { sheet = "" }
        "statistics" -> HorizonSheet(stringResource(R.string.qf_statistics), { sheet = "" }) {
            PersonalProgressSetting()
            QuranStatisticsCard(book)
        }
        "size" -> HorizonSheet(stringResource(R.string.reader_text_size), { sheet = "" }) {
            Text("بِسْمِ اللَّهِ", style = ArabicStyle.copy(fontSize = (30 * scale).sp))
            Slider(scale, onScale, valueRange = .85f..1.5f, steps = 12, modifier = Modifier.testTag("reader_scale"))
            Text((scale * 100).toInt().toString() + "%")
            PrimaryButton(stringResource(R.string.action_done), onClick = { sheet = "" })
        }
        "sources" -> QuranSources { sheet = "" }
    }
    if (session.timer.finished && sheet.isEmpty()) HorizonDialog(
        onDismissRequest = { session.setTimer(QuranReadingTimer()) },
        title = { Text(stringResource(R.string.qf_timer_finished)) },
        text = { Text(stringResource(R.string.qf_timer_finished_text)) },
        confirmButton = { TextButton(onClick = { session.setTimer(QuranReadingTimer()) }, modifier = Modifier.testTag("quran_timer_continue")) {
            Text(stringResource(R.string.qf_keep_reading)) } },
        dismissButton = { TextButton(onClick = { session.setTimer(QuranReadingTimer()); showIndex() }) { Text(stringResource(R.string.action_done)) } })
}

private fun Modifier.semanticsDescription(description: String): Modifier = semantics { contentDescription = description }
