package com.zikrtime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.QuranBook
import com.zikrtime.app.data.QuranProgress
import com.zikrtime.app.data.QuranTopic
import com.zikrtime.app.data.QuranTopicPassage
import com.zikrtime.app.i18n.Lang

internal fun QuranTopic.titleResource(): Int = when (id) {
    "patience" -> R.string.qti_patience
    "gratitude" -> R.string.qti_gratitude
    "parents" -> R.string.qti_parents
    "mercy" -> R.string.qti_mercy
    "repentance" -> R.string.qti_repentance
    "honesty" -> R.string.qti_honesty
    "justice" -> R.string.qti_justice
    "charity" -> R.string.qti_charity
    "knowledge" -> R.string.qti_knowledge
    "remembrance" -> R.string.qti_remembrance
    "trust" -> R.string.qti_trust
    "supplication" -> R.string.qti_supplication
    "forgiveness" -> R.string.qti_forgiveness
    "humility" -> R.string.qti_humility
    "family" -> R.string.qti_family
    "reflection" -> R.string.qti_reflection
    else -> error("Unknown Quran topic $id")
}

internal fun QuranTopic.descriptionResource(): Int = when (id) {
    "patience" -> R.string.qti_patience_description
    "gratitude" -> R.string.qti_gratitude_description
    "parents" -> R.string.qti_parents_description
    "mercy" -> R.string.qti_mercy_description
    "repentance" -> R.string.qti_repentance_description
    "honesty" -> R.string.qti_honesty_description
    "justice" -> R.string.qti_justice_description
    "charity" -> R.string.qti_charity_description
    "knowledge" -> R.string.qti_knowledge_description
    "remembrance" -> R.string.qti_remembrance_description
    "trust" -> R.string.qti_trust_description
    "supplication" -> R.string.qti_supplication_description
    "forgiveness" -> R.string.qti_forgiveness_description
    "humility" -> R.string.qti_humility_description
    "family" -> R.string.qti_family_description
    "reflection" -> R.string.qti_reflection_description
    else -> error("Unknown Quran topic $id")
}

/** Items share the index's one scrolling surface, including at large font scales. */
internal fun LazyListScope.quranTopicItems(
    book: QuranBook,
    selected: QuranTopic?,
    filtered: List<QuranTopic>,
    query: String,
    onQuery: (String) -> Unit,
    select: (String) -> Unit,
    close: () -> Unit,
    open: (QuranProgress.Location) -> Unit,
) {
    if (selected == null) {
        item("topics_heading") {
            val colors = quranPaperColors()
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.qti_title), style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink)
                Text(stringResource(R.string.qti_intro), style = MaterialTheme.typography.bodyLarge,
                    color = colors.muted)
                Text(stringResource(R.string.qti_topic_count, filtered.size, filtered.sumOf { it.passages.size }),
                    style = MaterialTheme.typography.labelLarge, color = colors.accent,
                    modifier = Modifier.testTag("quran_topics_count"))
                OutlinedTextField(query, onQuery, singleLine = true,
                    label = { Text(stringResource(R.string.qti_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) { {
                        IconButton(onClick = { onQuery("") }, modifier = Modifier.testTag("quran_topics_search_clear")) {
                            Icon(Icons.Filled.Close, stringResource(R.string.qti_clear))
                        }
                    } } else null,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().testTag("quran_topics_search"))
            }
        }
        if (filtered.isEmpty()) item("topics_empty") {
            val colors = quranPaperColors()
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("quran_topics_empty"),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.qti_no_results), style = MaterialTheme.typography.titleMedium,
                    color = colors.ink)
                Text(stringResource(R.string.qti_try_search), style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted)
                TextButton(onClick = { onQuery("") }) { Text(stringResource(R.string.qti_clear)) }
            }
        }
        items(filtered, key = { "topic_" + it.id }) { topic -> QuranTopicCard(topic) { select(topic.id) } }
        item("topics_scope") {
            Text(stringResource(R.string.qti_scope), style = MaterialTheme.typography.bodySmall,
                color = quranPaperColors().muted, modifier = Modifier.padding(vertical = 20.dp))
        }
    } else {
        item("topic_detail_heading_" + selected.id) {
            val colors = quranPaperColors()
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp)
                .testTag("quran_topic_detail_" + selected.id), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = close, modifier = Modifier.testTag("quran_topics_back"),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qti_back_to_topics))
                }
                Text(stringResource(selected.titleResource()), style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink)
                Text(stringResource(selected.descriptionResource()), style = MaterialTheme.typography.bodyLarge,
                    color = colors.muted)
                Text(stringResource(R.string.qti_passage_count, selected.passages.size),
                    style = MaterialTheme.typography.labelLarge, color = colors.accent)
                Text(stringResource(R.string.qti_translation_notice), style = MaterialTheme.typography.bodySmall,
                    color = colors.muted)
                Text(stringResource(R.string.qti_read_context), style = MaterialTheme.typography.bodySmall,
                    color = colors.muted)
            }
        }
        items(selected.passages, key = { "topic_" + selected.id + "_" + it.key }) { passage ->
            QuranTopicPassageCard(book, selected.id, passage, open)
        }
        item("topic_detail_scope") {
            Text(stringResource(R.string.qti_scope), style = MaterialTheme.typography.bodySmall,
                color = quranPaperColors().muted, modifier = Modifier.padding(vertical = 20.dp))
        }
    }
}

