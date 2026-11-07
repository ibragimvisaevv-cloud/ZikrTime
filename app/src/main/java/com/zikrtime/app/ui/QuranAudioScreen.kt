@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zikrtime.app.R
import com.zikrtime.app.audio.*
import com.zikrtime.app.data.QuranBook
import com.zikrtime.app.data.QuranRepository
import com.zikrtime.app.data.QuranSurah
import com.zikrtime.app.i18n.Lang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/** Playback is always an explicit action. Opening a surah only selects it. */
@Composable
fun QuranAudioScreen(initialSurah: Int = 0) {
    QuranReadingTheme { QuranAudioContent(initialSurah) }
}

@Composable
private fun QuranAudioContent(initialSurah: Int) {
    StandardSystemBars()
    val context = LocalContext.current
    val nav = LocalNav.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val storage by QuranAudioRepository.state.collectAsStateWithLifecycle()
    val playback by QuranAudioPlayer.state.collectAsStateWithLifecycle()
    var book by remember { mutableStateOf<QuranBook?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableIntStateOf(initialSurah.takeIf { it in 1..114 } ?: 0) }
    var query by rememberSaveable { mutableStateOf("") }
    var downloadsOnly by rememberSaveable { mutableStateOf(false) }
    var storageDialog by rememberSaveable { mutableStateOf(false) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ru = Lang.content(context) == "ru"
    val colors = quranPaperColors()

    LaunchedEffect(loadAttempt) {
        QuranAudioRepository.init(context)
        loadFailed = false
        try { book = QuranRepository.load(context) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { loadFailed = true }
    }
    DisposableEffect(lifecycleOwner, storage.ready) {
        val lifecycle = lifecycleOwner.lifecycle
        var attached = false
        fun attach() {
            if (storage.ready && !attached) { attached = true; QuranAudioPlayer.connect(context) }
        }
        fun detach() {
            if (attached) { attached = false; QuranAudioPlayer.disconnect() }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) attach()
            if (event == Lifecycle.Event.ON_STOP) detach()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) attach()
        onDispose { lifecycle.removeObserver(observer); detach() }
    }
    LaunchedEffect(playback.connected, playback.surahId) {
        if (selectedId == 0 && playback.connected) selectedId = playback.surahId ?: 1
    }

    val allSurahs = book?.surahs.orEmpty()
    val selected = allSurahs.firstOrNull { it.id == selectedId }
        ?: allSurahs.firstOrNull { it.id == (initialSurah.takeIf { n -> n in 1..114 } ?: 1) }
    val filtered = remember(allSurahs, query, downloadsOnly, storage.downloads.keys) {
        val search = query.trim()
        allSurahs.filter { surah ->
            (!downloadsOnly || storage.downloads.containsKey(surah.id)) &&
                (search.isEmpty() || surah.id.toString() == search || surah.nameRu.contains(search, true) ||
                    surah.nameLatin.contains(search, true) || surah.nameArabic.contains(search))
        }
    }
    val openPlayer: (Int) -> Unit = { id -> selectedId = id; scope.launch { list.animateScrollToItem(0) } }
    val playSurah: (Int) -> Unit = { id ->
        selectedId = id
        QuranAudioPlayer.play(id)
        scope.launch { list.animateScrollToItem(0) }
    }
    Surface(color = colors.canvas, modifier = Modifier.fillMaxSize().testTag("audio_screen")
        .semantics { testTagsAsResourceId = true }) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                Text(stringResource(R.string.qaudio_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { storageDialog = true }, modifier = Modifier.testTag("audio_storage_open")) {
                    Text(stringResource(R.string.qaudio_memory), style = MaterialTheme.typography.labelMedium)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("audio_list"), state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item("player") {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuranOrnament(Modifier.width(92.dp).height(14.dp))
                        Text(stringResource(R.string.qaudio_reciter), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() })
                        if (selected != null && storage.ready) QuranAudioPlayerCard(selected, ru, playback, storage,
                            onDelete = { deleteId = selected.id })
                        else if (loadFailed || (!storage.ready && storage.error != null)) {
                            Text(stringResource(R.string.qaudio_load_error))
                            OutlinedButton(onClick = { loadAttempt++ }, modifier = Modifier.testTag("audio_catalog_retry")) {
                                Text(stringResource(R.string.qaudio_retry))
                            }
                        } else LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.qaudio_intro), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                }
                storage.error?.let { error -> item("storage_error") {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(stringResource(audioStorageErrorText(error)), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = { QuranAudioRepository.dismissError() }) { Text(stringResource(R.string.qaudio_close)) }
                        }
                    }
                } }
                item("filters") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!downloadsOnly, { downloadsOnly = false }, label = { Text(stringResource(R.string.qaudio_all)) },
                            modifier = Modifier.testTag("audio_all_filter"))
                        FilterChip(downloadsOnly, { downloadsOnly = true }, label = { Text(stringResource(R.string.qaudio_downloads)) },
                            modifier = Modifier.testTag("audio_downloads_filter"))
                    }
                    OutlinedTextField(query, { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.qaudio_search)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (query.isEmpty()) null else {{
                            IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.qaudio_clear_search)) }
                        }}, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("audio_search"))
                }
                if (filtered.isEmpty() && book != null) item("empty") {
                    Text(stringResource(if (downloadsOnly && storage.downloads.isEmpty() && query.isBlank())
                        R.string.qaudio_empty_downloads else R.string.qaudio_no_results),
                        modifier = Modifier.padding(vertical = 16.dp), color = colors.muted)
                }
                items(filtered, key = { "audio_surah_${it.id}" }) { surah ->
                    QuranAudioTrackRow(surah, ru, storage, playback, onListen = { playSurah(surah.id) },
                        onDelete = { deleteId = surah.id }, onOpenPlayer = { openPlayer(surah.id) })
                }
                item("source") {
                    val uri = LocalUriHandler.current
                    Text(stringResource(R.string.qaudio_source_detail), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    TextButton(onClick = { runCatching { uri.openUri("https://www.mp3quran.net/eng/afs") } },
                        modifier = Modifier.testTag("audio_source")) { Text(stringResource(R.string.qaudio_source)) }
                }
            }
            if (playback.surahId != null && list.firstVisibleItemIndex > 0) {
                Surface(color = colors.paper, shadowElevation = 2.dp) {
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { playback.surahId?.let(openPlayer) }, modifier = Modifier.testTag("audio_show_player")) {
                            Text(stringResource(R.string.qaudio_show_player))
                        }
                        TextButton(onClick = { QuranAudioPlayer.togglePlayPause() }, enabled = playback.connected,
                            modifier = Modifier.testTag("audio_mini_play_pause")) {
                            Text(stringResource(when {
                                playback.ended -> R.string.qaudio_listen
                                playback.playWhenReady -> R.string.qaudio_pause
                                else -> R.string.qaudio_resume
                            }))
                        }
                    }
                }
            }
        }
    }
    if (storageDialog) QuranAudioStorageDialog(storage, close = { storageDialog = false }, clear = {
        storageDialog = false; clearDialog = true
    })
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false },
        title = { Text(stringResource(R.string.qaudio_clear_title)) },
        text = { Text(stringResource(R.string.qaudio_clear_detail)) },
        confirmButton = { TextButton(onClick = {
            clearDialog = false; QuranAudioRepository.clearTemporaryCache(); storageDialog = true
        }, modifier = Modifier.testTag("audio_confirm_clear_cache")) { Text(stringResource(R.string.qaudio_clear_cache)) } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.qaudio_cancel)) } })
    if (deleteId != 0) {
        val deletingSurah = allSurahs.firstOrNull { it.id == deleteId }
        AlertDialog(onDismissRequest = { deleteId = 0 }, title = { Text(stringResource(R.string.qaudio_delete_title)) },
            text = { Text(stringResource(R.string.qaudio_delete_detail,
                deletingSurah?.let { if (ru) it.nameRu else it.nameLatin } ?: deleteId.toString())) },
            confirmButton = { TextButton(onClick = {
                if (playback.surahId == deleteId) QuranAudioPlayer.pause()
                QuranAudioRepository.remove(deleteId); deleteId = 0
            }, modifier = Modifier.testTag("audio_confirm_delete")) { Text(stringResource(R.string.qaudio_confirm_delete)) } },
            dismissButton = { TextButton(onClick = { deleteId = 0 }) { Text(stringResource(R.string.qaudio_cancel)) } })
    }
}

