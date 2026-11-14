package com.zikrtime.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zikrtime.app.R
import com.zikrtime.app.data.Shop
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.LocalDarkTheme

/** Значок категории магазина. */
fun shopIcon(name: String): Int = when (name) {
    "perfume" -> R.drawable.ic_shop_perfume
    "car" -> R.drawable.ic_shop_car
    "book" -> R.drawable.ic_shop_book
    "clothes" -> R.drawable.ic_shop_clothes
    "food" -> R.drawable.ic_shop_food
    "gift" -> R.drawable.ic_shop_gift
    else -> R.drawable.ic_shop_store
}

private fun accentOf(seller: Shop.Seller?): Color = seller?.accent?.let { Color(0xFF000000 or it) } ?: Color(0xFF235D4C)

// ---------------- Витрина ----------------

@Composable
fun ShopScreen() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val lang = remember { Lang.content(context) }
    val catalog = Shop.catalog ?: remember { Shop.load(context) }
    var refreshAttempt by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(true) }
    LaunchedEffect(refreshAttempt) {
        refreshing = true
        try { Shop.refresh(context, force = refreshAttempt > 0) } finally { refreshing = false }
    }
    LaunchedEffect(Unit) { com.zikrtime.app.data.Reviews.refresh() }
    val (partners, sellers) = remember(catalog) { Shop.rankSellers(catalog.sellers).partition { it.partner } }
    var sellerLimit by rememberSaveable { mutableStateOf(12) }
    val openSeller: (Shop.Seller) -> Unit = { seller ->
        nav.shopSeller = seller.id
        nav.open(Screen.SHOP_SELLER)
    }
    var showTerms by rememberSaveable { mutableStateOf(false) }
    val hasMerchantContact = catalog.sellerBot.isNotBlank() || catalog.contactTelegram.isNotBlank()
    val hasMerchantEntry = hasMerchantContact || catalog.sellerPortal != null
    HorizonSection(title = stringResource(R.string.shop_title), image = R.drawable.shop_market, heroHeight = 146.dp) {
        val scheme = MaterialTheme.colorScheme
        val searchShape = RoundedCornerShape(16.dp)
        Row(Modifier.fillMaxWidth().testTag("shop_open_search").clip(searchShape)
            .background(scheme.surfaceContainerHigh).border(1.dp, scheme.outlineVariant, searchShape)
            .clickable(role = Role.Button) { nav.open(Screen.SHOP_SEARCH) }
            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Search, null, tint = scheme.primary)
            Text(stringResource(R.string.shop_search), style = MaterialTheme.typography.bodyLarge)
        }
        if (catalog.demo) StatusLine(stringResource(R.string.shop_demo_note))
        if (Shop.offline && catalog.sellers.isNotEmpty()) StatusLine(stringResource(R.string.shop_offline))

        if (partners.isNotEmpty()) PartnerGrid(partners, catalog, lang, openSeller)
        if (sellers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.store_companies))
                sellers.take(sellerLimit).forEach { seller ->
                    key(seller.id) { SellerCard(seller, lang, Modifier.fillMaxWidth(), openSeller) }
                }
                if (sellers.size > sellerLimit) {
                    TextButton(onClick = { sellerLimit += 12 }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.store_more_companies))
                    }
                }
            }
        }
        if (catalog.sellers.isEmpty()) {
            Box(Modifier.fillMaxWidth().testTag("shop_empty")) {
                when {
                    refreshing -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    Shop.refreshFailed -> StatePanel(stringResource(R.string.store_unavailable), icon = R.drawable.ic_shop_store,
                        action = stringResource(R.string.store_retry), onAction = { refreshAttempt++ })
                    else -> StatePanel(stringResource(R.string.store_empty_catalog), icon = R.drawable.ic_shop_store)
                }
            }
        }
        if (hasMerchantEntry) {
            Box(Modifier.testTag("shop_merchant")) {
                DestinationRow(stringResource(R.string.shop_add_company), R.drawable.ic_shop_store,
                    stringResource(R.string.store_terms_title)) { showTerms = true }
            }
        }
    }
    if (showTerms && hasMerchantEntry) {
        HorizonSheet(stringResource(R.string.store_terms_title), { showTerms = false }) {
            Text(stringResource(R.string.store_listing_terms), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.store_promotion_terms), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasMerchantContact) {
                PrimaryButton(stringResource(R.string.store_apply), onClick = {
                    showTerms = false
                    if (catalog.sellerBot.isNotBlank()) openAddCompany(context, catalog)
                    else openTelegram(context, catalog.contactTelegram)
                })
            }
            catalog.sellerPortal?.let { url ->
                TonalButton(stringResource(R.string.store_seller_portal), onClick = {
                    val link = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(link) }.onFailure {
                        android.widget.Toast.makeText(context, R.string.store_browser_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.testTag("shop_seller_portal"))
            }
        }
    }
}

