package com.zikrtime.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zikrtime.app.data.City
import java.util.Locale

/** Выбор города: по геолокации или из готового списка. */
@Composable
fun CityPicker(onPicked: (City) -> Unit, modifier: Modifier = Modifier, header: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun locate() {
        locating = true
        error = null
        LocationHelper.requestOnce(context) { loc ->
            locating = false
            if (loc == null) {
                error = "Не удалось определить местоположение. Включите геолокацию или выберите город из списка."
            } else {
                val name = String.format(Locale.US, "Моё местоположение (%.2f, %.2f)", loc.latitude, loc.longitude)
                onPicked(City(name, loc.latitude, loc.longitude))
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else error = "Без доступа к геолокации выберите город из списка."
    }

    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { header() }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                FilledTonalButton(
                    onClick = {
                        if (LocationHelper.hasPermission(context)) locate()
                        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    enabled = !locating,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (locating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (locating) "Определяем…" else "Определить по геолокации")
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        City.PRESETS.forEach { (group, cities) ->
            item(key = "h_$group") {
                Text(
                    group,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                )
            }
            items(cities, key = { "c_${group}_${it.name}" }) { city ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPicked(city) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(city.name, style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Одноразовое определение местоположения без сервисов Google. Ответ приходит в главном потоке. */
    @SuppressLint("MissingPermission")
    fun requestOnce(context: Context, onResult: (Location?) -> Unit) {
        if (!hasPermission(context)) {
            onResult(null)
            return
        }
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        // Для расчёта времени намаза достаточно точности до города, поэтому свежая «последняя» точка подходит.
        if (last != null && System.currentTimeMillis() - last.time < 6 * 60 * 60 * 1000L) {
            onResult(last)
            return
        }
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        if (provider == null) {
            onResult(last)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var delivered = false
        fun deliver(loc: Location?) {
            if (delivered) return
            delivered = true
            onResult(loc ?: last)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lm.getCurrentLocation(provider, null, context.mainExecutor) { deliver(it) }
            handler.postDelayed({ deliver(null) }, 20_000)
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    deliver(location)
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            handler.postDelayed({
                lm.removeUpdates(listener)
                deliver(null)
            }, 20_000)
        }
    }
}
