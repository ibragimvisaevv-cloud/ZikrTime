@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.zikrtime.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.zikrtime.app.ui.theme.LocalDarkTheme

/** Edge-to-edge scene and one continuous scroll surface. Hero height follows text scale. */
@Composable
fun HorizonSection(title: String, subtitle: String? = null, image: Int, heroHeight: Dp = 340.dp,
    hero: @Composable ColumnScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val view = LocalView.current
    val dark = LocalDarkTheme.current
    val motion = motionEnabled()
    val scheme = MaterialTheme.colorScheme
    var heroPx by remember { mutableIntStateOf(0) }
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val compactPx = with(density) { (status + 60.dp).toPx() }
    val transitionPx = with(density) { 64.dp.toPx() }
    val collapsed by remember(compactPx) { derivedStateOf { heroPx > 0 && scroll.value >= heroPx - compactPx } }
    val headerProgress by remember(compactPx, transitionPx, motion) { derivedStateOf {
        if (heroPx == 0) 0f else if (!motion) { if (collapsed) 1f else 0f }
        else ((scroll.value - (heroPx - compactPx - transitionPx)) / transitionPx).coerceIn(0f, 1f)
    } }
    SideEffect { (view.context as? android.app.Activity)?.let {
        androidx.core.view.WindowCompat.getInsetsController(it.window, view).apply {
            isAppearanceLightStatusBars = headerProgress > .6f && !dark
            isAppearanceLightNavigationBars = !dark
        }
    } }
    Box(Modifier.fillMaxSize().background(scheme.background).imePadding()) {
        Box(Modifier.fillMaxWidth().height(with(density) { heroPx.toDp() }.coerceAtLeast(heroHeight) + 80.dp)
            .graphicsLayer { translationY = if (motion) -scroll.value.coerceAtMost(heroPx) * .18f else 0f }) {
            if (!(com.zikrtime.app.BuildConfig.DEBUG && com.zikrtime.app.QaHooks.hideScene)) {
                Image(painterResource(image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color(0x9E062B2A), Color(0x29072E2C), Color(0xDB071F1B)))))
        }
        Column(Modifier.fillMaxSize().testTag("section_scroll").semantics { testTagsAsResourceId = true }.verticalScroll(scroll)) {
            CompositionLocalProvider(LocalContentColor provides Porcelain) {
                Column(Modifier.fillMaxWidth().heightIn(min = heroHeight + status).onSizeChanged { heroPx = it.height }
                    .padding(horizontal = 24.dp).padding(top = status + 8.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.headlineLarge, fontSize = 40.sp,
                            modifier = Modifier.weight(1f), color = Porcelain)
                        ThemeQuickButton(compact = true, onLandscape = true)
                    }
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Porcelain.copy(alpha = .88f))
                    hero()
                }
            }
            Column(Modifier.fillMaxWidth().testTag("section_panel").semantics { testTagsAsResourceId = true }
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(scheme.background)
                .padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp).size(34.dp, 3.dp)
                    .background(scheme.secondary.copy(alpha = .4f), RoundedCornerShape(4.dp)))
                content()
                Spacer(Modifier.height(100.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        if (headerProgress > 0f) Surface(color = scheme.background, modifier = Modifier.fillMaxWidth()
            .graphicsLayer { alpha = headerProgress }) {
            Row(Modifier.padding(top = status).heightIn(min = 60.dp).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                ThemeQuickButton(compact = true)
            }
        }
    }
}

/** Typographic destination tile. Its grid remains distinct with illustration disabled. */
@Composable
fun HorizonDestination(title: String, icon: Int, modifier: Modifier = Modifier, subtitle: String? = null,
    prominent: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 30.dp, bottomEnd = 10.dp, bottomStart = 10.dp)
    val fill = if (prominent) scheme.primaryContainer else scheme.surfaceContainerLow
    Column(modifier.clip(shape).background(fill).border(1.dp, scheme.outlineVariant.copy(alpha = .55f), shape)
        .clickable(onClick = onClick).heightIn(min = 138.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(painterResource(icon), null, tint = scheme.primary, modifier = Modifier.size(26.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = scheme.secondary, modifier = Modifier.size(18.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
fun HorizonFeature(title: String, icon: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), null, tint = Sand, modifier = Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Porcelain)
        }
        Surface(color = Sand, shape = RoundedCornerShape(50)) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFF163C32), modifier = Modifier.padding(16.dp).size(24.dp))
        }
    }
}
