package com.zikrtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Wide photographic morning/evening journeys, with an Islamic horizon. */
@Composable
fun EditorialTile(title: String, icon: Int, warm: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.pressScale(interaction).clip(RoundedCornerShape(26.dp))
        .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)) {
        androidx.compose.foundation.Image(painterResource(if (warm) com.zikrtime.app.R.drawable.azkar_morning else com.zikrtime.app.R.drawable.azkar_evening),
            null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
            androidx.compose.ui.graphics.Color(0x22051D18), androidx.compose.ui.graphics.Color(0xDD051D18)))))
        Column(Modifier.fillMaxWidth().heightIn(min = 154.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(painterResource(icon), null, tint = Sand, modifier = Modifier.size(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, color = Porcelain, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Surface(color = Porcelain.copy(alpha = .08f), shape = CircleShape, border = BorderStroke(1.dp, Porcelain.copy(alpha = .6f))) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text("›", color = Porcelain, style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
    }
}
