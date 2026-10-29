package com.zikrtime.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zikrtime.app.R
import com.zikrtime.app.data.Places
import com.zikrtime.app.data.Store
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.LocalDarkTheme
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon

private fun kindName(kind: String): Int = when (kind) {
    "mosque" -> R.string.place_kind_mosque
    "room" -> R.string.place_kind_room
    "mall" -> R.string.place_kind_mall
    else -> R.string.place_kind_other
}

private fun kindIcon(kind: String): Int = if (kind == "mosque") R.drawable.ic_mosque else R.drawable.ic_prayer_rug

/** Маршрут в приложении Яндекс Карт (если его нет — в браузере). */
fun openRoute(context: android.content.Context, lat: Double, lon: Double) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~$lat,$lon&rtt=pd"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?rtext=~$lat,$lon&rtt=pd"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(app) }.onFailure { runCatching { context.startActivity(web) } }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** «Где помолиться»: карта и список молельных мест рядом. */
@Composable
fun PlacesScreen() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val data = Places.data ?: remember { Places.load() }
    LaunchedEffect(Unit) {
        Places.refresh(context)
        com.zikrtime.app.data.Reviews.refresh()
    }
    var filter by rememberSaveable { mutableStateOf("all") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var here by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var focusMe by remember { mutableIntStateOf(0) }
    var locating by remember { mutableStateOf(false) }
    var locationUnavailable by remember { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    var showMap by rememberSaveable { mutableStateOf(!largeText) }
    LaunchedEffect(largeText) { if (largeText) showMap = false }
    fun locate() {
        locating = true
        LocationHelper.requestPrecise(context) { loc ->
            locating = false
            if (loc != null) {
                here = loc.latitude to loc.longitude
                focusMe++
                locationUnavailable = false
            } else locationUnavailable = true
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locate() else locationUnavailable = true
    }
    fun whereAmI() {
        if (LocationHelper.hasPermission(context)) locate()
        else locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    // Если доступ к месту уже есть — сразу ищем ближайшие к человеку, а не к центру города
    LaunchedEffect(Unit) { if (LocationHelper.hasPermission(context)) locate() }
    val origin = here ?: (Store.location.latitude to Store.location.longitude)
    val all = data.places.sortedBy { Places.distanceKm(origin.first, origin.second, it.lat, it.lon) }
    val places = all.filter {
        when (filter) {
            "mosque" -> it.kind == "mosque"
            "room" -> it.kind != "mosque"
            else -> true
        }
    }
    // Keep a selected distant map marker visible even outside the nearest 100.
    val displayed = places.take(100).let { nearest ->
        val picked = places.firstOrNull { it.id == selected }
        if (picked != null && nearest.none { it.id == picked.id }) listOf(picked) + nearest else nearest
    }
    val bot = Places.bot()
    val listState = rememberLazyListState()

    ScreenScaffold(title = stringResource(R.string.places_title), onBack = { nav.back() }, scrollable = false) {
        if (hasYandexMap && showMap) {
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                YandexPlacesMap(
                    places = places,
                    selected = selected,
                    centerLat = origin.first,
                    centerLon = origin.second,
                    dark = LocalDarkTheme.current,
                    onSelect = { id ->
                        selected = id
                    },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)),
                    me = here,
                    focusMe = focusMe,
                )
                // «Где я»: показать себя на карте и ближайшие места
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = { whereAmI() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) {
                    if (locating) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(androidx.compose.material.icons.Icons.Default.LocationOn, contentDescription = stringResource(R.string.places_where_am_i))
                    }
                }
            }
        }
        Text(
            stringResource(if (here != null) R.string.places_near_you else R.string.places_near_city),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(enabled = here == null) { whereAmI() },
        )
        if (locationUnavailable) Text(stringResource(R.string.place_location_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasYandexMap) FilterPill(stringResource(if (showMap) R.string.place_hide_map else R.string.place_show_map), showMap) { showMap = !showMap }
            FilterPill(stringResource(R.string.places_all), filter == "all") { filter = "all" }
            FilterPill(stringResource(R.string.places_mosques), filter == "mosque") { filter = "mosque" }
            FilterPill(stringResource(R.string.places_rooms), filter == "room") { filter = "room" }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (Places.refreshFailed || Places.usingSavedCopy) {
                item { Text(stringResource(if (Places.usingSavedCopy) R.string.place_saved_copy else R.string.place_load_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (places.isEmpty()) {
                item {
                    ZCard {
                        Text(stringResource(if (Places.loading) R.string.places_loading else R.string.places_empty), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            // В списке — ближайшие 100, на карте — все (кластерами)
            items(displayed, key = { it.id }) { p ->
                PlaceCard(
                    place = p,
                    distanceKm = Places.distanceKm(origin.first, origin.second, p.lat, p.lon),
                    expanded = p.id == selected,
                    onClick = { selected = if (selected == p.id) null else p.id },
                )
            }
            if (places.any { it.source == "osm" }) {
                item {
                    Text(
                        stringResource(R.string.places_osm_credit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            item {
                GlassHero {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.places_suggest_title), style = MaterialTheme.typography.titleMedium, color = Cream)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.places_suggest_text), style = MaterialTheme.typography.bodyMedium, color = Cream.copy(alpha = 0.85f))
                        if (bot.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            GoldButton(stringResource(R.string.places_suggest), onClick = { openUrl(context, "https://t.me/$bot?start=place") })
                        }
                    }
                }
            }
        }
    }
    // Выбрали метку на карте — прокручиваем список к этому месту.
    LaunchedEffect(selected) {
        val i = displayed.indexOfFirst { it.id == selected }
        val statusOffset = if (Places.refreshFailed || Places.usingSavedCopy) 1 else 0
        if (i >= 0) listState.animateScrollToItem(i + statusOffset)
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(interaction)
            .then(
                if (selected) Modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFF61734D), EmeraldDeep)))
                else Modifier.glass(shape, elevation = 3.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PlaceCard(place: Places.Place, distanceKm: Double, expanded: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var correctionDialog by remember { mutableStateOf(false) }
    val correctionUrl = Places.correctionUrl(place.id)
    val unknown = stringResource(R.string.place_unknown)
    fun status(value: Boolean?) = when (value) { true -> R.string.place_yes; false -> R.string.place_no; null -> R.string.place_unknown }
    ZCard(onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(kindIcon(place.kind)), container = if (place.kind == "mosque") Gold.copy(alpha = 0.45f) else MaterialTheme.colorScheme.primaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(kindName(place.kind)) + " · " + distanceText(distanceKm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RatingBadge("place:${place.id}")
            }
        }
        if (expanded) {
            if (place.photo.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = place.photo, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp)),
                )
            }
            Spacer(Modifier.height(10.dp))
            PlaceDetail(R.string.place_address, place.address.ifBlank { unknown })
            PlaceDetail(R.string.place_access_hours, place.hours.ifBlank { unknown })
            PlaceDetail(R.string.place_access_conditions, place.accessConditions.ifBlank { unknown })
            PlaceDetail(R.string.place_entrance, place.entrance.ifBlank { unknown })
            PlaceDetail(R.string.place_wudu, stringResource(status(place.wudu)))
            PlaceDetail(R.string.place_women, stringResource(status(place.women)))
            PlaceDetail(R.string.place_source, listOfNotNull(
                if (place.source == "osm") "OpenStreetMap" else place.source.takeIf { it.isNotBlank() },
                place.sourceDetails.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifBlank { unknown })
            PlaceDetail(R.string.place_confirmed, place.confirmedAt.ifBlank { unknown })
            if (place.note.isNotBlank()) InfoLine("ℹ️", place.note)
            Spacer(Modifier.height(12.dp))
            ShimmerButton(stringResource(R.string.places_route), onClick = { openRoute(context, place.lat, place.lon) })
            TextButton(onClick = { correctionDialog = true }) { Text(stringResource(R.string.place_changed)) }
            Spacer(Modifier.height(16.dp))
            ReviewsBlock("place:${place.id}", inCard = false)
        }
    }
    if (correctionDialog) AlertDialog(
        onDismissRequest = { correctionDialog = false },
        title = { Text(stringResource(R.string.place_changed)) },
        text = { Text(stringResource(if (correctionUrl != null) R.string.place_correction_help else R.string.place_correction_unavailable), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            if (correctionUrl != null) TextButton(onClick = {
                correctionDialog = false
                val web = Intent(Intent.ACTION_VIEW, Uri.parse(correctionUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val app = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=${Places.bot()}&start=fix_${place.id}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(app) }.recoverCatching { context.startActivity(web) }
                    .onFailure { android.widget.Toast.makeText(context, context.getString(R.string.place_open_failed), android.widget.Toast.LENGTH_LONG).show() }
            }) { Text(stringResource(R.string.place_open_bot)) }
        },
        dismissButton = { TextButton(onClick = { correctionDialog = false }) { Text(stringResource(R.string.place_close)) } },
    )
}

@Composable
private fun PlaceDetail(label: Int, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InfoLine(icon: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(icon, modifier = Modifier.width(28.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun distanceText(km: Double): String =
    if (km < 1) stringResource(R.string.places_distance_m, (km * 1000).toInt().coerceAtLeast(10) / 10 * 10)
    else stringResource(R.string.places_distance_km, String.format(Locale.US, "%.1f", km).replace('.', ','))

/** Маленькая кнопка «Поддержать проект» (ссылку задаёт владелец в боте командой /donate). */
@Composable
fun DonateCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = Places.donateUrl()
    if (url.isBlank()) return
    ZCard(modifier = modifier, onClick = { openUrl(context, url) }, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🤲", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.donate_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.donate_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Карточка на главной: «Где помолиться рядом». */
@Composable
fun PlacesEntryCard(onOpen: () -> Unit) {
    ZCard(onClick = onOpen, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(R.drawable.ic_mosque), container = Gold.copy(alpha = 0.45f))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.places_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.places_entry_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(8.dp))
        }
    }
}
