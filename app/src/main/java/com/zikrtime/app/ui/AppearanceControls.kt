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
fun ThemeQuickButton() {
    val dark = LocalDarkTheme.current
    val label = stringResource(if (dark) R.string.appearance_light else R.string.appearance_dark)
    Surface(onClick = { Store.appearance = if (dark) "light" else "dark" },
        color = Color(0xCC263C54), contentColor = Color.White, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.testTag("theme_quick_toggle").semantics { testTagsAsResourceId = true }) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(if (dark) R.drawable.ic_prayer_sunrise else R.drawable.ic_prayer_isha),
                null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AppearancePicker() {
    ZCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.selectableGroup()) {
            listOf("light" to R.string.appearance_light, "dark" to R.string.appearance_dark,
                "system" to R.string.appearance_system).forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("appearance_$value").semantics { testTagsAsResourceId = true }
                    .selectable(selected = Store.appearance == value, role = Role.RadioButton,
                        onClick = { Store.appearance = value }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = Store.appearance == value, onClick = null)
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (value != "system") Icon(painterResource(if (value == "light") R.drawable.ic_prayer_sunrise else R.drawable.ic_prayer_isha),
                        null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
