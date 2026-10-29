package com.zikrtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.ui.theme.LocalDarkTheme

/** Pointed niche, matte plaster and geometric tile: no botanical or landscape art. */
@Composable
fun EditorialTile(title: String, icon: Int, warm: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dark = LocalDarkTheme.current
    val background = if (warm) { if (dark) Color(0xFF674B2D) else Color(0xFFE6BB78) }
        else { if (dark) Color(0xFF793F2C) else Color(0xFFAA5539) }
    val ink = if (warm && !dark) Color(0xFF3C2916) else Porcelain
    val interaction = remember { MutableInteractionSource() }
    val shoulder = with(androidx.compose.ui.platform.LocalDensity.current) { 76.dp.toPx() }
    val arch = remember(shoulder) { GenericShape { size, _ ->
        val r = size.width / 2f
        moveTo(0f, size.height); lineTo(0f, shoulder)
        cubicTo(0f, shoulder * .45f, r * .45f, shoulder * .16f, r, 0f)
        cubicTo(r * 1.55f, shoulder * .16f, size.width, shoulder * .45f, size.width, shoulder)
        lineTo(size.width, size.height); close()
    } }
    Box(modifier.pressScale(interaction).clip(arch)
        .background(Brush.verticalGradient(listOf(background, background.copy(alpha = .92f))))
        .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)) {
        ArchitectureArt(Modifier.matchParentSize(), color = if (warm) Olive else Sand, evening = !warm)
        Column(Modifier.fillMaxWidth().heightIn(min = 214.dp).padding(start = 18.dp, end = 18.dp, top = 34.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = ink.copy(alpha = .12f), shape = CircleShape) {
                Icon(painterResource(icon), null, tint = ink, modifier = Modifier.padding(10.dp).size(26.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, color = ink, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("→", color = ink, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
