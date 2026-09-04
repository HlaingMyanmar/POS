package com.sspd.servicemgmt.feature.product

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.ProductDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.ProductPhotoImage
import com.sspd.servicemgmt.core.ui.component.ProductPhotoLoader
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.ui.scanner.BarcodeScannerView
import com.sspd.servicemgmt.feature.product.ProductListViewModel.ProductFilter
import com.sspd.servicemgmt.feature.product.ProductListViewModel.ProductSort
import kotlinx.coroutines.delay

@Composable
fun ProductListScreen(
    onBack: () -> Unit,
    onProductClick: (Int) -> Unit = {},
    onScanNavigate: (productId: Int, serial: String) -> Unit = { _, _ -> },
    onNewProduct: () -> Unit = {},
    canCreateProduct: Boolean = !BuildConfig.TECHNICIAN_ONLY
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
    LaunchedEffect(state.navigateToDetail) {
        state.navigateToDetail?.let { (productId, serial) ->
            onScanNavigate(productId, serial)
            vm.onNavigated()
        }
    }

    ProductListContent(
        items = state.items,
        search = state.search,
        filter = state.filter,
        sort = state.sort,
        loading = state.loading,
        error = state.error,
        scanLoading = state.scanLoading,
        showScanner = state.showScanner,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSearch = vm::setSearch,
        onFilter = vm::setFilter,
        onSort = vm::setSort,
        onScan = vm::showScanner,
        onScanResult = vm::onScanResult,
        onDismissScanner = vm::dismissScanner,
        onReload = vm::load,
        onProductClick = onProductClick,
        onNewProduct = onNewProduct,
        canCreateProduct = canCreateProduct
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListContent(
    items: List<ProductDTO>,
    search: String,
    filter: ProductListViewModel.ProductFilter,
    sort: ProductListViewModel.ProductSort,
    loading: Boolean,
    error: String?,
    scanLoading: Boolean,
    showScanner: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onFilter: (ProductFilter) -> Unit,
    onSort: (ProductSort) -> Unit,
    onScan: () -> Unit,
    onScanResult: (String) -> Unit,
    onDismissScanner: () -> Unit,
    onReload: () -> Unit,
    onProductClick: (Int) -> Unit,
    onNewProduct: () -> Unit,
    canCreateProduct: Boolean = true
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    val filtered = remember(items, search, filter, sort) {
        items.filter { product ->
            val matchesSearch = search.isBlank() ||
                product.name.contains(search, true) ||
                product.productCode.contains(search, true) ||
                (product.categoryName ?: "").contains(search, true) ||
                (product.brandName ?: "").contains(search, true)
            val qty = product.displayQty()
            val matchesFilter = when (filter) {
                ProductListViewModel.ProductFilter.ALL -> true
                ProductListViewModel.ProductFilter.LOW_STOCK -> qty <= (product.reorderLevel ?: 0) || qty <= 0
                ProductListViewModel.ProductFilter.SERIAL -> product.hasSerial == true
                ProductListViewModel.ProductFilter.NO_COST -> (product.costPrice ?: 0.0) <= 0.0
                ProductListViewModel.ProductFilter.NO_SELLING_PRICE -> product.sellingPrice <= 0.0
            }
            matchesSearch && matchesFilter
        }.let { products ->
            when (sort) {
                ProductSort.NAME -> products.sortedBy { it.name.lowercase() }
                ProductSort.STOCK_LOW -> products.sortedBy { it.displayQty() }
                ProductSort.STOCK_HIGH -> products.sortedByDescending { it.displayQty() }
                ProductSort.PRICE_LOW -> products.sortedBy { it.sellingPrice }
                ProductSort.PRICE_HIGH -> products.sortedByDescending { it.sellingPrice }
                ProductSort.VALUE_HIGH -> products.sortedByDescending {
                    (it.costPrice ?: 0.0) * it.displayQty()
                }
            }
        }
    }
    val totalUnits = items.sumOf { it.displayQty() }
    val lowStockCount = items.count {
        val qty = it.displayQty()
        qty <= 0 || qty <= (it.reorderLevel ?: 0)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = ScreenBg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "ကုန်ပစ္စည်းများ",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", modifier = Modifier.size(20.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = onScan, enabled = !scanLoading) {
                            if (scanLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Outlined.QrCodeScanner, "Scan", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    expandedHeight = 48.dp,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Primary,
                        navigationIconContentColor = Primary,
                        actionIconContentColor = Primary
                    )
                )
            },
            floatingActionButton = {
                if (canCreateProduct) {
                    FloatingActionButton(
                        onClick = onNewProduct,
                        containerColor = Primary,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Outlined.Add, "ကုန်ပစ္စည်းအသစ်")
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(ScreenBg)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = search,
                            onValueChange = onSearch,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = TextMain),
                            decorationBox = { inner ->
                                Box {
                                    if (search.isEmpty()) {
                                        Text(
                                            "အမည်၊ ကုဒ်၊ အမျိုးအစား ရှာရန်",
                                            fontSize = 13.sp,
                                            color = TextMuted,
                                            maxLines = 1
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                        if (search.isNotBlank()) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "ရှင်းရန်",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onSearch("") }
                            )
                        }
                    }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProductStatPill(
                                label = "စုစုပေါင်း",
                                value = "${items.size}",
                                color = Primary,
                                selected = filter == ProductFilter.ALL && search.isBlank(),
                                modifier = Modifier.weight(1f),
                                onClick = { onFilter(ProductFilter.ALL) }
                            )
                            ProductStatPill(
                                label = "လက်ကျန်",
                                value = totalUnits.toString(),
                                color = Success,
                                selected = false,
                                modifier = Modifier.weight(1f)
                            )
                            ProductStatPill(
                                label = "ကုန်နည်း",
                                value = lowStockCount.toString(),
                                color = if (lowStockCount > 0) Warning else Success,
                                selected = filter == ProductFilter.LOW_STOCK,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onFilter(
                                        if (filter == ProductFilter.LOW_STOCK) ProductFilter.ALL
                                        else ProductFilter.LOW_STOCK
                                    )
                                }
                            )
                        }
                    }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            ProductFilterChip("အားလုံး", filter == ProductFilter.ALL) {
                                onFilter(ProductFilter.ALL)
                            }
                        }
                        item {
                            ProductFilterChip("ကုန်နည်း", filter == ProductFilter.LOW_STOCK, Warning) {
                                onFilter(ProductFilter.LOW_STOCK)
                            }
                        }
                        item {
                            ProductFilterChip("Serial", filter == ProductFilter.SERIAL, Violet) {
                                onFilter(ProductFilter.SERIAL)
                            }
                        }
                        item {
                            ProductFilterChip("စျေးမရှိ", filter == ProductFilter.NO_SELLING_PRICE, Danger) {
                                onFilter(ProductFilter.NO_SELLING_PRICE)
                            }
                        }
                        item {
                            ProductFilterChip("ဝယ်စျေးမရှိ", filter == ProductFilter.NO_COST, Danger) {
                                onFilter(ProductFilter.NO_COST)
                            }
                        }
                    }
                    Box {
                        TextButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Outlined.Sort, null, modifier = Modifier.size(16.dp), tint = TextMuted)
                            Spacer(Modifier.width(4.dp))
                            Text(sort.label(), fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            ProductSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label(), fontSize = 13.sp) },
                                    onClick = {
                                        onSort(option)
                                        sortMenuOpen = false
                                    },
                                    trailingIcon = {
                                        if (sort == option) {
                                            Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (loading && items.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary,
                        trackColor = PrimaryLight
                    )
                }

                when {
                    loading && items.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
                    }
                    error != null && items.isEmpty() -> {
                        ProductEmptyState(
                            icon = Icons.Outlined.CloudOff,
                            title = "စာရင်း မရယူနိုင်ပါ",
                            subtitle = error.orEmpty(),
                            action = "ပြန်လည်ရယူမည်",
                            onAction = onReload
                        )
                    }
                    filtered.isEmpty() -> {
                        ProductEmptyState(
                            icon = Icons.Outlined.Inventory2,
                            title = "ကုန်ပစ္စည်း မတွေ့ပါ",
                            subtitle = if (search.isNotBlank() || filter != ProductFilter.ALL)
                                "ရှာဖွေမှု သို့မဟုတ် စစ်ထုတ်ချက်ကို ပြောင်းကြည့်ပါ"
                            else if (canCreateProduct)
                                "ကုန်ပစ္စည်းအသစ် ထည့်ရန် + ကို နှိပ်ပါ"
                            else
                                "ကုန်ပစ္စည်းစာရင်း မရှိသေးပါ"
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { product ->
                                ProductCard(product, onClick = { onProductClick(product.id) })
                            }
                        }
                    }
                }
            }
        }

        if (showScanner) {
            BarcodeScannerView(
                onResult = onScanResult,
                onClose = onDismissScanner
            )
        }
    }
}

