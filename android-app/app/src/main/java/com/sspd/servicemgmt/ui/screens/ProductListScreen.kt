package com.sspd.servicemgmt.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.api.ProductDTO
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.ProductListViewModel
import com.sspd.servicemgmt.ui.viewmodel.ProductListViewModel.ProductFilter
import com.sspd.servicemgmt.ui.viewmodel.ProductListViewModel.ProductSort
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onBack: () -> Unit,
    onProductClick: (Int) -> Unit = {},
    onScanNavigate: (productId: Int, serial: String) -> Unit = { _, _ -> },
    onNewProduct: () -> Unit = {}
) {
    val vm: ProductListViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            vm.load()
        }
    }

    LaunchedEffect(state.scanError) {
        state.scanError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearScanError()
        }
    }

    // Scan success → navigate to ProductDetail with serial highlighted
    LaunchedEffect(state.navigateToDetail) {
        state.navigateToDetail?.let { (productId, serial) ->
            onScanNavigate(productId, serial)
            vm.onNavigated()
        }
    }

    val filtered = state.items.filter {
        val matchesSearch = state.search.isBlank() ||
            it.name.contains(state.search, true) ||
            it.productCode.contains(state.search, true) ||
            (it.categoryName ?: "").contains(state.search, true) ||
            (it.brandName ?: "").contains(state.search, true)
        val qty = if (it.hasSerial == true) it.availableSerialCount ?: it.stockQty else it.stockQty
        val matchesFilter = when (state.filter) {
            ProductFilter.ALL -> true
            ProductFilter.LOW_STOCK -> qty <= (it.reorderLevel ?: 0) || qty <= 0
            ProductFilter.SERIAL -> it.hasSerial == true
            ProductFilter.NO_COST -> (it.costPrice ?: 0.0) <= 0.0
            ProductFilter.NO_SELLING_PRICE -> it.sellingPrice <= 0.0
        }
        matchesSearch && matchesFilter
    }.let { products ->
        when (state.sort) {
            ProductSort.NAME -> products.sortedBy { it.name.lowercase() }
            ProductSort.STOCK_LOW -> products.sortedBy { if (it.hasSerial == true) it.availableSerialCount ?: it.stockQty else it.stockQty }
            ProductSort.STOCK_HIGH -> products.sortedByDescending { if (it.hasSerial == true) it.availableSerialCount ?: it.stockQty else it.stockQty }
            ProductSort.PRICE_LOW -> products.sortedBy { it.sellingPrice }
            ProductSort.PRICE_HIGH -> products.sortedByDescending { it.sellingPrice }
            ProductSort.VALUE_HIGH -> products.sortedByDescending {
                val qty = if (it.hasSerial == true) it.availableSerialCount ?: it.stockQty else it.stockQty
                (it.costPrice ?: 0.0) * qty
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("ကုန်ပစ္စည်းများ", fontWeight = FontWeight.ExtraBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Primary, titleContentColor = Color.White
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onNewProduct,
                    containerColor = Primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("ကုန်ပစ္စည်းအသစ်", fontWeight = FontWeight.Bold) }
                )
            }
        ) { padding ->
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)) {

                // Search field with scan button
                OutlinedTextField(
                    value = state.search,
                    onValueChange = { vm.setSearch(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    placeholder = { Text("ကုန်ပစ္စည်း ရှာဖွေရန်...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.scanLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp, color = Primary
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                IconButton(onClick = { vm.showScanner() }) {
                                    Icon(Icons.Outlined.QrCodeScanner, "Scan", tint = Primary)
                                }
                            }
                            if (state.search.isNotBlank()) {
                                IconButton(onClick = { vm.setSearch("") }) {
                                    Icon(Icons.Outlined.Clear, "ရှင်းရန်", tint = TextMuted)
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                val totalUnits = state.items.sumOf { if (it.hasSerial == true) it.availableSerialCount ?: it.stockQty else it.stockQty }
                val lowStockCount = state.items.count {
                    val qty = if (it.hasSerial == true) it.availableSerialCount ?: it.stockQty else it.stockQty
                    qty <= (it.reorderLevel ?: 0)
                }
                val stockValue = state.items.sumOf {
                    val qty = if (it.hasSerial == true) it.availableSerialCount ?: it.stockQty else it.stockQty
                    (it.costPrice ?: 0.0) * qty
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProductSummaryCard("Items", state.items.size.toString(), Primary, Modifier.weight(1f))
                    ProductSummaryCard("Stock", totalUnits.toString(), Success, Modifier.weight(1f))
                    ProductSummaryCard("Low", lowStockCount.toString(), Warning, Modifier.weight(1f))
                    ProductSummaryCard("Value", stockValue.compactMoney(), Violet, Modifier.weight(1.35f))
                }

                LazyRow(
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { ProductFilterChip("All", state.filter == ProductFilter.ALL) { vm.setFilter(ProductFilter.ALL) } }
                    item { ProductFilterChip("Low Stock", state.filter == ProductFilter.LOW_STOCK, Warning) { vm.setFilter(ProductFilter.LOW_STOCK) } }
                    item { ProductFilterChip("Serial", state.filter == ProductFilter.SERIAL, Violet) { vm.setFilter(ProductFilter.SERIAL) } }
                    item { ProductFilterChip("No Cost", state.filter == ProductFilter.NO_COST, Danger) { vm.setFilter(ProductFilter.NO_COST) } }
                    item { ProductFilterChip("No Price", state.filter == ProductFilter.NO_SELLING_PRICE, Danger) { vm.setFilter(ProductFilter.NO_SELLING_PRICE) } }
                }

                LazyRow(
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { ProductSortChip("Name", state.sort == ProductSort.NAME) { vm.setSort(ProductSort.NAME) } }
                    item { ProductSortChip("Stock ↑", state.sort == ProductSort.STOCK_LOW) { vm.setSort(ProductSort.STOCK_LOW) } }
                    item { ProductSortChip("Stock ↓", state.sort == ProductSort.STOCK_HIGH) { vm.setSort(ProductSort.STOCK_HIGH) } }
                    item { ProductSortChip("Price ↑", state.sort == ProductSort.PRICE_LOW) { vm.setSort(ProductSort.PRICE_LOW) } }
                    item { ProductSortChip("Price ↓", state.sort == ProductSort.PRICE_HIGH) { vm.setSort(ProductSort.PRICE_HIGH) } }
                    item { ProductSortChip("Value ↓", state.sort == ProductSort.VALUE_HIGH) { vm.setSort(ProductSort.VALUE_HIGH) } }
                }

                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppLoading()
                    }
                } else if (state.error != null && state.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(Icons.Outlined.CloudOff, null, tint = Danger, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(state.error.orEmpty(), color = TextMain, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = vm::load, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                                Icon(Icons.Outlined.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text("ပြန်လည်ရယူမည်")
                            }
                        }
                    }
                } else if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.SearchOff, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("ကုန်ပစ္စည်း မတွေ့ပါ", color = TextMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { p ->
                            ProductCard(p, onClick = { onProductClick(p.id) })
                        }
                        item { Spacer(Modifier.height(88.dp)) }
                    }
                }
            }
        }

        // Full-screen barcode scanner overlay
        if (state.showScanner) {
            BarcodeScannerView(
                onResult = { serial -> vm.onScanResult(serial) },
                onClose  = { vm.dismissScanner() }
            )
        }
    }
}

