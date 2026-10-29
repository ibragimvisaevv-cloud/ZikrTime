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

/** One scene, a readable foreground and five short steps. Permissions always remain optional. */
@Composable
fun OnboardingScreen(onCityChosen: (City) -> Unit) {
    val nav = LocalNav.current
    var step by rememberSaveable { mutableIntStateOf(nav.onboardingStep) }
    val context = LocalContext.current
    val activity = context as? Activity
    val current = Lang.saved(context)
    val motion = motionEnabled()
    BackHandler(enabled = step > 0) { step-- }
    val titles = listOf(R.string.app_name, R.string.onboarding_language_title, R.string.onboarding_city_title,
        R.string.onboarding_perms_title, R.string.onboarding_ready_title)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("onboarding_step_$step")) {
        val reveal = if (LocalDensity.current.fontScale > 1.3f) 80.dp else if (step == 0) maxHeight * .40f else 112.dp
        SceneBackdrop(Modifier.fillMaxWidth().height(reveal + 200.dp))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ZikrTime", style = MaterialTheme.typography.titleLarge, color = Porcelain, modifier = Modifier.weight(1f))
                ThemeQuickButton(compact = true, onLandscape = true)
            }
            // Leave the mosque and minarets visible; smaller viewports give space back to content.
            Spacer(Modifier.height((reveal - 72.dp).coerceAtLeast(8.dp)))
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .background(MaterialTheme.colorScheme.background).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.onboarding_steps, step + 1, 5), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                    if (step > 0) TextButton(onClick = { step-- }, modifier = Modifier.testTag("onboarding_back")) { Text(stringResource(R.string.action_back)) }
                }
                Row(Modifier.padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { i -> Box(Modifier.weight(1f).height(3.dp).clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
                }
                AnimatedContent(targetState = step, transitionSpec = {
                    fadeIn(tween(if (motion) 240 else 0)) togetherWith fadeOut(tween(if (motion) 120 else 0))
                }, modifier = Modifier.weight(1f), label = "onboarding") { page ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(titles[page]), style = MaterialTheme.typography.headlineLarge)
                        when (page) {
                            0 -> {
                                Text(stringResource(R.string.slogan), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                                listOf(R.drawable.ic_nav_prayer to R.string.onboarding_feature_times,
                                    R.drawable.ic_alarm to R.string.onboarding_feature_alarm,
                                    R.drawable.ic_nav_knowledge to R.string.onboarding_feature_quiz,
                                    R.drawable.ic_nav_focus to R.string.onboarding_feature_focus,
                                    R.drawable.ic_qibla to R.string.onboarding_feature_tools).forEach { (icon, label) ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        IconBadge(painterResource(icon)); Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
                                ZCard {
                                    Text(Cities.displayName(context, Store.location), style = MaterialTheme.typography.titleLarge)
                                    Text(methodName(Store.method), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
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
