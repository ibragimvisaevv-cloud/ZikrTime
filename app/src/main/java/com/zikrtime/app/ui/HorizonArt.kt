package com.zikrtime.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.ui.theme.LocalDarkTheme
import kotlin.math.cos
import kotlin.math.sin

// Compatibility accents used by interruption screens and legacy callers.
val Clay = Color(0xFFA4523B)
val Olive = Color(0xFF3157C8)
val Sand = Color(0xFFB5C5FF)
val Porcelain = Color(0xFFF0F2F6)

@Composable
fun HorizonLandscape(period: NaturalPeriod, modifier: Modifier = Modifier, motion: Boolean = false) {
    Box(modifier.clip(MaterialTheme.shapes.large).background(if (period == NaturalPeriod.NIGHT) Color(0xFF14171D) else Color(0xFF223454)))
}

@Composable
fun HorizonBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

/** Kept source-compatible while the former decorative silhouette is retired. */
@Composable
fun HorizonSilhouette(modifier: Modifier = Modifier, color: Color = Sand) {
    Spacer(modifier)
}
