package com.zikrtime.app.ui

import android.content.Context
import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.AsrMadhab
import com.zikrtime.app.prayer.CalcMethod
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.ui.theme.LocalDarkTheme
import com.zikrtime.app.ui.theme.Tabular
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import kotlin.math.cos
import kotlin.math.sin

/** Счётчик возвращений в приложение: экраны перечитывают разрешения, когда пользователь вернулся из настроек. */
object AppEvents {
    var resumeCount by mutableIntStateOf(0)
}

// ---------------- Каркас экранов ----------------

/** Экран второго уровня: заголовок с кнопкой «назад» и прокручиваемое содержимое. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        val body = Modifier
            .fillMaxWidth()
            .weight(1f)
            .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
            .padding(horizontal = 20.dp)
        Column(body, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** Содержимое вкладки: заголовок крупно и прокрутка. */
@Composable
fun TabColumn(title: String? = null, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        if (title != null) {
            Column {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        content()
        Spacer(Modifier.height(112.dp))
    }
}

// ---------------- Карточки и строки ----------------

@Composable
fun ZCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Color.Unspecified,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val base = if (color == Color.Unspecified) modifier.fillMaxWidth().glass(shape) else modifier
        .fillMaxWidth()
        .shadow(8.dp, shape, ambientColor = color, spotColor = color)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.85f))))
        .border(glassBorder(), shape)
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Column(
            base
                .pressScale(interaction, 0.98f)
                .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
                .padding(padding),
            content = content,
        )
    } else {
        Column(base.padding(padding), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
fun IconBadge(
    painter: Painter,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Dp = 40.dp,
) {
    val shape = RoundedCornerShape(size * 0.34f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(container.copy(alpha = 0.55f), container)))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent)), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Строка настройки: значок, заголовок, пояснение и стрелка (или свой элемент справа). */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: Painter? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: Painter? = null,
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        trailing = { Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled) },
    )
}

/** Ползунок со значением справа от заголовка. */
@Composable
fun SliderRow(
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(((it / step).toInt() * step + if (it % step >= step / 2f) step else 0).coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    ShimmerButton(text, onClick, modifier, enabled)

@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .pressScale(interaction)
            .glass(shape, elevation = 4.dp)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .pressScale(interaction)
            .clip(shape)
            .background(color.copy(alpha = 0.06f))
            .border(BorderStroke(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.7f), color.copy(alpha = 0.2f)))), shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
    }
}

/** Кольцевой индикатор прогресса. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    stroke: Dp = 10.dp,
    content: @Composable () -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w))
            drawArc(
                color, -90f, 360f * progress.coerceIn(0f, 1f), false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w, cap = StrokeCap.Round),
            )
        }
        content()
    }
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

/** Круглая отметка «сделано». */
@Composable
fun CheckCircle(checked: Boolean, enabled: Boolean, onToggle: () -> Unit, contentDescription: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (checked) scheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(painterResource(R.drawable.ic_check), contentDescription, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = scheme.outline.copy(alpha = if (enabled) 0.9f else 0.3f),
                        radius = size.minDimension / 2 - 1.dp.toPx(),
                        style = Stroke(1.8.dp.toPx()),
                    )
                }
            }
        }
    }
}

// ---------------- Подписи ----------------

@Composable
fun prayerName(p: Prayer): String = stringResource(
    when (p) {
        Prayer.FAJR -> R.string.prayer_fajr
        Prayer.SUNRISE -> R.string.prayer_sunrise
        Prayer.DHUHR -> R.string.prayer_dhuhr
        Prayer.ASR -> R.string.prayer_asr
        Prayer.MAGHRIB -> R.string.prayer_maghrib
        Prayer.ISHA -> R.string.prayer_isha
    },
)

fun prayerName(resources: Resources, p: Prayer): String = resources.getString(
    when (p) {
        Prayer.FAJR -> R.string.prayer_fajr
        Prayer.SUNRISE -> R.string.prayer_sunrise
        Prayer.DHUHR -> R.string.prayer_dhuhr
        Prayer.ASR -> R.string.prayer_asr
        Prayer.MAGHRIB -> R.string.prayer_maghrib
        Prayer.ISHA -> R.string.prayer_isha
    },
)

