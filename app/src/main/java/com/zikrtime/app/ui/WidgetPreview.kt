package com.zikrtime.app.ui

import android.widget.FrameLayout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zikrtime.app.R
import com.zikrtime.app.widget.Widgets
import java.time.ZonedDateTime

/** QA surface: the real native widget renderer, current time/data and the selected app theme. */
@Composable
fun WidgetPreview() {
    StandardSystemBars()
    val now = ZonedDateTime.now(CityZones.forCity(com.zikrtime.app.data.Store.location.id))
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineSmall)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val width = maxWidth.value.toInt()
                val tile = (width - 12) / 2
                val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.widget_compact_name), style = MaterialTheme.typography.labelLarge)
                    Widget(0, now, minOf(180, width), 90)
                    Text(stringResource(R.string.widget_timeline_name), style = MaterialTheme.typography.labelLarge)
                    Widget(0, now, width, 90)
                    Text(stringResource(R.string.widget_square_name), style = MaterialTheme.typography.labelLarge)
                    Widget(0, now, minOf(180, width), if (largeText) 240 else 190)
                    Text(stringResource(R.string.widget_day_name), style = MaterialTheme.typography.labelLarge)
                    Widget(1, now, width, if (largeText) 380 else 210)
                    if (largeText) {
                        Widget(2, now, width, 200)
                        Widget(3, now, width, 220)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Widget(2, now, tile, 190)
                            Widget(3, now, tile, 190)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Widget(kind: Int, at: ZonedDateTime, w: Int, h: Int) {
    key(kind, w, h, com.zikrtime.app.data.Store.appearance) {
        AndroidView(
            factory = { ctx ->
                val parent = FrameLayout(ctx)
                Widgets.preview(ctx, kind, at, w to h).apply(ctx, parent)
            },
            modifier = Modifier.size(w.dp, h.dp),
        )
    }
}