@Composable
internal fun SellerCard(seller: Shop.Seller, lang: String, modifier: Modifier, onClick: (Shop.Seller) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.testTag("shop_seller_${seller.id}").clip(shape).background(scheme.surfaceContainerLow)
        .border(1.dp, scheme.outlineVariant, shape).clickable(role = Role.Button) { onClick(seller) }
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SellerLogo(seller, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(seller.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val city = seller.city.get(lang)
            if (city.isNotBlank()) Text(city, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            RatingBadge("seller:${seller.id}")
            if (seller.promoted) AdLabel(Modifier.testTag("shop_ad_${seller.id}"))
        }
    }
}

@Composable
internal fun SellerLogo(seller: Shop.Seller, size: androidx.compose.ui.unit.Dp) {
    val accent = accentOf(seller)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent, EmeraldDeep)))
            .border(2.dp, Gold.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val url = Shop.imageUrl(seller.logo)
        var logoFailed by remember(url) { mutableStateOf(url == null) }
        if (!logoFailed) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, onError = { logoFailed = true }, modifier = Modifier.fillMaxSize())
        } else {
            Text(seller.name.trim().take(1).uppercase(), color = Cream, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
private fun ProductList(products: List<Shop.Product>, catalog: Shop.Catalog, lang: String, onClick: (Shop.Product) -> Unit) {
    var limit by rememberSaveable { mutableStateOf(12) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        products.take(limit).forEach { product ->
            key(product.id) { ProductCard(product, catalog, lang) { onClick(product) } }
        }
        if (products.size > limit) {
            TextButton(onClick = { limit += 12 }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.store_more_products))
            }
        }
    }
}

@Composable
private fun StoreOffers(offers: List<Shop.Product>, catalog: Shop.Catalog, lang: String, onClick: (Shop.Product) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    var limit by rememberSaveable(offers.first().seller) { mutableStateOf(2) }
    Column(Modifier.fillMaxWidth().testTag("shop_seller_offers").clip(shape)
        .background(scheme.surfaceContainerLow).border(1.dp, Gold.copy(alpha = .65f), shape)
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.store_offers), modifier = Modifier.semantics { heading() }
            .padding(horizontal = 4.dp, vertical = 2.dp), color = scheme.onSurface,
            style = MaterialTheme.typography.titleMedium)
        offers.take(limit).forEach { product ->
            key(product.id) { ProductCard(product, catalog, lang, storeOffer = true) { onClick(product) } }
        }
        if (offers.size > limit) {
            TextButton(onClick = { limit += 12 }, modifier = Modifier.align(Alignment.CenterHorizontally)
                .testTag("shop_more_offers")) {
                Text(stringResource(R.string.store_more_offers))
            }
        }
    }
}

