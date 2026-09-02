package com.sspd.servicemgmt.feature.purchasereturn

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.PurchaseReturnDetailDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.*

import com.sspd.servicemgmt.core.util.PreferenceManager

private val PurchaseReturnDetailColor = Color(0xFF0F766E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseReturnDetailScreen(onBack: () -> Unit) {
    val vm: PurchaseReturnDetailViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager(context) }
    val canVoid = prefs.hasPermission("CAN_ACCESS_PURCHASE_RETURN_UPDATE")
    val ret = state.purchaseReturn
    var showVoidDialog by remember { mutableStateOf(false) }
    var voidReason by remember { mutableStateOf("") }

    if (showVoidDialog) {
        AlertDialog(
            onDismissRequest = { showVoidDialog = false },
            title = { Text("ဝယ်ပြန်ပို့ Void လုပ်မည်") },
            text = {
                OutlinedTextField(
                    value = voidReason,
                    onValueChange = { voidReason = it },
                    label = { Text("Void အကြောင်းအရင်း") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.voidReturn(voidReason); showVoidDialog = false }) { Text("Void") }
            },
            dismissButton = { TextButton(onClick = { showVoidDialog = false }) { Text("မလုပ်တော့ပါ") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ret?.returnNo ?: "ဝယ်ပြန်ပို့ အသေးစိတ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White) } },
                actions = {
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "Refresh", tint = Color.White) }
                    if (canVoid && !ret?.status.equals("VOIDED", ignoreCase = true)) {
                        IconButton(onClick = { showVoidDialog = true }) { Icon(Icons.Outlined.Block, "Void", tint = Color.White) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PurchaseReturnDetailColor, titleContentColor = OnPrimary)
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }
        if (ret == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "ဒေတာ မတွေ့ပါ", color = TextMuted)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(ret.returnNo ?: "#${ret.id}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseReturnDetailColor)
                            Surface(color = if (ret.status.equals("VOIDED", true)) DangerBg else Color(0xFFECFDF5), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    ret.status ?: "ACTIVE",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ret.status.equals("VOIDED", true)) Danger else PurchaseReturnDetailColor
                                )
                            }
                        }
                        Text(ret.returnDate?.take(16)?.replace("T", "  ") ?: "-", fontSize = 12.sp, color = TextMuted)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailInfoRow(Icons.Outlined.Storefront, "Supplier", state.purchase?.supplierName ?: ret.supplierName ?: "-")
                        HorizontalDivider(color = BorderColor)
                        DetailInfoRow(Icons.Outlined.ShoppingCart, "Purchase", state.purchase?.purchaseCode ?: ret.purchaseCode ?: "-")
                        HorizontalDivider(color = BorderColor)
                        DetailInfoRow(Icons.Outlined.Notes, "အကြောင်းအရင်း", ret.reason ?: "-")
                        if (!ret.paymentMethodName.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            DetailInfoRow(Icons.Outlined.AccountBalance, "ငွေလက်ခံနည်း", ret.paymentMethodName)
                        }
                        if ((ret.shippingCostAmount ?: 0.0) > 0.0) {
                            HorizontalDivider(color = BorderColor)
                            DetailInfoRow(Icons.Outlined.LocalShipping, "Return shipping",
                                "${moneyDetail(ret.shippingCostAmount ?: 0.0)} · ${ret.shippingPayerResponsibility ?: "COMPANY"}")
                            DetailInfoRow(Icons.Outlined.Payments, "Company / Supplier",
                                "${moneyDetail(ret.companyShippingPortion ?: 0.0)} / ${moneyDetail(ret.supplierShippingPortion ?: 0.0)}")
                            if (!ret.shippingPaymentMethodName.isNullOrBlank()) {
                                DetailInfoRow(Icons.Outlined.AccountBalance, "Shipping payment",
                                    "${ret.shippingPaymentMethodName} ${ret.shippingTransactionReference ?: ""}".trim())
                            }
                        }
                        if (!ret.voidReason.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            DetailInfoRow(Icons.Outlined.Block, "Void Reason", ret.voidReason)
                        }
                    }
                }
            }
            if (!ret.payments.isNullOrEmpty()) {
                item { Text("Refund payments (${ret.payments.size})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted) }
                items(ret.payments) { payment ->
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(payment.paymentMethodName ?: "Refund", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                if (!payment.transactionNo.isNullOrBlank()) {
                                    Text(payment.transactionNo, fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            Text(moneyDetail(payment.amount ?: 0.0), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseReturnDetailColor)
                        }
                    }
                }
            }

            if (!ret.details.isNullOrEmpty()) {
                item { Text("ပြန်ပို့ပစ္စည်းများ (${ret.details.size})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted) }
                items(ret.details) { detail -> PurchaseReturnDetailCard(detail) }
            }
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)), border = BorderStroke(1.dp, PurchaseReturnDetailColor.copy(0.25f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryLine("ပြန်ပို့တန်ဖိုး", moneyDetail(ret.totalReturnAmount ?: 0.0))
                        HorizontalDivider(color = PurchaseReturnDetailColor.copy(0.18f))
                        SummaryLine("Supplier ထံမှ ငွေလက်ခံ", moneyDetail(ret.refundAmount ?: 0.0), bold = true)
                    }
                }
            }
            state.error?.let {
                item {
                    Surface(color = DangerBg, shape = RoundedCornerShape(10.dp)) {
                        Text(it, Modifier.fillMaxWidth().padding(12.dp), color = Danger, fontSize = 13.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(15.dp).padding(top = 1.dp))
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(96.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PurchaseReturnDetailCard(detail: PurchaseReturnDetailDTO) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(detail.productName ?: "-", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Text("${detail.qty ?: 0} x ${moneyDetail(detail.unitPrice ?: 0.0)}", fontSize = 11.sp, color = TextMuted)
                }
                Text(moneyDetail(detail.subtotal ?: 0.0), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseReturnDetailColor)
            }
            if ((detail.allocatedShippingCost ?: 0.0) > 0.0) {
                Text("Allocated shipping: ${moneyDetail(detail.allocatedShippingCost ?: 0.0)}", fontSize = 10.sp, color = TextMuted)
            }
            if (!detail.serialNumbers.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("S/N: ${detail.serialNumbers.joinToString(", ")}", fontSize = 10.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = if (bold) 14.sp else 13.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal, color = PurchaseReturnDetailColor)
        Text(value, fontSize = if (bold) 15.sp else 13.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseReturnDetailColor)
    }
}

private fun moneyDetail(v: Double): String = "%,.0f Ks".format(v)

