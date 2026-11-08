@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.zikrtime.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.zikrtime.app.ui.theme.RhythmSpace

/** Compatibility entry point: a compact, open heading replaces the landscape hero. */
@Composable
fun HorizonSection(title: String, subtitle: String? = null, image: Int, heroHeight: Dp = 340.dp,
    hero: @Composable ColumnScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    StandardSystemBars()
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(scheme.background).imePadding()
        .testTag("section_scroll").semantics { testTagsAsResourceId = true }
        .windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState())
        .padding(horizontal = RhythmSpace.Page)) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                ThemeQuickButton(compact = true)
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            hero()
        }
        Column(Modifier.fillMaxWidth().testTag("section_panel").semantics { testTagsAsResourceId = true },
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            content()
            Spacer(Modifier.height(if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 132.dp else 96.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
fun HorizonDestination(title: String, icon: Int, modifier: Modifier = Modifier, subtitle: String? = null,
    prominent: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 80.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), null, tint = scheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        HorizontalDivider(color = scheme.outlineVariant)
    }
}

@Composable
fun HorizonFeature(title: String, icon: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(icon), null, tint = scheme.primary, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = scheme.primary)
    }
}
