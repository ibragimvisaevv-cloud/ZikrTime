package com.zikrtime.app.screentime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.quiz.QuestionBank
import com.zikrtime.app.ui.QuizRound
import com.zikrtime.app.ui.StarPattern
import com.zikrtime.app.ui.formatMinutes
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Emerald
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.ZikrTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Экран поверх соцсети: лимит исчерпан или наступило время намаза. */
class BlockActivity : ComponentActivity() {

    private var reason by mutableStateOf<BlockReason>(BlockReason.None)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Фон тёмно-зелёный, поэтому значки строки состояния всегда светлые.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        reason = parse(intent)
        setContent {
            ZikrTheme {
                BlockScreen(reason = reason, onClose = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reason = parse(intent)
    }

    companion object {
        private const val TYPE = "type"
        private const val USED = "used"
        private const val ALLOWED = "allowed"
        private const val PRAYER = "prayer"
        private const val DATE = "date"
        private const val ENDS = "ends"

        fun intent(context: Context, reason: BlockReason): Intent =
            Intent(context, BlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                when (reason) {
                    is BlockReason.LimitReached -> {
                        putExtra(TYPE, "limit")
                        putExtra(USED, reason.usedMinutes)
                        putExtra(ALLOWED, reason.allowedMinutes)
                    }
                    is BlockReason.PrayerTime -> {
                        putExtra(TYPE, "prayer")
                        putExtra(PRAYER, reason.prayer.name)
                        putExtra(DATE, reason.date.toString())
                        putExtra(ENDS, reason.endsAt.toEpochMilli())
                    }
                    BlockReason.None -> Unit
                }
            }

        private fun parse(intent: Intent?): BlockReason {
            intent ?: return BlockReason.None
            return when (intent.getStringExtra(TYPE)) {
                "limit" -> BlockReason.LimitReached(intent.getIntExtra(USED, 0), intent.getIntExtra(ALLOWED, 0))
                "prayer" -> {
                    val prayer = runCatching { Prayer.valueOf(intent.getStringExtra(PRAYER) ?: "") }.getOrNull()
                    val date = runCatching { LocalDate.parse(intent.getStringExtra(DATE)) }.getOrNull()
                    if (prayer == null || date == null) BlockReason.None
                    else BlockReason.PrayerTime(prayer, date, Instant.ofEpochMilli(intent.getLongExtra(ENDS, 0L)))
                }
                else -> BlockReason.None
            }
        }
    }
}

private sealed class BlockMode {
    data object Info : BlockMode()
    data object Quiz : BlockMode()
    data class Earned(val minutes: Int) : BlockMode()
}

@Composable
private fun BlockScreen(reason: BlockReason, onClose: () -> Unit) {
    val context = LocalContext.current
    var mode by remember(reason) { mutableStateOf<BlockMode>(BlockMode.Info) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Emerald, EmeraldDeep))),
    ) {
        StarPattern(Modifier.fillMaxSize(), color = Gold.copy(alpha = 0.09f), cell = 72f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val m = mode) {
                BlockMode.Quiz -> {
                    Text("Ответьте на вопросы", style = MaterialTheme.typography.headlineSmall, color = Cream)
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                        QuizRound(
                            questions = remember { QuestionBank.all(context) },
                            onFinished = {
                                val added = Store.addBonus()
                                Store.recordRoundCompleted()
                                mode = BlockMode.Earned(added)
                            },
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                    SecondaryButton("Закрыть", onClose)
                }

                is BlockMode.Earned -> {
                    Spacer(Modifier.height(48.dp))
                    Title("МашаАллах!")
                    Body(
                        if (m.minutes > 0) "Добавлено ${m.minutes} мин. Можете вернуться в приложение."
                        else "Раунд пройден, но бонус на сегодня уже набран полностью.",
                    )
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton("Готово", onClose)
                }

                BlockMode.Info -> when (reason) {
                    is BlockReason.LimitReached -> LimitInfo(reason, onQuiz = { mode = BlockMode.Quiz }, onClose = onClose)
                    is BlockReason.PrayerTime -> PrayerInfo(reason, onClose = onClose)
                    BlockReason.None -> {
                        Spacer(Modifier.height(48.dp))
                        Title("Зикр-тайм")
                        PrimaryButton("Закрыть", onClose)
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitInfo(reason: BlockReason.LimitReached, onQuiz: () -> Unit, onClose: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Title("Лимит на сегодня исчерпан")
    Body("Сегодня в соцсетях: ${formatMinutes(reason.usedMinutes.toLong())}. Лимит — ${formatMinutes(reason.allowedMinutes.toLong())}.")
    Text(
        "Сначала зикр, потом скролл",
        style = MaterialTheme.typography.titleMedium,
        fontStyle = FontStyle.Italic,
        color = Gold,
    )
    Spacer(Modifier.height(24.dp))
    if (Store.bonusMinutes() < Store.MAX_BONUS_MINUTES_PER_DAY) {
        PrimaryButton(
            "Ответить на ${Store.CORRECT_ANSWERS_PER_ROUND} вопроса: +${Store.BONUS_MINUTES_PER_ROUND} мин",
            onQuiz,
        )
    } else {
        Body("Бонус на сегодня уже набран полностью (${Store.MAX_BONUS_MINUTES_PER_DAY} мин). Завтра лимит начнётся заново.")
    }
    SecondaryButton("Закрыть", onClose)
}

@Composable
private fun PrayerInfo(reason: BlockReason.PrayerTime, onClose: () -> Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(reason) {
        while (now.isBefore(reason.endsAt)) {
            delay(1_000)
            now = Instant.now()
        }
    }
    val left = Duration.between(now, reason.endsAt)
    val opensAt = reason.endsAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

    Spacer(Modifier.height(48.dp))
    Text("Наступило время намаза", style = MaterialTheme.typography.titleMedium, color = Gold)
    Text(reason.prayer.title, fontSize = 48.sp, color = Cream, style = MaterialTheme.typography.displaySmall)
    if (left.isNegative || left.isZero) {
        Body("Доступ к соцсетям снова открыт.")
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Закрыть", onClose)
        return
    }
    Body("Соцсети откроются в $opensAt. Самое время встать на намаз.")
    Text(
        String.format("%d:%02d", left.toMinutes(), left.seconds % 60),
        fontSize = 40.sp,
        color = Cream.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    PrimaryButton("Я совершил намаз") {
        Store.setPrayed(reason.date, reason.prayer, true)
        Store.dismissLock(reason.date, reason.prayer)
        onClose()
    }
    SecondaryButton("Закрыть", onClose)
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = Cream)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Cream.copy(alpha = 0.9f))
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF2B1E00)),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(text, textAlign = TextAlign.Center) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(text) }
}
