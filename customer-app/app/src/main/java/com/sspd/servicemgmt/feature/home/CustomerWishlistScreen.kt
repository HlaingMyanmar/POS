package com.sspd.servicemgmt.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.CatalogProduct
import com.sspd.servicemgmt.core.ui.component.WishlistSkeletonList
import com.sspd.servicemgmt.core.ui.theme.*

private fun String.assetUrl(): String =
    if (startsWith("http://") || startsWith("https://")) this
    else BuildConfig.DEFAULT_BASE_URL.trimEnd('/') + "/" + trimStart('/')

@Composable
fun CustomerWishlistScreen(
    wishlistItems: List<CatalogProduct>,
    onToggleFavorite: (CatalogProduct) -> Unit,
    onAddToCart: (CatalogProduct) -> Unit,
    isLoading: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "အကြိုက်စာရင်း",
            style = MaterialTheme.typography.titleLarge,
            color = TextMain,
            fontWeight = FontWeight.Bold
        )

        when {
            isLoading && wishlistItems.isEmpty() -> {
                WishlistSkeletonList()
            }
            wishlistItems.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("အကြိုက်စာရင်း မရှိသေးပါ", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(wishlistItems, key = { it.id }) { product ->
                        WishlistItemCard(
                            product = product,
                            onToggleFavorite = { onToggleFavorite(product) },
                            onAddToCart = { onAddToCart(product) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistItemCard(
    product: CatalogProduct,
    onToggleFavorite: () -> Unit,
    onAddToCart: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceSoft),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = (product.thumbnailUrl ?: product.photoUrls.orEmpty().firstOrNull())?.assetUrl()
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Outlined.Inventory2, null, tint = Primary, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name ?: "ပစ္စည်း",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    text = "${(product.sellingPrice ?: 0.0).toLong()} Ks",
                    style = MaterialTheme.typography.bodySmall,
                    color = Primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onAddToCart,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("ခြင်းထည့်", fontSize = 12.sp, color = Primary)
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "အကြိုက်စာရင်းမှ ဖယ်မည်",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
