package com.zikrtime.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R

/** Existing compressed sky asset; stable backing preserves text contrast. */
@Composable
fun SectionBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
        Image(painterResource(R.drawable.sky_sunset), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xC7101E2B), Color(0xF0101E2B)))))
        CompositionLocalProvider(LocalContentColor provides Color(0xFFF2F5F3)) { content() }
    }
}
