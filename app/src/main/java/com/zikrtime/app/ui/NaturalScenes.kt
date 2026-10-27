package com.zikrtime.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.*
import androidx.compose.ui.platform.LocalContext
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import androidx.compose.ui.unit.*
import com.zikrtime.app.R
import com.zikrtime.app.prayer.*
import java.time.*

val LocalNatural = staticCompositionLocalOf { false }

@Composable
fun NaturalSkyHero(now: ZonedDateTime, prayer: Prayer, at: ZonedDateTime, times: PrayerTimes) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val period = if (com.zikrtime.app.BuildConfig.DEBUG && com.zikrtime.app.QaHooks.skyPreview != null)
        com.zikrtime.app.QaHooks.skyPreview!! else naturalPeriod(now.toInstant(), times[Prayer.FAJR].toInstant(), times[Prayer.SUNRISE].toInstant(), times[Prayer.MAGHRIB].toInstant(), times[Prayer.ISHA].toInstant())
    val res = when (period) {
        NaturalPeriod.DAWN -> R.drawable.sky_dawn
        NaturalPeriod.DAY -> R.drawable.sky_day
        NaturalPeriod.SUNSET -> R.drawable.sky_sunset
        NaturalPeriod.NIGHT -> R.drawable.sky_night
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).border(1.dp, Color(0x667E95AA), RoundedCornerShape(28.dp))) {
        Crossfade(res, animationSpec = tween(450), label = "daylight", modifier = Modifier.matchParentSize()) { image ->
            Image(painterResource(image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xCF101C2D), Color(0x10101C2D)))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA101C2D)))))
        Column(Modifier.padding(22.dp).heightIn(min = 350.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                Surface(onClick = { nav.open(Screen.CITY) }, color = Color(0x993A4D63), contentColor = Color.White, shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 150.dp)) {
                    Text(Cities.displayName(context, Store.location), style = MaterialTheme.typography.labelLarge, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            Text(gregorianDate(now.toLocalDate()), color = Color(0xFFE1E8F0), style = MaterialTheme.typography.bodyMedium)
            Text(hijriDate(now.toLocalDate()), color = Color(0xFFCFDAE7), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.home_next_prayer), color = Color(0xFFE1E8F0), style = MaterialTheme.typography.titleMedium)
            Text(prayerName(prayer), color = Color.White, style = MaterialTheme.typography.headlineLarge)
            BigClock(at, fontSize = 52.sp, color = Color.White)
            val mins = ((Duration.between(now, at).seconds.coerceAtLeast(0) + 59) / 60)
            Text(stringResource(R.string.home_in_time, durationText(mins)), color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xA62D3A4C)).padding(horizontal = 12.dp, vertical = 7.dp))
            Spacer(Modifier.height(38.dp))
        }
    }
}