@Composable
private fun QuranAudioPlayerCard(surah: QuranSurah, ru: Boolean, playback: QuranPlaybackState,
    storage: QuranAudioStorageState, onDelete: () -> Unit) {
    val colors = quranPaperColors()
    val active = playback.surahId == surah.id
    val playing = active && playback.isPlaying
    val position = if (active) playback.positionMs.coerceAtLeast(0) else 0L
    val duration = if (active) playback.durationMs.coerceAtLeast(0) else 0L
    var dragging by remember(surah.id) { mutableStateOf(false) }
    var seekValue by remember(surah.id) { mutableFloatStateOf(0f) }
    val seekDescription = stringResource(R.string.qaudio_seek)
    Surface(shape = RoundedCornerShape(20.dp), color = colors.paper, border = BorderStroke(1.dp, colors.rule),
        modifier = Modifier.fillMaxWidth().testTag("audio_player")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuranNumberSeal(surah.id.toString(), Modifier.size(40.dp))
                Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).testTag("audio_player_title"))
            }
            Text(stringResource(when {
                !playback.connected && playback.error != null -> R.string.qaudio_retry
                !playback.connected -> R.string.qaudio_connecting
                active && playback.isBuffering -> R.string.qaudio_buffering
                playing -> R.string.qaudio_playing
                active && playback.ended -> R.string.qaudio_finished
                active && position > 0 -> R.string.qaudio_paused
                else -> R.string.qaudio_ready
            }), color = colors.accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("audio_player_status"))
            if (!active && playback.isPlaying) Text(stringResource(R.string.qaudio_other_playing, playback.title),
                style = MaterialTheme.typography.bodySmall, color = colors.muted)
            if (active && playback.isBuffering) LinearProgressIndicator(Modifier.fillMaxWidth())
            if ((active || !playback.connected) && playback.error != null) Text(playback.error!!, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("audio_player_error"))
            if (duration > 0) {
                Slider(value = if (dragging) seekValue else position.coerceAtMost(duration).toFloat(),
                    onValueChange = { dragging = true; seekValue = it },
                    onValueChangeFinished = { QuranAudioPlayer.seekTo(seekValue.toLong()); dragging = false },
                    valueRange = 0f..duration.toFloat(), enabled = playback.connected,
                    modifier = Modifier.fillMaxWidth().testTag("audio_seek").semantics { contentDescription = seekDescription })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(audioClock(if (dragging) seekValue.toLong() else position), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag("audio_position"))
                    Text(audioClock(duration), style = MaterialTheme.typography.labelMedium)
                }
            } else Text(if (position > 0) stringResource(R.string.qaudio_saved_position, audioClock(position))
                else stringResource(R.string.qaudio_unknown_duration), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Button(onClick = {
                when {
                    !playback.connected -> QuranAudioPlayer.play(surah.id)
                    !active || playback.ended -> QuranAudioPlayer.play(surah.id)
                    playback.error != null -> QuranAudioPlayer.retry()
                    else -> QuranAudioPlayer.togglePlayPause()
                }
            }, enabled = storage.ready && (playback.connected || playback.error != null),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("audio_play_pause")) {
                Text(stringResource(when {
                    (active || !playback.connected) && playback.error != null -> R.string.qaudio_retry
                    active && playback.playWhenReady && !playback.ended -> R.string.qaudio_pause
                    active && position > 0 && !playback.ended -> R.string.qaudio_resume
                    else -> R.string.qaudio_listen
                }))
            }
            if (active) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { QuranAudioPlayer.skipBy(-15_000L) }, enabled = playback.connected && duration > 0,
                    modifier = Modifier.testTag("audio_seek_back")) { Text(stringResource(R.string.qaudio_back_15)) }
                OutlinedButton(onClick = { QuranAudioPlayer.skipBy(15_000L) }, enabled = playback.connected && duration > 0,
                    modifier = Modifier.testTag("audio_seek_forward")) { Text(stringResource(R.string.qaudio_forward_15)) }
                TextButton(onClick = { QuranAudioPlayer.stop() }, enabled = playback.connected,
                    modifier = Modifier.testTag("audio_stop")) { Text(stringResource(R.string.qaudio_stop)) }
            }
            HorizontalDivider(color = colors.rule)
            QuranAudioDownloadActions(surah.id, storage, onDelete, tagSuffix = "player")
        }
    }
}

