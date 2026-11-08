package com.zikrtime.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** A calm ink emphasis surface. Existing content keeps its readable light foreground. */
@Composable
fun LandscapePanel(@DrawableRes art: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Color(0xFF223454))) {
        CompositionLocalProvider(LocalContentColor provides Porcelain) { content() }
    }
}

/** A compact destination with a generous touch area and visible tactile response. */
@Composable
fun FeatureTile(title: String, icon: Int, modifier: Modifier = Modifier, detail: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    Column(modifier.pressScale(interaction).clip(shape)
        .background(scheme.surfaceContainerLow)
        .border(1.dp, scheme.outlineVariant.copy(alpha = .65f), shape)
        .clickable(interactionSource = interaction, indication = ripple(), role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .heightIn(min = 96.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(icon), size = 36.dp)
            Spacer(Modifier.weight(1f))
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}


/** Open rows alternate with photographic content; no tile grid for every destination. */
@Composable
fun DestinationRow(title: String, icon: Int, detail: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
        .heightIn(min = 68.dp).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBadge(painterResource(icon), size = 38.dp, container = scheme.primaryContainer, tint = scheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
    }
    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .5f))
}

@Composable
fun ReadingRow(title: String, art: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
        .heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(com.zikrtime.app.R.drawable.ic_shop_book), null, tint = scheme.primary, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
    }
}
