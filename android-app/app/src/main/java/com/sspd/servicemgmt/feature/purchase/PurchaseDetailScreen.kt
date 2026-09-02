package com.sspd.servicemgmt.feature.purchase

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.PurchaseItemDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.*

import com.sspd.servicemgmt.core.util.PreferenceManager

private val PurchaseColor = Color(0xFF0F766E)
private val PurchaseBg = Color(0xFFECFDF5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(onBack: () -> Unit) {
    val vm: PurchaseDetailViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager(context) }
    val canConfirm = prefs.hasPermission("CAN_ACCESS_PURCHASE_UPDATE")
    val canCancel = prefs.hasPermission("CAN_ACCESS_PURCHASE_DELETE")
    var showCancel by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("") }
    var refundMethodId by remember { mutableStateOf(0) }

    val purchase = state.purchase
    val status = (purchase?.status ?: "").uppercase()
    val paid = purchase?.paidAmount ?: 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.purchase?.purchaseCode ?: "ဝယ်ယူမှု", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                actions = {
                    if (status == "DRAFT" && canConfirm) {
                        IconButton(onClick = { vm.confirmDraft() }, enabled = !state.busy) {
                            Icon(Icons.Outlined.CheckCircle, "အတည်ပြု", tint = Color.White)
                        }
                    }
                    if (status != "CANCELLED" && canCancel) {
                        IconButton(onClick = { showCancel = true }, enabled = !state.busy) {
                            Icon(Icons.Outlined.Cancel, "ပယ်ဖျက်", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Outlined.Refresh, "ပြန်ဖတ်ရန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PurchaseColor,
                    titleContentColor = OnPrimary
                )
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }

        val purchase = state.purchase
        if (purchase == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "ဒေတာ မတွေ့ပါ", color = TextMuted)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(40.dp).background(PurchaseBg, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.ShoppingCart, null, tint = PurchaseColor, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(purchase.purchaseCode ?: "#${purchase.id}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = PurchaseColor)
                                Text(purchase.supplierName ?: "ပေးသွင်းသူ", fontSize = 12.sp, color = TextMain)
                            }
                        }
                        HorizontalDivider(color = BorderColor)
                        InfoRow("ရက်စွဲ", purchase.purchaseDate?.take(10) ?: "-")
                        InfoRow("ဝန်ထမ်း", purchase.staffName ?: "-")
                        InfoRow("ငွေချေမှု", purchase.paymentStatus ?: "-")
                        InfoRow("ပေးရန်ရက်", purchase.dueDate ?: "-")
                        InfoRow("ငွေချေကာလ", purchase.paymentTermDays?.let { "$it ရက်" } ?: "-")
                        if (!purchase.poCode.isNullOrBlank()) InfoRow("PO", purchase.poCode)
                        if (!purchase.supplierInvoiceNo.isNullOrBlank()) InfoRow("Supplier Inv", purchase.supplierInvoiceNo)
                        purchase.taxMode?.let { InfoRow("Tax Mode", it) }
                        if ((purchase.taxAmount ?: 0.0) > 0) InfoRow("အခွန်", moneyText(purchase.taxAmount))
                        if ((purchase.withholdingTaxAmount ?: 0.0) > 0) InfoRow("WHT", moneyText(purchase.withholdingTaxAmount))
                        if ((purchase.otherCharges ?: 0.0) > 0) InfoRow("အခြားကုန်ကျ", moneyText(purchase.otherCharges))
                        purchase.landedCostAllocationMethod?.let { InfoRow("Landed", it) }
                        val ccy = purchase.currencyCode?.takeIf { it.isNotBlank() }
                        if (ccy != null && ccy != "MMK") {
                            InfoRow("Currency", "$ccy @ ${purchase.exchangeRate ?: 1}")
                            InfoRow("Foreign", "%.2f %s".format(purchase.foreignNetAmount ?: 0.0, ccy))
                        }
                        if (!purchase.remark.isNullOrBlank()) InfoRow("မှတ်ချက်", purchase.remark)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TotalBox("မူလစုစုပေါင်း", moneyText(purchase.totalAmount), TextMain, Modifier.weight(1f))
                        TotalBox("လျှော့ငွေ", moneyText(purchase.discountAmount), Color(0xFFD97706), Modifier.weight(1f))
                        TotalBox("ကျသင့်ငွေ", moneyText(purchase.netAmount ?: purchase.totalAmount), PurchaseColor, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TotalBox("ပေးပြီး", moneyText(purchase.paidAmount), Color(0xFF16A34A), Modifier.weight(1f))
                        TotalBox("ပေးရန်ကျန်", moneyText(purchase.dueAmount), if ((purchase.dueAmount ?: 0.0) > 0) Danger else PurchaseColor, Modifier.weight(1f))
                    }
                }
            }

            item {
                Text("ပစ္စည်းများ", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            }

            val lines = purchase.details ?: emptyList()
            if (lines.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("ပစ္စည်း မရှိပါ", color = TextMuted)
                    }
                }
            } else {
                items(lines) { item -> PurchaseItemCard(item) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showCancel) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) showCancel = false },
            title = { Text("ဘောင်ချာ ပယ်ဖျက်မည်") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("အကြောင်းရင်း *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (paid > 0) {
                        Text("ငွေပြန်ဝင်မည့် Payment Method *", fontSize = 12.sp, color = TextMuted)
                        state.paymentMethods.forEach { method ->
                            FilterChip(
                                selected = refundMethodId == method.id,
                                onClick = { refundMethodId = method.id },
                                label = { Text(method.methodName) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.busy && cancelReason.isNotBlank() && (paid <= 0 || refundMethodId > 0),
                    onClick = {
                        vm.cancel(cancelReason.trim(), if (paid > 0) refundMethodId else null)
                        showCancel = false
                    }
                ) { Text("ပယ်ဖျက်မည်") }
            },
            dismissButton = {
                TextButton(onClick = { showCancel = false }, enabled = !state.busy) { Text("မလုပ်တော့") }
            }
        )
    }
}

@Composable
private fun PurchaseItemCard(item: PurchaseItemDTO) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.productName ?: "-", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMain)
                    Text("အရေအတွက် ${item.qty ?: 0} x ${moneyText(item.unitCost)}", fontSize = 11.sp, color = TextMuted)
                    if ((item.allocatedLandedCost ?: 0.0) > 0) {
                        Text("Landed: ${moneyText(item.allocatedLandedCost)}", fontSize = 11.sp, color = TextMuted)
                    }
                }
                Text(moneyText(item.subtotal), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseColor)
            }
            if ((item.warrantyMonths ?: 0) > 0) {
                Text("အာမခံ: ${item.warrantyMonths} လ", fontSize = 11.sp, color = TextMuted)
            }
            val serials = item.serialNumbers ?: emptyList()
            if (serials.isNotEmpty()) {
                Text("စီရီယယ်များ: ${serials.joinToString(", ")}", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun TotalBox(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = color.copy(0.08f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(0.18f))) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = TextMuted)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = TextMain, fontWeight = FontWeight.SemiBold)
    }
}

private fun moneyText(v: Double?): String = "%,.0f Ks".format(v ?: 0.0)
