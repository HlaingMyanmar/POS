package com.sspd.servicemgmt.ui.screens

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
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.utils.rememberIsTablet
import com.sspd.servicemgmt.ui.viewmodel.PurchaseReturnFormViewModel

private val PurchaseReturnFormColor = Color(0xFF0F766E)
private val PurchaseReturnFormBg = Color(0xFFECFDF5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseReturnFormScreen(onBack: () -> Unit, onSuccess: (Int) -> Unit) {
    val vm: PurchaseReturnFormViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showPmSheet by rememberSaveable { mutableStateOf(false) }

    if (showPmSheet) {
        ModalBottomSheet(onDismissRequest = { showPmSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("Supplier ထံမှ ငွေလက်ခံနည်း ရွေးပါ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                state.paymentMethods.forEach { pm ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { vm.selectPm(pm); showPmSheet = false }.padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pm.methodName, fontSize = 14.sp, color = TextMain)
                        if (state.selectedPm?.id == pm.id) Icon(Icons.Outlined.Check, null, tint = PurchaseReturnFormColor, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BorderColor)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဝယ်ပြန်ပို့ အသစ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PurchaseReturnFormColor, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }
        val isTablet = rememberIsTablet()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTablet) 64.dp else 16.dp, vertical = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ReturnSection(Icons.Outlined.ShoppingCart, "ဝယ်ယူမှုဘောင်ချာ ရွေးပါ *")
            Column {
                OutlinedTextField(
                    value = state.purchaseQuery,
                    onValueChange = { vm.setPurchaseQuery(it) },
                    label = { Text("Purchase No / Supplier ရှာပါ *") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = if (state.selectedPurchase != null) ({
                        Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                    }) else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                if (state.purchaseResults.isNotEmpty() && state.selectedPurchase == null) {
                    Card(shape = RoundedCornerShape(0.dp, 0.dp, 12.dp, 12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        state.purchaseResults.forEach { purchase ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { vm.selectPurchase(purchase) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(purchase.purchaseCode ?: "#${purchase.id}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                    Text(purchase.supplierName ?: "Supplier", fontSize = 11.sp, color = TextMuted)
                                }
                                Text(moneyReturn(purchase.totalAmount ?: 0.0), fontSize = 12.sp, color = PurchaseReturnFormColor, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = BorderColor)
                        }
                    }
                }
            }

            state.selectedPurchase?.let { purchase ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(purchase.purchaseCode ?: "#${purchase.id}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseReturnFormColor)
                                Text(purchase.supplierName ?: "Supplier", fontSize = 12.sp, color = TextMain)
                            }
                            if (state.returnContextLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PurchaseReturnFormColor)
                            } else {
                                Text("${state.purchaseReturns.size} previous", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniMetric("Invoice", moneyReturn((purchase.netAmount ?: 0.0) + (purchase.returnAmount ?: 0.0)), Modifier.weight(1f))
                            MiniMetric("Returned", moneyReturn(purchase.returnAmount ?: 0.0), Modifier.weight(1f))
                            MiniMetric("Max refund", moneyReturn(state.maxRefundAmount), Modifier.weight(1f))
                        }
                    }
                }
            }

            if (state.items.isNotEmpty()) {
                ReturnSection(Icons.Outlined.AssignmentReturn, "Return items")
                state.items.forEachIndexed { i, item ->
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, if (item.qty > 0) PurchaseReturnFormColor.copy(0.4f) else BorderColor)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.productName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                    Text("Bought ${item.originalQty} | Returned ${item.alreadyReturned} | Can return ${item.maxQty}", fontSize = 11.sp, color = TextMuted)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { vm.setItemQty(i, item.qty - 1) }, modifier = Modifier.size(40.dp).background(if (item.qty > 0) PurchaseReturnFormBg else ScreenBg, RoundedCornerShape(8.dp))) {
                                        Text("-", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (item.qty > 0) PurchaseReturnFormColor else TextMuted)
                                    }
                                    Text("${item.qty}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (item.qty > 0) PurchaseReturnFormColor else TextMuted, modifier = Modifier.widthIn(min = 28.dp))
                                    IconButton(onClick = { vm.setItemQty(i, item.qty + 1) }, modifier = Modifier.size(40.dp).background(if (item.qty < item.maxQty) PurchaseReturnFormBg else ScreenBg, RoundedCornerShape(8.dp))) {
                                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (item.qty < item.maxQty) PurchaseReturnFormColor else TextMuted)
                                    }
                                }
                            }
                            if (item.qty > 0) {
                                Surface(color = PurchaseReturnFormBg, shape = RoundedCornerShape(6.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Return value", fontSize = 11.sp, color = PurchaseReturnFormColor)
                                        Text(moneyReturn(item.qty * item.unitPrice), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PurchaseReturnFormColor)
                                    }
                                }
                            }
                            if (item.hasSerial && item.qty > 0) {
                                Text("S/N: ${item.serialNumbers.joinToString(", ")}", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }
                val total = state.items.sumOf { it.qty * it.unitPrice }
                if (total > 0) {
                    Surface(color = PurchaseReturnFormBg, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ပြန်ပို့တန်ဖိုး စုစုပေါင်း", fontSize = 13.sp, color = PurchaseReturnFormColor, fontWeight = FontWeight.Bold)
                            Text(moneyReturn(total), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseReturnFormColor)
                        }
                    }
                }
                ReturnSection(Icons.Outlined.Payments, "Supplier Refund")
                Text("Refund cannot exceed supplier credit: ${moneyReturn(state.maxRefundAmount)}", fontSize = 11.sp, color = TextMuted)
                OutlinedTextField(
                    value = state.refundAmountStr,
                    onValueChange = { vm.setRefundAmount(it) },
                    label = { Text("Supplier ထံမှ ငွေလက်ခံ (Ks)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { showPmSheet = true }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(state.selectedPm?.methodName ?: "ငွေလက်ခံနည်း ရွေးပါ", color = if (state.selectedPm != null) TextMain else TextMuted, fontSize = 13.sp)
                        Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                OutlinedTextField(
                    value = state.transactionNo,
                    onValueChange = { vm.setTransactionNo(it) },
                    label = { Text("Transaction No (optional)") },
                    leadingIcon = { Icon(Icons.Outlined.Receipt, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Button(
                    onClick = vm::addSplitRefund,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PurchaseReturnFormColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add split refund")
                }
                state.splitRefunds.forEachIndexed { index, payment ->
                    Surface(color = PurchaseReturnFormBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF99F6E4))) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(payment.paymentMethodName ?: "Refund", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(moneyReturn(payment.amount ?: 0.0), color = PurchaseReturnFormColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            IconButton(onClick = { vm.removeSplitRefund(index) }) {
                                Icon(Icons.Outlined.Delete, null, tint = Danger)
                            }
                        }
                    }
                }
            }

            ReturnSection(Icons.Outlined.Notes, "အကြောင်းအရင်း *")
            OutlinedTextField(
                value = state.reason,
                onValueChange = { vm.setReason(it) },
                label = { Text("ပြန်ပို့သောအကြောင်း *") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            state.saveError?.let {
                Surface(color = DangerBg, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(18.dp))
                        Text(it, fontSize = 13.sp, color = Danger, modifier = Modifier.weight(1f))
                    }
                }
            }

            Button(
                onClick = { vm.save { ret -> ret.id?.let(onSuccess) } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurchaseReturnFormColor),
                enabled = !state.saving && !state.returnContextLoading && state.returnContextLoaded
            ) {
                if (state.saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ဝယ်ပြန်ပို့ သိမ်းမည်", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = PurchaseReturnFormBg, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, fontSize = 10.sp, color = TextMuted, maxLines = 1)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseReturnFormColor, maxLines = 1)
        }
    }
}

@Composable
private fun ReturnSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = PurchaseReturnFormColor, modifier = Modifier.size(18.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PurchaseReturnFormColor)
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
    }
}

private fun moneyReturn(v: Double): String = "%,.0f Ks".format(v)