@Composable
private fun ProductStatPill(
    label: String,
    value: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = if (selected) color.copy(0.12f) else ScreenBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) color.copy(0.35f) else BorderColor)
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
            Text(label, fontSize = 11.sp, color = TextMuted, maxLines = 1)
        }
    }
}

@Composable
private fun ProductFilterChip(label: String, selected: Boolean, color: Color = Primary, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(0.14f),
            selectedLabelColor = color,
            containerColor = Color.White,
            labelColor = TextMuted
        )
    )
}

@Composable
private fun ProductEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextMain)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontSize = 13.sp, color = TextMuted)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Text(action, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProductCard(p: ProductDTO, onClick: () -> Unit = {}) {
    val photoSource = remember(p.id, p.thumbnailPath, p.imagePath, p.photoBase64) {
        ProductPhotoLoader.thumbSource(p)
    }
    val qty = p.displayQty()
    val stockColor = when {
        qty <= 0 -> Danger
        p.reorderLevel != null && qty <= p.reorderLevel -> Warning
        else -> Success
    }
    val stockLabel = when {
        qty <= 0 -> "ကုန်"
        p.reorderLevel != null && qty <= p.reorderLevel -> "နည်း"
        else -> "ရှိ"
    }
    val stockBg = when (stockColor) {
        Danger -> DangerBg
        Warning -> WarningBg
        else -> SuccessBg
    }
    val meta = listOfNotNull(
        p.productCode.takeIf { it.isNotBlank() },
        p.brandName?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductPhotoImage(
                source = photoSource,
                contentDescription = p.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PrimaryLight),
                placeholder = {
                    Icon(Icons.Outlined.Inventory2, null, tint = Primary, modifier = Modifier.size(28.dp))
                }
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    p.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        meta,
                        fontSize = 12.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${p.sellingPrice.fmt()} Ks",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Primary
                    )
                    if (p.hasSerial == true) {
                        Surface(color = VioletBg, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "S/N",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Violet
                            )
                        }
                    }
                    if (!p.productType.equals("New", true) && p.productType.isNotBlank()) {
                        Surface(color = WarningBg, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "အသုံးပြုပြီး",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Warning
                            )
                        }
                    }
                }
            }

            Surface(color = stockBg, shape = RoundedCornerShape(10.dp)) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(qty.toString(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = stockColor)
                    Text(stockLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = stockColor)
                }
            }
        }
    }
}

