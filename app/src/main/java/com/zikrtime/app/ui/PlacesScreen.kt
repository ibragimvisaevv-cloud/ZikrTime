package com.zikrtime.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zikrtime.app.R
import com.zikrtime.app.data.Places
import com.zikrtime.app.data.Store
import com.zikrtime.app.ui.theme.LocalDarkTheme
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow

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
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val data = Places.data ?: remember { Places.load() }
    LaunchedEffect(Unit) {
        Places.refresh(context)
        com.zikrtime.app.data.Reviews.refresh()
    }
    var filter by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var mapGestureActive by remember { mutableStateOf(false) }
    var here by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var focusMe by remember { mutableIntStateOf(0) }
    var locating by remember { mutableStateOf(false) }
    var locationUnavailable by remember { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    // Leave a real scroll surface below the map even on a landscape phone.
    val mapHeight = (LocalConfiguration.current.screenHeightDp.dp * .40f).coerceIn(112.dp, 280.dp)
    var showMap by rememberSaveable { mutableStateOf(!largeText) }
    var tilesLoaded by remember { mutableStateOf(false) }
    var slowMap by remember { mutableStateOf(false) }
    LaunchedEffect(showMap) {
        if (showMap) {
            tilesLoaded = false; slowMap = false
            kotlinx.coroutines.delay(12_000)
            if (!tilesLoaded) slowMap = true
        }
    }
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
    val all = remember(data, origin) { data.places.sortedBy { Places.distanceKm(origin.first, origin.second, it.lat, it.lon) } }
    val places = all.filter {
        val matchesKind = when (filter) {
            "mosque" -> it.kind == "mosque"
            "room" -> it.kind == "room" || it.kind == "mall"
            else -> true
        }
        matchesKind && (query.isBlank() || (it.name + " " + it.address).contains(query.trim(), ignoreCase = true))
    }
    // Keep a selected distant map marker visible even outside the nearest 100.
    val displayed = places.take(100).let { nearest ->
        val picked = places.firstOrNull { it.id == selected }
        if (picked != null && nearest.none { it.id == picked.id }) listOf(picked) + nearest else nearest
    }
    val bot = Places.bot()
    val listState = rememberLazyListState()
    fun openPlace(id: String) {
        focusManager.clearFocus()
        keyboard?.hide()
        selected = id
    }

    ScreenScaffold(title = stringResource(R.string.places_title), onBack = { nav.back() }, scrollable = false) {
        // The map, controls and results share one viewport. A tall header must not
        // leave the cards confined to a few pixels at the bottom of the screen.
        LazyColumn(
            state = listState,
            userScrollEnabled = !mapGestureActive,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("places_scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (hasYandexMap && showMap) {
                item(key = "places_header_map") {
                    Box(Modifier.fillMaxWidth().height(mapHeight).testTag("places_map")) {
                        YandexPlacesMap(
                            places = places,
                            selected = selected,
                            centerLat = origin.first,
                            centerLon = origin.second,
                            dark = LocalDarkTheme.current,
                            onSelect = { id -> openPlace(id) },
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                            me = here,
                            focusMe = focusMe,
                            onLoaded = { tilesLoaded = true; slowMap = false },
                            onGestureActive = { mapGestureActive = it },
                        )
                        if (!tilesLoaded) {
                            androidx.compose.material3.Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .96f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                            ) {
                                Text(stringResource(if (slowMap) R.string.map_slow else R.string.places_loading),
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                            }
                        }
                        androidx.compose.material3.SmallFloatingActionButton(
                            onClick = { whereAmI() },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        ) {
                            if (locating) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(androidx.compose.material.icons.Icons.Default.LocationOn,
                                    contentDescription = stringResource(R.string.places_where_am_i))
                            }
                        }
                    }
                }
                item(key = "places_map_hint") {
                    Text(stringResource(R.string.ux_places_map_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "places_header_origin") {
                Text(
                    stringResource(if (here != null) R.string.places_near_you else R.string.places_near_city),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(enabled = here == null) { whereAmI() },
                )
            }
            if (locationUnavailable) {
                item(key = "places_header_location") { StatusLine(stringResource(R.string.place_location_unavailable)) }
            }
            item(key = "places_header_search") {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(120) },
                    label = { Text(stringResource(R.string.places_search_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("places_search"),
                )
            }
            item(key = "places_header_filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasYandexMap) FilterPill(stringResource(if (showMap) R.string.place_hide_map else R.string.place_show_map), showMap) { showMap = !showMap }
                    FilterPill(stringResource(R.string.places_all), filter == "all") { filter = "all" }
                    FilterPill(stringResource(R.string.places_mosques), filter == "mosque") { filter = "mosque" }
                    FilterPill(stringResource(R.string.places_rooms), filter == "room") { filter = "room" }
                }
            }
            item(key = "places_header_external") {
                TextButton(onClick = {
                    openUrl(context, "https://yandex.ru/maps/?ll=${origin.second}%2C${origin.first}&z=13&text=${Uri.encode(query.trim().ifBlank { "мечеть" })}")
                }, modifier = Modifier.testTag("places_yandex_search")) {
                    Text(stringResource(R.string.places_yandex_search))
                }
            }
            if (Places.refreshFailed || Places.usingSavedCopy) {
                item(key = "places_load_status") { StatusLine(stringResource(if (Places.usingSavedCopy) R.string.place_saved_copy else R.string.place_load_failed)) }
            }
            if (places.isEmpty()) {
                item(key = "places_empty") {
                    StatePanel(stringResource(when {
                        Places.loading -> R.string.places_loading
                        query.isNotBlank() -> R.string.places_search_empty
                        else -> R.string.places_empty
                    }), icon = R.drawable.ic_mosque)
                }
            }
            // В списке — ближайшие 100, на карте — все (кластерами).
            items(displayed, key = { "place_" + it.id }) { p ->
                PlaceCard(
                    place = p,
                    distanceKm = Places.distanceKm(origin.first, origin.second, p.lat, p.lon),
                    onClick = { openPlace(p.id) },
                )
            }
            if (places.any { it.source == "osm" }) {
                item(key = "places_osm_credit") {
                    Text(stringResource(R.string.places_osm_credit), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            if (places.any { it.source == "wikidata" || it.wikidataUrl.isNotBlank() }) {
                item(key = "places_wikidata_credit") {
                    Text(stringResource(R.string.places_wikidata_credit), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "places_suggest") {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.places_suggest_title), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp))
                    Text(stringResource(R.string.places_suggest_text), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (bot.isNotBlank()) TonalButton(stringResource(R.string.places_suggest),
                        onClick = { openUrl(context, "https://t.me/$bot?start=place") })
                }
            }
        }
    }
    // The same independently scrollable details open from a marker and a card.
    // Keep identity, not an old Place snapshot, across rotation and catalog refresh.
    all.firstOrNull { it.id == selected }?.let { place ->
        key(place.id) {
            PlaceDetailsSheet(place,
                distanceKm = Places.distanceKm(origin.first, origin.second, place.lat, place.lon),
                onDismiss = { selected = null })
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.pressScale(interaction).clip(shape)
        .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerLow)
        .then(Modifier.border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape))
        .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Button,
            interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = scheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface)
    }
}

@Composable
private fun PlaceCard(place: Places.Place, distanceKm: Double, onClick: () -> Unit) {
    ZCard(onClick = onClick, modifier = Modifier.testTag("place_card_${place.id}"), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(kindIcon(place.kind)), container = MaterialTheme.colorScheme.primaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(kindName(place.kind)) + " · " + distanceText(distanceKm),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RatingBadge("place:${place.id}")
            }
        }
        TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.places_details))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PlaceDetailsSheet(place: Places.Place, distanceKm: Double, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val largeText = LocalDensity.current.fontScale >= 1.3f
    var expanded by rememberSaveable(place.id) { mutableStateOf(false) }
    var correctionDialog by rememberSaveable(place.id) { mutableStateOf(false) }
    val detailsScroll = rememberScrollState()
    LaunchedEffect(expanded) { detailsScroll.scrollTo(0) }
    val correctionUrl = Places.correctionUrl(place.id)
    val unknown = stringResource(R.string.place_unknown)
    fun status(value: Boolean?) = when (value) { true -> R.string.place_yes; false -> R.string.place_no; null -> R.string.place_unknown }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        // A tap first shows a compact summary. Only the explicit details action
        // grows the panel; neither a long address nor missing fields force it open.
        Column(Modifier.fillMaxWidth().heightIn(max = configuration.screenHeightDp.dp * if (expanded) .84f else .58f)
            .semantics { testTagsAsResourceId = true }.testTag("place_details_sheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (expanded) R.string.ux_places_details_title else R.string.ux_places_preview_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("place_details_close")) {
                    Icon(androidx.compose.material.icons.Icons.Default.Close, stringResource(R.string.place_close))
                }
            }
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(detailsScroll)
                .testTag(if (expanded) "place_details_scroll" else "place_preview_scroll").padding(horizontal = 20.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(place.name, style = MaterialTheme.typography.headlineSmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(kindName(place.kind)) + " · " + distanceText(distanceKm),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!expanded) {
                    Text(place.address.ifBlank { stringResource(R.string.ux_places_address_missing) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                } else {
                RatingBadge("place:${place.id}")
                if (place.photo.isNotBlank()) {
                    AsyncImage(model = place.photo, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(170.dp).clip(MaterialTheme.shapes.medium))
                }
                PlaceDetail(R.string.place_address, place.address.ifBlank { unknown })
                PlaceDetail(R.string.place_access_hours, place.hours.ifBlank { unknown })
                PlaceDetail(R.string.place_access_conditions, place.accessConditions.ifBlank { unknown })
                PlaceDetail(R.string.place_entrance, place.entrance.ifBlank { unknown })
                PlaceDetail(R.string.place_wudu, stringResource(status(place.wudu)))
                PlaceDetail(R.string.place_women, stringResource(status(place.women)))
                PlaceDetail(R.string.place_source, listOfNotNull(
                    when (place.source) { "osm" -> "OpenStreetMap"; "wikidata" -> "Wikidata"; else -> place.source.takeIf { it.isNotBlank() } },
                    place.sourceDetails.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { unknown })
                PlaceDetail(R.string.place_confirmed, place.confirmedAt.ifBlank { unknown })
                if (place.confirmedAt.isBlank() && place.source in listOf("osm", "wikidata")) {
                    Text(stringResource(R.string.places_access_check), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (place.sourceUrl.isNotBlank()) {
                    TextButton(onClick = { openUrl(context, place.sourceUrl) }) { Text(stringResource(R.string.places_open_source)) }
                }
                if (place.wikidataUrl.isNotBlank() && place.wikidataUrl != place.sourceUrl) {
                    TextButton(onClick = { openUrl(context, place.wikidataUrl) }) { Text("Wikidata") }
                }
                if (place.note.isNotBlank()) InfoLine("ℹ️", place.note)
                TextButton(onClick = { correctionDialog = true }) { Text(stringResource(R.string.place_changed)) }
                Column(Modifier.fillMaxWidth().testTag("place_details_reviews")) {
                    ReviewsBlock("place:${place.id}", inCard = false)
                }
                }
            }
            // Primary actions stay visible. Wider layouts use short side-by-side
            // labels; large text gets full-width targets instead of cramped words.
            val route: @Composable (Modifier) -> Unit = { modifier ->
                ShimmerButton(stringResource(R.string.ux_places_route_short), onClick = { openRoute(context, place.lat, place.lon) },
                    modifier = modifier.testTag("place_details_route"))
            }
            val toggle: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedButton(onClick = { expanded = !expanded },
                    modifier = modifier.heightIn(min = 54.dp)
                        .testTag(if (expanded) "place_details_collapse" else "place_details_expand"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                    Text(stringResource(if (expanded) R.string.places_less else R.string.places_details))
                }
            }
            if (largeText && configuration.screenHeightDp >= 480) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    route(Modifier.fillMaxWidth())
                    toggle(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    route(Modifier.weight(1f))
                    toggle(Modifier.weight(1f))
                }
            }
        }
    }
    if (correctionDialog) HorizonDialog(
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
fun DonateCard(modifier: Modifier = Modifier, prominent: Boolean = false) {
    val context = LocalContext.current
    val url = Places.donateUrl()
    if (url.isBlank()) return
    ZCard(modifier = modifier.testTag("project_support"), onClick = { openUrl(context, url) }, padding = PaddingValues(horizontal = 20.dp, vertical = if (prominent) 20.dp else 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(R.drawable.ic_prayer_isha), container = MaterialTheme.colorScheme.secondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (prominent) R.string.qf_support_title else R.string.donate_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (prominent) R.string.qf_support_text else R.string.donate_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (prominent) Text(stringResource(R.string.donate_title) + " →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Карточка на главной: «Где помолиться рядом». */
@Composable
fun PlacesEntryCard(onOpen: () -> Unit) {
    ZCard(onClick = onOpen, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(R.drawable.ic_mosque), container = MaterialTheme.colorScheme.primaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.places_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.places_entry_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(8.dp))
        }
    }
}
