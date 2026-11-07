@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.zikrtime.app.R

/** Stable keys are shared with saved navigation and the settings entry. */
enum class GuideTopic(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val steps: List<Int>,
    val controls: List<Int>,
    @DrawableRes val icon: Int,
) {
    WELCOME(R.string.guide_welcome_title, R.string.guide_welcome_summary,
        listOf(R.string.guide_welcome_step1, R.string.guide_welcome_step2, R.string.guide_welcome_step3),
        listOf(R.string.guide_welcome_control1, R.string.guide_welcome_control2, R.string.guide_welcome_control3), R.drawable.ic_nav_more),
    PRAYER(R.string.guide_prayer_title, R.string.guide_prayer_summary,
        listOf(R.string.guide_prayer_step1, R.string.guide_prayer_step2, R.string.guide_prayer_step3),
        listOf(R.string.guide_prayer_control1, R.string.guide_prayer_control2, R.string.guide_prayer_control3), R.drawable.ic_nav_prayer),
    JOURNAL(R.string.guide_journal_title, R.string.guide_journal_summary,
        listOf(R.string.guide_journal_step1, R.string.guide_journal_step2, R.string.guide_journal_step3),
        listOf(R.string.guide_journal_control1, R.string.guide_journal_control2, R.string.guide_journal_control3), R.drawable.ic_prayer_rug),
    QURAN(R.string.guide_quran_title, R.string.guide_quran_summary,
        listOf(R.string.guide_quran_step1, R.string.guide_quran_step2, R.string.guide_quran_step3),
        listOf(R.string.guide_quran_control1, R.string.guide_quran_control2, R.string.guide_quran_control3), R.drawable.ic_nav_knowledge),
    LIMITS(R.string.guide_limits_title, R.string.guide_limits_summary,
        listOf(R.string.guide_limits_step1, R.string.guide_limits_step2, R.string.guide_limits_step3),
        listOf(R.string.guide_limits_control1, R.string.guide_limits_control2, R.string.guide_limits_control3), R.drawable.ic_nav_focus),
    PERMISSIONS(R.string.guide_permissions_title, R.string.guide_permissions_summary,
        listOf(R.string.guide_permissions_step1, R.string.guide_permissions_step2, R.string.guide_permissions_step3),
        listOf(R.string.guide_permissions_control1, R.string.guide_permissions_control2, R.string.guide_permissions_control3), R.drawable.ic_phone),
    KNOWLEDGE(R.string.guide_knowledge_title, R.string.guide_knowledge_summary,
        listOf(R.string.guide_knowledge_step1, R.string.guide_knowledge_step2, R.string.guide_knowledge_step3),
        listOf(R.string.guide_knowledge_control1, R.string.guide_knowledge_control2, R.string.guide_knowledge_control3), R.drawable.ic_nav_knowledge),
    PLACES(R.string.guide_places_title, R.string.guide_places_summary,
        listOf(R.string.guide_places_step1, R.string.guide_places_step2, R.string.guide_places_step3),
        listOf(R.string.guide_places_control1, R.string.guide_places_control2, R.string.guide_places_control3), R.drawable.ic_mosque),
    TOOLS(R.string.guide_tools_title, R.string.guide_tools_summary,
        listOf(R.string.guide_tools_step1, R.string.guide_tools_step2, R.string.guide_tools_step3),
        listOf(R.string.guide_tools_control1, R.string.guide_tools_control2, R.string.guide_tools_control3), R.drawable.ic_qibla),
    SHOP(R.string.guide_shop_title, R.string.guide_shop_summary,
        listOf(R.string.guide_shop_step1, R.string.guide_shop_step2, R.string.guide_shop_step3),
        listOf(R.string.guide_shop_control1, R.string.guide_shop_control2, R.string.guide_shop_control3), R.drawable.ic_nav_shop);

    val key: String get() = name.lowercase(Locale.ROOT)
}

/**
 * Read-only guide: no permissions, timers, marks or settings change while paging.
 * The host owns first-run completion and destination navigation.
 */
