package com.sspd.servicemgmt.feature.purchase

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.PurchaseDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppSearchField
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.feature.purchase.PurchaseListViewModel.DateShortcut
import com.sspd.servicemgmt.feature.purchase.PurchaseListViewModel.StatusFilter
import java.time.LocalDate

private val PurchaseColor = Color(0xFF0F766E)
private val PurchaseBg = Color(0xFFECFDF5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseListScreen(
    onBack: () -> Unit,
    onPurchaseClick: (Int) -> Unit,
    onNewPurchase: () -> Unit
) {
    val vm: PurchaseListViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf(state.search) }
    var voucherText by remember { mutableStateOf("") }
    LaunchedEffect(state.search) { searchText = state.search }
    val visibleItems = remember(state.items, state.overdueItems, state.statusFilter, state.dateFrom, state.dateTo) {
        val source = if (state.statusFilter == StatusFilter.OVERDUE) state.overdueItems else state.items
        source.filter { purchase ->
            val matchesStatus = when (state.statusFilter) {
                StatusFilter.ALL, StatusFilter.OVERDUE -> true
                StatusFilter.PAID -> statusKey(purchase) == "paid"
                StatusFilter.PARTIAL -> statusKey(purchase) == "partial"
                StatusFilter.DUE -> statusKey(purchase) == "due"
            }
            val purchaseDate = purchase.purchaseDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val from = state.dateFrom.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val to = state.dateTo.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val matchesDate = purchaseDate != null &&
                (from == null || !purchaseDate.isBefore(from)) &&
                (to == null || !purchaseDate.isAfter(to))
            matchesStatus && (from == null && to == null || matchesDate)
        }
    }
    val totalAmount = visibleItems.sumOf { it.netAmount ?: it.totalAmount ?: 0.0 }
    val totalDue = visibleItems.sumOf { it.dueAmount ?: 0.0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဝယ်ယူရေး", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Outlined.Refresh, "ပြန်ဖတ်ရန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PurchaseColor,
                    titleContentColor = OnPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewPurchase, containerColor = PurchaseColor) {
                Icon(Icons.Outlined.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.overdueItems.isNotEmpty() || state.reorderSuggestions.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryBox("ရက်ကျော်", state.overdueItems.size.toString(), Danger, Modifier.weight(1f))
                        SummaryBox("ပြန်မှာယူ", state.reorderSuggestions.size.toString(), Color(0xFFD97706), Modifier.weight(1f))
                    }
                }
                OutlinedTextField(
                    value = voucherText,
                    onValueChange = { voucherText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ဘောင်ချာနံပါတ် တိုက်ရိုက်ရှာပါ...") },
                    leadingIcon = { Icon(Icons.Outlined.Tag, null, tint = TextMuted) },
                    trailingIcon = {
                        TextButton(
                            onClick = { vm.findVoucher(voucherText, onPurchaseClick) { searchText = voucherText } },
                            enabled = voucherText.isNotBlank()
                        ) { Text("ဖွင့်မည်", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.findVoucher(voucherText, onPurchaseClick) { searchText = voucherText } })
                )

                AppSearchField(
                    value = searchText,
                    onValueChange = { searchText = it; vm.setSearch(it) },
                    placeholder = "ဘောင်ချာ / ပေးသွင်းသူ / ဝယ်ယူသူ ရှာပါ..."
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { DateChip("ယနေ့", state.dateShortcut == DateShortcut.TODAY) { vm.applyDateShortcut(DateShortcut.TODAY) } }
                    item { DateChip("ဒီအပတ်", state.dateShortcut == DateShortcut.WEEK) { vm.applyDateShortcut(DateShortcut.WEEK) } }
                    item { DateChip("ဒီလ", state.dateShortcut == DateShortcut.MONTH) { vm.applyDateShortcut(DateShortcut.MONTH) } }
                    item { DateChip("အားလုံး", state.dateShortcut == DateShortcut.ALL) { vm.applyDateShortcut(DateShortcut.ALL) } }
                    item { DateChip("ပေးရန်ကျန်", state.statusFilter == StatusFilter.DUE, danger = true) { vm.setStatusFilter(StatusFilter.DUE) } }
                    item { DateChip("ကြွေးကျန်", state.statusFilter == StatusFilter.OVERDUE, danger = true) { vm.setStatusFilter(StatusFilter.OVERDUE) } }
                    item { DateChip("အားလုံး", state.statusFilter == StatusFilter.ALL) { vm.setStatusFilter(StatusFilter.ALL) } }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryBox("ဘောင်ချာ", visibleItems.size.toString(), PurchaseColor, Modifier.weight(1f))
                    SummaryBox("စုစုပေါင်း", money(totalAmount), TextMain, Modifier.weight(1f))
                    SummaryBox("ပေးရန်ကျန်", money(totalDue), if (totalDue > 0) Danger else PurchaseColor, Modifier.weight(1f))
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
            } else if (visibleItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ShoppingCart, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("ကိုက်ညီသော ဝယ်ယူမှု မရှိပါ", color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleItems) { purchase ->
                        PurchaseCard(purchase) { purchase.id?.let(onPurchaseClick) }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PurchaseCard(purchase: PurchaseDTO, onClick: () -> Unit) {
    val due = purchase.dueAmount ?: 0.0
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(purchase.purchaseCode ?: "#${purchase.id}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseColor)
                    Text(purchase.supplierName ?: "ပေးသွင်းသူ", fontSize = 12.sp, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(color = statusBg(purchase), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        statusLabel(purchase),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor(purchase)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallMetric("စုစုပေါင်း", money(purchase.netAmount ?: purchase.totalAmount))
                SmallMetric("ပေးပြီး", money(purchase.paidAmount))
                SmallMetric("ပေးရန်ကျန်", money(due), if (due > 0) Danger else PurchaseColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Text(purchase.purchaseDate?.take(10) ?: "-", fontSize = 11.sp, color = TextMuted)
                if (!purchase.staffName.isNullOrBlank()) {
                    Icon(Icons.Outlined.Person, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                    Text(purchase.staffName, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SmallMetric(label: String, value: String, color: Color = TextMain) {
    Column {
        Text(label, fontSize = 10.sp, color = TextMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val active = if (danger) Danger else PurchaseColor
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) active.copy(0.12f) else CardBg,
            labelColor = if (selected) active else TextMuted
        ),
        border = BorderStroke(1.dp, if (selected) active.copy(0.35f) else BorderColor)
    )
}

@Composable
private fun SummaryBox(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(0.08f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(0.16f))
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = TextMuted, maxLines = 1)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
        }
    }
}

private fun statusKey(purchase: PurchaseDTO): String {
    val raw = purchase.paymentStatus?.lowercase() ?: ""
    return when {
        raw.contains("paid") && !raw.contains("partial") -> "paid"
        raw.contains("partial") -> "partial"
        raw.contains("due") || raw.contains("unpaid") || raw.contains("pending") -> "due"
        (purchase.dueAmount ?: 0.0) > 0 && (purchase.paidAmount ?: 0.0) > 0 -> "partial"
        (purchase.dueAmount ?: 0.0) > 0 -> "due"
        else -> "paid"
    }
}

private fun statusLabel(purchase: PurchaseDTO) = when (statusKey(purchase)) {
    "paid" -> "ငွေချေပြီး"
    "partial" -> "တစ်စိတ်တစ်ပိုင်း"
    else -> "ပေးရန်ကျန်"
}

private fun statusColor(purchase: PurchaseDTO) = when (statusKey(purchase)) {
    "paid" -> Color(0xFF16A34A)
    "partial" -> Color(0xFFD97706)
    else -> Danger
}

private fun statusBg(purchase: PurchaseDTO) = when (statusKey(purchase)) {
    "paid" -> Color(0xFFF0FDF4)
    "partial" -> Color(0xFFFFFBEB)
    else -> DangerBg
}

private fun money(v: Double?): String = "%,.0f Ks".format(v ?: 0.0)
