package com.zikrtime.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Quiet, static architectural arcs. No bitmap, motion or competing ornament behind text. */
enum class SectionTone { Ink, Dusk, Dawn }

@Composable
fun MotifPanel(modifier: Modifier = Modifier, tone: SectionTone = SectionTone.Ink,
    content: @Composable BoxScope.() -> Unit) {
    val colors = when (tone) {
        SectionTone.Ink -> listOf(Color(0xFF213E5C), Color(0xFF345B7B))
        SectionTone.Dusk -> listOf(Color(0xFF343750), Color(0xFF575471))
        SectionTone.Dawn -> listOf(Color(0xFF41465B), Color(0xFF74645E))
    }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(colors))) {
        Canvas(Modifier.matchParentSize()) {
            for (i in 0..2) {
                val x = size.width * .82f
                val radius = size.width * (.16f + .07f * i)
                val top = size.height * (.12f - .08f * i)
                val shoulder = top + radius * 1.4f
                val arch = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x - radius, size.height)
                    lineTo(x - radius, shoulder)
                    cubicTo(x - radius, top + radius * .6f, x - radius * .3f, top + radius * .5f, x, top)
                    cubicTo(x + radius * .3f, top + radius * .5f, x + radius, top + radius * .6f, x + radius, shoulder)
                    lineTo(x + radius, size.height)
                }
                drawPath(arch, Color.White.copy(alpha = .09f), style = Stroke(1.dp.toPx()))
            }
        }
        CompositionLocalProvider(LocalContentColor provides Color(0xFFF7F2EA)) { content() }
    }
}

@Composable
fun SectionBackdrop(tone: SectionTone = SectionTone.Dawn, content: @Composable BoxScope.() -> Unit) {
    MotifPanel(tone = tone, content = content)
}
