package com.zikrtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.ui.theme.LocalDarkTheme

/** Small collections have their own identity, while their action and text stay explicit. */
@Composable
fun EditorialTile(title: String, icon: Int, warm: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dark = LocalDarkTheme.current
    val background = if (warm) { if (dark) Color(0xFF403B36) else Color(0xFFF0E5D5) }
        else { if (dark) Color(0xFF36364B) else Color(0xFFE9E4F0) }
    val ink = if (warm) { if (dark) Color(0xFFE8CBA4) else Color(0xFF765536) }
        else { if (dark) Color(0xFFD4C5E5) else Color(0xFF625378) }
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(background).clickable(onClick = onClick)
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, tint = ink, modifier = Modifier.size(28.dp))
            Spacer(Modifier.weight(1f))
            Text("↗", color = ink, style = MaterialTheme.typography.titleMedium)
        }
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
    }
}
