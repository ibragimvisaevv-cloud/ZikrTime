package com.zikrtime.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.CityZones
import com.zikrtime.app.ui.theme.ZikrTheme
import java.time.ZonedDateTime

/** Reconfigurable, per-instance appearance. Cancelling never changes the placed widget. */
class WidgetConfigActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Lang.wrap(newBase))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.packageName != packageName) { finish(); return }
        val options = AppWidgetManager.getInstance(this).getAppWidgetOptions(id)
        val previewWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160).coerceAtLeast(130)
        val previewHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 184).coerceAtLeast(64)
        val supportsDates = PrayerWidgetModel.layout(previewWidth, previewHeight, resources.configuration.fontScale) in
            listOf(PrayerWidgetModel.Layout.SQUARE, PrayerWidgetModel.Layout.DAY)
        enableEdgeToEdge()
        setContent {
            ZikrTheme(natural=true) {
                var theme by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(WidgetPreferences.theme(this,id)) }
                var dates by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(WidgetPreferences.dates(this,id)) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.widget_config_title),style=MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.widget_config_hint),style=MaterialTheme.typography.bodyMedium)
                        // Real native widget, with current data. The setting is committed only by Done.
                        key(theme, dates) {
                            Box(Modifier.fillMaxWidth(),contentAlignment=androidx.compose.ui.Alignment.Center) {
                            AndroidView(factory={ c -> FrameLayout(c).apply {
                                val systemDark = c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                                val dark = when (if (theme == "app") Store.appearance else theme) { "light" -> false; "dark" -> true; else -> systemDark }
                                addView(PrayerWidgetRenderer.render(c,id,previewWidth to previewHeight,ZonedDateTime.now(CityZones.forCity(Store.location.id)),dark,dates).apply(c,this))
                            } },modifier=Modifier.widthIn(max=previewWidth.dp).fillMaxWidth().height(previewHeight.dp))
                            }
                        }
                        listOf("app" to R.string.widget_theme_app, "system" to R.string.appearance_system, "light" to R.string.appearance_light, "dark" to R.string.appearance_dark).forEach { (value,label) ->
                            Surface(onClick={theme=value}, shape=MaterialTheme.shapes.large, color=if(theme==value) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp)) {
                                    RadioButton(theme==value,onClick={theme=value})
                                    Text(stringResource(label),Modifier.padding(top=14.dp))
                                }
                            }
                        }
                        if (supportsDates) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.widget_hijri_option),Modifier.weight(1f).padding(top=12.dp))
                            Switch(dates,{dates=it})
                        }
                        Button(onClick={
                            WidgetPreferences.save(this@WidgetConfigActivity,id,theme,dates)
                            Widgets.updateAll(this@WidgetConfigActivity)
                            setResult(Activity.RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)); finish()
                        },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(stringResource(R.string.action_done)) }
                        TextButton(onClick={finish()},modifier=Modifier.fillMaxWidth()) { Text(stringResource(android.R.string.cancel)) }
                    }
                }
            }
        }
    }
}
