package com.zikrtime.app.screentime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.quiz.QuestionBank
import com.zikrtime.app.ui.GhostButton
import com.zikrtime.app.ui.QuizRound
import com.zikrtime.app.ui.StarPattern
import com.zikrtime.app.ui.clock
import com.zikrtime.app.ui.durationText
import com.zikrtime.app.ui.prayerIconRes
import com.zikrtime.app.ui.prayerName
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Emerald
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.Tabular
import com.zikrtime.app.ui.theme.ZikrTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Экран поверх соцсети: лимит исчерпан или наступило время намаза. */
class BlockActivity : ComponentActivity() {

    private var reason by mutableStateOf<BlockReason>(BlockReason.None)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        // Фон тёмно-зелёный, поэтому значки строки состояния всегда светлые.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        reason = parse(intent)
        // «Назад» тоже уводит на рабочий стол, а не обратно в соцсеть.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeToHome()
        })
        setContent {
            ZikrTheme {
                BlockScreen(reason = reason, onClose = { closeToHome() }, onBackToApp = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        GuardService.hideOverlay(this)
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    private fun closeToHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reason = parse(intent)
    }

    companion object {
        /** Экран блокировки сейчас на экране — если нет, служба покажет запасной экран поверх приложений. */
        @Volatile
        var visible = false

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
private fun BlockScreen(reason: BlockReason, onClose: () -> Unit, onBackToApp: () -> Unit) {
    val context = LocalContext.current
    var mode by remember(reason) { mutableStateOf<BlockMode>(BlockMode.Info) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Emerald, EmeraldDeep)))) {
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
                    Text(stringResource(R.string.block_quiz_title), style = MaterialTheme.typography.headlineSmall, color = Cream)
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                        QuizRound(
                            bank = remember { QuestionBank.load(context) },
                            category = null,
                            onFinished = {
                                val added = Store.addBonus()
                                Store.recordRoundCompleted()
                                mode = BlockMode.Earned(added)
                            },
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                    GhostButton(stringResource(R.string.action_close), onClick = onClose, color = Cream)
                }

                is BlockMode.Earned -> {
                    Spacer(Modifier.height(48.dp))
                    Title(stringResource(R.string.quiz_mashallah))
                    Body(
                        if (m.minutes > 0) stringResource(R.string.block_earned, m.minutes)
                        else stringResource(R.string.quiz_result_no_bonus),
                    )
                    Spacer(Modifier.height(24.dp))
                    // Время добавлено — можно сразу вернуться туда, где пользователь был.
                    GoldButton(stringResource(R.string.action_done), if (m.minutes > 0) onBackToApp else onClose)
                }

                BlockMode.Info -> when (reason) {
                    is BlockReason.LimitReached -> LimitInfo(reason, onQuiz = { mode = BlockMode.Quiz }, onClose = onClose)
                    is BlockReason.PrayerTime -> PrayerInfo(reason, onClose = onClose)
                    BlockReason.None -> {
                        Spacer(Modifier.height(48.dp))
                        Title(stringResource(R.string.app_name))
                        GoldButton(stringResource(R.string.action_close), onClose)
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitInfo(reason: BlockReason.LimitReached, onQuiz: () -> Unit, onClose: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Icon(painterResource(R.drawable.ic_nav_focus), contentDescription = null, tint = Gold, modifier = Modifier.size(48.dp))
    Title(stringResource(R.string.block_limit_title))
    Body(stringResource(R.string.block_limit_text, durationText(reason.usedMinutes.toLong()), durationText(reason.allowedMinutes.toLong())))
    Text(stringResource(R.string.slogan), style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic, color = Gold)
    Spacer(Modifier.height(24.dp))
    if (Store.bonusMinutes() < Store.MAX_BONUS_MINUTES_PER_DAY) {
        GoldButton(stringResource(R.string.block_quiz_button, Store.CORRECT_ANSWERS_PER_ROUND, Store.BONUS_MINUTES_PER_ROUND), onQuiz)
    } else {
        Body(stringResource(R.string.block_bonus_used, Store.MAX_BONUS_MINUTES_PER_DAY))
    }
    GhostButton(stringResource(R.string.action_close), onClick = onClose, color = Cream)
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

    Spacer(Modifier.height(48.dp))
    Icon(painterResource(prayerIconRes(reason.prayer)), contentDescription = null, tint = Gold, modifier = Modifier.size(48.dp))
    Text(stringResource(R.string.block_prayer_label), style = MaterialTheme.typography.titleMedium, color = Gold)
    Text(prayerName(reason.prayer), fontSize = 48.sp, color = Cream, style = MaterialTheme.typography.displaySmall)
    if (left.isNegative || left.isZero) {
        Body(stringResource(R.string.block_prayer_open))
        Spacer(Modifier.height(24.dp))
        GoldButton(stringResource(R.string.action_close), onClose)
        return
    }
    Body(stringResource(R.string.block_prayer_text, clock(reason.endsAt.atZone(ZoneId.systemDefault()))))
    Text(
        String.format(Lang.locale(LocalContext.current), "%d:%02d", left.toMinutes(), left.seconds % 60),
        fontSize = 44.sp,
        color = Cream.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
        style = Tabular,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    GoldButton(stringResource(R.string.block_prayed)) {
        Store.setPrayed(reason.date, reason.prayer, true)
        Store.dismissLock(reason.date, reason.prayer)
        onClose()
    }
    GhostButton(stringResource(R.string.action_close), onClick = onClose, color = Cream)
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
private fun GoldButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF2B1E00)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(text, textAlign = TextAlign.Center) }
}
