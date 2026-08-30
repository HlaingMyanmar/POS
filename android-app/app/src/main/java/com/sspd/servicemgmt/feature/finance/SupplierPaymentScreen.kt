package com.sspd.servicemgmt.feature.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storefront
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.SupplierPayable
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppPickerSheet
import com.sspd.servicemgmt.core.ui.component.AppScaffold
import com.sspd.servicemgmt.core.ui.theme.*

import com.sspd.servicemgmt.core.util.PreferenceManager

private val PayColor = Color(0xFF2563EB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierPaymentScreen(onBack: () -> Unit) {
    val vm: SupplierPaymentViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager(context) }
    val canCreatePayment = prefs.hasPermission("CAN_ACCESS_PAYMENT_TRANSACTION_CREATE")
    var showSupplierPicker by remember { mutableStateOf(false) }
    var showMethodPicker by remember { mutableStateOf(false) }
    var showCreditPurchasePicker by remember { mutableStateOf(false) }

    if (showSupplierPicker) {
        AppPickerSheet(
            title = "ပေးသွင်းသူ ရွေးရန်",
            items = state.suppliers,
            label = { it.name },
            subtitle = { it.phone ?: it.code },
            onSelect = { vm.selectSupplier(it); showSupplierPicker = false },
            onDismiss = { showSupplierPicker = false },
            key = { i, s -> s.id.takeIf { it != 0 } ?: "sup-$i-${s.name}" }
        )
    }
    if (showMethodPicker) {
        AppPickerSheet(
            title = "ငွေပေးချေနည်း ရွေးရန်",
            items = state.paymentMethods,
            label = { it.methodName },
            onSelect = { vm.selectPaymentMethod(it); showMethodPicker = false },
            onDismiss = { showMethodPicker = false },
            key = { i, m -> m.id.takeIf { it != 0 } ?: "pm-$i" }
        )
    }
    if (showCreditPurchasePicker) {
        AppPickerSheet(
            title = "ပေးရန်ကျန် ရွေးရန်",
            items = state.payables,
            label = { it.purchaseCode ?: "#${it.purchaseId}" },
            subtitle = { "ကျန် ${money(it.dueAmount ?: 0.0)}" },
            onSelect = { vm.setCreditPurchaseId(it.purchaseId); showCreditPurchasePicker = false },
            onDismiss = { showCreditPurchasePicker = false },
            key = { i, p -> p.purchaseId ?: "pay-$i" }
        )
    }

    AppScaffold(
        title = "ပေးသွင်းသူ ငွေချေမှု",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                vm.clearMessages()
                state.selectedSupplier?.id?.let { vm.loadSupplierData(it) } ?: vm.loadMasters()
            }) { Icon(Icons.Outlined.Refresh, "ပြန်ဖတ်ရန်", tint = OnPrimary) }
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@AppScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.error?.let { err ->
                item { Text(err, color = Danger, fontSize = 12.sp) }
            }
            state.successMessage?.let { msg ->
                item { Text(msg, color = Color(0xFF059669), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerRow(
                            label = "ပေးသွင်းသူ",
                            value = state.selectedSupplier?.name ?: "ရွေးရန်",
                            icon = Icons.Outlined.Storefront,
                            onClick = { showSupplierPicker = true }
                        )
                        PickerRow(
                            label = "ငွေပေးချေနည်း",
                            value = state.selectedPaymentMethod?.methodName ?: "ရွေးရန်",
                            icon = Icons.Outlined.Payments,
                            onClick = { showMethodPicker = true }
                        )
                        OutlinedTextField(
                            value = state.amount,
                            onValueChange = vm::setAmount,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ပမာဏ (FIFO)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = state.transactionNo,
                            onValueChange = vm::setTransactionNo,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Txn No (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = state.remark,
                            onValueChange = vm::setRemark,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("မှတ်ချက်") },
                            minLines = 2,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick = { vm.pay() },
                            enabled = canCreatePayment && !state.busy && state.selectedSupplier != null,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PayColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (state.busy) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Pay (FIFO)", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            item {
                Text("Payables", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = TextMain)
            }
            if (state.selectedSupplier == null) {
                item { Text("Supplier ရွေးပါ", color = TextMuted, fontSize = 12.sp) }
            } else if (state.payables.isEmpty()) {
                item { Text("Payable မရှိပါ", color = TextMuted, fontSize = 12.sp) }
            } else {
                items(state.payables, key = { it.purchaseId ?: it.hashCode() }) { payable ->
                    PayableCard(payable)
                }
            }

            val available = state.creditSummary?.availableCredit ?: 0.0
            if (available > 0 && state.selectedSupplier != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Apply Credit", fontWeight = FontWeight.ExtraBold, color = Color(0xFF4338CA))
                            Text(
                                "Available ${money(available)}",
                                fontSize = 12.sp,
                                color = Color(0xFF4F46E5),
                                fontWeight = FontWeight.Bold
                            )
                            val creditLabel = state.payables
                                .firstOrNull { it.purchaseId == state.creditPurchaseId }
                                ?.let { "${it.purchaseCode ?: "#${it.purchaseId}"} · due ${money(it.dueAmount ?: 0.0)}" }
                                ?: "Payable ရွေးရန်"
                            PickerRow(
                                label = "Apply to purchase",
                                value = creditLabel,
                                icon = Icons.Outlined.Payments,
                                onClick = { showCreditPurchasePicker = true }
                            )
                            OutlinedTextField(
                                value = state.creditAmount,
                                onValueChange = vm::setCreditAmount,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Credit amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = state.creditReason,
                                onValueChange = vm::setCreditReason,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Reason (optional)") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            Button(
                                onClick = { vm.applyCredit() },
                                enabled = canCreatePayment && !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Apply Credit", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            if (state.payments.isNotEmpty()) {
                item {
                    Text("Recent payments", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = TextMain)
                }
                itemsIndexed(state.payments.take(8), key = { index, it -> it.id ?: "pay-$index" }) { _, payment ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(payment.paymentNo ?: "#${payment.id}", fontWeight = FontWeight.Bold, color = PayColor)
                                Text(money(payment.totalAmount ?: 0.0), fontWeight = FontWeight.ExtraBold)
                            }
                            Text(
                                "${payment.paymentMethodName ?: "-"} · ${payment.paymentDate?.take(10) ?: "-"}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PayableCard(payable: SupplierPayable) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(payable.purchaseCode ?: "#${payable.purchaseId}", fontWeight = FontWeight.ExtraBold, color = TextMain)
                Text(money(payable.dueAmount ?: 0.0), fontWeight = FontWeight.ExtraBold, color = Danger)
            }
            Text(
                "Net ${money(payable.netAmount ?: 0.0)} · Paid ${money(payable.paidAmount ?: 0.0)}",
                fontSize = 11.sp,
                color = TextMuted
            )
            Text(
                "Due date ${payable.dueDate?.take(10) ?: "-"}",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = PayColor, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 10.sp, color = TextMuted)
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = TextMuted)
        }
    }
}

private fun money(v: Double): String = "%,.0f Ks".format(v)
