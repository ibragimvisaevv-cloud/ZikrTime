@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.zikrtime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.ui.theme.LocalDarkTheme

/** Visible manual switch on Home; writes the same persisted preference as Settings. */
@Composable
fun ThemeQuickButton(compact: Boolean = false, onLandscape: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    if (compact) {
        val label = stringResource(if (dark) R.string.appearance_light else R.string.appearance_dark)
        IconButton(onClick = {
            Store.appearance = if (dark) "light" else "dark"
            com.zikrtime.app.widget.Widgets.updateAll(context)
        }, modifier = Modifier.size(48.dp).testTag("theme_quick_toggle").semantics { testTagsAsResourceId = true }) {
            Icon(painterResource(if (dark) R.drawable.ic_prayer_sunrise else R.drawable.ic_prayer_isha),
                contentDescription = label, tint = if (onLandscape) Porcelain else scheme.onSurface,
                modifier = Modifier.size(22.dp))
        }
        return
    }
    Surface(color = scheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.selectableGroup().padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(false, true).forEach { option ->
                val selected = dark == option
                val label = stringResource(if (option) R.string.appearance_dark else R.string.appearance_light)
                Surface(color = if (selected) scheme.primary else Color.Transparent,
                    contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                    shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.heightIn(min = 48.dp)
                        .then(if (!selected) Modifier.testTag("theme_quick_toggle").semantics { testTagsAsResourceId = true } else Modifier)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { Store.appearance = if (option) "dark" else "light"; com.zikrtime.app.widget.Widgets.updateAll(context) })
                        .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(if (option) R.drawable.ic_prayer_isha else R.drawable.ic_prayer_sunrise), null, modifier = Modifier.size(15.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun AppearancePicker() {
    val context = androidx.compose.ui.platform.LocalContext.current
    ZCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.selectableGroup()) {
            listOf("light" to R.string.appearance_light, "dark" to R.string.appearance_dark,
                "system" to R.string.appearance_system).forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("appearance_$value").semantics { testTagsAsResourceId = true }
                    .selectable(selected = Store.appearance == value, role = Role.RadioButton,
                        onClick = { Store.appearance = value; com.zikrtime.app.widget.Widgets.updateAll(context) }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = Store.appearance == value, onClick = null)
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (value != "system") Icon(painterResource(if (value == "light") R.drawable.ic_prayer_sunrise else R.drawable.ic_prayer_isha),
                        null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** All three persisted appearance modes in a compact control, available at the top of More. */
@Composable
fun AppearanceSegments() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    val options = listOf("light" to R.string.appearance_light, "dark" to R.string.appearance_dark, "system" to R.string.appearance_system)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.appearance_title), color = Porcelain, style = MaterialTheme.typography.labelLarge)
        val option: @Composable (String, Int, Modifier) -> Unit = { value, label, modifier ->
            val selected = Store.appearance == value
            Surface(color = if (selected) Sand else Color(0xB31A3933),
                contentColor = if (selected) Color(0xFF173B30) else Porcelain, shape = RoundedCornerShape(16.dp), modifier = modifier) {
                Box(Modifier.heightIn(min = 52.dp).testTag("appearance_$value").semantics { testTagsAsResourceId = true }
                    .selectable(selected = selected, role = Role.RadioButton, onClick = {
                        Store.appearance = value; com.zikrtime.app.widget.Widgets.updateAll(context)
                    }).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (large) Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, l) -> option(v, l, Modifier.fillMaxWidth()) }
        } else Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (v, l) -> option(v, l, Modifier.weight(1f)) }
        }
    }
}
