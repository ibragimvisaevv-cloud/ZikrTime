@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.zikrtime.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.layout.onSizeChanged
import com.zikrtime.app.R
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.*
import com.zikrtime.app.ui.theme.RhythmSpace
import java.time.Duration
import java.time.ZonedDateTime

/** Open prayer-time composition. One scroll owner retains the complete daily schedule. */
@Composable
fun HorizonHome(now: ZonedDateTime, prayer: Prayer, at: ZonedDateTime, times: PrayerTimes,
    content: @Composable ColumnScope.() -> Unit) {
    StandardSystemBars()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val context = LocalContext.current
    val nav = LocalNav.current
    val scheme = MaterialTheme.colorScheme
    val remaining = ((Duration.between(now, at).seconds.coerceAtLeast(0) + 59) / 60)
    var heroHeight by remember { mutableIntStateOf(0) }
    val compact by remember { derivedStateOf { heroHeight > 0 && scroll.value > heroHeight } }
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize().testTag("horizon_scroll").semantics { testTagsAsResourceId = true }
            .windowInsetsPadding(WindowInsets.statusBars).verticalScroll(scroll)
            .padding(horizontal = RhythmSpace.Page)) {
            Column(Modifier.fillMaxWidth().onSizeChanged { heroHeight = it.height }
                .padding(top = 8.dp, bottom = 28.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RhythmMark(Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { nav.tab = Tab.MORE }, modifier = Modifier.testTag("home_settings")) {
                        Icon(painterResource(R.drawable.ic_nav_more), clarityCopy(UiClarity0825.Settings))
                    }
                    ThemeQuickButton(compact = true)
                }
                TextButton(onClick = { nav.open(Screen.CITY) }, modifier = Modifier.testTag("home_change_city"),
                    contentPadding = PaddingValues(0.dp)) {
                    Icon(painterResource(R.drawable.ic_pin), null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Cities.displayName(context, Store.location), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Text("›", style = MaterialTheme.typography.titleMedium)
                }
                Text(gregorianDate(now.toLocalDate()) + " · " + hijriDate(now.toLocalDate()),
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).background(scheme.primary, androidx.compose.foundation.shape.CircleShape))
                    Text(stringResource(R.string.home_next_prayer), color = scheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                Text(prayerName(prayer), style = MaterialTheme.typography.headlineMedium)
                BigClock(at, if (density.fontScale > 1.3f) 52.sp else 76.sp, scheme.onSurface,
                    modifier = Modifier.testTag("horizon_time").semantics { testTagsAsResourceId = true }, weight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_in_time, durationText(remaining)),
                        style = MaterialTheme.typography.titleSmall, color = scheme.primary,
                        modifier = Modifier.weight(1f))
                }
                TextButton(onClick = { nav.open(Screen.READING_REMINDERS) },
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.testTag("home_reading_reminders")) {
                    Text(clarityCopy(UiClarity0825.Reading), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp)); Text("→")
                }
            }
            HorizontalDivider(color = scheme.outlineVariant)
            Column(Modifier.fillMaxWidth().testTag("horizon_panel").semantics { testTagsAsResourceId = true }
                .padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(RhythmSpace.Section)) {
                content()
                Spacer(Modifier.height(if (density.fontScale > 1.3f) 132.dp else 96.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        if (compact) Surface(color = scheme.background, modifier = Modifier.fillMaxWidth()
            .testTag("horizon_compact").semantics { testTagsAsResourceId = true }) {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = RhythmSpace.Page),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_next_prayer), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        Text(prayerName(prayer), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    BigClock(at, 28.sp, scheme.primary, weight = FontWeight.Medium)
                    ThemeQuickButton(compact = true)
                }
                HorizontalDivider(color = scheme.outlineVariant)
            }
        }
    }
}

/** The app's rhythm: two intervals and a quiet pause. Not the launcher icon. */
@Composable
fun RhythmMark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val stroke = size.width * .12f
        drawLine(ink, Offset(size.width * .2f, size.height * .18f), Offset(size.width * .2f, size.height * .82f), stroke, StrokeCap.Round)
        drawLine(ink, Offset(size.width * .5f, size.height * .38f), Offset(size.width * .5f, size.height * .82f), stroke, StrokeCap.Round)
        drawCircle(ink, stroke * .65f, Offset(size.width * .82f, size.height * .76f))
    }
}
