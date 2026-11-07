package com.zikrtime.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.zikrtime.app.BuildConfig
import com.zikrtime.app.R
import com.zikrtime.app.data.Places
import kotlinx.coroutines.launch

@Composable
internal fun FeedbackButton(source: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    val unavailable = stringResource(R.string.qf_feedback_unavailable)
    LaunchedEffect(Unit) { Places.load() }
    OutlinedButton(enabled = !opening, modifier = Modifier.fillMaxWidth().testTag("feedback_open"), onClick = {
        scope.launch {
            opening = true
            try {
                if (Places.bot().isBlank()) Places.refresh(context, force = true)
                val bot = Places.bot().removePrefix("@")
                if (!Regex("[A-Za-z0-9_]{5,32}").matches(bot)) {
                    Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
                } else {
                    val payload = ("feedback_" + BuildConfig.VERSION_NAME.replace('.', '-') + "_" + source)
                        .replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + bot + "?start=" + payload))
                    try { context.startActivity(intent) }
                    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show() }
                }
            } finally { opening = false }
        }
    }) { Text(stringResource(R.string.qf_feedback)) }
}