@Composable
fun AppGuideScreen(
    firstRun: Boolean,
    onFinish: () -> Unit,
    onOpenTopic: (GuideTopic) -> Unit,
) {
    StandardSystemBars()
    val topics = remember { GuideTopic.values().toList() }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var contents by rememberSaveable { mutableStateOf(false) }
    val topic = topics[page.coerceIn(topics.indices)]
    val motion = motionEnabled()
    val colors = MaterialTheme.colorScheme
    BackHandler(enabled = !contents) {
        if (page > 0) page-- else onFinish()
    }

    Column(
        Modifier.fillMaxSize().background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("guide_screen").semantics { testTagsAsResourceId = true },
    ) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
            .padding(start = 20.dp, end = 12.dp, top = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onFinish, modifier = Modifier.testTag(if (firstRun) "guide_skip" else "guide_close")) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(if (firstRun) R.string.guide_skip else R.string.guide_close))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.guide_progress, page + 1, topics.size),
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { contents = true }, modifier = Modifier.testTag("guide_contents")) {
                    Text(stringResource(R.string.guide_contents))
                }
            }
            Row(Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                topics.indices.forEach { index ->
                    Box(Modifier.weight(1f).height(3.dp).background(
                        if (index <= page) colors.primary else colors.outlineVariant, CircleShape))
                }
            }
        }

        AnimatedContent(
            targetState = topic,
            transitionSpec = {
                fadeIn(tween(if (motion) 180 else 0)) togetherWith fadeOut(tween(if (motion) 90 else 0))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "guide_page",
        ) { shown ->
            // Every topic gets its own scroll state. Returning from a real section
            // restores this page; changing topics always starts the explanation at the top.
            key(shown) {
                GuidePage(shown, firstRun, onFinish, onOpenTopic)
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), color = colors.surfaceContainer, tonalElevation = 1.dp) {
            Box(contentAlignment = Alignment.Center) {
            Row(
                Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page > 0) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("guide_back"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.guide_back))
                    }
                }
                val last = page == topics.lastIndex
                Button(
                    onClick = { if (last) onFinish() else page++ },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(if (last) "guide_finish" else "guide_next"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                ) { Text(stringResource(if (last) R.string.guide_finish else R.string.guide_next)) }
            }
            }
        }
    }

    if (contents) {
        AlertDialog(
            onDismissRequest = { contents = false },
            modifier = Modifier.testTag("guide_contents_dialog").semantics { testTagsAsResourceId = true },
            title = { Text(stringResource(R.string.guide_contents)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(topics, key = { _, item -> item.key }) { index, item ->
                        TextButton(
                            onClick = { page = index; contents = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("guide_topic_" + item.key),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Text((index + 1).toString(), color = colors.secondary,
                                modifier = Modifier.width(28.dp))
                            Text(stringResource(item.title), color = if (index == page) colors.primary else colors.onSurface,
                                fontWeight = if (index == page) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { contents = false }, modifier = Modifier.testTag("guide_contents_close")) {
                    Text(stringResource(R.string.guide_close))
                }
            },
        )
    }
}

@Composable
private fun GuidePage(topic: GuideTopic, firstRun: Boolean, onFinish: () -> Unit, onOpenTopic: (GuideTopic) -> Unit) {
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).testTag("guide_page_" + topic.key)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 800.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(topic.title), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(topic.summary), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (topic == GuideTopic.WELCOME) {
                    Text(stringResource(R.string.guide_subtitle), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (firstRun) TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().testTag("guide_skip_welcome")) {
                        Text(stringResource(R.string.guide_skip))
                    }
                }
            }
            if (wide) {
                Row(Modifier.widthIn(max = 800.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    AppGuidePreview(topic, Modifier.weight(1f))
                    GuideSteps(topic, Modifier.weight(1f))
                }
            } else {
                AppGuidePreview(topic, Modifier.widthIn(max = 560.dp).fillMaxWidth())
                GuideSteps(topic, Modifier.widthIn(max = 560.dp).fillMaxWidth())
            }
            if (!firstRun && topic != GuideTopic.WELCOME) {
                FilledTonalButton(
                    onClick = { onOpenTopic(topic) },
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 52.dp).testTag("guide_open"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                ) { Text(stringResource(R.string.guide_open)) }
            }
            if (topic == GuideTopic.WELCOME || topic == GuideTopic.SHOP) {
                Text(stringResource(R.string.guide_replay_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun GuideSteps(topic: GuideTopic, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        topic.steps.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GuideNumber(index + 1)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(topic.controls[index]), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun GuideNumber(number: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(30.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center) {
        Text(number.toString(), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

