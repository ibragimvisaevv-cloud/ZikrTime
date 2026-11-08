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
import com.zikrtime.app.ui.theme.LocalChocolateTheme

/** Dedicated images stay decorative; live text remains accessible and translated. */
@Composable
fun LandscapePanel(@DrawableRes art: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val chocolate = LocalChocolateTheme.current
    val shape = RoundedCornerShape(28.dp)
    val frame = if (chocolate) Modifier.border(.7.dp, MaterialTheme.colorScheme.secondary.copy(alpha = .45f), shape) else Modifier
    val horizontalScrim = if (chocolate) listOf(Color(0xB8281A12), Color(0x18281A12))
        else listOf(Color(0xB8171B18), Color(0x18171B18))
    val bottomScrim = if (chocolate) Color(0xDA281A12) else Color(0xDA171B18)
    Box(modifier.fillMaxWidth().clip(shape).then(frame)) {
        Image(painterResource(art), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(horizontalScrim)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, bottomScrim))))
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
        .background(scheme.surfaceContainerLow)
        .border(1.dp, scheme.outlineVariant.copy(alpha = .65f), shape)
        .clickable(interactionSource = interaction, indication = ripple(), role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .heightIn(min = 132.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
        .heightIn(min = 68.dp).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBadge(painterResource(icon), size = 38.dp, container = scheme.secondaryContainer, tint = scheme.secondary)
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
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(painterResource(art), null, Modifier.size(78.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
    }
}