@Composable
private fun ProductImage(product: Shop.Product, catalog: Shop.Catalog, modifier: Modifier, iconSize: androidx.compose.ui.unit.Dp = 48.dp, fit: Boolean = false) {
    val seller = catalog.seller(product.seller)
    val accent = accentOf(seller)
    val dark = LocalDarkTheme.current
    Box(
        modifier.background(
            Brush.linearGradient(
                if (dark) listOf(accent.copy(alpha = 0.55f), EmeraldDeep) else listOf(accent.copy(alpha = 0.16f), Gold.copy(alpha = 0.18f)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val url = product.images.firstOrNull()?.let { Shop.imageUrl(it) }
        var failed by remember(url) { mutableStateOf(url == null) }
        if (!failed) {
            AsyncImage(model = url, contentDescription = null, contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                onError = { failed = true }, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                painterResource(shopIcon(catalog.category(product.category)?.icon ?: "store")),
                contentDescription = null,
                tint = if (dark) Gold else accent,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
internal fun ProductCard(product: Shop.Product, catalog: Shop.Catalog, lang: String, storeOffer: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val seller = catalog.seller(product.seller)
    val tag = if (storeOffer) "shop_offer_${product.id}" else "shop_product_${product.id}"
    Row(Modifier.fillMaxWidth().testTag(tag).clip(RoundedCornerShape(16.dp))
        .background(if (storeOffer) scheme.surfaceContainerHigh else scheme.surfaceContainerLow)
        .clickable(role = Role.Button, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProductImage(product, catalog, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), iconSize = 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.title.get(lang), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (seller != null && !storeOffer) Text(seller.name, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            PriceLine(product, lang)
            if (seller?.promoted == true) AdLabel(Modifier.testTag("shop_ad_${product.id}"))
        }
    }
}

@Composable
private fun PriceLine(product: Shop.Product, lang: String, big: Boolean = false) {
    val price = product.price
    val old = product.oldPrice
    val discount = product.discountPercent
    val scheme = MaterialTheme.colorScheme
    val priceLabel = if (price != null) Shop.price(price, product.currency) else stringResource(R.string.shop_price_on_request)
    val amount = buildAnnotatedString {
        append(priceLabel)
        val unit = product.unit.get(lang)
        if (unit.isNotBlank()) {
            withStyle(SpanStyle(color = scheme.onSurfaceVariant, fontWeight = FontWeight.Normal)) { append(" / $unit") }
        }
        if (old != null && discount != null) {
            append("   ")
            withStyle(SpanStyle(color = scheme.onSurfaceVariant, fontWeight = FontWeight.Normal,
                textDecoration = TextDecoration.LineThrough)) { append(Shop.price(old, product.currency)) }
            if (discount >= 1) {
                withStyle(SpanStyle(color = scheme.secondary, fontWeight = FontWeight.Bold)) { append("  −$discount%") }
            }
        }
    }
    Text(amount, style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge,
        color = scheme.primary)
}

// ---------------- Продавец ----------------

@Composable
fun SellerScreen(sellerId: String? = null) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val lang = remember { Lang.content(context) }
    val catalog = Shop.catalog ?: remember { Shop.load(context) }
    val seller = catalog.seller(sellerId ?: nav.shopSeller)
    ScreenScaffold(title = seller?.name.orEmpty(), onBack = { nav.back() }, showHeading = false) {
        if (seller == null) {
            Text(stringResource(R.string.search_nothing), style = MaterialTheme.typography.bodyLarge)
            return@ScreenScaffold
        }
        val accent = accentOf(seller)
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.8f), EmeraldDeep))),
        ) {
            val coverUrl = Shop.imageUrl(seller.cover)
            if (coverUrl != null) {
                AsyncImage(model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                StarPattern(Modifier.fillMaxSize(), color = Gold.copy(alpha = 0.18f), cell = 56f)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD171B18)))))
            Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SellerLogo(seller, 60.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(seller.name, style = MaterialTheme.typography.titleMedium, color = Cream)
                    Text(seller.city.get(lang), style = MaterialTheme.typography.bodyMedium, color = Cream.copy(alpha = 0.85f))
                }
            }
            Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (seller.partner) CategoryChip(stringResource(R.string.shop_partner))
                if (seller.promoted) AdLabel(Modifier.testTag("shop_ad_${seller.id}"))
            }
        }
        val (offers, products) = remember(catalog, seller.id) {
            catalog.products.filter { it.seller == seller.id }.partition { it.discountPercent != null }
        }
        val openProduct: (Shop.Product) -> Unit = { product ->
            nav.shopProduct = product.id
            nav.open(Screen.SHOP_PRODUCT)
        }
        if (offers.isNotEmpty()) StoreOffers(offers, catalog, lang, openProduct)
        if (!seller.description.isEmpty) {
            ZCard { Text(seller.description.get(lang), style = MaterialTheme.typography.bodyLarge) }
        }
        ContactButtons(seller)
        if (seller.address.isNotBlank()) {
            ZCard(onClick = { openMap(context, seller) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(painterResource(R.drawable.ic_pin))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.shop_address), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(seller.address, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (products.isNotEmpty()) {
            SectionLabel(stringResource(R.string.shop_seller_products))
            ProductList(products, catalog, lang, openProduct)
        }
        ReviewsBlock("seller:${seller.id}")
    }
}

/** Кнопки связи: показываем только те, что указал продавец. */
@Composable
private fun ContactButtons(seller: Shop.Seller) {
    val context = LocalContext.current
    val items = buildList<Triple<Int, Int, () -> Unit>> {
        if (seller.telegram.isNotBlank()) add(Triple(R.drawable.ic_telegram, R.string.shop_contact_telegram) { openTelegram(context, seller.telegram) })
        if (seller.whatsapp.isNotBlank()) add(Triple(R.drawable.ic_chat, R.string.shop_whatsapp) { open(context, "https://wa.me/" + seller.whatsapp.filter { it.isDigit() }) })
        if (seller.phone.isNotBlank()) add(Triple(R.drawable.ic_phone, R.string.shop_call) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + seller.phone)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        if (seller.instagram.isNotBlank()) add(Triple(R.drawable.ic_camera, R.string.shop_instagram) { open(context, "https://instagram.com/" + seller.instagram) })
        if (seller.address.isNotBlank() || seller.lat != null) add(Triple(R.drawable.ic_pin, R.string.shop_route) { openMap(context, seller) })
    }
    if (items.isEmpty()) {
        Text(stringResource(R.string.shop_contacts_soon), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val first = items.first()
    if (seller.telegram.isNotBlank()) {
        ShimmerButton(stringResource(first.second), onClick = first.third)
    }
    val rest = if (seller.telegram.isNotBlank()) items.drop(1) else items
    val columns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360) 1 else 2
    rest.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (icon, label, action) ->
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .weight(1f)
                        .pressScale(interaction)
                        .glass(RoundedCornerShape(18.dp), elevation = 4.dp)
                        .clickable(interactionSource = interaction, indication = null, onClick = action)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (row.size < columns) Spacer(Modifier.weight(1f))
        }
    }
}

