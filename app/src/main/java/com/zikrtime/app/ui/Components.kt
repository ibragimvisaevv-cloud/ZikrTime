package com.zikrtime.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Счётчик возвращений в приложение: экраны перечитывают разрешения, когда пользователь вернулся из настроек. */
object AppEvents {
    var resumeCount by mutableIntStateOf(0)
}

/** Фоновый узор из восьмиконечных звёзд (руб эль-хизб). */
@Composable
fun StarPattern(modifier: Modifier = Modifier, color: Color, cell: Float = 64f) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.2.dp.toPx())
        val step = cell.dp.toPx()
        val r = step * 0.32f
        var y = 0f
        var row = 0
        while (y < size.height + step) {
            var x = if (row % 2 == 0) 0f else step / 2f
            while (x < size.width + step) {
                drawPath(star(Offset(x, y), r), color, style = stroke)
                x += step
            }
            y += step * 0.75f
            row++
        }
    }
}

private fun star(c: Offset, r: Float): Path {
    // Два квадрата, повёрнутые на 45° относительно друг друга.
    val path = Path()
    for (square in 0..1) {
        for (i in 0..3) {
            val a = Math.toRadians(45.0 * square + 90.0 * i + 45.0)
            val px = c.x + r * cos(a).toFloat()
            val py = c.y + r * sin(a).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }
    return path
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            content()
        }
    }
}

// ---------------- Тексты и даты ----------------

private val ru = Locale("ru")

/** 1 день, 2 дня, 5 дней. */
fun plural(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}

fun days(n: Int) = plural(n, "день", "дня", "дней")

/** 75 → «1 ч 15 мин». */
fun formatMinutes(totalMinutes: Long): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0L -> "$m мин"
        m == 0L -> "$h ч"
        else -> "$h ч $m мин"
    }
}

fun gregorianDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ru))

private val hijriMonths = listOf(
    "Мухаррам", "Сафар", "Раби аль-авваль", "Раби ас-сани", "Джумада аль-уля", "Джумада ас-сания",
    "Раджаб", "Шаабан", "Рамадан", "Шавваль", "Зуль-каада", "Зуль-хиджа",
)

/** Дата по хиджре (календарь Умм аль-Кура; местное начало месяца может отличаться на день). */
fun hijriDate(date: LocalDate): String = runCatching {
    val h = HijrahDate.from(date)
    val day = h.get(ChronoField.DAY_OF_MONTH)
    val month = hijriMonths[h.get(ChronoField.MONTH_OF_YEAR) - 1]
    val year = h.get(ChronoField.YEAR)
    "$day $month $year"
}.getOrDefault("")

val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
