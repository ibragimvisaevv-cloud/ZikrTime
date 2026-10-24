package com.zikrtime.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import com.zikrtime.app.BuildConfig
import com.zikrtime.app.data.Places

/** Есть ли ключ Яндекс Карт (без него показываем список и ссылки в приложение Яндекс Карт). */
val hasYandexMap: Boolean get() = BuildConfig.MAPKIT_KEY.isNotBlank()

private var mapKitReady = false

/** Метка на карте: изумрудный кружок с золотой обводкой (выбранная — крупнее и золотая). */
private fun markerBitmap(selected: Boolean, density: Float): Bitmap {
    val size = ((if (selected) 40 else 30) * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    p.color = 0x55000000
    c.drawCircle(r, r + density, r - density, p)
    p.color = 0xFFE2B659.toInt()
    c.drawCircle(r, r, r - 1.5f * density, p)
    p.color = if (selected) 0xFFFFE7A8.toInt() else 0xFF0F5C4A.toInt()
    c.drawCircle(r, r, r - 4.5f * density, p)
    p.color = if (selected) 0xFF0F5C4A.toInt() else 0xFFFFF6E0.toInt()
    c.drawCircle(r, r, r * 0.28f, p)
    return bmp
}

/** Карта Яндекса с метками мест для намаза. */
@Composable
fun YandexPlacesMap(
    places: List<Places.Place>,
    selected: String?,
    centerLat: Double,
    centerLon: Double,
    dark: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val mapView = remember {
        if (!mapKitReady) {
            MapKitFactory.initialize(context)
            mapKitReady = true
        }
        MapView(context).also {
            it.mapWindow.map.move(CameraPosition(Point(centerLat, centerLon), 11.5f, 0f, 0f))
        }
    }
    // Слушатель нужно держать в памяти — MapKit хранит слабую ссылку.
    val tap = remember {
        MapObjectTapListener { obj: MapObject, _: Point ->
            (obj.userData as? String)?.let(onSelect)
            true
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    MapKitFactory.getInstance().onStart()
                    mapView.onStart()
                }
                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                    MapKitFactory.getInstance().onStop()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            MapKitFactory.getInstance().onStart()
            mapView.onStart()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
            MapKitFactory.getInstance().onStop()
        }
    }
    LaunchedEffect(places, selected, dark) {
        val map = mapView.mapWindow.map
        map.isNightModeEnabled = dark
        val objects = map.mapObjects
        objects.clear()
        val normal = ImageProvider.fromBitmap(markerBitmap(false, density))
        val big = ImageProvider.fromBitmap(markerBitmap(true, density))
        places.forEach { p ->
            objects.addPlacemark().apply {
                geometry = Point(p.lat, p.lon)
                setIcon(if (p.id == selected) big else normal)
                userData = p.id
                addTapListener(tap)
            }
        }
        places.firstOrNull { it.id == selected }?.let {
            map.move(CameraPosition(Point(it.lat, it.lon), 15.5f, 0f, 0f))
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Ключ задаём при запуске приложения. */
fun initYandexMapKey() {
    if (hasYandexMap) MapKitFactory.setApiKey(BuildConfig.MAPKIT_KEY)
}
