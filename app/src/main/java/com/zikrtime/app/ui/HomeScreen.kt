package com.zikrtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.data.Store
import com.zikrtime.app.data.Streaks
import com.zikrtime.app.prayer.OnlineTimes
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerSchedule
import com.zikrtime.app.screentime.ScreenTime
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Emerald
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.Gold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZonedDateTime

@Composable
fun HomeScreen(onOpenQuiz: () -> Unit, onOpenControl: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = ZonedDateTime.now()
        }
    }
    val today = now.toLocalDate()
    // Пересчитываем при смене дня, города или метода расчёта.
    // Пересчитываем при смене дня, города, метода, поправок или после загрузки из интернета.
    val adjustments = Prayer.entries.map { Store.adjustment(it) }
    val times = remember(today, Store.location, Store.method, Store.madhab, Store.onlineTimes, OnlineTimes.version, adjustments) {
        PrayerSchedule.forDate(today)
    }
    val next = remember(now, times) { PrayerSchedule.next(now) }

    var usedMillis by remember { mutableStateOf<Long?>(null) }
    val resume = AppEvents.resumeCount
    LaunchedEffect(now, resume, Store.blockedPackages) {
        usedMillis = withContext(Dispatchers.IO) { ScreenTime.usedTodayMillis(context) }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("Ассаляму алейкум", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${gregorianDate(today).replaceFirstChar { it.uppercase() }} · ${hijriDate(today)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        NextPrayerHero(prayer = next.first, at = next.second, now = now, city = Store.location.name)

        SectionCard(title = "Намазы сегодня") {
            val prayed = Store.prayedOn(today)
            Prayer.entries.forEach { p ->
                val time = times[p]
                val started = !time.isAfter(now)
                val isNext = p == next.first && next.second.toLocalDate() == today
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = if (p.isPrayer) 2.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        p.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (p.isPrayer) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        time.format(clock),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (p.isPrayer) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (p.isPrayer) {
                        Checkbox(
                            checked = p in prayed,
                            enabled = started,
                            onCheckedChange = { Store.setPrayed(today, p, it) },
                        )
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }
            }
            Text(
                (if (PrayerSchedule.isOnline(today)) "Время: aladhan.com, " else "Время: расчёт на телефоне, ") +
                    Store.method.title + ". Отмечайте совершённые намазы — так растёт серия.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        ScreenTimeCard(usedMillis = usedMillis, onOpenQuiz = onOpenQuiz, onOpenControl = onOpenControl)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StreakCard(
                title = "Серия знаний",
                value = Streaks.current(Store.quizDays, today),
                hint = "дней с раундом викторины",
                modifier = Modifier.weight(1f),
            )
            StreakCard(
                title = "Серия намазов",
                value = Streaks.current(Store.prayerDays, today),
                hint = "дней со всеми пятью намазами",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NextPrayerHero(prayer: Prayer, at: ZonedDateTime, now: ZonedDateTime, city: String) {
    val left = Duration.between(now, at).toMinutes().coerceAtLeast(0)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Emerald, EmeraldDeep))),
    ) {
        StarPattern(Modifier.matchParentSize(), color = Gold.copy(alpha = 0.13f))
        Column(Modifier.padding(24.dp)) {
            Text("Следующий намаз", style = MaterialTheme.typography.labelLarge, color = Cream.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(prayer.title, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Cream)
                Spacer(Modifier.width(12.dp))
                Text(
                    at.format(clock),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                    color = Gold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("через ${formatMinutes(left + 1)}", style = MaterialTheme.typography.titleMedium, color = Cream)
            Spacer(Modifier.height(12.dp))
            Text(city, style = MaterialTheme.typography.bodySmall, color = Cream.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ScreenTimeCard(usedMillis: Long?, onOpenQuiz: () -> Unit, onOpenControl: () -> Unit) {
    SectionCard(title = "Соцсети сегодня") {
        if (!Store.blockingEnabled || usedMillis == null) {
            Text(
                "Контроль времени пока не настроен. Задайте дневной лимит, и соцсети будут закрываться, когда он закончится.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenControl) { Text("Настроить") }
            return@SectionCard
        }
        val usedMin = usedMillis / 60_000
        val allowed = Store.allowedMinutes()
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatMinutes(usedMin), style = MaterialTheme.typography.headlineMedium)
            Text(
                "  из ${formatMinutes(allowed.toLong())}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (usedMin.toFloat() / allowed.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            strokeCap = StrokeCap.Round,
            color = if (usedMin >= allowed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        val bonus = Store.bonusMinutes()
        Spacer(Modifier.height(8.dp))
        Text(
            "Лимит ${formatMinutes(Store.dailyLimitMinutes.toLong())}" +
                if (bonus > 0) " + заработано ${formatMinutes(bonus.toLong())}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bonus < Store.MAX_BONUS_MINUTES_PER_DAY) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onOpenQuiz) {
                Text("Ответить на вопросы: +${Store.BONUS_MINUTES_PER_ROUND} мин")
            }
        }
    }
}

@Composable
private fun StreakCard(title: String, value: Int, hint: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                days(value),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}
