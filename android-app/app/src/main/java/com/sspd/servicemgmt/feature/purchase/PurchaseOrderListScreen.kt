package com.sspd.servicemgmt.feature.purchase

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.PurchaseOrderDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.*

import com.sspd.servicemgmt.core.util.PreferenceManager

private val PurchaseColor = Color(0xFF0F766E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrderListScreen(onBack: () -> Unit) {
    val vm: PurchaseOrderListViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager(context) }
    val canApprovePermission = prefs.hasPermission("CAN_ACCESS_PURCHASE_ORDER_APPROVE")
    val canFinalApprovePermission = prefs.hasPermission("CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE")
    val canReceivePermission = prefs.hasPermission("CAN_ACCESS_PURCHASE_ORDER_RECEIVE")
    var rejectTarget by remember { mutableStateOf<PurchaseOrderDTO?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("အဝယ်အော်ဒါ", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "ပြန်ဖတ်ရန်", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PurchaseColor, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.lateItems.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        border = BorderStroke(1.dp, Color(0xFFFCD34D))
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Warning, null, tint = Color(0xFFD97706))
                            Text("ရောက်မည့်ရက်ကျော် ${state.lateItems.size} ခု", fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                        }
                    }
                }
            }
            if (!state.error.isNullOrBlank()) {
                item { Text(state.error ?: "", color = Danger, fontSize = 12.sp) }
            }
            if (state.items.isEmpty()) {
                item { Text("အော်ဒါ မရှိသေးပါ", color = TextMuted, modifier = Modifier.padding(24.dp)) }
            }
            itemsIndexed(state.items, key = { index, it -> it.id ?: "po-$index" }) { _, po ->
                PurchaseOrderCard(
                    po = po,
                    late = state.lateItems.any { it.id == po.id },
                    busy = state.busy,
                    canApprovePermission = canApprovePermission,
                    canFinalApprovePermission = canFinalApprovePermission,
                    canReceivePermission = canReceivePermission,
                    onApprove = { vm.approve(po) },
                    onReject = {
                        rejectReason = ""
                        rejectTarget = po
                    },
                    onReceive = { vm.startReceive(po) }
                )
            }
        }
    }

    rejectTarget?.let { po ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) rejectTarget = null },
            title = { Text("Reject ${po.poCode ?: "#${po.id}"}") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("အကြောင်းပြချက် (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.reject(po, rejectReason)
                        rejectTarget = null
                    },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("Reject") }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }, enabled = !state.busy) { Text("ပိတ်") }
            }
        )
    }

    val draft = state.receiveDraft
    if (draft != null) {
        Dialog(onDismissRequest = { if (!state.busy) vm.cancelReceive() }) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp).heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("${draft.poCode ?: "#${draft.poId}"} လက်ခံ", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = draft.invoiceNo,
                        onValueChange = vm::setReceiveInvoiceNo,
                        label = { Text("Supplier Invoice No") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Column(
                        Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        draft.lines.forEach { line ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${line.productName} × ${line.qty}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (line.hasSerial) {
                                    OutlinedTextField(
                                        value = line.serialText,
                                        onValueChange = { vm.setReceiveLine(line.detailId, serialText = it) },
                                        label = { Text("Serial ${line.qty} ခု (တစ်ကြောင်းတစ်ခု)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2
                                    )
                                }
                            }
                        }
                    }
                    if (!state.error.isNullOrBlank()) {
                        Text(state.error ?: "", color = Danger, fontSize = 12.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { vm.cancelReceive() }, enabled = !state.busy) { Text("ပိတ်") }
                        Button(
                            onClick = { vm.confirmReceive() },
                            enabled = !state.busy,
                            colors = ButtonDefaults.buttonColors(containerColor = PurchaseColor)
                        ) { Text("လက်ခံ") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseOrderCard(
    po: PurchaseOrderDTO,
    late: Boolean,
    busy: Boolean,
    canApprovePermission: Boolean,
    canFinalApprovePermission: Boolean,
    canReceivePermission: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onReceive: () -> Unit
) {
    val st = (po.status ?: "OPEN").uppercase()
    val canApprove = (canApprovePermission && (st == "PENDING_APPROVAL" || st == "OPEN"))
        || (canFinalApprovePermission && st == "PENDING_FINAL_APPROVAL")
    val canReceive = canReceivePermission && (st == "APPROVED" || st == "PARTIAL")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (late) Color(0xFFFFFBEB) else CardBg),
        border = BorderStroke(1.dp, if (late) Color(0xFFFCD34D) else BorderColor)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(po.poCode ?: "#${po.id}", fontWeight = FontWeight.ExtraBold, color = PurchaseColor)
                Text(st, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            }
            Text(po.supplierName ?: "-", fontSize = 13.sp, color = TextMain)
            Text("ရောက်မည့်ရက် ${po.expectedDate ?: "-"}", fontSize = 11.sp, color = if (late) Color(0xFFD97706) else TextMuted)
            if (canApprove || canReceive) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canApprove) {
                        Button(
                            onClick = onApprove,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) { Text("Approve", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = onReject,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                            border = BorderStroke(1.dp, Danger.copy(0.4f))
                        ) { Text("Reject", fontSize = 12.sp) }
                    }
                    if (canReceive) {
                        Button(
                            onClick = onReceive,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PurchaseColor)
                        ) { Text("Receive", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}
