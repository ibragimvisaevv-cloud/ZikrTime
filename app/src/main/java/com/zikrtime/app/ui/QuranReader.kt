package com.zikrtime.app.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QuranReader(book: QuranBook, surahId: Int, firstAyah: Int, scale: Float,
    onScale: (Float) -> Unit, progress: QuranProgress, showIndex: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.readerActivity()
    val configuration = LocalConfiguration.current
    val colors = quranPaperColors()
    val ru = Lang.content(context) == "ru"
    val surah = remember(book, surahId) { book.surahs.first { it.id == surahId } }
    val verses = remember(book, surahId) { book.verses(surahId) }
    var blockAnchor by rememberSaveable { mutableIntStateOf(firstAyah) }
    val blocks = remember(verses, blockAnchor) { QuranReading.blocks(verses, blockAnchor) }
    var wholeSurah by rememberSaveable { mutableStateOf(progress.wholeSurah) }
    val verseList = rememberLazyListState((firstAyah - 1).coerceIn(verses.indices))
    val wholeList = rememberLazyListState(QuranReading.blockIndex(blocks, firstAyah))
    val list = if (wholeSurah) wholeList else verseList
    var visibleAyah by rememberSaveable { mutableIntStateOf(firstAyah) }
    var selectedAyah by rememberSaveable { mutableIntStateOf(0) }
    // A sheet owns an immutable target. Scrolling, layout and rotation cannot retarget a note/copy.
    var actionAyah by rememberSaveable { mutableIntStateOf(firstAyah) }
    val currentAyah = selectedAyah.takeIf { it > 0 } ?: visibleAyah
    val geometry = remember(wholeSurah, blocks) { QuranTextGeometry() }
    val tracker = remember(verses) { QuranReadTracker(verses) }
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val savedLabel = stringResource(R.string.qt_bookmark_added)
    val removedLabel = stringResource(R.string.qt_bookmark_removed)
    val copiedLabel = stringResource(R.string.qp_copied)
    val noteSavedLabel = stringResource(R.string.qp_note_saved)
    val noteDeletedLabel = stringResource(R.string.qp_note_deleted)
    var bookmarks by remember { mutableStateOf(progress.bookmarks(book)) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf("") }
    var keepAwake by rememberSaveable { mutableStateOf(progress.keepAwake) }
    val originalOrientation = rememberSaveable { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    var orientation by rememberSaveable { mutableIntStateOf(-100) }
    val session = rememberQuranSession(progress, sheet.isEmpty()) { elapsed ->
        geometry.firstAyah()?.let { visibleAyah = it }
        if (elapsed > 0 && !list.isScrollInProgress && Store.personalProgressEnabled) {
            val observed = tracker.observe(elapsed, geometry.visibleText())
            QuranStatistics.observe(LocalDate.now(), surahId, observed, surah.ayahCount)
        } else tracker.pause()
    }
    val openSheet: (String) -> Unit = { session.tick(false); tracker.pause(); sheet = it }
    val openAyahSheet: (Int, String) -> Unit = { number, destination ->
        actionAyah = number
        selectedAyah = number
        openSheet(destination)
    }
    val choose: (Int, Boolean) -> Unit = { number, actions ->
        session.tick(false)
        tracker.pause()
        selectedAyah = number
        if (actions) openAyahSheet(number, "actions")
    }
    val bookmark: (Int) -> Unit = { number ->
        val location = QuranProgress.Location(surahId, number)
        bookmarks = progress.toggleReadingPlace(location, book)
        scope.launch { snackbar.showSnackbar((if (location in bookmarks) savedLabel else removedLabel) + " · " + location.key) }
    }
    val quickBookmark: () -> Unit = {
        val number = selectedAyah.takeIf { it > 0 } ?: geometry.firstAyah() ?: visibleAyah
        selectedAyah = number
        bookmark(number)
    }
    BackHandler(sheet.isEmpty() && (selectedAyah > 0 || fullscreen)) {
        if (selectedAyah > 0) selectedAyah = 0 else fullscreen = false
    }

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
    LaunchedEffect(list, geometry) {
        var moved = false
        snapshotFlow { list.isScrollInProgress to list.layoutInfo.visibleItemsInfo.map { it.index to it.offset } }
            .distinctUntilChanged().collect { (scrolling, _) ->
            geometry.firstAyah()?.let { visibleAyah = it }
            if (scrolling) {
                if (sheet.isEmpty()) selectedAyah = 0
                moved = true
                tracker.pause()
            }
            else if (moved) {
                withFrameNanos { }
                geometry.firstAyah()?.let { visibleAyah = it; progress.save(QuranProgress.Location(surahId, it)) }
                moved = false
            }
        }
    }
    val jumpTo: (Int) -> Unit = { number ->
        scope.launch {
            focus.clearFocus()
            tracker.pause()
            selectedAyah = number
            if (wholeSurah) {
                blockAnchor = number
                wholeList.requestScrollToItem(QuranReading.blockIndex(QuranReading.blocks(verses, number), number))
            } else verseList.scrollToItem(number - 1)
            progress.save(QuranProgress.Location(surahId, number))
            sheet = ""
        }
    }
    val switchMode: (Boolean) -> Unit = { reading ->
        if (reading != wholeSurah) {
            val number = currentAyah
            tracker.pause()
            if (reading) {
                blockAnchor = number
                wholeList.requestScrollToItem(QuranReading.blockIndex(QuranReading.blocks(verses, number), number))
            } else verseList.requestScrollToItem(number - 1)
            wholeSurah = reading
            selectedAyah = 0
            progress.wholeSurah = reading
            progress.save(QuranProgress.Location(surahId, number))
        }
    }
    val compact = fullscreen || configuration.screenHeightDp < 560 || configuration.fontScale > 1.25f
    Box(Modifier.fillMaxSize().background(colors.canvas)
        .windowInsetsPadding(if (fullscreen) WindowInsets.displayCutout else WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (fullscreen) {
                    IconButton(onClick = { fullscreen = false }, modifier = Modifier.testTag("quran_exit_fullscreen")) {
                        Text("⛶", fontSize = 26.sp, modifier = Modifier.semanticsDescription(stringResource(R.string.qf_controls)))
                    }
                } else {
                    IconButton(onClick = showIndex, modifier = Modifier.testTag("quran_surahs")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.quran_surahs))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.ink)
                    if (!compact) Text(stringResource(R.string.qp_selected_ayah, surahId, currentAyah),
                        style = MaterialTheme.typography.labelMedium, color = colors.muted)
                }
                QuranQuickBookmark(QuranProgress.Location(surahId, currentAyah) in bookmarks, quickBookmark)
                if (!fullscreen) IconButton(onClick = { fullscreen = true }, modifier = Modifier.testTag("quran_fullscreen")) {
                    Text("⛶", fontSize = 26.sp, modifier = Modifier.semanticsDescription(stringResource(R.string.qf_fullscreen)))
                }
                IconButton(onClick = { openSheet("tools") }, modifier = Modifier.testTag("quran_tools")) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.qf_tools))
                }
            }
            if (!fullscreen) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = wholeSurah, onClick = { switchMode(true) },
                        modifier = Modifier.weight(1f).then(if (!wholeSurah) Modifier.testTag("quran_read_mode") else Modifier),
                        label = { Text(stringResource(R.string.qp_reading_mode),
                            Modifier.fillMaxWidth().testTag("quran_mode_read"), textAlign = TextAlign.Center) })
                    FilterChip(selected = !wholeSurah, onClick = { switchMode(false) },
                        modifier = Modifier.weight(1f).then(if (wholeSurah) Modifier.testTag("quran_read_mode") else Modifier),
                        label = { Text(stringResource(R.string.qp_translation_mode),
                            Modifier.fillMaxWidth().testTag("quran_mode_translation"), textAlign = TextAlign.Center) })
                }
            }
            if (!compact && (session.timer.remaining > 0 || session.timer.paused)) TextButton(
                onClick = { openSheet("timer") }, modifier = Modifier.testTag("quran_timer_status")) {
                Text(stringResource(if (session.timer.paused) R.string.qf_timer_paused else R.string.qf_timer_remaining, readingClock(session.timer.remaining)))
            }
            QuranPageFrame(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 18.dp)) {
                    if (!compact) QuranSurahHeading(surah, Modifier.fillMaxWidth())
                    if (wholeSurah) {
                        // Source text and offsets stay exact. The frame has no fake Mushaf page numbers.
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f)
                                .onGloballyPositioned { geometry.viewport = it }
                                .testTag("quran_whole_surah"),
                            state = wholeList,
                            contentPadding = PaddingValues(top = if (compact) 12.dp else 4.dp, bottom = 28.dp),
                        ) {
                            items(blocks, key = { it.firstAyah }) { block ->
                                QuranTouchText(block.text, block.ranges, selectedAyah, geometry,
                                    ArabicStyle.copy(fontSize = (30 * scale).sp, textDirection = TextDirection.Rtl, textAlign = TextAlign.Justify),
                                    "quran_whole_" + block.firstAyah, Modifier.fillMaxWidth(), choose)
                            }
                        }
                    } else LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).onGloballyPositioned { geometry.viewport = it },
                        state = verseList, contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
                    ) {
                        items(verses, key = { it.number }) { ayah ->
                            val location = QuranProgress.Location(surahId, ayah.number)
                            var notes by rememberSaveable { mutableStateOf(false) }
                            Column(Modifier.fillMaxWidth().testTag("quran_ayah_" + ayah.number)
                                .padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = { openAyahSheet(ayah.number, "actions") },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        border = BorderStroke(1.dp, colors.rule),
                                        modifier = Modifier.testTag("quran_ayah_actions_" + ayah.number)
                                            .semanticsDescription(stringResource(R.string.qp_ayah_menu, ayah.number))) {
                                        Text("$surahId:" + ayah.number, style = MaterialTheme.typography.labelLarge)
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Filled.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { bookmark(ayah.number) }, modifier = Modifier.testTag("quran_bookmark_" + ayah.number)) {
                                        Icon(painterResource(if (location in bookmarks) R.drawable.ic_quran_bookmark_saved else R.drawable.ic_quran_bookmark),
                                            stringResource(if (location in bookmarks) R.string.qt_remove_bookmark else R.string.qt_save_place))
                                    }
                                }
                                val ranges = remember(ayah) { listOf(QuranTextRange(ayah.number, 0, ayah.arabic.length, ayah.arabic.length)) }
                                QuranTouchText(ayah.arabic, ranges, selectedAyah, geometry,
                                    ArabicStyle.copy(fontSize = (30 * scale).sp, textDirection = TextDirection.Rtl),
                                    "quran_arabic_" + ayah.number, Modifier.fillMaxWidth(), choose)
                                Text(stringResource(R.string.quran_translation_label), style = MaterialTheme.typography.labelSmall, color = colors.accent)
                                Text(ayah.translation, modifier = Modifier.testTag("quran_translation_" + ayah.number)
                                    .combinedClickable(onClick = { choose(ayah.number, false) }, onLongClick = { choose(ayah.number, true) }),
                                    color = colors.ink,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = (18 * scale).sp, lineHeight = (30 * scale).sp))
                                if (ayah.footnotes.isNotBlank()) {
                                    TextButton(onClick = { notes = !notes }) { Text(stringResource(R.string.quran_notes)) }
                                    if (notes) SelectionContainer {
                                        Text(ayah.footnotes, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                                    }
                                }
                                HorizontalDivider(Modifier.padding(top = 14.dp), color = colors.rule)
                            }
                        }
                    }
                }
            }
            if (snackbar.currentSnackbarData != null) SnackbarHost(snackbar)
            if (selectedAyah > 0) QuranSelectionBar(surahId, selectedAyah,
                QuranProgress.Location(surahId, selectedAyah) in bookmarks,
                clear = { selectedAyah = 0 },
                note = { openAyahSheet(selectedAyah, "note") },
                bookmark = { bookmark(selectedAyah) },
                copy = { openAyahSheet(selectedAyah, "copy") },
                actions = { openAyahSheet(selectedAyah, "actions") },
                compact = compact)
            else if (!compact) Text(stringResource(R.string.qp_selection_hint),
                Modifier.fillMaxWidth().padding(bottom = 6.dp), style = MaterialTheme.typography.labelSmall,
                color = colors.muted, textAlign = TextAlign.Center)
        }
    }
    when (sheet) {
        "tools" -> HorizonSheet(stringResource(R.string.qf_tools), { sheet = "" }) {
            Text(surahId.toString() + ":" + currentAyah, style = MaterialTheme.typography.titleLarge)
            ReaderAction(R.string.qf_jump, "quran_jump_open") { sheet = "jump" }
            ReaderAction(R.string.qt_note, "quran_note_open") { openAyahSheet(currentAyah, "note") }
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
        "actions" -> HorizonSheet((if (ru) surah.nameRu else surah.nameLatin) + " · " + surahId + ":" + actionAyah, { sheet = "" }) {
            ReaderAction(R.string.qt_note, "quran_note_open") { sheet = "note" }
            ReaderAction(if (QuranProgress.Location(surahId, actionAyah) in bookmarks) R.string.qt_remove_bookmark else R.string.qt_save_place,
                "quran_action_bookmark") { bookmark(actionAyah); sheet = "" }
            ReaderAction(R.string.qt_copy, "quran_copy_ayah") { sheet = "copy" }
            TextButton(onClick = { sheet = "" }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.qp_action_close))
            }
        }
        "copy" -> HorizonSheet(stringResource(R.string.qp_copy_title), { sheet = "" }) {
            Text((if (ru) surah.nameRu else surah.nameLatin) + " · " + surahId + ":" + actionAyah,
                modifier = Modifier.testTag("quran_copy_location"), style = MaterialTheme.typography.titleMedium)
            val ayah = verses.first { it.number == actionAyah }
            val copy: (String) -> Unit = { text ->
                clipboard.setText(AnnotatedString(text))
                sheet = ""
                scope.launch { snackbar.showSnackbar(copiedLabel) }
            }
            ReaderAction(R.string.qp_copy_arabic, "quran_copy_arabic") { copy(ayah.arabic) }
            ReaderAction(R.string.qp_copy_translation, "quran_copy_translation") { copy(ayah.translation) }
            ReaderAction(R.string.qp_copy_both, "quran_copy_both") { copy(ayah.arabic + "\n\n" + ayah.translation) }
        }
        "note" -> QuranNoteSheet(if (ru) surah.nameRu else surah.nameLatin,
            QuranProgress.Location(surahId, actionAyah), progress,
            onSaved = { deleted -> scope.launch { snackbar.showSnackbar(if (deleted) noteDeletedLabel else noteSavedLabel) } },
            close = { sheet = "" })
        "timer" -> QuranTimerSheet(progress, session) { sheet = "" }
        "statistics" -> HorizonSheet(stringResource(R.string.qf_statistics), { sheet = "" }) {
            PersonalProgressSetting()
            QuranStatisticsCard(book)
        }
        "size" -> HorizonSheet(stringResource(R.string.reader_text_size), { sheet = "" }) {
            Text(book.verses(1).first().arabic, style = ArabicStyle.copy(fontSize = (30 * scale).sp))
            Slider(scale, onScale, valueRange = .85f..1.5f, steps = 12, modifier = Modifier.testTag("reader_scale"))
            Text((scale * 100).toInt().toString() + "%")
            PrimaryButton(stringResource(R.string.action_done), onClick = { sheet = "" })
        }
        "sources" -> QuranSources { sheet = "" }
    }
    if (session.timer.finished && sheet.isEmpty()) HorizonDialog(
        onDismissRequest = { session.changeTimer(QuranReadingTimer()) },
        title = { Text(stringResource(R.string.qf_timer_finished)) },
        text = { Text(stringResource(R.string.qf_timer_finished_text)) },
        confirmButton = { TextButton(onClick = { session.changeTimer(QuranReadingTimer()) }, modifier = Modifier.testTag("quran_timer_continue")) {
            Text(stringResource(R.string.qf_keep_reading)) } },
        dismissButton = { TextButton(onClick = { session.changeTimer(QuranReadingTimer()); showIndex() }) { Text(stringResource(R.string.action_done)) } })
}

private fun Modifier.semanticsDescription(description: String): Modifier = semantics { contentDescription = description }

@Composable
private fun QuranQuickBookmark(saved: Boolean, save: () -> Unit) {
    IconButton(onClick = save, modifier = Modifier.testTag("quran_quick_bookmark")) {
        Icon(painterResource(if (saved) R.drawable.ic_quran_bookmark_saved else R.drawable.ic_quran_bookmark),
            stringResource(if (saved) R.string.qt_remove_bookmark else R.string.qt_save_place))
    }
}

