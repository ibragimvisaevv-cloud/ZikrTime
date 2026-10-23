package com.zikrtime.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.City
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Emerald
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.Gold

@Composable
fun OnboardingScreen(onCityChosen: (City) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    if (step == 0) {
        Welcome(onNext = { step = 1 })
    } else {
        CityPicker(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            onPicked = onCityChosen,
            header = {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("Где вы находитесь?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Город нужен, чтобы рассчитать время намаза. Его можно поменять в настройках.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Emerald, EmeraldDeep))),
    ) {
        StarPattern(Modifier.fillMaxSize(), color = Gold.copy(alpha = 0.10f), cell = 72f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(168.dp)
                    .clip(CircleShape)
                    .background(EmeraldDeep),
            )
            Spacer(Modifier.height(16.dp))
            Text("Зикр-тайм", fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Cream)
            Text(
                "Сначала зикр, потом скролл",
                style = MaterialTheme.typography.titleMedium,
                color = Gold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Feature("Время намаза для вашего города и напоминания об азане")
                Feature("Дневной лимит на соцсети — закончилось время, соцсеть закрывается")
                Feature("Ответьте на вопросы об Исламе и заработайте ещё несколько минут")
                Feature("Серии дней: знания и все пять намазов")
            }
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF2B1E00)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Начать", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Feature(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(Gold),
        )
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Cream)
    }
}
