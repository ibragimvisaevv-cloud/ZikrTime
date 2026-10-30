package com.zikrtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

enum class SectionTone { Ink, Dusk, Dawn }

@Composable
fun MotifPanel(modifier: Modifier = Modifier, tone: SectionTone = SectionTone.Ink,
    content: @Composable BoxScope.() -> Unit) {
    val colors = when (tone) {
        SectionTone.Ink -> listOf(Color(0xFF0C3B32), Color(0xFF24644D))
        SectionTone.Dusk -> listOf(Color(0xFF142F3A), Color(0xFF315763))
        SectionTone.Dawn -> listOf(Color(0xFF495B43), Color(0xFF746442))
    }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(colors))) {
        HorizonSilhouette(Modifier.matchParentSize(), color = Sand)
        CompositionLocalProvider(LocalContentColor provides Porcelain) { content() }
    }
}

@Composable
fun SectionBackdrop(tone: SectionTone = SectionTone.Dawn, content: @Composable BoxScope.() -> Unit) {
    MotifPanel(tone = tone, content = content)
}
