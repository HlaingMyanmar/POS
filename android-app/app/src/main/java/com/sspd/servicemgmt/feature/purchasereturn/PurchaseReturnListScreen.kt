package com.sspd.servicemgmt.feature.purchasereturn

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
import com.sspd.servicemgmt.core.network.PurchaseReturnDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.feature.purchasereturn.PurchaseReturnListViewModel.DateShortcut
import java.time.LocalDate

private val PurchaseReturnColor = Color(0xFF0F766E)
private val PurchaseReturnBg = Color(0xFFECFDF5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseReturnListScreen(
    onBack: () -> Unit,
    onReturnClick: (Int) -> Unit,
    onNewReturn: () -> Unit
) {
    val vm: PurchaseReturnListViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf(state.search) }
    var voucherText by remember { mutableStateOf("") }
    LaunchedEffect(state.search) { searchText = state.search }

    val visibleItems = remember(state.items, state.dateFrom, state.dateTo) {
        state.items.filter { ret ->
            val d = ret.returnDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val from = state.dateFrom.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val to = state.dateTo.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            d != null && (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to)) || (from == null && to == null)
        }
    }
    val total = visibleItems.sumOf { it.totalReturnAmount ?: 0.0 }
    val refund = visibleItems.sumOf { it.refundAmount ?: 0.0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဝယ်ပြန်ပို့", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White) } },
                actions = { IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "Refresh", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PurchaseReturnColor, titleContentColor = OnPrimary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewReturn, containerColor = PurchaseReturnColor) {
                Icon(Icons.Outlined.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = voucherText,
                    onValueChange = { voucherText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Return No တိတိကျကျ ရှာပါ...") },
                    leadingIcon = { Icon(Icons.Outlined.Tag, null, tint = TextMuted) },
                    trailingIcon = {
                        TextButton(
                            onClick = { vm.findVoucher(voucherText, onReturnClick) { searchText = voucherText } },
                            enabled = voucherText.isNotBlank()
                        ) { Text("ဖွင့်မည်", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.findVoucher(voucherText, onReturnClick) { searchText = voucherText } })
                )
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Return No / Supplier / Reason ရှာပါ...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextMuted) },
                    trailingIcon = {
                        if (searchText.isNotBlank()) IconButton(onClick = { searchText = ""; vm.setSearch("") }) {
                            Icon(Icons.Outlined.Close, null, tint = TextMuted)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.setSearch(searchText) })
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { ReturnChip("Today", state.dateShortcut == DateShortcut.TODAY) { vm.applyDateShortcut(DateShortcut.TODAY) } }
                    item { ReturnChip("This Week", state.dateShortcut == DateShortcut.WEEK) { vm.applyDateShortcut(DateShortcut.WEEK) } }
                    item { ReturnChip("This Month", state.dateShortcut == DateShortcut.MONTH) { vm.applyDateShortcut(DateShortcut.MONTH) } }
                    item { ReturnChip("All", state.dateShortcut == DateShortcut.ALL) { vm.applyDateShortcut(DateShortcut.ALL) } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryBoxLocal("Return", visibleItems.size.toString(), PurchaseReturnColor, Modifier.weight(1f))
                    SummaryBoxLocal("တန်ဖိုး", moneyLocal(total), PurchaseReturnColor, Modifier.weight(1f))
                    SummaryBoxLocal("ငွေလက်ခံ", moneyLocal(refund), Color(0xFF16A34A), Modifier.weight(1f))
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
            } else if (visibleItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AssignmentReturn, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("ဝယ်ပြန်ပို့ မှတ်တမ်း မရှိသေးပါ", color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleItems) { ret -> PurchaseReturnCard(ret) { ret.id?.let(onReturnClick) } }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PurchaseReturnCard(ret: PurchaseReturnDTO, onClick: () -> Unit) {
    val isVoid = ret.status.equals("VOIDED", ignoreCase = true)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (isVoid) Danger.copy(0.25f) else BorderColor),
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ret.returnNo ?: "#${ret.id}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseReturnColor)
                    Text(ret.supplierName ?: ret.purchaseCode ?: "Supplier", fontSize = 12.sp, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(color = if (isVoid) DangerBg else PurchaseReturnBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        if (isVoid) "VOID" else moneyLocal(ret.totalReturnAmount ?: 0.0),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isVoid) Danger else PurchaseReturnColor
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Text(ret.returnDate?.take(10) ?: "-", fontSize = 11.sp, color = TextMuted)
                if (!ret.reason.isNullOrBlank()) {
                    Icon(Icons.Outlined.Notes, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                    Text(ret.reason, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ReturnChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) PurchaseReturnColor.copy(0.12f) else CardBg,
            labelColor = if (selected) PurchaseReturnColor else TextMuted
        ),
        border = BorderStroke(1.dp, if (selected) PurchaseReturnColor.copy(0.35f) else BorderColor)
    )
}

@Composable
private fun SummaryBoxLocal(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = color.copy(0.08f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, color.copy(0.16f))) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = TextMuted, maxLines = 1)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
        }
    }
}

private fun moneyLocal(v: Double): String = "%,.0f Ks".format(v)

