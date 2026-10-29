@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.zikrtime.app.R
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.*
import com.zikrtime.app.ui.theme.LocalDarkTheme
import java.time.Duration
import java.time.ZonedDateTime

/** One native scroll owner covers both the open landscape and the sheet. No drag sheet,
 * nested scrolling hand-off, blur, or bitmap allocation in a scroll frame. */
@Composable
fun HorizonHome(now: ZonedDateTime, prayer: Prayer, at: ZonedDateTime, times: PrayerTimes,
    content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val large = density.fontScale > 1.3f
    val dark = LocalDarkTheme.current
    val context = LocalContext.current
    val nav = LocalNav.current
    val scheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val motion = remember(AppEvents.resumeCount) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val period = if (com.zikrtime.app.BuildConfig.DEBUG && com.zikrtime.app.QaHooks.skyPreview != null)
        com.zikrtime.app.QaHooks.skyPreview!! else naturalPeriod(now.toInstant(), times[Prayer.FAJR].toInstant(),
            times[Prayer.SUNRISE].toInstant(), times[Prayer.MAGHRIB].toInstant(), times[Prayer.ISHA].toInstant())
    val remaining = ((Duration.between(now, at).seconds.coerceAtLeast(0) + 59) / 60)
    val countdown = stringResource(R.string.home_in_time, durationText(remaining))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val compactHeight = status + if (large) 112.dp else 76.dp
        val revealHeight = maxOf(maxHeight * .72f, status + if (large) 510.dp else 410.dp)
        var measuredHero by remember { mutableIntStateOf(0) }
        val collapsePx = (if (measuredHero > 0) measuredHero - with(density) { compactHeight.toPx() }
            else with(density) { (revealHeight - compactHeight).toPx() }).coerceAtLeast(1f)
        val compact by remember(collapsePx) { derivedStateOf { scroll.value / collapsePx >= .86f } }
        // A light sheet uses dark status icons only once it reaches the system bar.
        SideEffect {
            (view.context as? android.app.Activity)?.let { activity ->
                androidx.core.view.WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = compact && !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }

        val imageId = if (period == NaturalPeriod.NIGHT) R.drawable.horizon_portrait_night else R.drawable.horizon_portrait_day
        Box(Modifier.matchParentSize().background(Color(0xFF34615D))) {
            if (!(com.zikrtime.app.BuildConfig.DEBUG && com.zikrtime.app.QaHooks.hideScene)) {
                Image(painterResource(imageId), null, Modifier.fillMaxSize().graphicsLayer {
                    // Image overfill keeps the frame covered throughout its small parallax travel.
                    scaleX = 1.08f; scaleY = 1.08f
                    translationY = if (motion) -scroll.value.coerceAtMost(collapsePx.toInt()) * .055f else 0f
                }, contentScale = ContentScale.Crop, alignment = Alignment.Center)
            }
            Box(Modifier.matchParentSize().drawBehind {
                val p = (scroll.value / collapsePx).coerceIn(0f, 1f)
                // Local protection for typography, leaving the mosque and warm light exposed.
                drawRect(Brush.verticalGradient(0f to Color(0xB309242D), .44f to Color(0x2409242D), .66f to Color.Transparent))
                drawRect(Brush.horizontalGradient(listOf(Color(0x4807242B), Color.Transparent)))
                val tint = when (period) {
                    NaturalPeriod.DAWN -> Color(0x12E6B18D)
                    NaturalPeriod.SUNSET -> Color(0x28C67A49)
                    else -> Color.Transparent
                }
                drawRect(tint)
                drawRect(Color(0xFF061D24).copy(alpha = p * .28f))
            })
        }

        Column(Modifier.fillMaxSize().testTag("horizon_scroll").semantics { testTagsAsResourceId = true }
            .verticalScroll(scroll)) {
            Column(Modifier.fillMaxWidth().heightIn(min = revealHeight).onSizeChanged { measuredHero = it.height }.padding(horizontal = 24.dp)
                .padding(top = status + 8.dp)
                .graphicsLayer { alpha = (1f - (scroll.value / collapsePx) / .72f).coerceIn(0f, 1f) }
                .then(if (compact) Modifier.clearAndSetSemantics {} else Modifier),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), color = Porcelain, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    ThemeQuickButton(compact = true, onLandscape = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { nav.open(Screen.CITY) }, contentPadding = PaddingValues(end = 8.dp)) {
                        Icon(painterResource(R.drawable.ic_mosque), null, tint = Sand, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(Cities.displayName(context, Store.location), color = Porcelain,
                            style = MaterialTheme.typography.labelLarge, maxLines = 2)
                    }
                }
                Text(gregorianDate(now.toLocalDate()), color = Porcelain, style = MaterialTheme.typography.labelMedium)
                Text(hijriDate(now.toLocalDate()), color = Sand, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(if (large) 10.dp else 22.dp))
                Text(stringResource(R.string.home_next_prayer), color = Porcelain.copy(alpha = .88f), style = MaterialTheme.typography.labelLarge)
                Text(prayerName(prayer), color = Porcelain, style = MaterialTheme.typography.headlineLarge,
                    fontSize = if (large) 28.sp else 36.sp)
                BigClock(at, if (large) 44.sp else 68.sp, Porcelain, modifier = Modifier.testTag("horizon_time").semantics { testTagsAsResourceId = true }, weight = FontWeight.Normal)
                Text(countdown, color = Sand, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xB307252D))
                        .padding(horizontal = 12.dp, vertical = 7.dp))
                Spacer(Modifier.height(24.dp))
            }
            Column(Modifier.fillMaxWidth().testTag("horizon_panel").semantics { testTagsAsResourceId = true }
                .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .background(scheme.background)
                .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // A tactile seam signals ordinary vertical scrolling, without a competing drag handle.
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp).width(38.dp).height(3.dp)
                    .background(scheme.secondary.copy(alpha = .45f), RoundedCornerShape(2.dp)))
                content()
                Spacer(Modifier.height(96.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        val compactAlpha by remember(collapsePx) { derivedStateOf {
            ((scroll.value / collapsePx - .72f) / .22f).coerceIn(0f, 1f)
        } }
        if (compactAlpha > 0f) {
            Column(Modifier.fillMaxWidth().height(compactHeight).graphicsLayer { alpha = compactAlpha }
                .background(scheme.background.copy(alpha = .98f))
                .padding(top = status).padding(horizontal = 20.dp)
                .testTag("horizon_compact").semantics { testTagsAsResourceId = true },
                verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_next_prayer), style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(prayerName(prayer), style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BigClock(at, if (large) 22.sp else 30.sp, scheme.onSurface, weight = FontWeight.Medium)
                    ThemeQuickButton(compact = true)
                }
            }
        }
    }
}
