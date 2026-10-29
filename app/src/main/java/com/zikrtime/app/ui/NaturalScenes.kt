package com.zikrtime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
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

/** Compact next-prayer header; the full timetable remains immediately underneath. */
@Composable
fun NaturalSkyHero(now: ZonedDateTime, prayer: Prayer, at: ZonedDateTime, times: PrayerTimes) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val scheme = MaterialTheme.colorScheme
    val period = if (com.zikrtime.app.BuildConfig.DEBUG && com.zikrtime.app.QaHooks.skyPreview != null)
        com.zikrtime.app.QaHooks.skyPreview!! else naturalPeriod(now.toInstant(), times[Prayer.FAJR].toInstant(), times[Prayer.SUNRISE].toInstant(), times[Prayer.MAGHRIB].toInstant(), times[Prayer.ISHA].toInstant())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                ThemeQuickButton()
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                ThemeQuickButton()
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(gregorianDate(now.toLocalDate()), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(hijriDate(now.toLocalDate()), style = MaterialTheme.typography.labelSmall, color = scheme.secondary)
            }
            Surface(onClick = { nav.open(Screen.CITY) }, color = scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 142.dp)) {
                Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_nav_prayer), null, tint = scheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Cities.displayName(context, Store.location), style = MaterialTheme.typography.labelMedium, maxLines = 2)
                }
            }
        }
        CourtyardPanel(period) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_next_prayer), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 255.dp || LocalDensity.current.fontScale > 1.3f) {
                        Column {
                            Text(prayerName(prayer), style = MaterialTheme.typography.headlineMedium)
                            BigClock(at, fontSize = 42.sp, color = scheme.onSurface, weight = FontWeight.SemiBold)
                        }
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(prayerName(prayer), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        BigClock(at, fontSize = 42.sp, color = scheme.onSurface, weight = FontWeight.SemiBold)
                    }
                }
                val mins = ((Duration.between(now, at).seconds.coerceAtLeast(0) + 59) / 60)
                Surface(color = Clay, contentColor = Porcelain, shape = RoundedCornerShape(20.dp)) {
                    Text(stringResource(R.string.home_in_time, durationText(mins)), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
        }
    }
}
