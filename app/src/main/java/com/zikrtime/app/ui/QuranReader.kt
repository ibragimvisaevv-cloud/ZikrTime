package com.zikrtime.app.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
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
import com.zikrtime.app.BuildConfig
import com.zikrtime.app.audio.QuranAudioPlayer
import com.zikrtime.app.audio.QuranAudioRepository
import com.zikrtime.app.audio.QuranPlaybackService
import com.zikrtime.app.audio.QuranReadingPhrase
import com.zikrtime.app.audio.QuranWordTimings
import com.zikrtime.app.audio.quranWordRange
import com.zikrtime.app.data.*
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.theme.ArabicStyle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuranReader(book: QuranBook, surahId: Int, firstAyah: Int, scale: Float,
    onScale: (Float) -> Unit, progress: QuranProgress, showIndex: () -> Unit,
    backLabel: Int = R.string.quran_surahs) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colors = quranPaperColors()
    val ru = Lang.content(context) == "ru"
    val surah = remember(book, surahId) { book.surahs.first { it.id == surahId } }
    val verses = remember(book, surahId) { book.verses(surahId) }
    val startAyah = firstAyah.coerceIn(1, surah.ayahCount)
    var blockAnchor by rememberSaveable { mutableIntStateOf(startAyah) }
    val blocks = remember(verses, blockAnchor) { QuranReading.blocks(verses, blockAnchor) }
    var wholeSurah by rememberSaveable { mutableStateOf(progress.wholeSurah) }
    val verseList = rememberLazyListState(startAyah - 1)
    val wholeList = rememberLazyListState(QuranReading.blockIndex(blocks, startAyah))
    val list = if (wholeSurah) wholeList else verseList
    var visibleAyah by rememberSaveable { mutableIntStateOf(startAyah) }
    var selectedAyah by rememberSaveable(surahId) { mutableIntStateOf(0) }
    var selectedWord by rememberSaveable(surahId) { mutableStateOf<Int?>(null) }
    // A sheet owns an immutable target. Scrolling, layout and rotation cannot retarget a note/copy.
    var actionAyah by rememberSaveable { mutableIntStateOf(startAyah) }
    val geometry = remember(wholeSurah, blocks) { QuranTextGeometry() }
    if (BuildConfig.DEBUG) DisposableEffect(surahId, geometry) {
        QuranWordProbe.surah = surahId
        QuranWordProbe.geometry = geometry
        onDispose { if (QuranWordProbe.geometry === geometry) QuranWordProbe.geometry = null }
    }
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
    fun loadNotes(): Map<Int, String> = progress.notes(book).filter { it.surah == surahId }
        .associate { it.ayah to progress.note(it) }.filterValues { it.isNotBlank() }
    var noteTexts by remember(progress, book, surahId) { mutableStateOf(loadNotes()) }
    val savedMarks = remember(bookmarks, noteTexts, surahId) {
        (bookmarks.filter { it.surah == surahId }.map { it.ayah } + noteTexts.keys).distinct().sorted().map { number ->
            QuranSavedMark(number, QuranProgress.Location(surahId, number) in bookmarks, noteTexts[number].orEmpty())
        }
    }
    val savedByAyah = remember(savedMarks) { savedMarks.associateBy { it.ayah } }
    val savedAyahs = remember(savedMarks) { savedByAyah.keys }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf("") }
    var audioOpened by rememberSaveable { mutableStateOf(false) }
    val playback = QuranReaderAudioConnection(audioOpened)
    val reciter by QuranAudioRepository.selectedReciter.collectAsState()
    val activeHere = playback.connected && playback.surahId == surahId && playback.reciterId == reciter.id
    val playingAyah = playback.playingAyah.takeIf { activeHere }
    var followAudio by rememberSaveable(surahId) { mutableStateOf(true) }
    var showPlayer by rememberSaveable(surahId) { mutableStateOf(false) }
    var startRequested by remember(surahId) { mutableStateOf(false) }
    var tapWhileConnecting by remember(surahId) { mutableStateOf(false) }
    var repeats by rememberSaveable(surahId) { mutableIntStateOf(1) }
    var speed by rememberSaveable(surahId) { mutableFloatStateOf(1f) }
    var continueAfter by rememberSaveable(surahId) { mutableStateOf(true) }
    val activeAyah = if (activeHere) playback.positionAyah ?: playback.clipAyah else null
    var lastPlaybackAyah by rememberSaveable(surahId, reciter.id) { mutableIntStateOf(0) }
    var lastSpokenWord by rememberSaveable(surahId, reciter.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(activeHere, activeAyah, playback.playing, playback.phraseWord) {
        if (activeHere && activeAyah != null) {
            if (lastPlaybackAyah != activeAyah || playback.phraseWord != null) lastSpokenWord = playback.phraseWord
            lastPlaybackAyah = activeAyah
        }
    }
    val target = if (followAudio && activeHere) activeAyah ?: lastPlaybackAyah.takeIf { it > 0 } ?: visibleAyah
        else selectedAyah.takeIf { it > 0 } ?: visibleAyah
    val currentAyah = if (showPlayer || activeHere) target else selectedAyah.takeIf { it > 0 } ?: visibleAyah
    val playerVisible = showPlayer || (activeHere && !playback.ended && activeAyah != null)
    val liveHere = activeHere && !playback.ended && (playback.playing || playback.buffering && playback.playWhenReady)
    LaunchedEffect(liveHere, playback.error, startRequested) {
        if (liveHere) followAudio = true
        if (liveHere || playback.error != null) startRequested = false
    }
    val pendingAyah = selectedAyah.takeIf { !followAudio && !liveHere && it > 0 }
    val pausedAyah = activeAyah.takeIf { activeHere && !playback.playWhenReady && !playback.ended }
    val focusedAyah = if (liveHere) playingAyah ?: activeAyah else pendingAyah ?: pausedAyah
    val focusedWord = if (pendingAyah != null) selectedWord
        else playback.positionWord ?: lastSpokenWord.takeIf { focusedAyah == lastPlaybackAyah }
    val highlight = rememberReaderHighlight(surahId, verses, reciter.id, playback, playingAyah,
        liveHere, pendingAyah, focusedAyah, focusedWord, selectedWord)
    val activeTarget = activeHere && target == activeAyah
    val actualRepeats = if (activeTarget && playback.clipAyah != null) playback.repeatCount else repeats
    val actualSpeed = if (activeTarget) playback.speed else speed
    val actualContinue = if (activeTarget && playback.clipAyah != null) playback.continueAfter else continueAfter
    fun configure(count: Int, rate: Float, advance: Boolean) {
        repeats = count
        speed = rate
        continueAfter = advance
        if (activeTarget && playback.clipAyah != null) QuranAudioPlayer.configureAyah(count, rate, advance)
        else if (activeTarget && playback.playWhenReady && (count != 1 || !advance)) {
            selectedAyah = target
            followAudio = true
            QuranAudioPlayer.playAyah(surahId, target, count, rate, advance)
        } else if (activeTarget) QuranAudioPlayer.setSpeed(rate)
    }
    FollowReaderPassage(list, geometry, blocks, wholeSurah, surah.ayahCount, reciter.id, surahId, playingAyah,
        followAudio, sheet, playback.playingWord, highlight.phrase)
    var keepAwake by rememberSaveable { mutableStateOf(progress.keepAwake) }
    var orientation by rememberSaveable { mutableIntStateOf(-100) }
    val followingPlayback = playback.playing && activeHere
    val session = rememberQuranSession(progress, sheet.isEmpty() && !followingPlayback) { elapsed ->
        geometry.firstAyah()?.let { visibleAyah = it }
        if (elapsed > 0 && !list.isScrollInProgress && Store.personalProgressEnabled) {
            val observed = tracker.observe(elapsed, geometry.visibleText())
            QuranStatistics.observe(LocalDate.now(), surahId, observed, surah.ayahCount)
        } else tracker.pause()
    }
    val openSheet: (String) -> Unit = { session.tick(false); tracker.pause(); sheet = it }
    val openAyahSheet: (Int, String) -> Unit = { number, destination ->
        actionAyah = number
        openSheet(destination)
    }
    fun applyWordTap(number: Int, word: Int?) {
        // Read at gesture time so a second quick tap cannot use the previous frame's position.
        val now = QuranAudioPlayer.state.value
        if (followAudio && now.connected && now.surahId == surahId) {
            speed = now.speed
            if (now.ayah != null) {
                repeats = now.repeatCount
                continueAfter = now.continueAfter
            }
        }
        val moving = now.connected && !now.ended && now.error == null &&
            (now.isPlaying || now.isBuffering && now.playWhenReady)
        followAudio = moving
        if (moving) {
            if (!QuranAudioPlayer.seekToWord(surahId, number, word)) {
                startRequested = true
                QuranAudioPlayer.playAyahFromWord(surahId, number, word, now.repeatCount, now.speed,
                    if (now.ayah == null) true else now.continueAfter)
            }
        } else if (startRequested) {
            // Replace an explicit Play request that is still waiting for the controller.
            QuranAudioPlayer.playAyahFromWord(surahId, number, word, actualRepeats, actualSpeed, actualContinue)
        }
    }
    LaunchedEffect(playback.connected, tapWhileConnecting) {
        if (tapWhileConnecting && playback.connected) {
            tapWhileConnecting = false
            if (selectedAyah > 0) applyWordTap(selectedAyah, selectedWord)
        }
    }
    val choose: (Int, Int?, Boolean) -> Unit = { number, word, longPress ->
        if (longPress) openAyahSheet(number, "actions")
        else {
            session.tick(false)
            tracker.pause()
            selectedAyah = number
            selectedWord = word
            audioOpened = true
            showPlayer = true
            tapWhileConnecting = !startRequested && !QuranAudioPlayer.state.value.connected && QuranPlaybackService.hasActiveSession
            applyWordTap(number, word)
        }
    }
    val playOrPause: () -> Unit = {
        tapWhileConnecting = false
        if (startRequested || activeTarget && playback.playWhenReady && !playback.ended && playback.error == null) {
            startRequested = false
            QuranAudioPlayer.pause()
        } else {
            startRequested = true
            selectedAyah = target
            followAudio = pendingAyah == null
            showPlayer = true
            if (pendingAyah != null) {
                if (activeHere && playback.clipAyah == null && !playback.ended && actualRepeats == 1 && actualContinue &&
                    QuranAudioPlayer.seekToWord(surahId, target, selectedWord)) QuranAudioPlayer.togglePlayPause()
                else QuranAudioPlayer.playAyahFromWord(surahId, target, selectedWord, actualRepeats, actualSpeed, actualContinue)
            } else if (activeTarget && !playback.ended && playback.error == null &&
                (playback.clipAyah != null || actualRepeats == 1 && actualContinue)) QuranAudioPlayer.togglePlayPause()
            else QuranAudioPlayer.playAyah(surahId, target, actualRepeats, actualSpeed, actualContinue)
        }
    }
    val closePlayer: () -> Unit = {
        if (activeHere || startRequested) QuranAudioPlayer.stop()
        startRequested = false
        tapWhileConnecting = false
        showPlayer = false
        selectedAyah = 0
        selectedWord = null
        lastSpokenWord = null
        lastPlaybackAyah = 0
        followAudio = true
    }
    val bookmark: (Int) -> Unit = { number ->
        val location = QuranProgress.Location(surahId, number)
        bookmarks = progress.toggleReadingPlace(location, book)
        scope.launch { snackbar.showSnackbar((if (location in bookmarks) savedLabel else removedLabel) + " · " + location.key) }
    }
    val quickBookmark: () -> Unit = { bookmark(currentAyah) }
    BackHandler(sheet.isEmpty() && (showPlayer || selectedAyah > 0 || fullscreen)) {
        if (showPlayer) closePlayer() else if (selectedAyah > 0) selectedAyah = 0 else fullscreen = false
    }

    ReaderWindow(fullscreen, orientation, keepAwake)
    LaunchedEffect(list, geometry) {
        var moved = false
        snapshotFlow { list.isScrollInProgress to list.layoutInfo.visibleItemsInfo.map { it.index to it.offset } }
            .distinctUntilChanged().collect { (scrolling, _) ->
            geometry.firstAyah()?.let { visibleAyah = it }
            if (scrolling) {
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
            selectedWord = null
            followAudio = false
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
            progress.wholeSurah = reading
            progress.save(QuranProgress.Location(surahId, number))
        }
    }
    val compact = fullscreen || configuration.screenHeightDp < 560 || configuration.fontScale > 1.25f
    ReaderScaffold(fullscreen, compact, controls = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (fullscreen) {
                IconButton(onClick = { fullscreen = false }, modifier = Modifier.testTag("quran_exit_fullscreen")) {
                    Text("⛶", fontSize = 26.sp, modifier = Modifier.semanticsDescription(stringResource(R.string.qf_controls)))
                }
            } else {
                IconButton(onClick = showIndex, modifier = Modifier.testTag("quran_surahs")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(backLabel))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.ink)
                if (playingAyah != null) Box(Modifier.testTag("quran_playing_ayah_$playingAyah")) {
                    Box(if (highlight.phrase != null) Modifier.testTag("quran_playing_pair_${surahId}_${playingAyah}_${highlight.phrase.firstWord}_${highlight.phrase.lastWord}") else Modifier) {
                        Text(stringResource(R.string.ql_reading_now, "$surahId:$playingAyah"),
                            modifier = if (playback.playingWord != null) Modifier.testTag("quran_playing_word_${playback.playingWord}") else Modifier,
                            style = MaterialTheme.typography.labelMedium, color = colors.accent)
                    }
                }
                else if (!liveHere && focusedAyah != null) Box(Modifier.testTag("quran_selected_ayah_${surahId}_$focusedAyah")) {
                    Text(stringResource(R.string.qp_selected_ayah, surahId, focusedAyah),
                        modifier = if (highlight.quietPhrase != null) Modifier.testTag("quran_selected_pair_${surahId}_${focusedAyah}_${highlight.quietPhrase.firstWord}_${highlight.quietPhrase.lastWord}") else Modifier,
                        style = MaterialTheme.typography.labelMedium, color = colors.muted)
                } else if (!compact) Text(stringResource(R.string.qp_selected_ayah, surahId, currentAyah),
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
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Keep labels readable at large font sizes instead of squeezing two columns.
                val stackModes = maxWidth / LocalDensity.current.fontScale < 320.dp
                val modeChips: @Composable (Modifier) -> Unit = { chipModifier ->
                    FilterChip(selected = wholeSurah, onClick = { switchMode(true) },
                        modifier = chipModifier.then(if (!wholeSurah) Modifier.testTag("quran_read_mode") else Modifier),
                        label = { Text(stringResource(R.string.qp_reading_mode),
                            Modifier.fillMaxWidth().testTag("quran_mode_read"), textAlign = TextAlign.Center) })
                    FilterChip(selected = !wholeSurah, onClick = { switchMode(false) },
                        modifier = chipModifier.then(if (wholeSurah) Modifier.testTag("quran_read_mode") else Modifier),
                        label = { Text(stringResource(R.string.qp_translation_mode),
                            Modifier.fillMaxWidth().testTag("quran_mode_translation"), textAlign = TextAlign.Center) })
                }
                if (stackModes) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        modeChips(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        modeChips(Modifier.weight(1f))
                    }
                }
            }
        }
        if (!compact && (session.timer.remaining > 0 || session.timer.paused)) TextButton(
            onClick = { openSheet("timer") }, modifier = Modifier.testTag("quran_timer_status")) {
            Text(stringResource(if (session.timer.paused) R.string.qf_timer_paused else R.string.qf_timer_remaining, readingClock(session.timer.remaining)))
        }
    }, passage = {
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
                            Column(Modifier.fillMaxWidth()) {
                                val marks = block.ranges.mapNotNull { savedByAyah[it.ayah] }
                                if (marks.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    marks.forEach { mark -> QuranSavedIndicator(surahId, mark) {
                                        openAyahSheet(mark.ayah, if (mark.note.isNotEmpty()) "note" else "actions")
                                    } }
                                }
                                QuranTouchText(block.text, block.ranges, geometry,
                                    ArabicStyle.copy(fontSize = (30 * scale).sp, textDirection = TextDirection.Rtl, textAlign = TextAlign.Justify),
                                    "quran_whole_" + block.firstAyah, Modifier.fillMaxWidth(), choose, savedAyahs, focusedAyah, playingAyah, highlight.focusRanges, highlight.ayahWithoutWords, highlight.unmappedTap)
                            }
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
                                    border = BorderStroke(if (highlight.ayahWithoutWords == ayah.number) 2.dp else 1.dp,
                                        if (pendingAyah == ayah.number || highlight.ayahWithoutWords == ayah.number) colors.accent else colors.rule),
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
                            savedByAyah[ayah.number]?.let { mark ->
                                QuranSavedIndicator(surahId, mark) {
                                    openAyahSheet(mark.ayah, if (mark.note.isNotEmpty()) "note" else "actions")
                                }
                                if (mark.note.isNotEmpty()) QuranSavedNotePreview(mark) { openAyahSheet(mark.ayah, "note") }
                            }
                            val ranges = remember(ayah) { listOf(QuranTextRange(ayah.number, 0, ayah.arabic.length, ayah.arabic.length)) }
                            QuranTouchText(ayah.arabic, ranges, geometry,
                                ArabicStyle.copy(fontSize = (30 * scale).sp, textDirection = TextDirection.Rtl),
                                "quran_arabic_" + ayah.number, Modifier.fillMaxWidth(), choose, savedAyahs, focusedAyah, playingAyah, highlight.focusRanges, highlight.ayahWithoutWords, highlight.unmappedTap)
                            Text(stringResource(R.string.quran_translation_label), style = MaterialTheme.typography.labelSmall, color = colors.accent)
                            Text(ayah.translation, modifier = Modifier.testTag("quran_translation_" + ayah.number)
                                .combinedClickable(onClick = { choose(ayah.number, null, false) }, onLongClick = { choose(ayah.number, null, true) }),
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
    }, player = {
        if (snackbar.currentSnackbarData != null) SnackbarHost(snackbar)
        if (playerVisible) QuranInlinePlayer(surahId, target, startRequested || activeTarget && playback.playWhenReady && !playback.ended,
            startRequested || activeTarget && playback.buffering, playback.error, playOrPause,
            options = { openSheet("audioOptions") }, close = closePlayer)
    }, dialogs = {
        when (sheet) {
            "audioOptions" -> QuranInlineOptions(actualRepeats, actualSpeed, actualContinue, ::configure) { sheet = "" }
            "tools" -> HorizonSheet(stringResource(R.string.qf_tools), { sheet = "" }) {
                ReaderAction(R.string.qf_jump, "quran_jump_open") { sheet = "jump" }
                ReaderAction(R.string.qm_saved_here, "quran_saved_tools") { sheet = "saved" }
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
            "saved" -> QuranSavedSheet(surahId, if (ru) surah.nameRu else surah.nameLatin, savedMarks,
                close = { sheet = "" }, jump = jumpTo, edit = { openAyahSheet(it, "note") })
            "actions" -> HorizonSheet(stringResource(R.string.qp_selected_ayah, surahId, actionAyah), { sheet = "" }) {
                Column(Modifier.fillMaxWidth().testTag("quran_personal_actions")) {
                    ReaderAction(if (QuranProgress.Location(surahId, actionAyah) in bookmarks) R.string.qt_remove_bookmark
                        else R.string.quran_save, "quran_action_bookmark") { bookmark(actionAyah); sheet = "" }
                    ReaderAction(R.string.qt_note, "quran_note_open") { sheet = "note" }
                    ReaderAction(R.string.qt_copy, "quran_copy_ayah") { sheet = "copy" }
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
                onSaved = { deleted ->
                    noteTexts = loadNotes()
                    scope.launch { snackbar.showSnackbar(if (deleted) noteDeletedLabel else noteSavedLabel) }
                },
                close = { sheet = "" })
            "timer" -> QuranTimerSheet(progress, session) { sheet = "" }
            "statistics" -> HorizonSheet(stringResource(R.string.qf_statistics), { sheet = "" }) {
                QuranStatisticsCard(book, showTitle = false)
                PersonalProgressSetting()
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
    })
}

private data class ReaderHighlight(
    val phrase: QuranReadingPhrase?,
    val quietPhrase: QuranReadingPhrase?,
    val focusRanges: List<IntRange>,
    val ayahWithoutWords: Int?,
    val unmappedTap: IntRange?,
)

@Composable
private fun rememberReaderHighlight(
    surahId: Int, verses: List<QuranAyah>, reciterId: String,
    playback: QuranReaderPlayback, playingAyah: Int?, liveHere: Boolean, pendingAyah: Int?,
    focusedAyah: Int?, focusedWord: Int?, selectedWord: Int?,
): ReaderHighlight {
    val context = LocalContext.current
    val ayahWithoutWords = remember(reciterId, surahId, playingAyah) {
        playingAyah?.takeUnless { QuranWordTimings.hasWords(context, surahId, it) }
    }
    val phrase = remember(reciterId, surahId, playingAyah, playback.phraseWord, playback.phraseGapNeighbor) {
        val word = playback.phraseWord
        val verse = playingAyah?.let { verses.getOrNull(it - 1) }
        if (verse != null && word != null)
            QuranWordTimings.phrase(context, surahId, verse.number, verse.arabic, word, playback.phraseGapNeighbor)
        else null
    }
    val quietPhrase = remember(reciterId, surahId, liveHere, pendingAyah, focusedAyah, focusedWord, playback.gapNeighbor) {
        val verse = focusedAyah?.let { verses.getOrNull(it - 1) }
        if (liveHere || verse == null || focusedWord == null) null
        else {
            val gap = playback.gapNeighbor.takeIf { pendingAyah == null }
            if (pendingAyah != null)
                QuranWordTimings.selectedPhrase(context, surahId, verse.number, verse.arabic, focusedWord)
            else QuranWordTimings.phrase(context, surahId, verse.number, verse.arabic, focusedWord, gap)
        }
    }
    // A user's choice can be shown precisely even where the recording has no verified start.
    val unmappedTap = remember(reciterId, surahId, pendingAyah, selectedWord) {
        val word = selectedWord
        val verse = pendingAyah?.let { verses.getOrNull(it - 1) }
        if (verse != null && word != null && !QuranWordTimings.hasWord(context, surahId, verse.number, word))
            quranWordRange(verse.arabic, word)
        else null
    }
    val focusRanges = (if (liveHere) phrase else quietPhrase)?.ranges.orEmpty()
    return ReaderHighlight(phrase, quietPhrase, focusRanges, ayahWithoutWords, unmappedTap)
}

@Composable
private fun FollowReaderPassage(list: LazyListState, geometry: QuranTextGeometry, blocks: List<QuranReadingBlock>,
    wholeSurah: Boolean, verseCount: Int, reciterId: String, surahId: Int, playingAyah: Int?, followAudio: Boolean,
    sheet: String, playingWord: Int?, phrase: QuranReadingPhrase?) {
    val spokenOffset = phrase?.let {
        if (playingWord == it.lastWord) it.ranges.last().first else it.ranges.first().first
    }
    // Keep the last line in view during a rest, without painting a word that is no longer sounding.
    var lastWordOffset by remember(reciterId, surahId, playingAyah) { mutableIntStateOf(0) }
    // Follow word lines only as they leave the visible page. A separately selected verse
    // or an open notes/settings sheet retains its own position.
    LaunchedEffect(playingAyah, phrase, spokenOffset, wholeSurah, sheet, followAudio) {
        val number = playingAyah ?: return@LaunchedEffect
        if (!followAudio || sheet.isNotEmpty() || number !in 1..verseCount) return@LaunchedEffect
        val offset = spokenOffset ?: lastWordOffset
        lastWordOffset = offset
        val first = phrase?.ranges?.first()?.first ?: offset
        val last = phrase?.ranges?.last()?.last ?: offset
        var distance = geometry.scrollToPassage(number, first, last, offset)
        if (distance == null) {
            list.scrollToItem(if (wholeSurah) QuranReading.blockIndex(blocks, number) else number - 1)
            withFrameNanos { }
            distance = geometry.scrollToPassage(number, first, last, offset)
        }
        if (distance != null && distance != 0f) list.scrollBy(distance)
    }
}

@Composable
private fun ReaderWindow(fullscreen: Boolean, orientation: Int, keepAwake: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current.readerActivity()
    val originalOrientation = rememberSaveable { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
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
}

@Composable
private fun ReaderScaffold(fullscreen: Boolean, compact: Boolean, controls: @Composable ColumnScope.() -> Unit,
    passage: @Composable ColumnScope.() -> Unit, player: @Composable ColumnScope.() -> Unit,
    dialogs: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(quranPaperColors().canvas)
        .windowInsetsPadding(if (fullscreen) WindowInsets.displayCutout else WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
            controls()
            passage()
            player()
        }
    }
    dialogs()
}

private fun Modifier.semanticsDescription(description: String): Modifier = semantics { contentDescription = description }

@Composable
private fun QuranQuickBookmark(saved: Boolean, save: () -> Unit) {
    IconButton(onClick = save, modifier = Modifier.testTag("quran_quick_bookmark")) {
        Icon(painterResource(if (saved) R.drawable.ic_quran_bookmark_saved else R.drawable.ic_quran_bookmark),
            stringResource(if (saved) R.string.qt_remove_bookmark else R.string.qt_save_place))
    }
}

