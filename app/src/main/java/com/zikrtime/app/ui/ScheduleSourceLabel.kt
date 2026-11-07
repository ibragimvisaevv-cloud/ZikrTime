package com.zikrtime.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.OnlineTimes
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerSchedule
import java.time.LocalDate

@Composable
internal fun scheduleSourceLabel(date: LocalDate, polarEstimate: Boolean = false): String {
    val publisher = PrayerSchedule.publisher(date)
    val label = when {
        polarEstimate -> stringResource(R.string.regional_polar, methodName(Store.method))
        publisher != null -> stringResource(R.string.regional_published, publisher)
        OnlineTimes.day(date) != null -> stringResource(R.string.regional_calculated, methodName(Store.method), "AlAdhan")
        else -> stringResource(R.string.regional_calculated, methodName(Store.method), stringResource(R.string.regional_offline))
    }
    val withZone = if (com.zikrtime.app.prayer.RegionalTimes.cityId() == null && OnlineTimes.manualZone() == null && OnlineTimes.cachedZone() == null) {
        stringResource(R.string.regional_device_zone, label, PrayerSchedule.zone().id)
    } else label
    return if (Prayer.entries.any { Store.adjustment(it) != 0 }) stringResource(R.string.regional_adjusted, withZone) else withZone
}