// ---------------- Товар ----------------

@Composable
fun ProductScreen(productId: String? = null) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val lang = remember { Lang.content(context) }
    val catalog = Shop.catalog ?: remember { Shop.load(context) }
    val product = catalog.product(productId ?: nav.shopProduct)
    ScreenScaffold(title = product?.title?.get(lang) ?: stringResource(R.string.shop_products), onBack = { nav.back() }, showHeading = false) {
        if (product == null) {
            ZCard {
                Text(stringResource(R.string.shop_empty), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(stringResource(R.string.action_back), onClick = { nav.back() })
            }
            return@ScreenScaffold
        }
        val seller = catalog.seller(product.seller)
        ProductImage(product, catalog, Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(28.dp)), iconSize = 96.dp, fit = true)
        if (product.images.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                product.images.drop(1).forEach { img ->
                    AsyncImage(
                        model = Shop.imageUrl(img),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (seller?.promoted == true) AdLabel(Modifier.testTag("shop_ad_${product.id}"))
            Text(product.title.get(lang), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            PriceLine(product, lang, big = true)
        }
        if (!product.description.isEmpty) {
            ZCard { Text(product.description.get(lang), style = MaterialTheme.typography.bodyLarge) }
        }
        if (seller != null) {
            ZCard(onClick = {
                nav.shopSeller = seller.id
                nav.open(Screen.SHOP_SELLER)
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SellerLogo(seller, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(seller.name, style = MaterialTheme.typography.titleMedium)
                        Text(seller.city.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (seller.partner) CategoryChip(stringResource(R.string.shop_partner))
                }
            }
            ContactButtons(seller)
        }
    }
}

// ---------------- Переходы ----------------

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openTelegram(context: Context, user: String) = open(context, "https://t.me/" + user.removePrefix("@"))

/** Бот продавцов сразу на шаге «Добавить компанию». */
private fun openAddCompany(context: Context, catalog: Shop.Catalog) = open(context, "https://t.me/" + catalog.sellerBot + "?start=add")

private fun openMap(context: Context, seller: Shop.Seller) {
    val uri = if (seller.lat != null && seller.lon != null) {
        "geo:${seller.lat},${seller.lon}?q=${seller.lat},${seller.lon}(${Uri.encode(seller.name)})"
    } else {
        "geo:0,0?q=" + Uri.encode(seller.address)
    }
    open(context, uri)
}

/** Пометка «Реклама» у продвигаемых за плату компаний и их товаров. */
@Composable
internal fun AdLabel(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF343732)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(stringResource(R.string.shop_ad), style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