@Composable
private fun QuranAudioTrackRow(surah: QuranSurah, ru: Boolean, storage: QuranAudioStorageState,
    playback: QuranPlaybackState, onListen: () -> Unit, onDelete: () -> Unit, onOpenPlayer: () -> Unit) {
    val colors = quranPaperColors()
    Column(Modifier.fillMaxWidth().testTag("audio_track_${surah.id}").padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuranNumberSeal(surah.id.toString(), Modifier.size(36.dp))
            Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = if (playback.surahId == surah.id && playback.isPlaying) onOpenPlayer else onListen,
                enabled = playback.connected && storage.ready, modifier = Modifier.testTag("audio_play_${surah.id}")) {
                Text(stringResource(if (playback.surahId == surah.id && playback.isPlaying)
                    R.string.qaudio_show_player else R.string.qaudio_listen))
            }
        }
        QuranAudioDownloadActions(surah.id, storage, onDelete)
        HorizontalDivider(color = colors.rule.copy(alpha = .65f), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun QuranAudioDownloadActions(surahId: Int, storage: QuranAudioStorageState, onDelete: () -> Unit,
    tagSuffix: String = surahId.toString()) {
    val download = storage.downloads[surahId]
    val track = QuranAudioRepository.track(surahId)
    val complete = download?.status == QuranAudioDownloadStatus.COMPLETED
    val colors = quranPaperColors()
    Text(stringResource(if (complete) R.string.qaudio_offline else R.string.qaudio_online),
        style = MaterialTheme.typography.bodySmall, color = if (complete) colors.accent else colors.muted,
        modifier = Modifier.testTag("audio_availability_$tagSuffix"))
    if (track != null) Text(stringResource(R.string.qaudio_download_size, audioSize(track.sizeBytes)),
        style = MaterialTheme.typography.bodySmall, color = colors.muted)
    if (download != null && !complete) {
        Text(stringResource(audioDownloadStatusText(download.status)), style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag("audio_download_status_$tagSuffix"))
        if (download.totalBytes > 0 && download.bytesDownloaded > 0) {
            LinearProgressIndicator(progress = { (download.bytesDownloaded.toFloat() / download.totalBytes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.qaudio_download_progress, audioSize(download.bytesDownloaded), audioSize(download.totalBytes)),
                style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        download.error?.let { Text(stringResource(audioStorageErrorText(it)), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            complete -> TextButton(onClick = onDelete, modifier = Modifier.testTag("audio_delete_$tagSuffix")) {
                Text(stringResource(R.string.qaudio_delete))
            }
            download == null || download.status == QuranAudioDownloadStatus.FAILED -> OutlinedButton(
                onClick = { QuranAudioRepository.download(surahId) }, enabled = storage.ready && track != null,
                modifier = Modifier.testTag(if (download == null) "audio_download_$tagSuffix" else "audio_retry_$tagSuffix")) {
                Text(stringResource(if (download == null) R.string.qaudio_download else R.string.qaudio_retry))
            }
        }
        if (download != null && download.status !in setOf(QuranAudioDownloadStatus.COMPLETED, QuranAudioDownloadStatus.REMOVING)) {
            TextButton(onClick = { QuranAudioRepository.cancel(surahId) }, modifier = Modifier.testTag("audio_cancel_$tagSuffix")) {
                Text(stringResource(R.string.qaudio_cancel))
            }
        }
    }
}

@Composable
private fun QuranAudioStorageDialog(storage: QuranAudioStorageState, close: () -> Unit, clear: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.qaudio_storage)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).testTag("audio_storage_content"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().toggleable(storage.wifiOnly, role = Role.Switch) { QuranAudioRepository.setWifiOnly(it) }
                    .testTag("audio_wifi_only"), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.qaudio_wifi_only), modifier = Modifier.weight(1f))
                    Switch(storage.wifiOnly, onCheckedChange = null)
                }
                Text(stringResource(R.string.qaudio_wifi_detail), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(stringResource(R.string.qaudio_cache_usage, audioSize(storage.temporaryBytes)), modifier = Modifier.testTag("audio_cache_usage"))
                Text(stringResource(R.string.qaudio_download_usage, audioSize(storage.downloadedBytes)), modifier = Modifier.testTag("audio_download_usage"))
                Text(stringResource(R.string.qaudio_cache_detail), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = clear, enabled = storage.ready && !storage.clearingCache,
                    modifier = Modifier.fillMaxWidth().testTag("audio_clear_cache")) {
                    Text(stringResource(if (storage.clearingCache) R.string.qaudio_clearing_cache else R.string.qaudio_clear_cache))
                }
            }
        },
        confirmButton = { TextButton(onClick = close, modifier = Modifier.testTag("audio_storage_close")) { Text(stringResource(R.string.qaudio_close)) } })
}

