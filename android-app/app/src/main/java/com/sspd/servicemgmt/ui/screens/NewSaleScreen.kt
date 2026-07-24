package com.sspd.servicemgmt.ui.screens

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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.api.CustomerDTO
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.ProductDTO
import com.sspd.servicemgmt.api.StaffDTO
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.CartItem
import com.sspd.servicemgmt.ui.viewmodel.NewSaleViewModel
import com.sspd.servicemgmt.utils.fmtWarranty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSaleScreen(
    onBack:    () -> Unit,
    onSuccess: (Int) -> Unit   // navigate to SaleDetail with new sale id
) {
    val vm: NewSaleViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var errorDialog  by remember { mutableStateOf("") }
    var successSale  by remember { mutableStateOf<com.sspd.servicemgmt.api.SaleDTO?>(null) }
    var showSaleDatePicker by remember { mutableStateOf(false) }

    // Snackbar errors
    LaunchedEffect(state.scanError)   { state.scanError?.let   { snackbarHostState.showSnackbar(it); vm.clearScanError() } }
    LaunchedEffect(state.serialError) { state.serialError?.let { snackbarHostState.showSnackbar(it); vm.clearSerialError() } }

    // Error dialog (credit / validation)
    if (errorDialog.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { errorDialog = "" },
            icon  = { Icon(Icons.Outlined.Warning, null, tint = Danger, modifier = Modifier.size(28.dp)) },
            title = { Text("ရောင်းချ၍ မရပါ", fontWeight = FontWeight.ExtraBold, color = Danger) },
            text  = { Text(errorDialog, fontSize = 14.sp, lineHeight = 22.sp) },
            confirmButton = {
                Button(onClick = { errorDialog = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Success dialog
    successSale?.let { sale ->
        AlertDialog(
            onDismissRequest = {},
            icon  = { Icon(Icons.Outlined.CheckCircle, null, tint = Success, modifier = Modifier.size(32.dp)) },
            title = { Text("ရောင်းချမှု အောင်မြင်ပါသည်", fontWeight = FontWeight.ExtraBold, color = Success) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sale Code: ${sale.saleCode ?: "#${sale.id}"}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("ဖောက်သည်: ${sale.customerName ?: "—"}", fontSize = 13.sp)
                    Text("စုစုပေါင်း: ${sale.netAmount.fmtS()} Ks", fontSize = 13.sp, color = Primary, fontWeight = FontWeight.Bold)
                    if ((sale.dueAmount ?: 0.0) > 0)
                        Text("ကျန်ငွေ: ${sale.dueAmount.fmtS()} Ks", fontSize = 13.sp, color = Danger, fontWeight = FontWeight.Bold)
                    else
                        Text("ငွေအပြည့်ပေးပြီး ✓", fontSize = 13.sp, color = Success, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(
                    onClick = { sale.id?.let { onSuccess(it) }; successSale = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("အသေးစိတ်ကြည့်မည်", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { successSale = null; onBack() }) {
                    Text("List သို့ပြန်မည်")
                }
            }
        )
    }

    // Computed totals
    val gross    = state.cart.sumOf { it.unitPrice * it.qty.toLong() }
    val lineDisc = state.cart.filterNot { it.foc }.sumOf { it.discountAmount }
    val focAmount = state.cart.filter { it.foc }.sumOf { it.unitPrice * it.qty.toLong() }
    val subtotal = maxOf(0L, gross - focAmount - lineDisc)
    val overallD = state.overallDiscount.toLongOrNull() ?: 0L
    val net      = maxOf(0L, subtotal - overallD)
    val paid     = state.paidAmount.toLongOrNull() ?: net
    val due      = maxOf(0L, net - paid)

    // Pickers
    if (state.showCustomerPicker) {
        PickerSheet(
            title    = "Customer ရွေးပါ",
            items    = state.customers,
            label    = { it.name },
            subLabel = { it.phone?.ifBlank { null } },
            onSelect = { vm.setCustomer(it); vm.dismissCustomerPicker() },
            onDismiss = { vm.dismissCustomerPicker() },
            extraHeader = {
                var showNew by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { showNew = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) {
                    Icon(Icons.Outlined.PersonAdd, null, tint = Primary)
                    Spacer(Modifier.width(8.dp))
                    Text("New Customer ဖန်တီးမည်", color = Primary, fontWeight = FontWeight.Bold)
                }
                if (showNew) {
                    var saveError by remember { mutableStateOf("") }
                    NewCustomerDialog(
                        onDismiss = { showNew = false; saveError = "" },
                        onSave    = { name, phone, addr ->
                            vm.createCustomer(name, phone, addr) { err ->
                                if (err == null) {
                                    showNew = false
                                    vm.dismissCustomerPicker()
                                } else {
                                    saveError = err
                                }
                            }
                        }
                    )
                }
            }
        )
    }

    if (state.showStaffPicker) {
        PickerSheet(
            title    = "Staff ရွေးပါ",
            items    = state.staffList,
            label    = { it.name },
            onSelect = { vm.setStaff(it); vm.dismissStaffPicker() },
            onDismiss = { vm.dismissStaffPicker() }
        )
    }

    if (state.showPayPicker) {
        PickerSheet(
            title    = "ငွေပေးချေမှု ရွေးပါ",
            items    = state.paymentMethods,
            label    = { it.methodName },
            onSelect = { vm.setPayMethod(it); vm.dismissPayPicker() },
            onDismiss = { vm.dismissPayPicker() }
        )
    }

    if (showSaleDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = nsDateToMillis(state.saleDate)
        )
        DatePickerDialog(
            onDismissRequest = { showSaleDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setSaleDate(nsMillisToDate(it)) }
                    showSaleDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showSaleDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    if (state.showProductPicker) {
        PickerSheet(
            title    = "ကုန်ပစ္စည်း ရွေးပါ",
            items    = state.products,
            label    = { it.name },
            subLabel = { it.productCode },
            onSelect = { vm.selectProductForSale(it) },
            onDismiss = { vm.dismissProductPicker() }
        )
    }

    state.serialSelectProduct?.let { serialProduct ->
        val serialError = state.serialSelectError
        ModalBottomSheet(
            onDismissRequest = { vm.dismissSerialSelector() },
            modifier = Modifier.fillMaxHeight(0.82f)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Serial number ရွေးပါ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                Spacer(Modifier.height(4.dp))
                Text(serialProduct.name, fontSize = 12.sp, color = TextMuted)
                Spacer(Modifier.height(12.dp))

                when {
                    state.serialSelectLoading -> {
                        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary)
                        }
                    }
                    serialError != null -> {
                        Surface(color = DangerBg, shape = RoundedCornerShape(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(18.dp))
                                Text(serialError, fontSize = 13.sp, color = Danger, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    else -> {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                            items(state.serialSelectOptions) { serial ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.selectSerialForSale(serial) }
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(36.dp).background(VioletBg, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.QrCode2, null, tint = Violet, modifier = Modifier.size(18.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(serial.serialNumber, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                                        val sub = listOfNotNull(
                                            serial.condition?.takeIf { it.isNotBlank() },
                                            fmtWarranty(serial.warrantyMonths).takeIf { it.isNotEmpty() }?.let { "🛡 $it" }
                                        ).joinToString(" • ")
                                        if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = TextMuted)
                                    }
                                    Surface(color = SuccessBg, shape = RoundedCornerShape(8.dp)) {
                                        Text("ရွေးမည်", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Success)
                                    }
                                }
                                HorizontalDivider(color = BorderColor)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { vm.dismissSerialSelector() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("မရွေးတော့ပါ", color = TextMuted) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("ရောင်းချမှု အသစ်", fontWeight = FontWeight.ExtraBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
                )
            }
        ) { padding ->
            if (state.loading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    AppLoading()
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Sale Info ─────────────────────────────────────────
                item {
                    SectionCard(title = "SALE INFO") {
                        PickerRow("Customer", state.selectedCustomer?.name, state.selectedCustomer?.phone) { vm.showCustomerPicker() }

                        // Credit status banner
                        state.selectedCustomer?.let { c ->
                            val term = state.creditTerm
                            val (bg, color, icon, msg) = when {
                                c.blacklisted == true      -> CreditBanner(DangerBg,  Danger,  Icons.Outlined.Block,       "Blacklist — Cash sale ကိုသာ ခွင့်ပြုသည်")
                                c.creditHold == true       -> CreditBanner(WarningBg, Warning, Icons.Outlined.PauseCircle, "Credit Hold — Cash sale ကိုသာ ခွင့်ပြုသည်")
                                state.creditTermLoading    -> CreditBanner(BorderColor, TextMuted, Icons.Outlined.HourglassTop, "Credit term စစ်ဆေးနေသည်...")
                                term == null               -> CreditBanner(DangerBg,  Danger,  Icons.Outlined.ErrorOutline,"Credit Terms မသတ်မှတ်ရသေးပါ — Cash sale ကိုသာ ခွင့်ပြုသည်")
                                term.creditAllowed != true -> CreditBanner(DangerBg,  Danger,  Icons.Outlined.Block,       "Credit ခွင့်မပြုပါ — Cash sale ကိုသာ ခွင့်ပြုသည်")
                                else -> {
                                    val limitStr = term.creditLimit?.let { " | Limit: ${String.format("%,.0f", it)} Ks" } ?: ""
                                    val daysStr  = term.creditDays?.let { " | ${it} ရက်" } ?: ""
                                    CreditBanner(SuccessBg, Success, Icons.Outlined.CheckCircle, "Credit ခွင့်ပြုသည်$daysStr$limitStr")
                                }
                            }
                            Surface(color = bg, shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
                                    Text(msg, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                                }
                            }
                        }

                        HorizontalDivider(color = BorderColor, modifier = Modifier.padding(top = 8.dp))
                        PickerRow("Staff", state.selectedStaff?.name) { vm.showStaffPicker() }
                        PickerRow(
                            label = "Sale Date",
                            value = state.saleDate,
                            sub = if (state.hasBackdatePermission) null else "Back date requires permission"
                        ) { showSaleDatePicker = true }
                    }
                }

                // ── Items ─────────────────────────────────────────────
                item {
                    SectionCard(title = "ITEMS${if (state.cart.isNotEmpty()) " (${state.cart.size})" else ""}",
                        headerExtra = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.scanLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                                } else {
                                    Button(
                                        onClick = { vm.showProductScanner() },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                    ) { Text("📷 Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
                                OutlinedButton(
                                    onClick = { vm.showProductPicker() },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Primary)
                                ) { Text("+ Search", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary) }
                            }
                        }
                    ) {
                        if (state.cart.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.ShoppingCart, null, tint = TextMuted, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text("Scan or Search to add items", fontSize = 13.sp, color = TextMuted)
                                }
                            }
                        } else {
                            state.cart.forEachIndexed { idx, item ->
                                CartItemRow(
                                    item       = item,
                                    idx        = idx,
                                    onRemove   = { vm.removeItem(idx) },
                                    onQtyPlus  = { vm.updateQty(idx, 1) },
                                    onQtyMinus = { vm.updateQty(idx, -1) },
                                    onDiscount = { v -> vm.updateDiscount(idx, v) },
                                    onFoc      = { checked -> vm.updateFoc(idx, checked) },
                                    onSerialScan   = { vm.showSerialScanner(idx) },
                                    onSerialRemove = { sn -> vm.removeSerial(idx, sn) },
                                    onSerialAdd    = { sn -> vm.addSerial(idx, sn) }
                                )
                                if (idx < state.cart.size - 1) HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }

                // ── Payment ───────────────────────────────────────────
                item {
                    SectionCard(title = "PAYMENT") {
                        PickerRow("Payment Method", state.selectedPayMethod?.methodName) { vm.showPayPicker() }
                        HorizontalDivider(color = BorderColor)
                        Spacer(Modifier.height(8.dp))
                        InputRow("Overall Discount (Ks)", state.overallDiscount) { vm.setOverallDiscount(it) }
                        Spacer(Modifier.height(8.dp))
                        // Paid amount — cannot exceed net
                        InputRow(
                            label       = "Paid Amount (Ks)",
                            value       = state.paidAmount,
                            placeholder = net.toString(),
                            onChange    = { input ->
                                val v = input.toLongOrNull()
                                if (v != null && v > net) {
                                    vm.setPaidAmount(net.toString())  // cap to net
                                } else {
                                    vm.setPaidAmount(input)
                                }
                            }
                        )
                        if (state.paidAmount.isNotBlank()) {
                            val paidVal = state.paidAmount.toLongOrNull() ?: 0L
                            if (paidVal > net) {
                                Text(
                                    "⚠ Net Amount (${net.fmtL()} Ks) ထက် မကျော်ရပါ",
                                    fontSize = 11.sp, color = Danger, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.paymentTransactionNo,
                            onValueChange = vm::setPaymentTransactionNo,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Transaction No (optional)", color = TextMuted) },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = vm::addSplitPayment,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add split payment")
                        }
                        state.splitPayments.forEachIndexed { index, payment ->
                            Spacer(Modifier.height(6.dp))
                            Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFBFDBFE))) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(payment.paymentMethodName ?: "Payment", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("${(payment.amount ?: 0.0).toLong().fmtL()} Ks", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    IconButton(onClick = { vm.removeSplitPayment(index) }) {
                                        Icon(Icons.Outlined.Delete, null, tint = Danger)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.remark,
                            onValueChange = { vm.setRemark(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Remark (optional)", color = TextMuted) },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }

                // ── Summary ───────────────────────────────────────────
                item {
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            SummaryRow("Gross Total",     "${gross.fmtL()} Ks",    TextMuted)
                            if (lineDisc > 0) SummaryRow("Line Discounts", "− ${lineDisc.fmtL()} Ks", Warning)
                            SummaryRow("Subtotal",        "${subtotal.fmtL()} Ks",  TextMain)
                            if (overallD > 0) SummaryRow("Overall Discount", "− ${overallD.fmtL()} Ks", Warning)
                            HorizontalDivider(color = BorderColor)
                            SummaryRow("Net Amount",      "${net.fmtL()} Ks",      Primary, bold = true)
                            SummaryRow("Paid",            "${paid.fmtL()} Ks",     Success)
                            if (due > 0) SummaryRow("ကျန်ငွေ",  "${due.fmtL()} Ks", Danger, bold = true)
                        }
                    }
                }

                // ── Submit ────────────────────────────────────────────
                item {
                    Button(
                        onClick = {
                            vm.submit(
                                onSuccess = { sale -> successSale = sale },
                                onError   = { msg -> errorDialog = msg }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                        enabled  = !state.submitting,
                        colors   = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (state.submitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("💾  Sale သိမ်းမည်", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // Product scanner
        if (state.showProductScanner) {
            BarcodeScannerView(
                onResult = { vm.onProductScan(it) },
                onClose  = { vm.dismissProductScanner() }
            )
        }

        // Serial scanner
        if (state.serialScanIdx != null) {
            val idx  = state.serialScanIdx!!
            val name = state.cart.getOrNull(idx)?.product?.name ?: ""
            BarcodeScannerView(
                onResult = { vm.onSerialScan(idx, it) },
                onClose  = { vm.dismissSerialScanner() }
            )
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title:       String,
    headerExtra: (@Composable RowScope.() -> Unit)? = null,
    content:     @Composable ColumnScope.() -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.6.sp)
                headerExtra?.let { Row(content = it) }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PickerRow(label: String, value: String?, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
            if (sub != null) Text(sub, fontSize = 11.sp, color = TextMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value ?: "ရွေးပါ...", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (value != null) TextMain else TextMuted)
            Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun InputRow(label: String, value: String, placeholder: String = "0", onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value           = value,
            onValueChange   = onChange,
            modifier        = Modifier.width(140.dp),
            placeholder     = { Text(placeholder, color = TextMuted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine      = true,
            shape           = RoundedCornerShape(8.dp),
            textStyle       = LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End)
        )
    }
}

@Composable
private fun CartItemRow(
    item:          CartItem,
    idx:           Int,
    onRemove:      () -> Unit,
    onQtyPlus:     () -> Unit,
    onQtyMinus:    () -> Unit,
    onDiscount:    (String) -> Unit,
    onFoc:         (Boolean) -> Unit,
    onSerialScan:  () -> Unit,
    onSerialRemove:(String) -> Unit,
    onSerialAdd:   (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(item.product.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = PrimaryLight, shape = RoundedCornerShape(5.dp)) {
                        Text(item.product.productCode, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                    }
                    if (item.product.hasSerial == true) {
                        Surface(color = Violet.copy(0.1f), shape = RoundedCornerShape(5.dp)) {
                            Text("Serial Tracked", modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Violet)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("${item.unitPrice.fmtL()} Ks × ${item.qty} = ${(item.unitPrice * item.qty).fmtL()} Ks",
                    fontSize = 12.sp, color = TextMuted)
                // FOC and per-line discount
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("FOC", fontSize = 12.sp, color = TextMain, fontWeight = FontWeight.ExtraBold)
                        Text("အခမဲ့ပေးမည့်ပစ္စည်း", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked = item.foc,
                        onCheckedChange = onFoc,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Success)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Discount (Ks)", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value           = if (item.discountAmount > 0) item.discountAmount.toString() else "",
                        onValueChange   = onDiscount,
                        enabled         = !item.foc,
                        modifier        = Modifier.width(120.dp),
                        placeholder     = { Text("0", color = TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine      = true,
                        shape           = RoundedCornerShape(7.dp),
                        textStyle       = LocalTextStyle.current.copy(
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            fontSize  = 13.sp
                        )
                    )
                }
                if (item.foc) {
                    Text("FOC • Net: 0 Ks", fontSize = 11.sp, color = Success, fontWeight = FontWeight.ExtraBold)
                } else if (item.discountAmount > 0) {
                    Text("Net: ${(item.unitPrice * item.qty - item.discountAmount).fmtL()} Ks",
                        fontSize = 11.sp, color = Success, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Remove button
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)
                    .background(DangerBg, RoundedCornerShape(7.dp))) {
                    Icon(Icons.Outlined.Close, "ဖယ်ရှားရန်", tint = Danger, modifier = Modifier.size(14.dp))
                }
                // Qty controls (non-serial only)
                if (item.product.hasSerial != true) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = onQtyMinus, modifier = Modifier.size(36.dp).background(PrimaryLight, RoundedCornerShape(7.dp))) {
                            Text("−", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                        }
                        Text("${item.qty}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                        IconButton(onClick = onQtyPlus, modifier = Modifier.size(36.dp).background(PrimaryLight, RoundedCornerShape(7.dp))) {
                            Text("+", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                        }
                    }
                }
            }
        }

        // Serial section
        if (item.product.hasSerial == true) {
            var serialInput by remember { mutableStateOf("") }

            Spacer(Modifier.height(8.dp))
            Surface(color = ScreenBg, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    // Header
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SERIAL (${item.serialNumbers.size} ခု)", fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                        if (item.serialNumbers.isEmpty())
                            Text("⚠ Required", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Danger)
                    }

                    // Serial chips
                    if (item.serialNumbers.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        item.serialNumbers.forEach { sn ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(color = SuccessBg, shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)) {
                                    Text(sn, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Success)
                                }
                                Spacer(Modifier.width(6.dp))
                                IconButton(onClick = { onSerialRemove(sn) }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Outlined.Close, "ဖယ်ရှားရန်", tint = Danger, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Manual text input
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = serialInput,
                            onValueChange = { serialInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Serial number ရိုက်ထည့်", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            leadingIcon = { Icon(Icons.Outlined.QrCode2, null, tint = TextMuted, modifier = Modifier.size(16.dp)) }
                        )
                        Button(
                            onClick = {
                                val sn = serialInput.trim()
                                if (sn.isNotBlank()) {
                                    onSerialAdd(sn)
                                    serialInput = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) { Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Scan button
                    OutlinedButton(
                        onClick = onSerialScan,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Primary),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, "ဘားကုဒ် ဖတ်ရန်", tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("📷  Scan Serial Number", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = if (bold) 14.sp else 13.sp,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (bold) color else TextMuted)
        Text(value, fontSize = if (bold) 15.sp else 13.sp,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold,
            color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PickerSheet(
    title:       String,
    items:       List<T>,
    label:       (T) -> String,
    subLabel:    (T) -> String? = { null },
    onSelect:    (T) -> Unit,
    onDismiss:   () -> Unit,
    extraHeader: (@Composable ColumnScope.() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    val filtered = items.filter { label(it).contains(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight(0.85f)) {
        Column(Modifier.fillMaxSize()) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextMain,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            extraHeader?.invoke(this)
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("ရှာဖွေရန်...") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = RoundedCornerShape(10.dp)
            )
            LazyColumn {
                items(filtered) { item ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onSelect(item) }
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                    ) {
                        Text(label(item), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                        subLabel(item)?.let { Text(it, fontSize = 12.sp, color = TextMuted) }
                    }
                    HorizontalDivider(color = BorderColor)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewCustomerDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name    by remember { mutableStateOf("") }
    var phone   by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var error   by remember { mutableStateOf("") }
    var saving  by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Customer", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = "" },
                    label = { Text("Name *") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    isError = name.isBlank() && error.isNotBlank()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true, shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Address") }, modifier = Modifier.fillMaxWidth(),
                    maxLines = 2, shape = RoundedCornerShape(10.dp)
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) { error = "Name ဖြည့်ပါ"; return@Button }
                    saving = true
                    onSave(name.trim(), phone.trim(), address.trim())
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun Long.fmtL() = String.format("%,d", this)
private fun Double?.fmtS() = String.format("%,.0f", this ?: 0.0)

private fun nsMillisToDate(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(millis))
}

private fun nsDateToMillis(dateStr: String): Long {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(dateStr)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}

private data class CreditBanner(
    val bg:    androidx.compose.ui.graphics.Color,
    val color: androidx.compose.ui.graphics.Color,
    val icon:  androidx.compose.ui.graphics.vector.ImageVector,
    val msg:   String
)
