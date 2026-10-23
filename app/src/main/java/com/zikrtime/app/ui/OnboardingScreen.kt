package com.zikrtime.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.City
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Emerald
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.EmeraldNight
import com.zikrtime.app.ui.theme.Gold

/** Первый запуск: язык → знакомство → город. */
@Composable
fun OnboardingScreen(onCityChosen: (City) -> Unit) {
    // Шаг сохраняется при пересоздании экрана (после смены языка экран перезапускается).
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    when (step) {
        0 -> LanguageStep(onNext = { step = 1 })
        1 -> Welcome(onNext = { step = 2 })
        else -> Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.onboarding_city_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.onboarding_city_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CityPicker(onPicked = onCityChosen)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LanguageStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val current = Lang.saved(context)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(Modifier.size(56.dp))
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        }
        Text(stringResource(R.string.onboarding_language_title), style = MaterialTheme.typography.headlineMedium)
        ZCard(padding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)) {
            LanguageList(current = current, onPick = { code -> if (code != current) activity?.let { Lang.set(it, code) } })
        }
        PrimaryButton(stringResource(R.string.action_continue), onClick = onNext)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Emerald, EmeraldDeep, EmeraldNight)))) {
        StarPattern(Modifier.fillMaxSize(), color = Gold.copy(alpha = 0.09f), cell = 72f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            AppIconImage(Modifier.size(132.dp))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name), fontSize = 38.sp, fontWeight = FontWeight.SemiBold, color = Cream)
            Text(stringResource(R.string.slogan), style = MaterialTheme.typography.titleMedium, color = Gold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Feature(R.drawable.ic_nav_prayer, stringResource(R.string.onboarding_feature_times))
                Feature(R.drawable.ic_alarm, stringResource(R.string.onboarding_feature_alarm))
                Feature(R.drawable.ic_nav_focus, stringResource(R.string.onboarding_feature_focus))
                Feature(R.drawable.ic_nav_knowledge, stringResource(R.string.onboarding_feature_quiz))
                Feature(R.drawable.ic_qibla, stringResource(R.string.onboarding_feature_tools))
            }
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF2B1E00)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.action_start), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Feature(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Cream.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = Gold, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Cream)
    }
}
