package com.zikrtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zikrtime.app.R
import com.zikrtime.app.data.Shop
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.Gold

// Editorial placement uses catalog identities so a company can rename itself.
private val partnerOrder = mapOf("sfb443720" to 0, "s24a178b0" to 1)

@Composable
internal fun PartnerGrid(
    partners: List<Shop.Seller>,
    catalog: Shop.Catalog,
    lang: String,
    onClick: (Shop.Seller) -> Unit,
) {
    if (partners.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("shop_partners"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.shop_partners_title))
        val ordered = partners.sortedBy { partnerOrder[it.id] ?: Int.MAX_VALUE }
        for (pair in ordered.chunked(2)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (seller in pair) key(seller.id) {
                    PartnerTile(seller, catalog, lang, Modifier.weight(1f).fillMaxHeight(), onClick)
                }
                if (pair.size == 1 && ordered.size > 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PartnerTile(
    seller: Shop.Seller,
    catalog: Shop.Catalog,
    lang: String,
    modifier: Modifier,
    onClick: (Shop.Seller) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 17.dp, topEnd = 9.dp, bottomEnd = 17.dp, bottomStart = 9.dp)
    val category = seller.categories.firstNotNullOfOrNull { catalog.category(it) }
    val caption = category?.name?.get(lang).orEmpty().ifBlank { seller.city.get(lang) }
    Column(modifier.testTag("shop_partner_${seller.id}").clip(shape)
        .background(scheme.surfaceContainerLow).border(1.dp, Gold.copy(alpha = .50f), shape)
        .clickable(role = Role.Button) { onClick(seller) }) {
        Box(Modifier.fillMaxWidth().height(58.dp).background(EmeraldDeep)) {
            StarPattern(Modifier.matchParentSize(), color = Cream.copy(alpha = .10f), cell = 46f)
            Shop.imageUrl(seller.cover)?.let { cover ->
                AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize())
            }
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                listOf(Color.Black.copy(alpha = .58f), Color.Black.copy(alpha = .25f)))))
            Box(Modifier.align(Alignment.CenterStart).padding(start = 10.dp)) {
                SellerLogo(seller, 38.dp)
            }
            if (seller.promoted) {
                AdLabel(Modifier.align(Alignment.TopEnd).padding(4.dp).testTag("shop_ad_${seller.id}"))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(seller.name, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (caption.isNotBlank()) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            RatingBadge("seller:${seller.id}")
        }
    }
}
