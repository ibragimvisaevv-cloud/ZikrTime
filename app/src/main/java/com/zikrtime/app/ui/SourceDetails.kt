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
    val context = LocalContext.current
    var linkError by remember(source) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.source_heading), style = MaterialTheme.typography.titleMedium)
        Text(source.originalReference?.takeIf { it.isNotBlank() } ?: stringResource(R.string.source_unknown), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.source_translation, source.translationAttribution?.takeIf { it.isNotBlank() } ?: stringResource(R.string.source_attribution_unknown)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.source_original_status, source.reviewedOriginal?.let { "${it.reviewer}: ${it.scope} · v${it.reviewedContentVersion}" + (it.reviewedAt?.let { date -> " · $date" } ?: "") } ?: stringResource(R.string.source_unconfirmed)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.source_translation_status, source.reviewedTranslation?.let { "${it.reviewer}: ${it.scope} · v${it.reviewedContentVersion}" + (it.reviewedAt?.let { date -> " · $date" } ?: "") } ?: stringResource(R.string.source_unconfirmed)), style = MaterialTheme.typography.bodyMedium)
        source.safeWebUrl?.let { url ->
            TextButton(onClick = { linkError = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure }) {
                Text(stringResource(R.string.source_open_link))
            }
            Text(stringResource(R.string.source_link_network), style = MaterialTheme.typography.bodyMedium)
        }
        if (linkError) Text(stringResource(R.string.source_link_failed), color = MaterialTheme.colorScheme.error)
    }
}
