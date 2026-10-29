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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Dedicated images stay decorative; live text remains accessible and translated. */
@Composable
fun LandscapePanel(@DrawableRes art: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))) {
        Image(painterResource(art), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xB805211A), Color(0x1805211A)))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDA05211A)))))
        CompositionLocalProvider(LocalContentColor provides Porcelain) { content() }
    }
}

/** A compact destination with a generous touch area and visible tactile response. */
@Composable
fun FeatureTile(title: String, icon: Int, modifier: Modifier = Modifier, detail: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.pressScale(interaction).clip(shape)
        .background(Brush.linearGradient(listOf(scheme.surfaceContainer, scheme.surfaceContainerHigh)))
        .border(1.dp, scheme.outlineVariant.copy(alpha = .65f), shape)
        .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
        .heightIn(min = 132.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(icon), size = 36.dp)
            Spacer(Modifier.weight(1f))
            Text("↗", color = scheme.primary, style = MaterialTheme.typography.titleMedium)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}


/** Open rows alternate with photographic content; no tile grid for every destination. */
@Composable
fun DestinationRow(title: String, icon: Int, detail: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
        .heightIn(min = 68.dp).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBadge(painterResource(icon), size = 38.dp, container = scheme.secondaryContainer, tint = scheme.secondary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurfaceVariant)
    }
    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .5f))
}

@Composable
fun ReadingRow(title: String, art: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(painterResource(art), null, Modifier.size(78.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
    }
}
