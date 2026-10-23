package com.zikrtime.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.PrayerAlarms
import com.zikrtime.app.ui.AppEvents
import com.zikrtime.app.ui.ControlScreen
import com.zikrtime.app.ui.HomeScreen
import com.zikrtime.app.ui.OnboardingScreen
import com.zikrtime.app.ui.SettingsScreen
import com.zikrtime.app.ui.TasksScreen
import com.zikrtime.app.ui.theme.ZikrTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZikrTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Экраны перепроверят разрешения — пользователь мог вернуться из системных настроек.
        AppEvents.resumeCount++
        if (Store.onboarded) PrayerAlarms.scheduleNext(this)
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Главная", Icons.Default.Home),
    Tab("Задачи", Icons.Default.Star),
    Tab("Контроль", Icons.Default.Lock),
    Tab("Настройки", Icons.Default.Settings),
)

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    if (!Store.onboarded) {
        OnboardingScreen(onCityChosen = { city ->
            Store.setLocation(city)
            Store.onboarded = true
            PrayerAlarms.scheduleNext(context)
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        })
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(onOpenQuiz = { tab = 1 }, onOpenControl = { tab = 2 })
                1 -> TasksScreen()
                2 -> ControlScreen()
                else -> SettingsScreen()
            }
        }
    }
}
