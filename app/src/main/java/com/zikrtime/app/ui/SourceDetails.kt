package com.zikrtime.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.SourceInfo

@Composable
fun SourceDetails(source: SourceInfo) {
    val original = source.originalReference?.takeIf { it.isNotBlank() }
    val translation = source.translationAttribution?.takeIf { it.isNotBlank() }
    if (original == null && translation == null && source.safeWebUrl == null) return
    val context = LocalContext.current
    var linkError by remember(source) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.source_heading), style = MaterialTheme.typography.titleSmall)
        original?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        translation?.let { Text(stringResource(R.string.source_translation, it), style = MaterialTheme.typography.bodySmall) }
        source.safeWebUrl?.let { url ->
            TextButton(onClick = { linkError = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure }) {
                Text(stringResource(R.string.source_open_link))
            }
            Text(stringResource(R.string.source_link_network), style = MaterialTheme.typography.bodyMedium)
        }
        if (linkError) Text(stringResource(R.string.source_link_failed), color = MaterialTheme.colorScheme.error)
    }
}
