package com.sspd.servicemgmt.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.CatalogOption
import com.sspd.servicemgmt.core.network.CatalogProduct
import com.sspd.servicemgmt.core.ui.component.CatalogSkeletonGrid
import com.sspd.servicemgmt.core.ui.component.ErrorRetryBanner
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.DangerBg
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.Success
import com.sspd.servicemgmt.core.ui.theme.SuccessBg
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.util.formatWarranty
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val ALL = "အားလုံး"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProductScreen(
    products: List<CatalogProduct>,
    categoryOptions: List<CatalogOption> = emptyList(),
    brandOptions: List<String> = emptyList(),
    loading: Boolean,
    cartCount: Int,
    cartQtyByProductId: Map<Int, Int> = emptyMap(),
    onChangeQty: (CatalogProduct, Int) -> Unit,
    onOpenCart: () -> Unit,
    page: Int = 0,
    total: Long = products.size.toLong(),
    hasNext: Boolean = false,
    error: String? = null,
    onPage: (Int) -> Unit = {},
    onCatalogQuery: ((String, Int?, String?, String?, String) -> Unit)? = null,
    wishlistIds: Set<Int> = emptySet(),
    onToggleFavorite: ((CatalogProduct) -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ALL) }
    var subCategory by remember { mutableStateOf(ALL) }
    var brand by remember { mutableStateOf(ALL) }
    var productType by remember { mutableStateOf(ALL) }
    var sort by remember { mutableStateOf("name") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<CatalogProduct?>(null) }

    BackHandler(enabled = selectedProduct != null || showFilterSheet) {
        when {
            selectedProduct != null -> selectedProduct = null
            showFilterSheet -> showFilterSheet = false
        }
    }

    val rootCategories = remember(categoryOptions, products) {
        val explicitRoots = categoryOptions
            .filter { it.parentId == null && !it.name.isNullOrBlank() }
            .map { it.name!!.trim() }
        val parentNames = categoryOptions.mapNotNull { it.parentName?.trim()?.takeIf(String::isNotBlank) } +
            products.mapNotNull { it.parentCategoryName?.trim()?.takeIf(String::isNotBlank) }
        val names = (explicitRoots + parentNames).distinct()
        val fallbackAll = (
            categoryOptions.mapNotNull { it.name?.trim()?.takeIf(String::isNotBlank) } +
                products.mapNotNull { it.categoryName?.trim()?.takeIf(String::isNotBlank) }
            ).distinct()
        listOf(ALL) + (names.ifEmpty { fallbackAll })
    }
    val subCategories = remember(categoryOptions, products, category) {
        if (category == ALL) return@remember emptyList()
        val fromMaster = categoryOptions.filter { opt ->
            !opt.name.isNullOrBlank() &&
                (
                    opt.parentName?.trim().equals(category, ignoreCase = true) == true ||
                        categoryOptions.any { root ->
                            root.name?.trim().equals(category, ignoreCase = true) == true &&
                                opt.parentId != null &&
                                opt.parentId == root.id
                        }
                    )
        }.map { it.name!!.trim() }
        val fromProducts = products
            .filter { it.parentCategoryName?.trim().equals(category, ignoreCase = true) == true }
            .mapNotNull { it.categoryName?.trim()?.takeIf(String::isNotBlank) }
        val names = (fromMaster + fromProducts).distinct()
        if (names.isEmpty()) emptyList() else listOf(ALL) + names
    }
    // Master options remain complete even when only one product page is loaded.
    val brands = remember(brandOptions) { listOf(ALL) + brandOptions.distinct().sorted() }
    val selectedCategoryId = categoryOptions.firstOrNull {
        it.name == (if (subCategory != ALL) subCategory else category)
    }?.id
    LaunchedEffect(query, selectedCategoryId, brand, productType, sort) {
        onCatalogQuery?.invoke(query, selectedCategoryId, brand.takeUnless { it == ALL },
            productType.takeUnless { it == ALL }, sort)
    }
    val filtered = products
    val filterCount = listOf(category != ALL, subCategory != ALL, brand != ALL).count { it }
    val hasActiveFilter = filterCount > 0 || productType != ALL || query.isNotBlank()

    val clearCategoryBrand = {
        category = ALL
        subCategory = ALL
        brand = ALL
    }

    val clearAll = {
        query = ""
        category = ALL
        subCategory = ALL
        brand = ALL
        productType = ALL
    }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBg,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactSearchField(
                            value = query,
                            onValueChange = { query = it.take(120) },
                            modifier = Modifier.weight(1f)
                        )
                        HeaderIconButton(
                            onClick = { showFilterSheet = true },
                            active = filterCount > 0,
                            badge = filterCount.takeIf { it > 0 }
                        ) {
                            Icon(
                                Icons.Outlined.FilterList,
                                contentDescription = "Filter",
                                tint = if (filterCount > 0) OnPrimary else Primary
                            )
                        }
                        HeaderIconButton(
                            onClick = onOpenCart,
                            active = false,
                            badge = cartCount.takeIf { it > 0 }?.coerceAtMost(9)
                        ) {
                            Icon(Icons.Outlined.ShoppingBag, contentDescription = "ခြင်းတောင်း", tint = Primary)
                        }
                    }

                    TypeSegmentRow(
                        selected = productType,
                        onSelect = { productType = it }
                    )

                    if (filterCount > 0) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (category != ALL) ActiveFilterChip(category) {
                                category = ALL
                                subCategory = ALL
                                brand = ALL
                            }
                            if (subCategory != ALL) ActiveFilterChip(subCategory) {
                                subCategory = ALL
                                brand = ALL
                            }
                            if (brand != ALL) ActiveFilterChip(brand) { brand = ALL }
                            Text(
                                "ရှင်းမည်",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = clearCategoryBrand)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                color = Primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when {
                                query.isNotBlank() -> "ရှာဖွေမှုရလဒ်"
                                productType != ALL -> productType
                                else -> "ပစ္စည်းအားလုံး"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        Text("$total မျိုး", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("name" to "အမည်", "price_asc" to "ဈေးနည်းမှများ",
                    "price_desc" to "ဈေးများမှနည်း", "newest" to "အသစ်တင်ထားသော").forEach { (value, label) ->
                    FilterChip(selected = sort == value, onClick = { sort = value }, label = { Text(label) })
                }
            }
            if (error != null) {
                ErrorRetryBanner(
                    message = error,
                    onRetry = { onPage(page) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onPage(page - 1) }, enabled = !loading && page > 0) { Text("ရှေ့စာမျက်နှာ") }
                Text("${page + 1}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onPage(page + 1) }, enabled = !loading && hasNext) { Text("နောက်စာမျက်နှာ") }
            }
            when {
                loading && products.isEmpty() -> {
                    CatalogSkeletonGrid()
                }
                filtered.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(PrimaryLight)
                                    .border(1.dp, Primary.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Inventory2, null, tint = Primary, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.height(14.dp))
                            Text("ပစ္စည်း မတွေ့ပါ", fontWeight = FontWeight.Bold, color = TextMain)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "ရှာဖွေမှု သို့မဟုတ် filter ကို ပြောင်းကြည့်ပါ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                            if (hasActiveFilter) {
                                Spacer(Modifier.height(14.dp))
                                TextButton(onClick = clearAll) {
                                    Text("အားလုံး ရှင်းမည်", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filtered, key = { index, product -> "${product.id}-$index" }) { _, product ->
                            CustomerProductCard(
                                product = product,
                                qty = cartQtyByProductId[product.id] ?: 0,
                                wishlisted = product.id in wishlistIds,
                                onOpen = { selectedProduct = product },
                                onChangeQty = { onChangeQty(product, it) },
                                onToggleFavorite = onToggleFavorite?.let { cb -> { cb(product) } }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ProductFilterSheet(
            categories = rootCategories,
            subCategories = subCategories,
            brands = brands,
            category = category,
            subCategory = subCategory,
            brand = brand,
            onCategory = {
                category = it
                subCategory = ALL
                brand = ALL
            },
            onSubCategory = {
                subCategory = it
                brand = ALL
            },
            onBrand = { brand = it },
            onReset = clearCategoryBrand,
            onDismiss = { showFilterSheet = false }
        )
    }

    selectedProduct?.let { product ->
        val qty = cartQtyByProductId[product.id] ?: 0
        ProductDetailSheet(
            product = product,
            qty = qty,
            wishlisted = product.id in wishlistIds,
            onDismiss = { selectedProduct = null },
            onChangeQty = { onChangeQty(product, it) },
            onToggleFavorite = onToggleFavorite?.let { cb -> { cb(product) } }
        )
    }
}

@Composable
private fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceSoft)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, null, tint = TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isBlank()) {
                Text("ပစ္စည်း ရှာမည်", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextMain),
                cursorBrush = SolidColor(Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotBlank()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Clear, contentDescription = "ရှင်းရန်", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    active: Boolean,
    badge: Int?,
    content: @Composable () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) Primary else SurfaceSoft)
                .border(
                    1.dp,
                    if (active) Primary else BorderColor,
                    RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Danger),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badge.toString(),
                    color = OnPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TypeSegmentRow(
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceSoft)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(ALL to "အားလုံး", "New" to "New", "Second" to "Second").forEach { (value, label) ->
            val isSelected = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) Primary else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSelected) OnPrimary else TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ActiveFilterChip(label: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PrimaryLight)
            .border(1.dp, Primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClear)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Outlined.Close, contentDescription = "ဖယ်မည်", tint = Primary, modifier = Modifier.size(14.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFilterSheet(
    categories: List<String>,
    subCategories: List<String>,
    brands: List<String>,
    category: String,
    subCategory: String,
    brand: String,
    onCategory: (String) -> Unit,
    onSubCategory: (String) -> Unit,
    onBrand: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = CardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Filter",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain
                )
                TextButton(onClick = onReset) {
                    Text("ရှင်းမည်", color = Primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "1) Category → 2) Sub category → 3) Brand",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                FilterChipSection("1. Category", categories, category, { it }, onCategory)
                if (category == ALL) {
                    Text(
                        "Category ရွေးပါ — သက်ဆိုင်ရာ Sub category ပေါ်လာပါမည်",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    if (subCategories.isNotEmpty()) {
                        FilterChipSection("2. Sub category", subCategories, subCategory, { it }, onSubCategory)
                    } else {
                        Text(
                            "ဤ Category တွင် Sub category မရှိပါ — Brand ဆက်ရွေးပါ",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    if (brands.isNotEmpty()) {
                        FilterChipSection("3. Brand", brands, brand, { it }, onBrand)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text("ရလဒ် ကြည့်မည်", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    onReset()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Filter မပါဘဲ ကြည့်မည်")
            }
        }
    }
}

@Composable
private fun FilterChipSection(
    title: String,
    options: List<String>,
    selected: String,
    labelOf: (String) -> String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = TextMuted, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            options.forEach { item ->
                FilterChip(
                    selected = selected == item,
                    onClick = { onSelect(item) },
                    label = { Text(labelOf(item)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryLight,
                        selectedLabelColor = Primary
                    )
                )
            }
        }
    }
}

@Composable
private fun CustomerProductCard(
    product: CatalogProduct,
    qty: Int,
    onOpen: () -> Unit,
    onChangeQty: (Int) -> Unit,
    wishlisted: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    val stock = product.availableStock()
    val outOfStock = stock <= 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(206.dp)
            .alpha(if (outOfStock) 0.72f else 1f)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
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
                    Icon(Icons.Outlined.Inventory2, null, tint = Primary, modifier = Modifier.size(28.dp))
                }
                if (outOfStock) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DangerBg)
                            .border(1.dp, Danger.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("ကုန်နေ", color = Danger, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(product.productTypeChipBg())
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        product.productTypeLabel(),
                        color = product.productTypeChipFg(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (onToggleFavorite != null) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(2.dp)
                            .size(34.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (wishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (wishlisted) "အကြိုက်စာရင်းမှ ဖယ်မည်" else "အကြိုက်စာရင်းထည့်မည်",
                                tint = if (wishlisted) Danger else OnPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                val photoCount = product.photoUrls.orEmpty().size
                if (photoCount > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "1/$photoCount",
                            color = OnPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        product.name.orEmpty().ifBlank { "ပစ္စည်း" },
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMain
                    )
                    Text(
                        listOfNotNull(product.brandName, product.categoryName)
                            .joinToString(" • ")
                            .ifBlank { product.productCode.orEmpty().ifBlank { " " } },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    val warranty = formatWarranty(product.warrantyTerms, product.warrantyMonths)
                    Text(
                        buildString {
                            append(if (outOfStock) "ကုန်နေသည်" else "ကျန် $stock ခု")
                            if (warranty.isNotBlank()) append(" · $warranty")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (outOfStock) Danger else Success,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        product.sellingPrice.money(),
                        modifier = Modifier.weight(1f),
                        color = Primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    QtyStepper(
                        qty = qty,
                        maxQty = stock,
                        enabled = !outOfStock,
                        compact = true,
                        onChangeQty = onChangeQty
                    )
                }
            }
        }
    }
}

@Composable
private fun QtyStepper(
    qty: Int,
    maxQty: Int,
    enabled: Boolean,
    compact: Boolean,
    onChangeQty: (Int) -> Unit
) {
    val height = if (compact) 28.dp else 40.dp
    val btn = if (compact) 26.dp else 36.dp
    val icon = if (compact) 14.dp else 18.dp
    val canIncrease = enabled && qty < maxQty
    if (qty <= 0) {
        Box(
            modifier = Modifier
                .size(btn)
                .clip(RoundedCornerShape(8.dp))
                .background(if (canIncrease) Primary else SurfaceSoft)
                .border(
                    1.dp,
                    if (canIncrease) Primary else BorderColor,
                    RoundedCornerShape(8.dp)
                )
                .clickable(enabled = canIncrease) { onChangeQty(1) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "ခြင်းထည့်ရန်",
                tint = if (canIncrease) OnPrimary else TextMuted,
                modifier = Modifier.size(icon)
            )
        }
    } else {
        Row(
            modifier = Modifier
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceSoft)
                .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(btn)
                    .clickable(enabled = enabled) { onChangeQty(qty - 1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = "လျှော့မည်", tint = Primary, modifier = Modifier.size(icon))
            }
            Text(
                qty.toString(),
                modifier = Modifier.width(if (compact) 22.dp else 28.dp),
                textAlign = TextAlign.Center,
                color = TextMain,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .size(btn)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (canIncrease) Primary else Color.Transparent)
                    .alpha(if (canIncrease || !enabled) 1f else 0.35f)
                    .clickable(enabled = canIncrease) { onChangeQty(qty + 1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "တိုးမည်",
                    tint = if (canIncrease) OnPrimary else TextMuted,
                    modifier = Modifier.size(icon)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDetailSheet(
    product: CatalogProduct,
    qty: Int,
    onDismiss: () -> Unit,
    onChangeQty: (Int) -> Unit,
    wishlisted: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = CardBg
    ) {
        ProductDetailContent(
            product = product,
            qty = qty,
            onDismiss = onDismiss,
            onChangeQty = onChangeQty,
            wishlisted = wishlisted,
            onToggleFavorite = onToggleFavorite
        )
    }
}

@Composable
private fun ProductDetailContent(
    product: CatalogProduct,
    qty: Int,
    onDismiss: () -> Unit,
    onChangeQty: (Int) -> Unit,
    modifier: Modifier = Modifier,
    wishlisted: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    val stock = product.availableStock()
    val outOfStock = stock <= 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
            ProductPhotoGallery(
                photoUrls = product.photoUrls.orEmpty(),
                productName = product.name,
                onClose = onDismiss
            )

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        product.name.orEmpty().ifBlank { "ပစ္စည်း" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                    Text(
                        listOfNotNull(product.brandName, product.categoryName).joinToString(" • "),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onToggleFavorite != null) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (wishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (wishlisted) "အကြိုက်စာရင်းမှ ဖယ်မည်" else "အကြိုက်စာရင်းထည့်မည်",
                                tint = if (wishlisted) Danger else TextMuted
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(product.productTypeChipBg())
                            .border(1.dp, product.productTypeChipFg().copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            product.productTypeLabel(),
                            color = product.productTypeChipFg(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (outOfStock) DangerBg else SuccessBg)
                            .border(
                                1.dp,
                                if (outOfStock) Danger.copy(alpha = 0.15f) else Success.copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            if (outOfStock) "ကုန်နေသည်" else "ကျန် $stock ခု",
                            color = if (outOfStock) Danger else Success,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(
                product.sellingPrice.money(),
                color = Primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(color = BorderColor.copy(alpha = 0.8f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductDetailValue("Code", product.productCode.orEmpty().ifBlank { "—" }, Modifier.weight(1f))
                ProductDetailValue(
                    "Warranty",
                    formatWarranty(product.warrantyTerms, product.warrantyMonths).ifBlank { "မရှိပါ" },
                    Modifier.weight(1f)
                )
            }
            val remark = product.remark?.trim().orEmpty()
            if (!outOfStock && remark.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSoft)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Remark", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(remark, style = MaterialTheme.typography.bodyMedium, color = TextMain)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("အရေအတွက်", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = TextMain)
                QtyStepper(
                    qty = qty,
                    maxQty = stock,
                    enabled = !outOfStock,
                    compact = false,
                    onChangeQty = onChangeQty
                )
            }
            if (qty > 0) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ခြင်းတောင်းထဲ $qty ခု ရှိသည်", fontWeight = FontWeight.SemiBold)
                }
            }
        }
}

@Composable
private fun ProductPhotoGallery(
    photoUrls: List<String>,
    productName: String?,
    onClose: () -> Unit
) {
    val urls = photoUrls.map { it.assetUrl() }.filter { it.isNotBlank() }
    val pagerState = rememberPagerState(pageCount = { urls.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceSoft)
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (urls.isEmpty()) {
                Icon(Icons.Outlined.Inventory2, null, tint = Primary, modifier = Modifier.size(62.dp))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    AsyncImage(
                        model = urls[page],
                        contentDescription = "${productName.orEmpty()} ${page + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(CardBg.copy(alpha = 0.9f))
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "ပိတ်မည်")
            }

            if (urls.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${pagerState.currentPage + 1}/${urls.size}",
                        color = OnPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(urls.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == index) Primary
                                    else OnPrimary.copy(alpha = 0.75f)
                                )
                        )
                    }
                }
            }
        }

        if (urls.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                urls.forEachIndexed { index, url ->
                    val selected = pagerState.currentPage == index
                    AsyncImage(
                        model = url,
                        contentDescription = "Preview ${index + 1}",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Primary else BorderColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductDetailValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceSoft)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(3.dp))
        Text(value, fontWeight = FontWeight.SemiBold, color = TextMain)
    }
}

private fun CatalogProduct.availableStock(): Int = (stockQty ?: 0).coerceAtLeast(0)

private fun CatalogProduct.normalizedType(): String = when (productType?.trim()?.uppercase()?.replace(' ', '_')) {
    "SECOND", "SECOND_NEW", "SECONDNEW" -> "Second"
    else -> "New"
}

private fun CatalogProduct.productTypeLabel(): String = normalizedType()

@Composable
private fun CatalogProduct.productTypeChipBg(): Color = when (normalizedType()) {
    "Second" -> Color(0xFFFFF1E6)
    else -> PrimaryLight
}

@Composable
private fun CatalogProduct.productTypeChipFg(): Color = when (normalizedType()) {
    "Second" -> Color(0xFFC2410C)
    else -> Primary
}

private fun String.assetUrl(): String =
    if (startsWith("http://") || startsWith("https://")) this
    else BuildConfig.DEFAULT_BASE_URL.trimEnd('/') + "/" + trimStart('/')

private fun Double?.money(): String {
    val safe = this?.takeIf { it.isFinite() } ?: 0.0
    val absValue = abs(safe).toLong()
    val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Preview(name = "Catalog — dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerProductPreview() {
    AppTheme {
        CustomerProductScreen(
            products = listOf(
                CatalogProduct(
                    id = 1,
                    name = "Dell Inspiron 15 3530",
                    productCode = "DELL-3530",
                    categoryName = "Laptop",
                    brandName = "Dell",
                    productType = "New",
                    sellingPrice = 1_350_000.0,
                    warrantyMonths = 12,
                    inStock = true,
                    stockQty = 5,
                    photoUrls = listOf(
                        "https://picsum.photos/seed/dell1/800/600",
                        "https://picsum.photos/seed/dell2/800/600",
                        "https://picsum.photos/seed/dell3/800/600"
                    )
                ),
                CatalogProduct(
                    id = 2,
                    name = "HP 15.6 Backpack",
                    productCode = "HP-BAG-15",
                    categoryName = "Accessories",
                    brandName = "HP",
                    productType = "New",
                    sellingPrice = 48_000.0,
                    warrantyMonths = 0,
                    inStock = true,
                    stockQty = 12
                ),
                CatalogProduct(
                    id = 3,
                    name = "Logitech Wireless Mouse M331",
                    productCode = "LOG-M331",
                    categoryName = "Accessories",
                    brandName = "Logitech",
                    productType = "Second",
                    sellingPrice = 45_000.0,
                    warrantyMonths = 3,
                    inStock = true,
                    stockQty = 2
                ),
                CatalogProduct(
                    id = 4,
                    name = "Samsung 24 inch Monitor",
                    productCode = "SAM-24",
                    categoryName = "Monitor",
                    brandName = "Samsung",
                    productType = "Second",
                    sellingPrice = 280_000.0,
                    warrantyMonths = 0,
                    inStock = false,
                    stockQty = 0
                )
            ),
            categoryOptions = listOf(
                CatalogOption(id = 1, name = "Laptop"),
                CatalogOption(id = 2, name = "Accessories"),
                CatalogOption(id = 3, name = "Monitor")
            ),
            brandOptions = listOf("Dell", "HP", "Logitech", "Samsung"),
            loading = false,
            cartCount = 2,
            cartQtyByProductId = mapOf(1 to 2),
            onChangeQty = { _, _ -> },
            onOpenCart = {}
        )
    }
}

@Composable
private fun ProductDetailSheetPreviewHost(
    product: CatalogProduct,
    qty: Int
) {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                color = CardBg,
                shadowElevation = 8.dp
            ) {
                ProductDetailContent(
                    product = product,
                    qty = qty,
                    onDismiss = {},
                    onChangeQty = {},
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

@Preview(name = "Detail — in stock", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ProductDetailInStockPreview() {
    ProductDetailSheetPreviewHost(
        product = CatalogProduct(
            id = 1,
            name = "Dell Inspiron 15 3530",
            productCode = "DELL-3530",
            categoryName = "Laptop",
            brandName = "Dell",
            productType = "New",
            sellingPrice = 1_350_000.0,
            warrantyMonths = 12,
            remark = "i5 / 16GB RAM / 512GB SSD — အသစ်၊ box ပါ",
            inStock = true,
            stockQty = 5,
            photoUrls = listOf(
                "https://picsum.photos/seed/dell1/800/600",
                "https://picsum.photos/seed/dell2/800/600"
            )
        ),
        qty = 2
    )
}

@Preview(name = "Detail — out of stock", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ProductDetailOutOfStockPreview() {
    ProductDetailSheetPreviewHost(
        product = CatalogProduct(
            id = 4,
            name = "Samsung 24 inch Monitor",
            productCode = "SAM-24",
            categoryName = "Monitor",
            brandName = "Samsung",
            productType = "Second",
            sellingPrice = 280_000.0,
            warrantyMonths = 0,
            inStock = false,
            stockQty = 0
        ),
        qty = 0
    )
}

@Preview(name = "Detail — second hand", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ProductDetailSecondPreview() {
    ProductDetailSheetPreviewHost(
        product = CatalogProduct(
            id = 3,
            name = "Logitech Wireless Mouse M331",
            productCode = "LOG-M331",
            categoryName = "Accessories",
            brandName = "Logitech",
            productType = "Second",
            sellingPrice = 45_000.0,
            warrantyMonths = 3,
            inStock = true,
            stockQty = 2
        ),
        qty = 1
    )
}
