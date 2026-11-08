package com.zikrtime.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.City
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang

/** Five compact steps on the app surface; permission requests remain optional. */
@Composable
fun OnboardingScreen(onCityChosen: (City) -> Unit) {
    val nav = LocalNav.current
    var step by rememberSaveable { mutableIntStateOf(nav.onboardingStep) }
    val context = LocalContext.current
    val activity = context as? Activity
    val current = Lang.saved(context)
    val motion = motionEnabled()
    BackHandler(enabled = step > 0) { step-- }
    val titles = listOf(R.string.slogan, R.string.onboarding_language_title, R.string.onboarding_city_title,
        R.string.onboarding_perms_title, R.string.onboarding_ready_title)
    StandardSystemBars()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("onboarding_step_$step")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ZikrTime", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                ThemeQuickButton(compact = true)
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.onboarding_steps, step + 1, 5), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (step > 0) TextButton(onClick = { step-- }, modifier = Modifier.testTag("onboarding_back")) { Text(stringResource(R.string.action_back)) }
                }
                Row(Modifier.padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { i -> Box(Modifier.weight(1f).height(3.dp).clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
                }
                AnimatedContent(targetState = step, transitionSpec = {
                    fadeIn(tween(if (motion) 180 else 0)) togetherWith fadeOut(tween(if (motion) 120 else 0))
                }, modifier = Modifier.weight(1f), label = "onboarding") { page ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(titles[page]), style = MaterialTheme.typography.headlineLarge)
                        when (page) {
                            0 -> {
                                listOf(R.drawable.ic_nav_prayer to R.string.onboarding_feature_times,
                                    R.drawable.ic_alarm to R.string.onboarding_feature_alarm,
                                    R.drawable.ic_nav_knowledge to R.string.onboarding_feature_quiz,
                                    R.drawable.ic_nav_focus to R.string.onboarding_feature_focus,
                                    R.drawable.ic_qibla to R.string.onboarding_feature_tools).forEach { (icon, label) ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Icon(painterResource(icon), contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            1 -> LanguageList(current, onPick = { code -> if (code != current) activity?.let { Lang.set(it, code) } })
                            2 -> {
                                Text(stringResource(R.string.onboarding_city_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CityPicker { city -> Store.setLocation(city); step = 3 }
                            }
                            3 -> {
                                Text(stringResource(R.string.onboarding_perms_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                EssentialPermissions()
                            }
                            4 -> {
                                IconBadge(painterResource(R.drawable.ic_check), size = 64.dp)
                                Text(stringResource(R.string.onboarding_ready_text), style = MaterialTheme.typography.bodyLarge)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(Cities.displayName(context, Store.location), style = MaterialTheme.typography.titleLarge)
                                    Text(uiCopy(UiCopy0823.PrayerSettingsHint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                AppearancePicker()
                            }
                        }
                    }
                }
                if (step != 2) PrimaryButton(stringResource(when (step) {
                    0, 4 -> R.string.action_start
                    3 -> if (Perms.allGranted(context).also { AppEvents.resumeCount }) R.string.action_continue else R.string.onboarding_perms_later
                    else -> R.string.action_continue
                }), onClick = { if (step == 4) onCityChosen(Store.location) else step++ },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).testTag("onboarding_next"))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