@DrawableRes
fun prayerIconRes(p: Prayer): Int = when (p) {
    Prayer.FAJR -> R.drawable.ic_prayer_fajr
    Prayer.SUNRISE -> R.drawable.ic_prayer_sunrise
    Prayer.DHUHR -> R.drawable.ic_prayer_dhuhr
    Prayer.ASR -> R.drawable.ic_prayer_asr
    Prayer.MAGHRIB -> R.drawable.ic_prayer_maghrib
    Prayer.ISHA -> R.drawable.ic_prayer_isha
}

@Composable
fun methodName(m: CalcMethod): String = stringResource(
    when (m) {
        CalcMethod.MOSCOW_CATHEDRAL -> R.string.method_moscow
        CalcMethod.RUSSIA -> R.string.method_russia
        CalcMethod.MWL -> R.string.method_mwl
        CalcMethod.TURKEY -> R.string.method_turkey
        CalcMethod.MAKKAH -> R.string.method_makkah
        CalcMethod.EGYPT -> R.string.method_egypt
        CalcMethod.KARACHI -> R.string.method_karachi
        CalcMethod.ISNA -> R.string.method_isna
    },
)

@Composable
fun madhabName(m: AsrMadhab): String = stringResource(
    when (m) {
        AsrMadhab.STANDARD -> R.string.madhab_standard
        AsrMadhab.HANAFI -> R.string.madhab_hanafi
    },
)

/** «1 ч 15 мин», «45 мин», «2 ч». */
fun durationText(resources: Resources, totalMinutes: Long): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0L -> resources.getString(R.string.duration_m, m.toInt())
        m == 0L -> resources.getString(R.string.duration_h, h.toInt())
        else -> resources.getString(R.string.duration_h_m, h.toInt(), m.toInt())
    }
}

@Composable
fun durationText(totalMinutes: Long): String = durationText(LocalContext.current.resources, totalMinutes)

@Composable
fun daysText(n: Int): String = pluralStringResource(R.plurals.days, n, n)

/** Формат времени по настройкам телефона (24 или 12 часов). */
fun clockFormatter(context: Context): DateTimeFormatter =
    if (android.text.format.DateFormat.is24HourFormat(context)) DateTimeFormatter.ofPattern("HH:mm", Lang.locale(context))
    else DateTimeFormatter.ofPattern("h:mm a", Lang.locale(context))

@Composable
fun clock(time: ZonedDateTime): String {
    val context = LocalContext.current
    return time.format(clockFormatter(context))
}

/** Крупное время: цифры большим шрифтом, «AM/PM» (если телефон в 12-часовом формате) — мелко рядом. */
@Composable
fun BigClock(time: ZonedDateTime, fontSize: TextUnit, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Light) {
    val context = LocalContext.current
    val locale = Lang.locale(context)
    val h24 = android.text.format.DateFormat.is24HourFormat(context)
    val digits = time.format(DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm", locale))
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(digits, fontSize = fontSize, fontWeight = weight, color = color, style = Tabular, maxLines = 1, softWrap = false)
        if (!h24) {
            Spacer(Modifier.width(6.dp))
            Text(
                time.format(DateTimeFormatter.ofPattern("a", locale)),
                fontSize = fontSize * 0.32f,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
                modifier = Modifier.padding(bottom = (fontSize.value * 0.18f).dp),
            )
        }
    }
}

/** Короткие названия дней недели («Пн», «Mo»…), от понедельника. */
@Composable
fun weekdayShort(day: DayOfWeek): String = stringArrayResource(R.array.weekdays_short)[day.value - 1]

@Composable
fun gregorianDate(date: LocalDate): String {
    val locale = Lang.locale(LocalContext.current)
    return date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)).replaceFirstChar { it.uppercase(locale) }
}

/** Дата по хиджре (календарь Умм аль-Кура; местное начало месяца может отличаться на день). */
@Composable
fun hijriDate(date: LocalDate): String {
    val months = stringArrayResource(R.array.hijri_months)
    val parts = runCatching {
        val h = HijrahDate.from(date)
        Triple(h.get(ChronoField.DAY_OF_MONTH), months[h.get(ChronoField.MONTH_OF_YEAR) - 1], h.get(ChronoField.YEAR))
    }.getOrNull() ?: return ""
    return stringResource(R.string.hijri_date_format, parts.first, parts.second, parts.third)
}
