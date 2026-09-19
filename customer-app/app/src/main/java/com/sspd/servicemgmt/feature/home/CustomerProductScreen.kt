package com.sspd.servicemgmt.feature.home

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.Success
import com.sspd.servicemgmt.core.ui.theme.SuccessBg
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.ui.theme.Warning
import com.sspd.servicemgmt.core.ui.theme.WarningBg
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
    var appliedCategory by remember { mutableStateOf(ALL) }
    var appliedSubCategory by remember { mutableStateOf(ALL) }
    var appliedBrand by remember { mutableStateOf(ALL) }
    var appliedProductType by remember { mutableStateOf(ALL) }
    var sort by remember { mutableStateOf("name") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<CatalogProduct?>(null) }

    val gridState = rememberLazyGridState()

    BackHandler(enabled = selectedProduct != null || showFilterSheet) {
        when {
            selectedProduct != null -> selectedProduct = null
            showFilterSheet -> showFilterSheet = false
        }
    }

    val selectedCategoryId = categoryOptions.firstOrNull {
        it.name == (if (appliedSubCategory != ALL) appliedSubCategory else appliedCategory)
    }?.id

    LaunchedEffect(query, selectedCategoryId, appliedBrand, appliedProductType, sort) {
        gridState.scrollToItem(0)
        onCatalogQuery?.invoke(
            query,
            selectedCategoryId,
            appliedBrand.takeUnless { it == ALL },
            appliedProductType.takeUnless { it == ALL },
            sort
        )
    }

    // Prefetch next page 4-6 items before reaching bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore, hasNext, loading, error) {
        if (shouldLoadMore && hasNext && !loading && error == null) {
            onPage(page + 1)
        }
    }

    val filtered = products
    val filterCount = listOf(appliedCategory != ALL, appliedSubCategory != ALL, appliedBrand != ALL, appliedProductType != ALL).count { it }
    val hasActiveFilter = filterCount > 0 || query.isNotBlank()

    val clearAppliedFilters = {
        appliedCategory = ALL
        appliedSubCategory = ALL
        appliedBrand = ALL
        appliedProductType = ALL
    }

    val clearAll = {
        query = ""
        appliedCategory = ALL
        appliedSubCategory = ALL
        appliedBrand = ALL
        appliedProductType = ALL
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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

                    if (filterCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (appliedProductType != ALL) ActiveFilterChip("$appliedProductType ×") {
                                appliedProductType = ALL
                            }
                            if (appliedCategory != ALL) ActiveFilterChip("$appliedCategory ×") {
                                appliedCategory = ALL
                                appliedSubCategory = ALL
                                // Validate if brand is still valid at root level
                                if (appliedBrand != ALL) {
                                    val isValidRoot = products.any { p ->
                                        p.brandName?.trim().equals(appliedBrand, ignoreCase = true) == true
                                    } || brandOptions.any { it.equals(appliedBrand, ignoreCase = true) }
                                    if (!isValidRoot) appliedBrand = ALL
                                }
                            }
                            if (appliedSubCategory != ALL) ActiveFilterChip("$appliedSubCategory ×") {
                                appliedSubCategory = ALL
                                // Validate if brand is still valid for category
                                if (appliedBrand != ALL && appliedCategory != ALL) {
                                    val isValidCat = products.any { p ->
                                        val matchesCat = p.parentCategoryName?.trim().equals(appliedCategory, ignoreCase = true) == true ||
                                            p.categoryName?.trim().equals(appliedCategory, ignoreCase = true) == true
                                        matchesCat && p.brandName?.trim().equals(appliedBrand, ignoreCase = true) == true
                                    } || brandOptions.any { it.equals(appliedBrand, ignoreCase = true) }
                                    if (!isValidCat) appliedBrand = ALL
                                }
                            }
                            if (appliedBrand != ALL) ActiveFilterChip("$appliedBrand ×") {
                                appliedBrand = ALL
                            }
                            TextButton(
                                onClick = clearAppliedFilters,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Clear all", color = Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (query.isNotBlank()) "ရှာဖွေမှုရလဒ်" else "ပစ္စည်းများ",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        Text("$total မျိုး", color = TextMuted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "name" to "အမည်",
                    "price_asc" to "ဈေးနည်းမှများ",
                    "price_desc" to "ဈေးများမှနည်း",
                    "newest" to "အသစ်တင်ထားသော"
                ).forEach { (value, label) ->
                    FilterChip(selected = sort == value, onClick = { sort = value }, label = { Text(label) })
                }
            }
            if (error != null && products.isEmpty()) {
                ErrorRetryBanner(
                    message = error,
                    onRetry = { onPage(0) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
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
                                OutlinedButton(
                                    onClick = clearAll,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Clear Filters", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filtered, key = { index, product -> "prod-${product.id}-$index" }) { _, product ->
                            CustomerProductCard(
                                product = product,
                                qty = cartQtyByProductId[product.id] ?: 0,
                                wishlisted = product.id in wishlistIds,
                                onOpen = { selectedProduct = product },
                                onChangeQty = { onChangeQty(product, it) },
                                onToggleFavorite = onToggleFavorite?.let { cb -> { cb(product) } }
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            when {
                                loading && products.isNotEmpty() -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Primary,
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                }
                                error != null && products.isNotEmpty() -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "Couldn't load more products",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted
                                        )
                                        OutlinedButton(
                                            onClick = { onPage(page + 1) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Retry", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                !hasNext && products.size >= 12 -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "All products loaded",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ProductFilterSheet(
            categoryOptions = categoryOptions,
            brandOptions = brandOptions,
            products = products,
            appliedCategory = appliedCategory,
            appliedSubCategory = appliedSubCategory,
            appliedBrand = appliedBrand,
            appliedProductType = appliedProductType,
            appliedTotalCount = total,
            onApplyFilters = { newCat, newSubCat, newBrand, newType ->
                appliedCategory = newCat
                appliedSubCategory = newSubCat
                appliedBrand = newBrand
                appliedProductType = newType
            },
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
private fun FilterSelectionRow(
    label: String,
    selectedValue: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceSoft,
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) TextMain else TextMuted
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (selectedValue == ALL) "အားလုံး" else selectedValue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedValue != ALL && enabled) Primary else TextMuted
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (enabled) TextMuted else TextMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableSelectionSheet(
    title: String,
    searchPlaceholder: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredOptions = remember(options, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) options
        else options.filter { it == ALL || it.lowercase().contains(q) }
    }

    BackHandler(enabled = true) {
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = CardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceSoft)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "ပိတ်မည်",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            CompactSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                placeholderText = searchPlaceholder
            )

            Spacer(Modifier.height(12.dp))

            if (filteredOptions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ရလဒ် မတွေ့ပါ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(filteredOptions, key = { index, item -> "$item-$index" }) { _, option ->
                        val isSelected = selected == option
                        val displayLabel = if (option == ALL) "အားလုံး (All)" else option
                        Surface(
                            onClick = {
                                onSelect(option)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) PrimaryLight else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = displayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Primary else TextMain,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelect(option)
                                        onDismiss()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = Primary)
                                )
                            }
                        }
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

private enum class SelectionType { CATEGORY, SUB_CATEGORY, BRAND }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFilterSheet(
    categoryOptions: List<CatalogOption>,
    brandOptions: List<String>,
    products: List<CatalogProduct>,
    appliedCategory: String,
    appliedSubCategory: String,
    appliedBrand: String,
    appliedProductType: String,
    appliedTotalCount: Long,
    onApplyFilters: (category: String, subCategory: String, brand: String, productType: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Draft Filter States
    var draftCategory by remember { mutableStateOf(appliedCategory) }
    var draftSubCategory by remember { mutableStateOf(appliedSubCategory) }
    var draftBrand by remember { mutableStateOf(appliedBrand) }
    var draftProductType by remember { mutableStateOf(appliedProductType) }
    var activeSelectionSheet by remember { mutableStateOf<SelectionType?>(null) }

    // Root Categories
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

    // Dependent SubCategories for draftCategory
    val availableSubCategories = remember(categoryOptions, products, draftCategory) {
        if (draftCategory == ALL) emptyList()
        else {
            val fromMaster = categoryOptions.filter { opt ->
                !opt.name.isNullOrBlank() &&
                    (
                        opt.parentName?.trim().equals(draftCategory, ignoreCase = true) == true ||
                            categoryOptions.any { root ->
                                root.name?.trim().equals(draftCategory, ignoreCase = true) == true &&
                                    opt.parentId != null &&
                                    opt.parentId == root.id
                            }
                        )
            }.map { it.name!!.trim() }
            val fromProducts = products
                .filter { it.parentCategoryName?.trim().equals(draftCategory, ignoreCase = true) == true }
                .mapNotNull { it.categoryName?.trim()?.takeIf(String::isNotBlank) }
            val names = (fromMaster + fromProducts).distinct()
            if (names.isEmpty()) emptyList() else listOf(ALL) + names
        }
    }

    // Dependent Brands for draftCategory & draftSubCategory
    val availableBrands = remember(brandOptions, products, draftCategory, draftSubCategory) {
        val availableFromProducts = products.filter { p ->
            val matchesCat = draftCategory == ALL ||
                p.parentCategoryName?.trim().equals(draftCategory, ignoreCase = true) == true ||
                p.categoryName?.trim().equals(draftCategory, ignoreCase = true) == true
            val matchesSubCat = draftSubCategory == ALL ||
                p.categoryName?.trim().equals(draftSubCategory, ignoreCase = true) == true
            matchesCat && matchesSubCat
        }.mapNotNull { it.brandName?.trim()?.takeIf(String::isNotBlank) }

        val allMasterBrands = brandOptions.distinct()
        val filteredBrands = if (draftCategory == ALL && draftSubCategory == ALL) {
            allMasterBrands
        } else {
            if (availableFromProducts.isNotEmpty()) {
                (availableFromProducts + allMasterBrands.filter { b -> availableFromProducts.any { it.equals(b, ignoreCase = true) } }).distinct()
            } else {
                allMasterBrands
            }
        }
        listOf(ALL) + filteredBrands.sorted()
    }

    val isDraftUnchanged = draftCategory == appliedCategory &&
        draftSubCategory == appliedSubCategory &&
        draftBrand == appliedBrand &&
        draftProductType == appliedProductType

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = CardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Filter (စစ်ထုတ်ရန်)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain
                )
                TextButton(onClick = {
                    draftCategory = ALL
                    draftSubCategory = ALL
                    draftBrand = ALL
                    draftProductType = ALL
                }) {
                    Text("Reset", color = Primary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Condition Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Condition (ပစ္စည်း အခြေအနေ)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(ALL to "အားလုံး (All)", "New" to "New", "Second" to "Second").forEach { (value, displayLabel) ->
                            val isSelected = draftProductType == value
                            Surface(
                                onClick = { draftProductType = value },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) PrimaryLight else SurfaceSoft,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Primary else BorderColor
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = displayLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Primary else TextMain
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

                // 2. Category Row
                FilterSelectionRow(
                    label = "Category",
                    selectedValue = draftCategory,
                    onClick = { activeSelectionSheet = SelectionType.CATEGORY }
                )

                // 3. Sub Category Row
                FilterSelectionRow(
                    label = "Sub Category",
                    selectedValue = draftSubCategory,
                    enabled = draftCategory != ALL && availableSubCategories.isNotEmpty(),
                    onClick = { activeSelectionSheet = SelectionType.SUB_CATEGORY }
                )

                // 4. Brand Row
                FilterSelectionRow(
                    label = "Brand",
                    selectedValue = draftBrand,
                    enabled = availableBrands.isNotEmpty(),
                    onClick = { activeSelectionSheet = SelectionType.BRAND }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Sticky Bottom Action Area
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        draftCategory = ALL
                        draftSubCategory = ALL
                        draftBrand = ALL
                        draftProductType = ALL
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Clear all", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        onApplyFilters(draftCategory, draftSubCategory, draftBrand, draftProductType)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(2f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = if (isDraftUnchanged && appliedTotalCount > 0) "Show $appliedTotalCount Products" else "Show Products",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    // Dependent Searchable Selection Bottom Sheets
    when (activeSelectionSheet) {
        SelectionType.CATEGORY -> SearchableSelectionSheet(
            title = "Select Category",
            searchPlaceholder = "Search category...",
            options = rootCategories,
            selected = draftCategory,
            onSelect = { selectedCat ->
                draftCategory = selectedCat
                // Dependent Validation on Category Change
                val newSubCats = if (selectedCat == ALL) emptyList() else {
                    val fromMaster = categoryOptions.filter { opt ->
                        !opt.name.isNullOrBlank() &&
                            (
                                opt.parentName?.trim().equals(selectedCat, ignoreCase = true) == true ||
                                    categoryOptions.any { root ->
                                        root.name?.trim().equals(selectedCat, ignoreCase = true) == true &&
                                            opt.parentId != null &&
                                            opt.parentId == root.id
                                    }
                                )
                    }.map { it.name!!.trim() }
                    val fromProducts = products
                        .filter { it.parentCategoryName?.trim().equals(selectedCat, ignoreCase = true) == true }
                        .mapNotNull { it.categoryName?.trim()?.takeIf(String::isNotBlank) }
                    val names = (fromMaster + fromProducts).distinct()
                    if (names.isEmpty()) emptyList() else listOf(ALL) + names
                }

                if (draftSubCategory !in newSubCats) {
                    draftSubCategory = ALL
                }

                val availCat = products.filter { p ->
                    val matchesCat = selectedCat == ALL ||
                        p.parentCategoryName?.trim().equals(selectedCat, ignoreCase = true) == true ||
                        p.categoryName?.trim().equals(selectedCat, ignoreCase = true) == true
                    val matchesSubCat = draftSubCategory == ALL ||
                        p.categoryName?.trim().equals(draftSubCategory, ignoreCase = true) == true
                    matchesCat && matchesSubCat
                }.mapNotNull { it.brandName?.trim()?.takeIf(String::isNotBlank) }
                val masterCat = brandOptions.distinct()
                val newBrandsCat = if (selectedCat == ALL && draftSubCategory == ALL) masterCat
                else if (availCat.isNotEmpty()) (availCat + masterCat.filter { b -> availCat.any { it.equals(b, ignoreCase = true) } }).distinct()
                else masterCat

                if (draftBrand != ALL && draftBrand !in newBrandsCat) {
                    draftBrand = ALL
                }
            },
            onDismiss = { activeSelectionSheet = null }
        )
        SelectionType.SUB_CATEGORY -> SearchableSelectionSheet(
            title = "Select Sub Category",
            searchPlaceholder = "Search subcategory...",
            options = availableSubCategories,
            selected = draftSubCategory,
            onSelect = { selectedSubCat ->
                draftSubCategory = selectedSubCat
                // Dependent Validation on Sub Category Change
                val availSub = products.filter { p ->
                    val matchesCat = draftCategory == ALL ||
                        p.parentCategoryName?.trim().equals(draftCategory, ignoreCase = true) == true ||
                        p.categoryName?.trim().equals(draftCategory, ignoreCase = true) == true
                    val matchesSubCat = selectedSubCat == ALL ||
                        p.categoryName?.trim().equals(selectedSubCat, ignoreCase = true) == true
                    matchesCat && matchesSubCat
                }.mapNotNull { it.brandName?.trim()?.takeIf(String::isNotBlank) }
                val masterSub = brandOptions.distinct()
                val newBrandsSub = if (draftCategory == ALL && selectedSubCat == ALL) masterSub
                else if (availSub.isNotEmpty()) (availSub + masterSub.filter { b -> availSub.any { it.equals(b, ignoreCase = true) } }).distinct()
                else masterSub

                if (draftBrand != ALL && draftBrand !in newBrandsSub) {
                    draftBrand = ALL
                }
            },
            onDismiss = { activeSelectionSheet = null }
        )
        SelectionType.BRAND -> SearchableSelectionSheet(
            title = "Select Brand",
            searchPlaceholder = "Search brand...",
            options = availableBrands,
            selected = draftBrand,
            onSelect = { selectedBrand ->
                draftBrand = selectedBrand
            },
            onDismiss = { activeSelectionSheet = null }
        )
        null -> {}
    }
}

@Composable
private fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "ပစ္စည်း ရှာမည်"
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
                Text(placeholderText, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
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
            .height(224.dp)
            .alpha(if (outOfStock) 0.78f else 1f)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(SurfaceSoft),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = remember(product.photoUrls, product.thumbnailUrl) {
                    extractPhotoUrls(product.photoUrls, product.thumbnailUrl).firstOrNull()
                }
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Condition Badge (NEW / SECOND)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(product.productTypeChipBg())
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = product.productTypeLabel(),
                        color = product.productTypeChipFg(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp
                    )
                }

                // Favorite Toggle Button
                if (onToggleFavorite != null) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                            .size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(CardBg.copy(alpha = 0.85f))
                                .border(1.dp, BorderColor.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (wishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (wishlisted) "အကြိုက်စာရင်းမှ ဖယ်မည်" else "အကြိုက်စာရင်းထည့်မည်",
                                tint = if (wishlisted) Danger else TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
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
                        text = product.name.orEmpty().ifBlank { "ပစ္စည်း" },
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        fontSize = 12.5.sp
                    )
                    Text(
                        text = listOfNotNull(product.brandName, product.categoryName)
                            .joinToString(" • ")
                            .ifBlank { " " },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = product.sellingPrice.money(),
                        color = Primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val stockText = when {
                            outOfStock -> "Out of Stock"
                            (product.warrantyMonths ?: 0) > 0 -> "In Stock • ${product.warrantyMonths}M Warranty"
                            else -> "In Stock"
                        }
                        Text(
                            text = stockText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (outOfStock) Danger else Success,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(4.dp))
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
private fun ProductInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMain,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
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
    val warrantyMonths = product.warrantyMonths ?: 0
    val warrantyTerms = product.warrantyTerms?.trim().orEmpty()
    val remark = product.remark?.trim().orEmpty()

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Photo Gallery
            ProductPhotoGallery(
                photoUrls = product.photoUrls.orEmpty(),
                thumbnailUrl = product.thumbnailUrl,
                productName = product.name,
                onClose = onDismiss,
                wishlisted = wishlisted,
                onToggleFavorite = onToggleFavorite
            )

            // 2. Header Info: Name & Brand/Category
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = product.name.orEmpty().ifBlank { "ပစ္စည်း" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    text = listOfNotNull(product.brandName, product.categoryName)
                        .joinToString(" • ")
                        .ifBlank { " " },
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 3. Status Chips Row (Condition & Stock)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Condition Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = product.productTypeChipBg(),
                    border = BorderStroke(1.dp, product.productTypeChipFg().copy(alpha = 0.15f))
                ) {
                    Text(
                        text = product.productTypeLabel(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = product.productTypeChipFg(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Stock Status Chip
                val (stockText, stockFg, stockBg) = when {
                    outOfStock -> Triple("Out of Stock", Danger, DangerBg)
                    stock == 1 -> Triple("Only 1 left", Warning, WarningBg)
                    else -> Triple("In Stock · $stock available", Success, SuccessBg)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = stockBg,
                    border = BorderStroke(1.dp, stockFg.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = stockText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = stockFg,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 4. Prominent Price
            Text(
                text = product.sellingPrice.money(),
                color = Primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            HorizontalDivider(color = BorderColor.copy(alpha = 0.6f))

            // 5. Product Information Table
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Product Information",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceSoft,
                    border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        product.productCode?.takeIf { it.isNotBlank() }?.let {
                            ProductInfoRow("Product Code", it)
                            HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                        }
                        ProductInfoRow("Condition", product.productTypeLabel())
                        product.brandName?.takeIf { it.isNotBlank() }?.let {
                            HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                            ProductInfoRow("Brand", it)
                        }
                        product.categoryName?.takeIf { it.isNotBlank() }?.let {
                            HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                            ProductInfoRow("Category", it)
                        }
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                        ProductInfoRow("Available Stock", if (outOfStock) "0 (Out of Stock)" else "$stock")
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                        ProductInfoRow(
                            "Warranty",
                            if (warrantyMonths > 0) "$warrantyMonths Months" else "No Warranty"
                        )
                    }
                }
            }

            // 6. Warranty Details (if warrantyMonths > 0 or terms present)
            if (warrantyMonths > 0 || warrantyTerms.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Warranty",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDark
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SuccessBg.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Success.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (warrantyMonths > 0) "$warrantyMonths Months Warranty" else "No Warranty",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Success
                            )
                            if (warrantyTerms.isNotBlank()) {
                                Text(
                                    text = warrantyTerms,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMain,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // 7. Remark / Condition Note (if remark present)
            if (remark.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Remark / Condition Note",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDark
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceSoft,
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = remark,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMain,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        // Sticky Bottom Purchase Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardBg,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (outOfStock) {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = SurfaceSoft,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Text("Out of Stock", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else if (qty <= 0) {
                    Button(
                        onClick = { onChangeQty(1) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add to Cart — ${product.sellingPrice.money()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    QtyStepper(
                        qty = qty,
                        maxQty = stock,
                        enabled = true,
                        compact = false,
                        onChangeQty = onChangeQty
                    )

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "In Cart ($qty)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductPhotoGallery(
    photoUrls: List<String>,
    thumbnailUrl: String?,
    productName: String?,
    onClose: () -> Unit,
    wishlisted: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    val urls = remember(photoUrls, thumbnailUrl) {
        extractPhotoUrls(photoUrls, thumbnailUrl)
    }
    val pagerState = rememberPagerState(pageCount = { urls.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceSoft)
                .border(1.dp, BorderColor, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (urls.isEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(64.dp)
                )
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

            // Close Button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CardBg.copy(alpha = 0.9f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "ပိတ်မည်",
                    tint = TextMain,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Favorite Button
            if (onToggleFavorite != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CardBg.copy(alpha = 0.9f))
                        .clickable(onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (wishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (wishlisted) "အကြိုက်စာရင်းမှ ဖယ်မည်" else "အကြိုက်စာရင်းထည့်မည်",
                        tint = if (wishlisted) Danger else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Page Indicator Badge
            if (urls.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${urls.size}",
                        color = OnPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Thumbnail Row if Multiple Images
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
                            .size(60.dp)
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

private fun normalizeImageKey(url: String): String {
    val clean = url.trim().substringBefore('?').lowercase()
    val filename = clean.substringAfterLast('/')
    return if (filename.isNotBlank()) filename else clean
}

private fun extractPhotoUrls(photoUrls: List<String>?, thumbnailUrl: String?): List<String> {
    val rawList = mutableListOf<String>()

    photoUrls?.forEach { raw ->
        if (raw.isNotBlank()) {
            rawList.addAll(raw.split(',', ';', '\n', '|').map { it.trim() })
        }
    }

    if (rawList.isEmpty()) {
        thumbnailUrl?.takeIf { it.isNotBlank() }?.let { raw ->
            rawList.addAll(raw.split(',', ';', '\n', '|').map { it.trim() })
        }
    }

    return rawList
        .filter { it.isNotBlank() }
        .map { it.assetUrl() }
        .distinctBy { normalizeImageKey(it) }
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
