package com.zikrtime.app.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.Reviews
import com.zikrtime.app.ui.theme.Gold
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val StarOff = Color(0x55B9A57A)

/** Звёзды рейтинга (дробные округляются до половинки — показываем целые и пустые). */
@Composable
fun Stars(rating: Double, size: TextUnit = 14.sp) {
    val full = (rating + 0.25).toInt().coerceIn(0, 5)
    Row {
        repeat(5) { i -> Text("★", fontSize = size, color = if (i < full) Gold else StarOff) }
    }
}

/** Компактный рейтинг: ★ 4,6 (12). Ничего не показывает, если отзывов нет. */
@Composable
fun RatingBadge(target: String, modifier: Modifier = Modifier) {
    val s = Reviews.data?.reviews?.get(target) ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("★", color = Gold, fontSize = 14.sp)
        Spacer(Modifier.width(3.dp))
        Text(
            String.format(Locale.US, "%.1f", s.avg).replace('.', ',') + " (${s.count})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Блок «Отзывы»: средняя оценка, последние отзывы и кнопка «Оставить отзыв». */
@Composable
fun ReviewsBlock(target: String, inCard: Boolean = true) {
    LaunchedEffect(Unit) { Reviews.refresh() }
    val summary = Reviews.data?.reviews?.get(target)
    var showAll by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    var sent by remember(target) { mutableStateOf(Reviews.alreadySent(target)) }
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reviews_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (summary != null) {
                    Text(
                        String.format(Locale.US, "%.1f", summary.avg).replace('.', ','),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Stars(summary.avg, 16.sp)
                }
            }
            if (summary == null) {
                Text(stringResource(R.string.reviews_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val shown = if (showAll) summary.items else summary.items.take(3)
                shown.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ReviewItem(item)
                }
                if (!showAll && summary.items.size > 3) {
                    TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.reviews_show_all, summary.count)) }
                }
            }
            if (sent) {
                Text(stringResource(R.string.reviews_sent_already), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                TonalButton(stringResource(R.string.reviews_write), onClick = { writing = true })
            }
        }
    }
    if (inCard) ZCard { content() } else content()
    if (writing) {
        ReviewDialog(target, onDismiss = { writing = false }, onSent = {
            writing = false
            sent = true
        })
    }
}

@Composable
private fun ReviewItem(item: Reviews.Item) {
    val date = remember(item.date) {
        Instant.ofEpochSecond(item.date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stars(item.rating.toDouble(), 13.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                listOf(item.name, date).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.text.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(item.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReviewDialog(target: String, onDismiss: () -> Unit, onSent: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rating by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val thanks = stringResource(R.string.reviews_thanks)
    val offline = stringResource(R.string.reviews_offline)
    val error = stringResource(R.string.reviews_error)
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.reviews_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reviews_rate), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 1..5) {
                        val pop = remember { Animatable(1f) }
                        LaunchedEffect(rating) {
                            if (rating == i) {
                                pop.snapTo(1.4f)
                                pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f))
                            }
                        }
                        Text(
                            "★",
                            fontSize = 36.sp,
                            color = if (i <= rating) Gold else StarOff,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = pop.value
                                    scaleY = pop.value
                                }
                                .clickable { rating = i },
                        )
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text(stringResource(R.string.reviews_name_hint)) },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(600) }, minLines = 3, maxLines = 6,
                    label = { Text(stringResource(R.string.reviews_text_hint)) },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.reviews_rules), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = rating > 0 && !sending, onClick = {
                sending = true
                scope.launch {
                    val r = Reviews.send(target, rating, text, name)
                    sending = false
                    when (r) {
                        Reviews.SendResult.OK -> {
                            Toast.makeText(context, thanks, Toast.LENGTH_LONG).show()
                            onSent()
                        }
                        Reviews.SendResult.OFFLINE -> Toast.makeText(context, offline, Toast.LENGTH_LONG).show()
                        Reviews.SendResult.ERROR -> Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                }
            }) { Text(stringResource(R.string.reviews_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(android.R.string.cancel)) } },
    )
}
