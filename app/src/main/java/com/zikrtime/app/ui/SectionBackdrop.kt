package com.zikrtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zikrtime.app.ui.theme.LocalChocolateTheme

enum class SectionTone { Ink, Dusk, Dawn }

@Composable
fun MotifPanel(modifier: Modifier = Modifier, tone: SectionTone = SectionTone.Ink,
    content: @Composable BoxScope.() -> Unit) {
    val chocolate = LocalChocolateTheme.current
    val colors = if (chocolate) when (tone) {
        SectionTone.Ink -> listOf(Color(0xFF35271D), Color(0xFF4E3928))
        SectionTone.Dusk -> listOf(Color(0xFF2C231E), Color(0xFF47382D))
        SectionTone.Dawn -> listOf(Color(0xFF51402D), Color(0xFF756044))
    } else when (tone) {
        SectionTone.Ink -> listOf(Color(0xFF243B32), Color(0xFF3C5546))
        SectionTone.Dusk -> listOf(Color(0xFF293A45), Color(0xFF455660))
        SectionTone.Dawn -> listOf(Color(0xFF514736), Color(0xFF766547))
    }
    val shape = RoundedCornerShape(26.dp)
    val frame = if (chocolate) Modifier.border(.7.dp, Sand.copy(alpha = .38f), shape) else Modifier
    Box(modifier.fillMaxWidth().clip(shape)
        .background(Brush.linearGradient(colors)).then(frame)) {
        HorizonSilhouette(Modifier.matchParentSize(), color = Sand)
        CompositionLocalProvider(LocalContentColor provides Porcelain) { content() }
    }
}

@Composable
fun SectionBackdrop(tone: SectionTone = SectionTone.Dawn, content: @Composable BoxScope.() -> Unit) {
    MotifPanel(tone = tone, content = content)
}