@Composable
private fun ProductSummaryCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = color.copy(alpha = 0.09f), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
            Text(label, fontSize = 9.sp, color = TextMuted, maxLines = 1)
        }
    }
}

@Composable
private fun ProductSortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 10.sp) })
}

@Composable
private fun ProductFilterChip(label: String, selected: Boolean, color: Color = Primary, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) color.copy(0.12f) else CardBg,
            labelColor = if (selected) color else TextMuted
        ),
        border = BorderStroke(1.dp, if (selected) color.copy(0.35f) else BorderColor)
    )
}

@Composable
private fun ProductCard(p: ProductDTO, onClick: () -> Unit = {}) {
    val imgBitmap = remember(p.photoBase64) {
        if (p.photoBase64.isNullOrBlank()) null
        else runCatching {
            // Backend stores photo as data URL: "data:image/jpeg;base64,/9j/..."
            // Strip the prefix before decoding
            val raw = p.photoBase64.substringAfter("base64,", p.photoBase64)
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

            // Photo thumbnail
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                if (imgBitmap != null) {
                    Image(
                        bitmap = imgBitmap,
                        contentDescription = p.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Outlined.Inventory2, null,
                        tint = Primary, modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name / code / category
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    p.name, fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold, color = TextMain
                )
                Spacer(Modifier.height(2.dp))
                Text(p.productCode, fontSize = 11.sp, color = TextMuted)
                if (!p.categoryName.isNullOrBlank()) {
                    Text(p.categoryName, fontSize = 11.sp, color = TextMuted)
                }
                if (!p.brandName.isNullOrBlank()) {
                    Text(p.brandName, fontSize = 10.sp, color = TextMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (p.hasSerial == true) ProductMiniBadge("Serial", Violet, Violet.copy(0.10f))
                    if ((p.costPrice ?: 0.0) <= 0.0) ProductMiniBadge("No Cost", Danger, DangerBg)
                    if (p.sellingPrice <= 0.0) ProductMiniBadge("No Price", Danger, DangerBg)
                }
            }

            // Type badge / price / stock
            Column(horizontalAlignment = Alignment.End) {
                val typeBg    = if (p.productType.equals("New", true)) PrimaryLight else WarningBg
                val typeColor = if (p.productType.equals("New", true)) Primary else Warning
                val typeLabel = if (p.productType.equals("New", true)) "အသစ်" else "အသုံးပြုပြီး"
                Surface(color = typeBg, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        typeLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, color = typeColor
                    )
                }
                Text(
                    "${p.sellingPrice.fmt()} Ks",
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary
                )
                Spacer(Modifier.height(4.dp))
                val displayQty = if (p.hasSerial == true) p.availableSerialCount ?: p.stockQty else p.stockQty
                val stockColor = when {
                    displayQty <= 0                                       -> Danger
                    p.reorderLevel != null && displayQty <= p.reorderLevel -> Warning
                    else                                                   -> Success
                }
                val stockLabel = when {
                    displayQty <= 0                                       -> "ပစ္စည်းကုန်"
                    p.reorderLevel != null && displayQty <= p.reorderLevel -> "နည်းပါး"
                    else                                                   -> "ရှိ"
                }
                val badgeBg = when (stockColor) { Danger -> DangerBg; Warning -> WarningBg; else -> SuccessBg }
                Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "$stockLabel ($displayQty)",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = stockColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductMiniBadge(label: String, color: Color, bg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(5.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun Double.fmt() = if (this % 1.0 == 0.0) String.format("%,.0f", this) else String.format("%,.2f", this)
private fun Double.compactMoney(): String = when {
    this >= 1_000_000_000 -> String.format("%.1fB", this / 1_000_000_000)
    this >= 1_000_000 -> String.format("%.1fM", this / 1_000_000)
    this >= 1_000 -> String.format("%.1fK", this / 1_000)
    else -> String.format("%.0f", this)
}


@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360)
@Composable
fun UIPrevice(modifier: Modifier = Modifier) {
    val sampleProduct = com.sspd.servicemgmt.api.ProductDTO(
        id = 1,
        productCode = "PRD-001",
        name = "Samsung Galaxy A55",
        stockQty = 15,
        availableSerialCount = 12,
        productType = "New",
        sellingPrice = 850000.0,
        costPrice = 720000.0,
        categoryName = "မိုဘိုင်းဖုန်း",
        brandName = "Samsung",
        unitName = "လုံး",
        reorderLevel = 5,
        hasSerial = true,
        warrantyMonths = 12,
        photoBase64 = null
    )
    com.sspd.servicemgmt.ui.theme.AppTheme {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .background(ScreenBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ProductCard(sampleProduct)
        }
    }
}