private fun ProductDTO.displayQty(): Int =
    if (hasSerial == true) availableSerialCount ?: stockQty else stockQty

private fun ProductSort.label(): String = when (this) {
    ProductSort.NAME -> "အမည်"
    ProductSort.STOCK_LOW -> "လက်ကျန် ↑"
    ProductSort.STOCK_HIGH -> "လက်ကျန် ↓"
    ProductSort.PRICE_LOW -> "စျေး ↑"
    ProductSort.PRICE_HIGH -> "စျေး ↓"
    ProductSort.VALUE_HIGH -> "တန်ဖိုး"
}

private fun Double.fmt() = if (this % 1.0 == 0.0) String.format("%,.0f", this) else String.format("%,.2f", this)

private val previewProducts = listOf(
    ProductDTO(
        id = 1, productCode = "PRD-001", name = "Samsung Galaxy A55",
        stockQty = 15, availableSerialCount = 12, productType = "New",
        sellingPrice = 850000.0, costPrice = 720000.0,
        categoryName = "မိုဘိုင်းဖုန်း", brandName = "Samsung", unitName = "လုံး",
        reorderLevel = 5, hasSerial = true
    ),
    ProductDTO(
        id = 2, productCode = "PRD-014", name = "iPhone 14",
        stockQty = 2, availableSerialCount = 2, productType = "New",
        sellingPrice = 1850000.0, costPrice = 1600000.0,
        categoryName = "မိုဘိုင်းဖုန်း", brandName = "Apple", unitName = "လုံး",
        reorderLevel = 3, hasSerial = true
    ),
    ProductDTO(
        id = 3, productCode = "ACC-088", name = "Type-C Charger 20W",
        stockQty = 48, productType = "New",
        sellingPrice = 18500.0, costPrice = 12000.0,
        categoryName = "အသုံးအဆောင်", brandName = "Anker", unitName = "ခု",
        reorderLevel = 10, hasSerial = false
    ),
    ProductDTO(
        id = 4, productCode = "PRD-220", name = "Dell Latitude 5440",
        stockQty = 0, availableSerialCount = 0, productType = "Used",
        sellingPrice = 980000.0, costPrice = 750000.0,
        categoryName = "Laptop", brandName = "Dell", unitName = "လုံး",
        reorderLevel = 2, hasSerial = true
    ),
    ProductDTO(
        id = 5, productCode = "PRT-003", name = "Screen Protector",
        stockQty = 6, productType = "New",
        sellingPrice = 0.0, costPrice = 1500.0,
        categoryName = "အသုံးအဆောင်", brandName = "Generic", unitName = "ခု",
        reorderLevel = 20, hasSerial = false
    )
)

