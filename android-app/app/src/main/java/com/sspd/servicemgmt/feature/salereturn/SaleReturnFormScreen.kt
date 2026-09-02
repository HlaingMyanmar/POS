package com.sspd.servicemgmt.feature.salereturn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppPickerSheet
import com.sspd.servicemgmt.core.ui.util.rememberIsTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleReturnFormScreen(onBack: () -> Unit, onSuccess: () -> Unit) {
    val vm: SaleReturnFormViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    var showPmSheet by rememberSaveable { mutableStateOf(false) }

    // Payment method sheet
    if (showPmSheet) {
        AppPickerSheet(
            title = "ငွေပြန်ပေးနည်း ရွေးပါ",
            items = state.paymentMethods,
            label = { it.methodName },
            onSelect = { vm.selectPm(it); showPmSheet = false },
            onDismiss = { showPmSheet = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Return ပြင်ဆင်ရန်" else "Return အသစ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Danger, titleContentColor = OnPrimary)
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                AppLoading()
            }
            return@Scaffold
        }

        val isTablet = rememberIsTablet()
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTablet) 64.dp else 16.dp, vertical = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Sale ရွေးပါ ──────────────────────────────────────────────────
            SectionHeader(Icons.Outlined.Receipt, "Sale ရွေးပါ *")
            Column {
                OutlinedTextField(
                    value           = state.saleQuery,
                    onValueChange   = { vm.setSaleQuery(it) },
                    label           = { Text("Sale Code / ဖောက်သည် ရှာပါ *") },
                    leadingIcon     = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon    = if (state.selectedSale != null) ({
                        Icon(Icons.Outlined.CheckCircle, null, tint = Success, modifier = Modifier.size(20.dp))
                    }) else null,
                    modifier        = Modifier.fillMaxWidth(), singleLine = true,
                    shape           = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                if (state.saleResults.isNotEmpty() && state.selectedSale == null) {
                    Card(shape = RoundedCornerShape(0.dp, 0.dp, 12.dp, 12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        state.saleResults.forEach { sale ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { vm.selectSale(sale) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(sale.saleCode ?: "#${sale.id}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                    Text(sale.customerName ?: "—", fontSize = 11.sp, color = TextMuted)
                                }
                                Text("${String.format("%,.0f", sale.netAmount ?: 0.0)} Ks", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = BorderColor)
                        }
                    }
                }
            }

            // ── ပြန်ပေးမည့် ပစ္စည်းများ ──────────────────────────────────────
            if (state.items.isNotEmpty()) {
                SectionHeader(Icons.Outlined.AssignmentReturn, "ပြန်ပေးမည့် ပစ္စည်းများ")
                state.items.forEachIndexed { i, item ->
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, if (item.qty > 0) Danger.copy(0.4f) else BorderColor)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.productName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                    Text("${String.format("%,.0f", item.unitPrice)} Ks × Max ${item.maxQty}", fontSize = 11.sp, color = TextMuted)
                                }
                                // Qty stepper
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { vm.setItemQty(i, item.qty - 1) }, modifier = Modifier.size(40.dp).background(if (item.qty > 0) DangerBg else ScreenBg, RoundedCornerShape(8.dp))) {
                                        Text("−", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (item.qty > 0) Danger else TextMuted)
                                    }
                                    Text("${item.qty}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (item.qty > 0) Danger else TextMuted, modifier = Modifier.widthIn(min = 28.dp))
                                    IconButton(onClick = { vm.setItemQty(i, item.qty + 1) }, modifier = Modifier.size(40.dp).background(if (item.qty < item.maxQty) DangerBg else ScreenBg, RoundedCornerShape(8.dp))) {
                                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (item.qty < item.maxQty) Danger else TextMuted)
                                    }
                                }
                            }
                            if (item.qty > 0) {
                                Surface(color = DangerBg, shape = RoundedCornerShape(6.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Subtotal", fontSize = 11.sp, color = Danger)
                                        Text("${String.format("%,.0f", item.qty * item.unitPrice)} Ks", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger)
                                    }
                                }
                            }
                            // Serial section — auto-populated from original sale
                            if (item.hasSerial && item.qty > 0) {
                                Surface(
                                    color  = SuccessBg,
                                    shape  = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Success.copy(alpha = 0.3f))
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.QrCode2, null, tint = Success, modifier = Modifier.size(12.dp))
                                            Text(
                                                "Serial Numbers (${item.serialNumbers.size}/${item.qty}) — auto",
                                                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Success
                                            )
                                        }
                                        item.serialNumbers.forEach { sn ->
                                            Surface(color = CardBg, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Success.copy(alpha = 0.4f))) {
                                                Row(
                                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(sn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                                    Icon(Icons.Outlined.CheckCircle, null, tint = Success, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                        // Warn if qty set but serials fewer than expected
                                        if (item.serialNumbers.size < item.qty) {
                                            Surface(color = WarningBg, shape = RoundedCornerShape(6.dp)) {
                                                Text(
                                                    "⚠ Serial ${item.serialNumbers.size}/${item.qty} ခုသာ ရှိသည်",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontSize = 10.sp, color = Warning, fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Total summary
                val total = state.items.filter { it.qty > 0 }.sumOf { it.qty * it.unitPrice }
                if (total > 0) {
                    Surface(color = DangerBg, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ပြန်ပေးမည့် စုစုပေါင်း", fontSize = 13.sp, color = Danger, fontWeight = FontWeight.Bold)
                            Text("${String.format("%,.0f", total)} Ks", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                        }
                    }
                }

                // ── ငွေပြန်ပေး ────────────────────────────────────────────────
                SectionHeader(Icons.Outlined.Payments, "ငွေပြန်ပေးခြင်း")
                OutlinedTextField(
                    value = state.refundAmountStr, onValueChange = { vm.setRefundAmount(it) },
                    label = { Text("ငွေပြန်ပေးသည့် ပမာဏ (Ks)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
                )
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { showPmSheet = true },
                    shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(state.selectedPm?.methodName ?: "ငွေပြန်ပေးနည်း ရွေးပါ (optional)",
                            color = if (state.selectedPm != null) TextMain else TextMuted, fontSize = 13.sp)
                        Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                OutlinedTextField(
                    value = state.transactionNo, onValueChange = { vm.setTransactionNo(it) },
                    label = { Text("Transaction No (optional)") },
                    leadingIcon = { Icon(Icons.Outlined.Receipt, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Button(
                    onClick = vm::addSplitRefund,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add split refund")
                }
                state.splitRefunds.forEachIndexed { index, payment ->
                    Surface(color = DangerBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFFECACA))) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(payment.paymentMethodName ?: "Refund", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${String.format("%,.0f", payment.amount ?: 0.0)} Ks", color = Danger, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            IconButton(onClick = { vm.removeSplitRefund(index) }) {
                                Icon(Icons.Outlined.Delete, null, tint = Danger)
                            }
                        }
                    }
                }
            }

            // ── အကြောင်းအရင်း ─────────────────────────────────────────────────
            SectionHeader(Icons.Outlined.Notes, "အကြောင်းအရင်း *")
            OutlinedTextField(
                value = state.reason, onValueChange = { vm.setReason(it) },
                label = { Text("ပြန်ပေးသောအကြောင်း *") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                maxLines = 4, shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            // Error
            state.saveError?.let { err ->
                Surface(color = DangerBg, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(18.dp))
                        Text(err, fontSize = 13.sp, color = Danger, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Save
            Button(
                onClick  = { vm.save { onSuccess() } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Danger),
                enabled  = !state.saving
            ) {
                if (state.saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.isEdit) "Return ပြင်ဆင်မှု သိမ်းမည်" else "Return သိမ်းမည်", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Danger, modifier = Modifier.size(18.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
    }
}
