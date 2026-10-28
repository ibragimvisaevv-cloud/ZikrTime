package com.zikrtime.app.ui

import android.widget.FrameLayout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zikrtime.app.widget.Widgets
import java.time.ZonedDateTime

/** Только для проверки: виджеты рабочего стола в разное время суток. */
@Composable
fun WidgetPreview() {
    val now = ZonedDateTime.now()
    val day = now.withHour(13).withMinute(5)
    val night = now.withHour(22).withMinute(40)
    val evening = now.withHour(17).withMinute(30)
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Widget(0, day, 170, 170)
            Widget(0, night, 170, 170)
        }
        Widget(1, evening, 350, 170)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Widget(2, day, 170, 170)
            Widget(3, now, 170, 170)
        }
    }
}

@Composable
private fun Widget(kind: Int, at: ZonedDateTime, w: Int, h: Int) {
    AndroidView(
        factory = { ctx ->
            val parent = FrameLayout(ctx)
            Widgets.preview(ctx, kind, at, w to h).apply(ctx, parent)
        },
        modifier = Modifier.size(w.dp, h.dp),
    )
}
