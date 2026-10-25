package com.zikrtime.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.zikrtime.app.i18n.Lang
import java.util.concurrent.Executors

/** Определение местоположения без сервисов Google и название населённого пункта. */
object LocationHelper {

    private val io = Executors.newSingleThreadExecutor()

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Одноразовое определение местоположения. Ответ приходит в главном потоке. */
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
        // Для времени намаза достаточно точности до города, поэтому свежая «последняя» точка подходит.
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

    fun hasPrecise(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Свежее точное место (для карты мечетей): GPS, если разрешено, иначе — как requestOnce. */
    @SuppressLint("MissingPermission")
    fun requestPrecise(context: Context, onResult: (Location?) -> Unit) {
        if (!hasPrecise(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestOnce(context, onResult)
            return
        }
        val lm = context.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            requestOnce(context, onResult)
            return
        }
        val last = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        // Пока GPS ищет спутники, сразу показываем последнюю точку (если она свежая — до 10 минут)
        if (last != null && System.currentTimeMillis() - last.time < 10 * 60 * 1000L) onResult(last)
        val handler = Handler(Looper.getMainLooper())
        var delivered = false
        lm.getCurrentLocation(provider, null, context.mainExecutor) { loc ->
            if (!delivered) {
                delivered = true
                if (loc != null) onResult(loc) else if (last == null) requestOnce(context, onResult)
            }
        }
        handler.postDelayed({
            if (!delivered) {
                delivered = true
                if (last == null) requestOnce(context, onResult)
            }
        }, 25_000)
    }

    /** Название города по координатам (если на телефоне есть геокодер). Ответ — в главном потоке. */
    fun placeName(context: Context, lat: Double, lon: Double, onResult: (String?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        if (!Geocoder.isPresent()) {
            onResult(null)
            return
        }
        val geocoder = Geocoder(context, Lang.locale(context))
        io.execute {
            val name = runCatching {
                @Suppress("DEPRECATION")
                val list = geocoder.getFromLocation(lat, lon, 1)
                list?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            }.getOrNull()
            main.post { onResult(name) }
        }
    }
}
