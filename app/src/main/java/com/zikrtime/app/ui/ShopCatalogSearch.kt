package com.zikrtime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.Shop
import com.zikrtime.app.data.ShopSearch
import com.zikrtime.app.i18n.Lang
import kotlinx.coroutines.launch

private data class SearchFilters(
    val category: String = "",
    val city: String = "",
    val min: String = "",
    val max: String = "",
) {
    fun count(products: Boolean): Int = listOf(category, city).count(String::isNotBlank) +
        if (products) listOf(min, max).count(String::isNotBlank) else 0

    companion object {
        val saver = listSaver<SearchFilters, String>(
            save = { listOf(it.category, it.city, it.min, it.max) },
            restore = { SearchFilters(it[0], it[1], it[2], it[3]) },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShopSearchScreen() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val lang = remember { Lang.content(context) }
    val catalog = Shop.catalog ?: remember { Shop.load(context) }
    var refreshAttempt by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(true) }
    LaunchedEffect(refreshAttempt) {
        try {
            refreshing = true
            Shop.refresh(context, force = refreshAttempt > 0)
        } finally {
            refreshing = false
        }
    }
    val search = remember(catalog, lang) { ShopSearch(catalog, lang) }
    var productsMode by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable(stateSaver = SearchFilters.saver) { mutableStateOf(SearchFilters()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val count = filters.count(productsMode)
    val hasCriteria = query.isNotBlank() || count > 0
    val products = remember(search, productsMode, query, filters) {
        if (productsMode && hasCriteria) search.products(query, filters.category.ifEmpty { null },
            filters.city.ifEmpty { null }, filters.min.toLongOrNull(), filters.max.toLongOrNull()) else emptyList()
    }
    val companies = remember(search, productsMode, query, filters) {
        if (!productsMode && hasCriteria) search.sellers(query, filters.category.ifEmpty { null },
            filters.city.ifEmpty { null }) else emptyList()
    }
    val askFocus = remember { FocusRequester() }
    var focusOnEntry by rememberSaveable { mutableStateOf(true) }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val filtersLabel = stringResource(R.string.store_filters)
    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        ScreenScaffold(stringResource(R.string.store_search_title), onBack = { nav.back() }, scrollable = false, actions = {
            TextButton(onClick = { focus.clearFocus(); showFilters = true }, modifier = Modifier.testTag("shop_filters")) {
                Text(if (count == 0) filtersLabel else "$filtersLabel · $count")
            }
        }) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "search") {
                    val label = stringResource(if (productsMode) R.string.store_search_products else R.string.store_search_companies)
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(askFocus).testTag("shop_search")
                            .semantics { contentDescription = label },
                        placeholder = { Text(label) }, shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (query.isEmpty()) null else ({
                            IconButton(onClick = { query = "" }, modifier = Modifier.testTag("shop_clear_search")) {
                                Icon(Icons.Default.Close, stringResource(R.string.store_clear))
                            }
                        }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    )
                    LaunchedEffect(Unit) {
                        if (focusOnEntry) {
                            focusOnEntry = false
                            askFocus.requestFocus()
                        }
                    }
                }
                item(key = "modes") {
                    TabRow(selectedTabIndex = if (productsMode) 0 else 1) {
                        Tab(selected = productsMode, onClick = { productsMode = true },
                            modifier = Modifier.testTag("shop_search_mode_products"),
                            text = { Text(stringResource(R.string.shop_products)) })
                        Tab(selected = !productsMode, onClick = { productsMode = false },
                            modifier = Modifier.testTag("shop_search_mode_companies"),
                            text = { Text(stringResource(R.string.store_companies)) })
                    }
                }
                if (Shop.offline && catalog.sellers.isNotEmpty()) {
                    item(key = "offline") { StatusLine(stringResource(R.string.shop_offline)) }
                }
                when {
                    !hasCriteria -> item(key = "prompt") {
                        Text(stringResource(R.string.store_search_prompt), modifier = Modifier.testTag("shop_search_prompt"),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    products.isNotEmpty() -> items(products, key = { "product:${it.id}" }) { product ->
                        ProductCard(product, catalog, lang) {
                            focus.clearFocus()
                            nav.shopProduct = product.id
                            nav.open(Screen.SHOP_PRODUCT)
                        }
                    }
                    companies.isNotEmpty() -> items(companies, key = { "seller:${it.id}" }) { seller ->
                        SellerCard(seller, lang, Modifier.fillMaxWidth()) {
                            focus.clearFocus()
                            nav.shopSeller = it.id
                            nav.open(Screen.SHOP_SELLER)
                        }
                    }
                    catalog.sellers.isEmpty() && refreshing -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    catalog.sellers.isEmpty() && Shop.refreshFailed -> item {
                        StatePanel(stringResource(R.string.store_unavailable), icon = R.drawable.ic_shop_store,
                            action = stringResource(R.string.store_retry), onAction = { refreshAttempt++ })
                    }
                    else -> item(key = "empty") {
                        Box(Modifier.fillMaxWidth().testTag("shop_empty")) {
                            StatePanel(stringResource(R.string.shop_empty), icon = R.drawable.ic_shop_store,
                                action = stringResource(R.string.action_reset), onAction = { query = ""; filters = SearchFilters() })
                        }
                    }
                }
            }
        }
    }
    if (showFilters) {
        ShopFilterSheet(filters, catalog.categories, search.cities, lang, productsMode,
            onDismiss = { showFilters = false }, onApply = {
                filters = it
                showFilters = false
                scope.launch { listState.scrollToItem(0) }
            })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopFilterSheet(
    applied: SearchFilters, categories: List<Shop.Category>, cities: List<String>, lang: String,
    productsMode: Boolean, onDismiss: () -> Unit, onApply: (SearchFilters) -> Unit,
) {
    var draft by rememberSaveable(stateSaver = SearchFilters.saver) { mutableStateOf(applied) }
    var cityQuery by rememberSaveable { mutableStateOf("") }
    val low = draft.min.toLongOrNull()
    val high = draft.max.toLongOrNull()
    val invalidRange = productsMode && low != null && high != null && low > high
    HorizonSheet(stringResource(R.string.store_filters), onDismiss) {
        if (categories.isNotEmpty()) {
            SectionLabel(stringResource(R.string.store_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = draft.category.isEmpty(), onClick = { draft = draft.copy(category = "") },
                    modifier = Modifier.testTag("shop_category_all"), label = { Text(stringResource(R.string.shop_all)) })
                categories.forEach { category ->
                    FilterChip(selected = draft.category == category.id, onClick = { draft = draft.copy(category = category.id) },
                        modifier = Modifier.testTag("shop_category_${category.id}"), label = { Text(category.name.get(lang)) })
                }
            }
        }
        if (cities.isNotEmpty()) {
            val cityLabel = stringResource(R.string.settings_city)
            if (cities.size > 8) {
                OutlinedTextField(value = cityQuery, onValueChange = { cityQuery = it }, singleLine = true,
                    label = { Text(cityLabel) }, modifier = Modifier.fillMaxWidth().testTag("shop_city_search"))
            } else SectionLabel(cityLabel)
            val choices = if (cities.size <= 8) cities else
                (listOf(draft.city).filter(String::isNotBlank) + cities.filter { it.contains(cityQuery.trim(), ignoreCase = true) }.take(8)).distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = draft.city.isEmpty(), onClick = { draft = draft.copy(city = "") },
                    modifier = Modifier.testTag("shop_city_all"), label = { Text(stringResource(R.string.store_any_city)) })
                choices.forEach { city ->
                    FilterChip(selected = draft.city == city, onClick = { draft = draft.copy(city = city) },
                        modifier = Modifier.testTag("shop_city_$city"), label = { Text(city) })
                }
            }
        }
        if (productsMode) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriceField(draft.min, stringResource(R.string.store_price_from), "shop_price_min", Modifier.weight(1f)) {
                    draft = draft.copy(min = it)
                }
                PriceField(draft.max, stringResource(R.string.store_price_to), "shop_price_max", Modifier.weight(1f)) {
                    draft = draft.copy(max = it)
                }
            }
            if (invalidRange) Text(stringResource(R.string.store_price_range_error), color = MaterialTheme.colorScheme.error)
        }
        PrimaryButton(stringResource(R.string.store_apply_filters), onClick = { onApply(draft) },
            enabled = !invalidRange, modifier = Modifier.testTag("shop_apply_filters"))
        TextButton(onClick = { onApply(SearchFilters()) }, modifier = Modifier.fillMaxWidth().testTag("shop_reset_filters")) {
            Text(stringResource(R.string.action_reset))
        }
    }
}

@Composable
private fun PriceField(value: String, label: String, tag: String, modifier: Modifier, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value = value, onValueChange = { input ->
        val digits = input.filterNot(Char::isWhitespace)
        if (digits.isEmpty() || digits.all { it in '0'..'9' } && digits.toLongOrNull() != null) onChange(digits)
    }, modifier = modifier.testTag(tag), singleLine = true, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
}
