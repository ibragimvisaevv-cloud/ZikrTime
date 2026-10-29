package com.zikrtime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import com.zikrtime.app.R
import com.zikrtime.app.prayer.*
import java.time.*

val LocalNatural = staticCompositionLocalOf { false }

/** Panorama and a compact, readable prayer clock above the six-cell timetable. */
@Composable
fun NaturalSkyHero(now: ZonedDateTime, prayer: Prayer, at: ZonedDateTime, times: PrayerTimes) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val large = LocalDensity.current.fontScale > 1.3f
    val period = if (com.zikrtime.app.BuildConfig.DEBUG && com.zikrtime.app.QaHooks.skyPreview != null)
        com.zikrtime.app.QaHooks.skyPreview!! else naturalPeriod(now.toInstant(), times[Prayer.FAJR].toInstant(), times[Prayer.SUNRISE].toInstant(), times[Prayer.MAGHRIB].toInstant(), times[Prayer.ISHA].toInstant())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 300.dp || large) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                ThemeQuickButton()
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                ThemeQuickButton()
            }
        }
        Box(Modifier.fillMaxWidth()) {
            HorizonLandscape(period, Modifier.matchParentSize(), motion = true)
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val date: @Composable () -> Unit = {
                    Text(gregorianDate(now.toLocalDate()), style = MaterialTheme.typography.labelSmall, color = Porcelain)
                    Text(hijriDate(now.toLocalDate()), style = MaterialTheme.typography.labelSmall, color = Sand)
                }
                val city: @Composable () -> Unit = {
                    Surface(onClick = { nav.open(Screen.CITY) }, color = Color(0xA6083028), contentColor = Porcelain,
                        shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Sand.copy(alpha = .35f))) {
                        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_mosque), null, tint = Sand, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(Cities.displayName(context, Store.location), style = MaterialTheme.typography.labelMedium, maxLines = 2)
                        }
                    }
                }
                if (large) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { date(); city() }
                else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { date() }
                    Box(Modifier.widthIn(max = 142.dp)) { city() }
                }
                Spacer(Modifier.height(if (large) 6.dp else 24.dp))
                Text(stringResource(R.string.home_next_prayer), color = Porcelain.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(prayerName(prayer), style = MaterialTheme.typography.headlineMedium, color = Porcelain)
                        BigClock(at, fontSize = if (large) 38.sp else 48.sp, color = Porcelain, weight = FontWeight.SemiBold)
                    }
                }
                val mins = ((Duration.between(now, at).seconds.coerceAtLeast(0) + 59) / 60)
                Text(stringResource(R.string.home_in_time, durationText(mins)), style = MaterialTheme.typography.labelMedium, color = Sand)
            }
        }
    }
}
