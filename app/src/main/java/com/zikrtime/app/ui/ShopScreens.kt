package com.zikrtime.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

private fun accentOf(seller: Shop.Seller?): Color = seller?.accent?.let { Color(0xFF000000 or it) } ?: Color(0xFF0F5C4A)

// ---------------- Витрина ----------------

@Composable
fun ShopScreen() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val lang = remember { Lang.content(context) }
    val catalog = Shop.catalog ?: remember { Shop.load(context) }
    LaunchedEffect(Unit) { Shop.refresh(context) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val products = catalog.products.filter { p ->
        (category == null || p.category == category) &&
            (query.isBlank() || p.title.get(lang).contains(query.trim(), ignoreCase = true) ||
                catalog.seller(p.seller)?.name?.contains(query.trim(), ignoreCase = true) == true)
    }
    val sellers = catalog.sellers.filter { category == null || category in it.categories }

    TabColumn(title = stringResource(R.string.shop_title), subtitle = stringResource(R.string.shop_subtitle)) {
        if (catalog.demo) {
            ZCard(color = Gold.copy(alpha = 0.22f), padding = PaddingValues(16.dp)) {
                Text(stringResource(R.string.shop_demo_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (Shop.offline) {
            Text(stringResource(R.string.shop_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Поиск
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), elevation = 4.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_nav_shop), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.shop_search), color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Категории
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryPill(stringResource(R.string.shop_all), R.drawable.ic_shop_store, category == null) { category = null }
            catalog.categories.forEach { c ->
                CategoryPill(c.name.get(lang), shopIcon(c.icon), category == c.id) { category = if (category == c.id) null else c.id }
            }
        }

        if (sellers.isNotEmpty() && query.isBlank()) {
            SectionLabel(stringResource(R.string.shop_sellers))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                sellers.forEach { s ->
                    SellerCard(s, lang, catalog) {
                        nav.shopSeller = s.id
                        nav.open(Screen.SHOP_SELLER)
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.shop_products))
        if (products.isEmpty()) {
            Text(stringResource(R.string.shop_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ProductGrid(products, catalog, lang) { p ->
            nav.shopProduct = p.id
            nav.open(Screen.SHOP_PRODUCT)
        }

        // Стать продавцом
        GlassHero {
            Column(Modifier.padding(22.dp)) {
                Text(stringResource(R.string.shop_become_seller), style = MaterialTheme.typography.titleLarge, color = Cream)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.shop_become_seller_text), style = MaterialTheme.typography.bodyMedium, color = Cream.copy(alpha = 0.85f))
                if (catalog.contactTelegram.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    GoldButton(stringResource(R.string.shop_contact_telegram), onClick = { openTelegram(context, catalog.contactTelegram) })
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(text: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .pressScale(interaction)
            .then(
                if (selected) Modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFF1A7D63), EmeraldDeep)))
                else Modifier.glass(shape, elevation = 3.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (selected) Gold else scheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else scheme.onSurface)
    }
}

@Composable
private fun SellerCard(seller: Shop.Seller, lang: String, catalog: Shop.Catalog, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(26.dp)
    val accent = accentOf(seller)
    Column(
        Modifier
            .width(250.dp)
            .pressScale(interaction)
            .glass(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.75f), EmeraldDeep))),
        ) {
            val coverUrl = Shop.imageUrl(seller.cover)
            if (coverUrl != null) {
                AsyncImage(model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                StarPattern(Modifier.fillMaxSize(), color = Gold.copy(alpha = 0.18f), cell = 48f)
                Icon(
                    painterResource(shopIcon(catalog.category(seller.categories.firstOrNull() ?: "")?.icon ?: "store")),
                    contentDescription = null,
                    tint = Cream.copy(alpha = 0.35f),
                    modifier = Modifier.size(64.dp).align(Alignment.CenterEnd).padding(end = 16.dp),
                )
            }
            if (seller.partner) {
                CategoryChip(stringResource(R.string.shop_partner), Modifier.align(Alignment.TopStart).padding(10.dp))
            }
        }
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SellerLogo(seller, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(seller.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    seller.city.get(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SellerLogo(seller: Shop.Seller, size: androidx.compose.ui.unit.Dp) {
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
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(seller.name.trim().take(1).uppercase(), color = Cream, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
private fun ProductGrid(products: List<Shop.Product>, catalog: Shop.Catalog, lang: String, onClick: (Shop.Product) -> Unit) {
    products.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { p -> ProductCard(p, catalog, lang, Modifier.weight(1f)) { onClick(p) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProductImage(product: Shop.Product, catalog: Shop.Catalog, modifier: Modifier, iconSize: androidx.compose.ui.unit.Dp = 48.dp) {
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
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
private fun ProductCard(product: Shop.Product, catalog: Shop.Catalog, lang: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .pressScale(interaction)
            .glass(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box {
            ProductImage(product, catalog, Modifier.fillMaxWidth().aspectRatio(1f))
            if (product.oldPrice != null && product.price != null && product.oldPrice > product.price) {
                val percent = 100 - product.price * 100 / product.oldPrice
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFFB3261E))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) { Text("−$percent%", color = Color.White, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(product.title.get(lang), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            PriceLine(product, lang)
            Text(
                catalog.seller(product.seller)?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PriceLine(product: Shop.Product, lang: String, big: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        val price = product.price
        Text(
            if (price != null) Shop.price(price, product.currency) else stringResource(R.string.shop_price_on_request),
            style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!product.unit.isEmpty) {
            Text(
                " / " + product.unit.get(lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = if (big) 4.dp else 2.dp),
            )
        }
        val old = product.oldPrice
        if (old != null && price != null && old > price) {
            Spacer(Modifier.width(8.dp))
            Text(
                Shop.price(old, product.currency),
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

// ---------------- Продавец ----------------

@Composable
fun SellerScreen() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val lang = remember { Lang.content(context) }
    val catalog = Shop.catalog ?: remember { Shop.load(context) }
    val seller = catalog.seller(nav.shopSeller)
    ScreenScaffold(title = seller?.name.orEmpty(), onBack = { nav.back() }) {
        if (seller == null) return@ScreenScaffold
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
            Box(Modifier.fillMaxSize().sheen(alpha = 0.12f, periodMs = 5_000))
            Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SellerLogo(seller, 60.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(seller.name, style = MaterialTheme.typography.titleLarge, color = Cream)
                    Text(seller.city.get(lang), style = MaterialTheme.typography.bodyMedium, color = Cream.copy(alpha = 0.85f))
                }
            }
            if (seller.partner) CategoryChip(stringResource(R.string.shop_partner), Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
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
        val products = catalog.products.filter { it.seller == seller.id }
        if (products.isNotEmpty()) {
            SectionLabel(stringResource(R.string.shop_seller_products))
            ProductGrid(products, catalog, lang) { p ->
                nav.shopProduct = p.id
                nav.open(Screen.SHOP_PRODUCT)
            }
        }
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
    rest.chunked(2).forEach { row ->
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
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

// ---------------- Товар ----------------

@Composable
fun ProductScreen() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val lang = remember { Lang.content(context) }
    val catalog = Shop.catalog ?: remember { Shop.load(context) }
    val product = catalog.product(nav.shopProduct)
    ScreenScaffold(title = product?.title?.get(lang).orEmpty(), onBack = { nav.back() }) {
        if (product == null) return@ScreenScaffold
        val seller = catalog.seller(product.seller)
        ProductImage(product, catalog, Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(28.dp)), iconSize = 96.dp)
        if (product.images.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                product.images.drop(1).forEach { img ->
                    AsyncImage(
                        model = Shop.imageUrl(img),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
        }
        Column {
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

private fun openMap(context: Context, seller: Shop.Seller) {
    val uri = if (seller.lat != null && seller.lon != null) {
        "geo:${seller.lat},${seller.lon}?q=${seller.lat},${seller.lon}(${Uri.encode(seller.name)})"
    } else {
        "geo:0,0?q=" + Uri.encode(seller.address)
    }
    open(context, uri)
}
