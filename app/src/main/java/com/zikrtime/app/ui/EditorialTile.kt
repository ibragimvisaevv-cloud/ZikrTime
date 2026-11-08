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

/** Source-compatible destination row; no decorative artwork or tile grid. */
@Composable
fun EditorialTile(title: String, icon: Int, warm: Boolean, modifier: Modifier = Modifier, prominent: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
        .heightIn(min = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), null, tint = scheme.primary, modifier = Modifier.size(24.dp))
        Text(title, style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f))
        Text("→", color = scheme.primary)
    }
}
