package com.sspd.servicemgmt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.api.StockAdjItemDTO
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.viewmodel.StockAdjDetailViewModel

private val AdjColor = Color(0xFF0891B2)
private val AdjBg    = Color(0xFFECFEFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjDetailScreen(
    onBack:    () -> Unit,
    onDeleted: () -> Unit = {}
) {
    val vm: StockAdjDetailViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Delete confirmation dialog
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissDeleteDialog,
            title = { Text("ဖျက်မည်လား?") },
            text  = { Text("ဤ Stock Adjustment ကို ဖျက်မည်။ ဖျက်ပြီးနောက် ပြန်လည်ရယူ၍ မရပါ။") },
            confirmButton = {
                Button(
                    onClick = { vm.delete(onDeleted) },
                    colors  = ButtonDefaults.buttonColors(containerColor = Danger),
                    enabled = !state.deleteLoading
                ) {
                    if (state.deleteLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("ဖျက်ရန်")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = vm::dismissDeleteDialog) { Text("မဖျက်ပါ") }
            }
        )
    }

    // Error snackbar
    state.error?.let { err ->
        LaunchedEffect(err) {
            // just show via the top-level error section below
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.adj?.adjCode ?: "Stock Adjustment",
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White)
                    }
                    IconButton(onClick = vm::showDeleteDialog) {
                        Icon(Icons.Outlined.Delete, "ဖျက်ရန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = AdjColor,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }

        val adj = state.adj
        if (adj == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("ဒေတာ မတွေ့ပါ", color = TextMuted)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier           = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding     = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header card ────────────────────────────────────────────────
            item {
                Card(
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.cardColors(containerColor = CardBg),
                    border    = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(40.dp).background(AdjBg, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Inventory, null, tint = AdjColor, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(adj.adjCode ?: "#${adj.id}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = AdjColor)
                                Text("Stock Adjustment", fontSize = 11.sp, color = TextMuted)
                            }
                        }

                        HorizontalDivider(color = BorderColor)

                        DetailRow("ရက်စွဲ",         adj.adjDate?.take(10) ?: "—")
                        DetailRow("ဝန်ထမ်း",         adj.staffName ?: "—")
                        if (!adj.reason.isNullOrBlank())
                            DetailRow("အကြောင်းအရင်း", adj.reason)
                        DetailRow("ပစ္စည်းအရေအတွက်", "${adj.items?.size ?: 0} မျိုး")
                    }
                }
            }

            // ── Error ──────────────────────────────────────────────────────
            state.error?.let { err ->
                item {
                    Surface(color = DangerBg, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Danger.copy(0.3f))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(err, color = Danger, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = vm::clearError, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Close, null, tint = Danger, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // ── Items header ───────────────────────────────────────────────
            item {
                Text(
                    "ပစ္စည်းများ",
                    fontSize     = 13.sp,
                    fontWeight   = FontWeight.ExtraBold,
                    color        = TextMain
                )
            }

            // ── Item cards ─────────────────────────────────────────────────
            val itemList = adj.items ?: emptyList()
            if (itemList.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("ပစ္စည်း မရှိပါ", color = TextMuted)
                    }
                }
            } else {
                items(itemList) { item ->
                    AdjItemCard(item)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AdjItemCard(item: StockAdjItemDTO) {
    val isGain   = item.type == "GAIN"
    val typeColor = if (isGain) Color(0xFF16A34A) else Danger
    val typeBg    = if (isGain) Color(0xFFF0FDF4) else DangerBg
    val typeLabel = if (isGain) "ရရှိ (GAIN)" else "ဆုံးရှုံး (LOSS)"

    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.productName ?: "—",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    color      = TextMain
                )
                if (!item.productCode.isNullOrBlank()) {
                    Text(item.productCode, fontSize = 11.sp, color = TextMuted)
                }
                if (!item.remark.isNullOrBlank()) {
                    Text(item.remark, fontSize = 11.sp, color = TextMuted)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = typeBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        typeLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = typeColor
                    )
                }
                Text(
                    "× ${item.qty ?: 0}",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = typeColor
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = TextMain, fontWeight = FontWeight.SemiBold)
    }
}
