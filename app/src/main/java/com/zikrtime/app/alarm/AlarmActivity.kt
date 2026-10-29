package com.zikrtime.app.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerSchedule
import com.zikrtime.app.quiz.QuestionBank
import com.zikrtime.app.ui.BigClock
import com.zikrtime.app.ui.GhostButton
import com.zikrtime.app.ui.QuizRound
import com.zikrtime.app.ui.StarPattern
import com.zikrtime.app.ui.clock
import com.zikrtime.app.ui.theme.ArabicStyle
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.EmeraldNight
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.ZikrTheme
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

/** Полноэкранный будильник поверх экрана блокировки. Кнопка «назад» его не закрывает. */
class AlarmActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit // только кнопками на экране
        })
        val test = intent.getBooleanExtra(FajrAlarm.EXTRA_TEST, false)
        setContent {
            ZikrTheme {
                AlarmScreen(
                    test = test,
                    onStop = {
                        AlarmService.stop(this)
                        finish()
                    },
                    onSnooze = {
                        AlarmService.snooze(this)
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onPause() {
        super.onPause()
        visible = false
    }

    companion object {
        /** Экран будильника сейчас на экране (служба проверяет, удалось ли его показать). */
        @Volatile
        var visible = false

        fun intent(context: Context, test: Boolean): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(FajrAlarm.EXTRA_TEST, test)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun AlarmScreen(test: Boolean, onStop: () -> Unit, onSnooze: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = ZonedDateTime.now()
        }
    }
    val fajr = remember {
        val n = ZonedDateTime.now()
        val today = PrayerSchedule.forDate(n.toLocalDate())[Prayer.FAJR]
        if (today.isBefore(n.minusHours(3))) PrayerSchedule.forDate(n.toLocalDate().plusDays(1))[Prayer.FAJR] else today
    }
    var checking by remember { mutableStateOf(false) }
    val bank = remember { QuestionBank.load(context) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(EmeraldNight, EmeraldDeep, EmeraldNight))),
    ) {
        com.zikrtime.app.ui.SceneBackdrop(Modifier.fillMaxSize(), night = true)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (test) {
                Text(stringResource(R.string.alarm_test_label), style = MaterialTheme.typography.labelLarge, color = Gold)
            }
            // Время и слова азана — по центру экрана; если не помещаются, прокручиваются.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                ) {
                    BigClock(now, fontSize = if (checking) 48.sp else 84.sp, color = Cream)
                    Text(
                        stringResource(R.string.alarm_fajr_at, clock(fajr)),
                        style = MaterialTheme.typography.titleLarge,
                        color = Gold,
                        textAlign = TextAlign.Center,
                    )
                    if (!checking) {
                        Spacer(Modifier.height(12.dp))
                        // «Намаз лучше сна» — слова из утреннего азана
                        Text(
                            "الصَّلَاةُ خَيْرٌ مِنَ النَّوْمِ",
                            style = ArabicStyle.copy(fontSize = 34.sp, lineHeight = 58.sp, textDirection = TextDirection.Rtl),
                            color = Cream,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.alarm_better_than_sleep),
                            style = MaterialTheme.typography.titleMedium,
                            color = Cream.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                            QuizRound(
                                bank = bank,
                                category = null,
                                target = 1,
                                finishText = stringResource(R.string.alarm_stop),
                                sessionScope = "alarm",
                                onFinished = {
                                    com.zikrtime.app.quiz.LearningStore.clearSession("alarm")
                                    onStop()
                                },
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }
            }
            if (!checking) {
                Spacer(Modifier.height(16.dp))
                if (Store.alarmWakeCheck) {
                    AlarmButton(stringResource(R.string.alarm_answer_to_stop), onClick = { checking = true })
                } else {
                    AlarmButton(stringResource(R.string.alarm_awake), onClick = onStop)
                }
                if (Store.alarmSnoozeAllowed) {
                    Spacer(Modifier.height(10.dp))
                    GhostButton(stringResource(R.string.alarm_snooze, AlarmService.SNOOZE_MINUTES), onClick = onSnooze, color = Cream)
                }
            }
        }
    }
}

@Composable
private fun AlarmButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Gold, contentColor = androidx.compose.ui.graphics.Color(0xFF2B1E00)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}