@Composable
private fun QuranTopicCard(topic: QuranTopic, open: () -> Unit) {
    val colors = quranPaperColors()
    Surface(onClick = open, shape = MaterialTheme.shapes.small, color = colors.canvas,
        modifier = Modifier.fillMaxWidth().testTag("quran_topic_" + topic.id)) {
        Row(Modifier.padding(vertical = 20.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(topic.titleResource()), style = MaterialTheme.typography.titleMedium,
                    color = colors.ink)
                Text(stringResource(topic.descriptionResource()), style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted)
                Text(stringResource(R.string.qti_passage_count, topic.passages.size),
                    style = MaterialTheme.typography.labelMedium, color = colors.accent)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = colors.accent)
        }
    }
    HorizontalDivider(color = colors.rule)
}

@Composable
private fun QuranTopicPassageCard(book: QuranBook, topicId: String, passage: QuranTopicPassage,
    open: (QuranProgress.Location) -> Unit) {
    val colors = quranPaperColors()
    val uri = LocalUriHandler.current
    val ru = Lang.content(LocalContext.current) == "ru"
    val surah = book.surahs.first { it.id == passage.surah }
    val first = book.verses(passage.surah).first { it.number == passage.first }
    Surface(shape = MaterialTheme.shapes.medium, color = colors.paper, border = BorderStroke(1.dp, colors.rule),
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(passage.reference, style = MaterialTheme.typography.titleLarge, color = colors.accent)
            Text(if (ru) surah.nameRu else surah.nameLatin, style = MaterialTheme.typography.titleMedium,
                color = colors.ink)
            HorizontalDivider(color = colors.rule)
            Text(stringResource(R.string.qti_excerpt, "${passage.surah}:${passage.first}"),
                style = MaterialTheme.typography.labelSmall, color = colors.muted)
            Text(first.translation, style = MaterialTheme.typography.bodyLarge, color = colors.ink,
                maxLines = 4, overflow = TextOverflow.Ellipsis)
            Button(onClick = { open(QuranProgress.Location(passage.surah, passage.first)) },
                modifier = Modifier.fillMaxWidth().testTag("quran_topic_passage_" + topicId + "_" + passage.key)) {
                Text(stringResource(R.string.qti_open, passage.reference))
            }
            TextButton(onClick = { runCatching { uri.openUri(passage.sourceUrl) } },
                modifier = Modifier.fillMaxWidth().testTag("quran_topic_source_" + topicId + "_" + passage.key)) {
                Text(stringResource(R.string.qti_source))
            }
        }
    }
}