private fun audioDownloadStatusText(status: QuranAudioDownloadStatus): Int = when (status) {
    QuranAudioDownloadStatus.QUEUED -> R.string.qaudio_queued
    QuranAudioDownloadStatus.WAITING_FOR_WIFI -> R.string.qaudio_wait_wifi
    QuranAudioDownloadStatus.WAITING_FOR_NETWORK -> R.string.qaudio_wait_network
    QuranAudioDownloadStatus.DOWNLOADING -> R.string.qaudio_downloading
    QuranAudioDownloadStatus.PAUSED -> R.string.qaudio_download_paused
    QuranAudioDownloadStatus.COMPLETED -> R.string.qaudio_offline
    QuranAudioDownloadStatus.FAILED -> R.string.qaudio_download_failed
    QuranAudioDownloadStatus.REMOVING -> R.string.qaudio_removing
}

private fun audioStorageErrorText(error: QuranAudioStorageError): Int = when (error) {
    QuranAudioStorageError.CATALOG -> R.string.qaudio_load_error
    QuranAudioStorageError.STORAGE -> R.string.qaudio_error_storage
    QuranAudioStorageError.NOT_ENOUGH_SPACE -> R.string.qaudio_error_space
    QuranAudioStorageError.NETWORK -> R.string.qaudio_error_network
    QuranAudioStorageError.START_FAILED -> R.string.qaudio_error_start
}

@Composable
private fun audioSize(bytes: Long): String = stringResource(R.string.qaudio_mb,
    String.format(Locale.getDefault(), "%.1f", bytes.coerceAtLeast(0) / (1024.0 * 1024.0)))

private fun audioClock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
