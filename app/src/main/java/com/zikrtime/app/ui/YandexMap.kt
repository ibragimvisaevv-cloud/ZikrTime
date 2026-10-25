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
import com.yandex.mapkit.map.Cluster
import com.yandex.mapkit.map.ClusterListener
import com.yandex.mapkit.map.ClusterTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** «Я здесь»: синяя точка с белой обводкой и мягким ореолом. */
private fun meBitmap(density: Float): Bitmap {
    val size = (34 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    p.color = 0x402F80ED
    c.drawCircle(r, r, r, p)
    p.color = 0xFFFFFFFF.toInt()
    c.drawCircle(r, r, 9 * density, p)
    p.color = 0xFF2F80ED.toInt()
    c.drawCircle(r, r, 6.5f * density, p)
    return bmp
}

/** Кластер: золотой круг с числом мест. */
private fun clusterBitmap(count: Int, density: Float): Bitmap {
    val text = if (count > 999) "999+" else count.toString()
    val size = ((if (count > 99) 46 else 38) * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    p.color = 0xFFE2B659.toInt()
    c.drawCircle(r, r, r - density, p)
    p.color = 0xFF0F5C4A.toInt()
    c.drawCircle(r, r, r - 3.5f * density, p)
    p.color = 0xFFFFF6E0.toInt()
    p.textSize = 13 * density
    p.isFakeBoldText = true
    p.textAlign = Paint.Align.CENTER
    c.drawText(text, r, r - (p.descent() + p.ascent()) / 2, p)
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
    /** Где сейчас человек (если известно) и счётчик нажатий «Где я» — по нему камера летит к нему. */
    me: Pair<Double, Double>? = null,
    focusMe: Int = 0,
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
    // Метки (их по России тысячи) собираем в кластеры: на мелком масштабе — кружки с числом мест.
    val clusterTap = remember {
        ClusterTapListener { cluster: Cluster ->
            val map = mapView.mapWindow.map
            map.move(CameraPosition(cluster.appearance.geometry, map.cameraPosition.zoom + 2.5f, 0f, 0f))
            true
        }
    }
    val clusterListener = remember {
        ClusterListener { cluster: Cluster ->
            cluster.appearance.setIcon(ImageProvider.fromBitmap(clusterBitmap(cluster.size, density)))
            cluster.addClusterTapListener(java.lang.ref.WeakReference(clusterTap))
        }
    }
    val marks = remember { HashMap<String, PlacemarkMapObject>() }
    val meMark = remember { arrayOfNulls<PlacemarkMapObject>(1) }
    val icons = remember { arrayOfNulls<ImageProvider>(2) }
    LaunchedEffect(places, dark) {
        val map = mapView.mapWindow.map
        map.isNightModeEnabled = dark
        map.mapObjects.clear()
        marks.clear()
        val normal = ImageProvider.fromBitmap(markerBitmap(false, density))
        val big = ImageProvider.fromBitmap(markerBitmap(true, density))
        icons[0] = normal
        icons[1] = big
        val collection = map.mapObjects.addClusterizedPlacemarkCollection(java.lang.ref.WeakReference(clusterListener))
        places.forEach { p ->
            marks[p.id] = collection.addPlacemark().apply {
                geometry = Point(p.lat, p.lon)
                setIcon(if (p.id == selected) big else normal)
                userData = p.id
                addTapListener(java.lang.ref.WeakReference(tap))
            }
        }
        collection.clusterPlacemarks(55.0, 14)
        meMark[0] = null
        me?.let { meMark[0] = addMe(map, it, density) }
    }
    LaunchedEffect(me) {
        val map = mapView.mapWindow.map
        meMark[0]?.let { runCatching { map.mapObjects.remove(it) } }
        meMark[0] = me?.let { addMe(map, it, density) }
    }
    LaunchedEffect(focusMe, me) {
        if (focusMe > 0 && me != null) {
            mapView.mapWindow.map.move(
                CameraPosition(Point(me.first, me.second), 13.5f, 0f, 0f),
                com.yandex.mapkit.Animation(com.yandex.mapkit.Animation.Type.SMOOTH, 0.8f),
                null,
            )
        }
    }
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selected) {
        val (normal, big) = icons[0] to icons[1]
        if (normal != null && big != null) {
            shown?.let { marks[it]?.setIcon(normal) }
            selected?.let { marks[it]?.setIcon(big) }
        }
        shown = selected
        places.firstOrNull { it.id == selected }?.let {
            mapView.mapWindow.map.move(CameraPosition(Point(it.lat, it.lon), 15.5f, 0f, 0f))
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addMe(map: com.yandex.mapkit.map.Map, me: Pair<Double, Double>, density: Float): PlacemarkMapObject =
    map.mapObjects.addPlacemark().apply {
        geometry = Point(me.first, me.second)
        setIcon(ImageProvider.fromBitmap(meBitmap(density)))
        zIndex = 100f
    }

/** Ключ задаём при запуске приложения. */
fun initYandexMapKey() {
    if (hasYandexMap) MapKitFactory.setApiKey(BuildConfig.MAPKIT_KEY)
}