@Composable
private fun ProductListPreviewHost(
    items: List<ProductDTO> = previewProducts,
    initialFilter: ProductFilter = ProductFilter.ALL,
    loading: Boolean = false,
    error: String? = null
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(initialFilter) }
    var sort by remember { mutableStateOf(ProductSort.NAME) }
    AppTheme {
        ProductListContent(
            items = items,
            search = search,
            filter = filter,
            sort = sort,
            loading = loading,
            error = error,
            scanLoading = false,
            showScanner = false,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onSearch = { search = it },
            onFilter = { filter = it },
            onSort = { sort = it },
            onScan = {},
            onScanResult = {},
            onDismissScanner = {},
            onReload = {},
            onProductClick = {},
            onNewProduct = {}
        )
    }
}

@Preview(name = "ကုန်ပစ္စည်းများ", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun ProductListPreview() {
    ProductListPreviewHost()
}

@Preview(name = "ကုန်နည်း", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun ProductListLowStockPreview() {
    ProductListPreviewHost(initialFilter = ProductFilter.LOW_STOCK)
}

@Preview(name = "ဗလာ", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun ProductListEmptyPreview() {
    ProductListPreviewHost(items = emptyList())
}

@Preview(name = "ကတ်", showBackground = true, widthDp = 360)
@Composable
fun UIPrevice() {
    AppTheme {
        Box(Modifier.background(ScreenBg).padding(16.dp)) {
            ProductCard(previewProducts.first())
        }
    }
}
