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
        SectionTone.Ink -> listOf(Color(0xFF33442F), Color(0xFF526143))
        SectionTone.Dusk -> listOf(Color(0xFF3B3027), Color(0xFF64513A))
        SectionTone.Dawn -> listOf(Color(0xFF85442F), Color(0xFFA85F40))
    }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 40.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
        .background(Brush.linearGradient(colors))) {
        ArchitectureArt(Modifier.matchParentSize(), color = Sand)
        CompositionLocalProvider(LocalContentColor provides Porcelain) { content() }
    }
}

@Composable
fun SectionBackdrop(tone: SectionTone = SectionTone.Dawn, content: @Composable BoxScope.() -> Unit) {
    MotifPanel(tone = tone, content = content)
}
