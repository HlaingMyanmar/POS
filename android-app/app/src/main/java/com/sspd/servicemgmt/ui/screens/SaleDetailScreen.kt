package com.sspd.servicemgmt.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.PaymentTransactionDTO
import com.sspd.servicemgmt.api.ProductSerialDTO
import com.sspd.servicemgmt.api.SaleDTO
import com.sspd.servicemgmt.api.SaleItemDTO
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.SaleDetailViewModel
import com.sspd.servicemgmt.utils.fmtWarranty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(onBack: () -> Unit, onPrint: () -> Unit = {}) {
    val vm: SaleDetailViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showVoidDialog by remember { mutableStateOf(false) }
    var voidReason by remember { mutableStateOf("") }

    LaunchedEffect(state.paySuccess) {
        if (state.paySuccess) { snackbar.showSnackbar("ငွေဆပ်မှု အောင်မြင်ပါသည် ✓"); vm.clearPaySuccess() }
    }
    LaunchedEffect(state.payError) {
        state.payError?.let { snackbar.showSnackbar(it); vm.clearPayError() }
    }

    // Pay Due Dialog
    if (state.showPayDialog) {
        PayDueDialog(
            dueAmount      = state.sale?.dueAmount ?: 0.0,
            paymentMethods = state.paymentMethods,
            paying         = state.paying,
            onDismiss      = { vm.dismissPayDialog() },
            onPay          = { amount, methodId, transactionNo, note -> vm.payDue(amount, methodId, transactionNo, note) }
        )
    }

    if (showVoidDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.voiding) showVoidDialog = false },
            title = { Text("Void Sale") },
            text = {
                OutlinedTextField(
                    value = voidReason,
                    onValueChange = { voidReason = it },
                    label = { Text("Reason (required)") },
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(
                    enabled = voidReason.isNotBlank() && !state.voiding,
                    onClick = { vm.voidSale(voidReason) { showVoidDialog = false } }
                ) { Text(if (state.voiding) "Voiding..." else "Void", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { showVoidDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.sale?.saleCode ?: "ရောင်းချမှု အချက်အလက်",
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                actions = {
                    state.sale?.let { sale ->
                        if (sale.voided != true) {
                            IconButton(onClick = { showVoidDialog = true }) {
                                Icon(Icons.Outlined.Block, "Void Sale", tint = Color.White)
                            }
                        }
                        IconButton(onClick = { shareSale(context, sale) }) {
                            Icon(Icons.Outlined.Share, "မျှဝေရန်", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onPrint) {
                        Icon(Icons.Outlined.Print, "ပရင့်", tint = Color.White)
                    }
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary, titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                AppLoading()
            }
            return@Scaffold
        }

        val sale = state.sale
        if (sale == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("ဒေတာ မတွေ့ပါ", color = TextMuted)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Header card ───────────────────────────────────────────
            item {
                Card(
                    shape  = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                sale.saleCode ?: "#${sale.id}",
                                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Primary
                            )
                            SaleStatusBadge(sale.paymentStatus)
                        }
                        if (sale.voided == true) {
                            Spacer(Modifier.height(8.dp))
                            Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text("VOIDED", color = Danger, fontWeight = FontWeight.ExtraBold)
                                    Text(sale.voidReason ?: "-", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            sale.saleDate?.take(16)?.replace("T", "  ") ?: "—",
                            fontSize = 12.sp, color = TextMuted
                        )
                        if ((sale.dueAmount ?: 0.0) > 0) {
                            Spacer(Modifier.height(8.dp))
                            Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("ကျန်ငွေ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Danger)
                                    Text("${sale.dueAmount.fmtD()} Ks", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                                }
                            }
                        }
                    }
                }
            }

            // ── Info rows ─────────────────────────────────────────────
            item {
                Card(
                    shape  = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SaleInfoRow(Icons.Outlined.Person,       "ဖောက်သည်",      sale.customerName ?: "—")
                        HorizontalDivider(color = BorderColor)
                        SaleInfoRow(Icons.Outlined.Badge,        "ဝန်ထမ်း",        sale.staffName ?: "—")
                        HorizontalDivider(color = BorderColor)
                        SaleInfoRow(Icons.Outlined.Payment, "ငွေပေးချေမှု",
                            when {
                                !sale.paymentMethodName.isNullOrBlank() -> sale.paymentMethodName
                                state.transactions.isNotEmpty() ->
                                    state.transactions.mapNotNull { it.paymentMethodName }
                                        .distinct().joinToString(", ").ifBlank { "—" }
                                (sale.paidAmount ?: 0.0) <= 0 -> "ငွေမပေးရသေးပါ"
                                else -> "—"
                            }
                        )
                    }
                }
            }

            // ── Items ─────────────────────────────────────────────────
            if (!sale.details.isNullOrEmpty()) {
                item {
                    Text(
                        "ကုန်ပစ္စည်းများ (${sale.details.size})",
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        color = TextMuted, letterSpacing = 0.5.sp
                    )
                }
                items(sale.details) { item ->
                    SaleItemCard(item, state.serialWarrantyMap)
                }
            }

            // ── Summary ───────────────────────────────────────────────
            item {
                Card(
                    shape  = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaleSummaryRow("Subtotal",   "${sale.totalAmount.fmtD()} Ks",   TextMain)
                        if ((sale.discountAmount ?: 0.0) > 0) {
                            SaleSummaryRow("ရောင်းပြန်",  "-${sale.discountAmount.fmtD()} Ks", Warning)
                        }
                        if ((sale.taxAmount ?: 0.0) > 0) {
                            SaleSummaryRow("Tax / VAT", "+${sale.taxAmount.fmtD()} Ks", Color(0xFFD97706))
                        }
                        HorizontalDivider(color = BorderColor)
                        SaleSummaryRow("စုစုပေါင်း",  "${sale.netAmount.fmtD()} Ks",    Primary, bold = true)
                        SaleSummaryRow("ပေးပြီး",     "${sale.paidAmount.fmtD()} Ks",   Success)
                        if ((sale.dueAmount ?: 0.0) > 0) {
                            SaleSummaryRow("ကျန်ငွေ",  "${sale.dueAmount.fmtD()} Ks",   Danger,  bold = true)
                        }
                    }
                }
            }

            // ── Payment History ───────────────────────────────────────
            if (state.transactions.isNotEmpty()) {
                item {
                    Text(
                        "ငွေပေးချေမှု မှတ်တမ်း (${state.transactions.size})",
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        color = TextMuted, letterSpacing = 0.5.sp
                    )
                }
                items(state.transactions) { tx ->
                    PaymentTransactionRow(tx)
                }
            }

            // ── Pay Due button ────────────────────────────────────────
            if ((sale.dueAmount ?: 0.0) > 0 && sale.voided != true) {
                item {
                    Button(
                        onClick = { vm.showPayDialog() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Danger)
                    ) {
                        Icon(Icons.Outlined.Payment, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ကျန်ငွေ ${sale.dueAmount.fmtD()} Ks ဆပ်မည်",
                            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // ── Remark ────────────────────────────────────────────────
            if (!sale.remark.isNullOrBlank()) {
                item {
                    Card(
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("REMARK", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                color = Warning, letterSpacing = 0.5.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(sale.remark, fontSize = 13.sp, color = TextMain,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SaleStatusBadge(status: String?) {
    val (bg, color, label) = when (status?.uppercase()) {
        "PAID"    -> Triple(SuccessBg, Success, "ပြောင်းပြီး")
        "PARTIAL" -> Triple(WarningBg, Warning, "တစ်စိတ်")
        "UNPAID","PENDING" -> Triple(DangerBg, Danger, "မပြောင်းရ")
        else      -> Triple(BorderColor, TextMuted, status ?: "—")
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SaleInfoRow(
    icon:  androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(15.dp))
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(80.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SaleItemCard(
    item:  SaleItemDTO,
    snMap: Map<String, ProductSerialDTO> = emptyMap()
) {
    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    item.productName ?: "—",
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextMain,
                    modifier = Modifier.weight(1f)
                )
                if (item.foc == true) {
                    Surface(color = SuccessBg, shape = RoundedCornerShape(4.dp)) {
                        Text("FOC", modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Success)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${item.qty ?: 0} × ${item.unitPrice.fmtD()} Ks",
                    fontSize = 12.sp, color = TextMuted
                )
                Text(
                    "${item.subtotal.fmtD()} Ks",
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary
                )
            }
            if (!item.serialNumbers.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                item.serialNumbers.forEach { sn ->
                    val wLabel = fmtWarranty(snMap[sn]?.warrantyMonths ?: item.warrantyMonths)
                    Text("S/N: $sn", fontSize = 10.sp, color = Primary)
                    if (wLabel.isNotEmpty()) {
                        Text("🛡 $wLabel", fontSize = 10.sp, color = Color(0xFF0891B2))
                    }
                }
            } else {
                val wLabel = fmtWarranty(item.warrantyMonths)
                if (wLabel.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text("🛡 $wLabel", fontSize = 10.sp, color = Color(0xFF0891B2))
                }
            }
            if ((item.discountAmount ?: 0.0) > 0) {
                Spacer(Modifier.height(2.dp))
                Text("ရောင်းပြန်: -${item.discountAmount.fmtD()} Ks",
                    fontSize = 10.sp, color = Warning)
            }
        }
    }
}

@Composable
private fun SaleSummaryRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = if (bold) color else TextMuted,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal)
        Text(value, fontSize = 13.sp, color = color,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold,
            textAlign = TextAlign.End)
    }
}

// ── Share ─────────────────────────────────────────────────────────────────────

@Composable
private fun PaymentTransactionRow(tx: PaymentTransactionDTO) {
    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).background(SuccessBg, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Payment, null, tint = Success, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(
                        tx.paymentMethodName ?: "—",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain
                    )
                    Text(
                        tx.paymentDate?.take(16)?.replace("T", "  ") ?: "—",
                        fontSize = 11.sp, color = TextMuted
                    )
                    if (!tx.transactionNo.isNullOrBlank()) {
                        Text("Ref: ${tx.transactionNo}", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
            Text(
                "${String.format("%,.0f", tx.amount ?: 0.0)} Ks",
                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Success
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayDueDialog(
    dueAmount:      Double,
    paymentMethods: List<PaymentMethodDTO>,
    paying:         Boolean,
    onDismiss:      () -> Unit,
    onPay:          (amount: Double, methodId: Int, transactionNo: String?, note: String?) -> Unit
) {
    var amountStr  by remember { mutableStateOf(String.format("%.0f", dueAmount)) }
    var selectedPm by remember { mutableStateOf<PaymentMethodDTO?>(null) }
    var note       by remember { mutableStateOf("") }
    var transactionNo by remember { mutableStateOf("") }
    var showPmPick by remember { mutableStateOf(false) }
    var error      by remember { mutableStateOf("") }

    if (showPmPick) {
        ModalBottomSheet(onDismissRequest = { showPmPick = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("ငွေပေးချေမှု နည်းလမ်း", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                paymentMethods.forEach { pm ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedPm = pm; showPmPick = false }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pm.methodName, fontSize = 14.sp, color = TextMain)
                        if (selectedPm?.id == pm.id)
                            Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BorderColor)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Payment, null, tint = Danger, modifier = Modifier.size(22.dp))
                Text("ကျန်ငွေ ဆပ်မည်", fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Due amount display
                Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("စုစုပေါင်း ကျန်ငွေ", fontSize = 12.sp, color = Danger)
                        Text("${String.format("%,.0f", dueAmount)} Ks", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                    }
                }
                // Amount input
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; error = "" },
                    label = { Text("ဆပ်မည့် ပမာဏ (Ks)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    isError = error.isNotBlank()
                )
                // Payment method picker
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { showPmPick = true },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (selectedPm == null && error.isNotBlank()) MaterialTheme.colorScheme.error else BorderColor)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selectedPm?.methodName ?: "ငွေပေးချေမှု နည်းလမ်း ရွေးပါ",
                            color = if (selectedPm != null) TextMain else TextMuted,
                            fontSize = 13.sp
                        )
                        Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                // Note
                OutlinedTextField(
                    value = transactionNo, onValueChange = { transactionNo = it },
                    label = { Text("Transaction / Reference No. (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("မှတ်ချက် (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp)
                )
                if (error.isNotBlank())
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    when {
                        amt == null || amt <= 0 -> error = "ပမာဏ မှန်ကန်စွာ ရိုက်ထည့်ပါ"
                        amt > dueAmount + 0.01  -> error = "ဆပ်မည့် ပမာဏ ကျန်ငွေထက် မကျော်ရပါ"
                        selectedPm == null      -> error = "ငွေပေးချေမှု နည်းလမ်း ရွေးပါ"
                        else -> onPay(amt, selectedPm!!.id, transactionNo.ifBlank { null }, note.ifBlank { null })
                    }
                },
                enabled = !paying,
                colors  = ButtonDefaults.buttonColors(containerColor = Danger)
            ) {
                if (paying) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("ဆပ်မည်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !paying) { Text("ပယ်ဖျက်") }
        }
    )
}

private fun shareSale(context: Context, sale: SaleDTO) {
    val text = buildString {
        appendLine("🧾 ${sale.saleCode ?: "#${sale.id}"}")
        appendLine("ရက်စွဲ: ${sale.saleDate?.take(16)?.replace("T", " ") ?: "—"}")
        appendLine("ဖောက်သည်: ${sale.customerName ?: "—"}")
        appendLine("ဝန်ထမ်း: ${sale.staffName ?: "—"}")
        appendLine("ငွေပေးချေမှု: ${sale.paymentMethodName ?: "—"}")
        appendLine("─────────────────")
        sale.details?.forEach { item ->
            appendLine("• ${item.productName} × ${item.qty}  =  ${item.subtotal.fmtD()} Ks")
            if (!item.serialNumbers.isNullOrEmpty())
                appendLine("  S/N: ${item.serialNumbers.joinToString(", ")}")
        }
        appendLine("─────────────────")
        appendLine("စုစုပေါင်း : ${sale.netAmount.fmtD()} Ks")
        appendLine("ပေးပြီး   : ${sale.paidAmount.fmtD()} Ks")
        if ((sale.dueAmount ?: 0.0) > 0)
            appendLine("ကျန်ငွေ   : ${sale.dueAmount.fmtD()} Ks")
        if (!sale.remark.isNullOrBlank())
            appendLine("မှတ်ချက်  : ${sale.remark}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

private fun Double?.fmtD() = String.format("%,.0f", this ?: 0.0)

