package com.sspd.servicemgmt.ui.screens

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.ProductDTO
import com.sspd.servicemgmt.api.PurchaseDTO
import com.sspd.servicemgmt.api.StaffDTO
import com.sspd.servicemgmt.api.SupplierDTO
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.viewmodel.PurchaseFormViewModel
import com.sspd.servicemgmt.ui.viewmodel.netAmount
import com.sspd.servicemgmt.ui.viewmodel.totalAmount
import java.time.LocalDate

private val PurchaseColor = Color(0xFF0F766E)
private val PurchaseBg    = Color(0xFFECFDF5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseFormScreen(
    onBack:    () -> Unit,
    onSuccess: (PurchaseDTO) -> Unit
) {
    val vm: PurchaseFormViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    if (state.showSupplierPicker) {
        SupplierPickerSheet(
            query     = state.pickerQuery,
            suppliers = state.suppliers,
            onQuery   = vm::setPickerQuery,
            onSelect  = { vm.selectSupplier(it); vm.dismissPicker() },
            onDismiss = vm::dismissPicker
        )
    }
    if (state.showStaffPicker) {
        StaffPickerSheet(
            query     = state.pickerQuery,
            staff     = state.staff,
            onQuery   = vm::setPickerQuery,
            onSelect  = { vm.selectStaff(it); vm.dismissPicker() },
            onDismiss = vm::dismissPicker
        )
    }
    if (state.showPaymentPicker) {
        PaymentPickerSheet(
            query     = state.pickerQuery,
            methods   = state.paymentMethods,
            onQuery   = vm::setPickerQuery,
            onSelect  = { vm.selectPaymentMethod(it); vm.dismissPicker() },
            onDismiss = vm::dismissPicker
        )
    }
    if (state.showProductPicker) {
        PurchaseProductPickerSheet(
            query     = state.pickerQuery,
            products  = state.products,
            onQuery   = vm::setPickerQuery,
            onSelect  = vm::selectProduct,
            onDismiss = vm::dismissPicker
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဝယ်ယူမှု အသစ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = PurchaseColor,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = Color.White) {
                Button(
                    onClick  = { vm.save(onSuccess) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape   = RoundedCornerShape(12.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = PurchaseColor),
                    enabled = !state.saving && !state.loading
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("သိမ်းဆည်းရန်", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }

        // Keep line-entry cards full width. Splitting complex serial/product
        // cards into narrow landscape columns makes them wrap and grow taller.
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding      = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.saveError?.let { err ->
                item {
                    Surface(
                        color  = DangerBg,
                        shape  = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Danger.copy(0.3f))
                    ) {
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

            item {
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerField("ပေးသွင်းသူ", state.selectedSupplier?.name ?: "ရွေးရန်", Icons.Outlined.Storefront, vm::openSupplierPicker)
                        PickerField("ဝန်ထမ်း",    state.selectedStaff?.name    ?: "ရွေးရန်", Icons.Outlined.Person,    vm::openStaffPicker)
                        DateField("ဝယ်ယူသည့်ရက်", state.purchaseDate, vm::setPurchaseDate)
                        Text("ငွေချေကာလ", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0 to "Cash", 7 to "7", 15 to "15", 30 to "30", 45 to "45", 60 to "60").forEach { (days, label) ->
                                FilterChip(
                                    selected = state.paymentTermDays == days,
                                    onClick = { vm.setPaymentTermDays(days) },
                                    label = { Text(label, fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text("Supplier default: ${state.selectedSupplier?.defaultCreditDays ?: 30} ရက်", fontSize = 10.sp, color = PurchaseColor)
                        DateField("ပေးရန်ရက်", state.dueDate, vm::setDueDate, optional = true)
                        OutlinedTextField(
                            value         = state.remark,
                            onValueChange = vm::setRemark,
                            modifier      = Modifier.fillMaxWidth(),
                            label         = { Text("မှတ်ချက်") },
                            minLines      = 2,
                            shape         = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("ပစ္စည်းများ (${state.lines.size})", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = TextMain)
                    OutlinedButton(
                        onClick        = vm::openProductPicker,
                        shape          = RoundedCornerShape(10.dp),
                        border         = BorderStroke(1.dp, PurchaseColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = PurchaseColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ထည့်ရန်", fontSize = 12.sp, color = PurchaseColor)
                    }
                }
            }

            if (state.lines.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.openProductPicker() }
                            .background(PurchaseBg, RoundedCornerShape(12.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.AddShoppingCart, null, tint = PurchaseColor, modifier = Modifier.size(40.dp))
                            Text("ဝယ်ယူမည့် ပစ္စည်းထည့်ပါ", color = PurchaseColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                itemsIndexed(state.lines) { index, line ->
                    PurchaseLineCard(
                        index                 = index,
                        line                  = line,
                        isAutoAssigning       = state.autoAssigningIndex == index,
                        onQty                 = { vm.setLineQty(index, it) },
                        onCost                = { vm.setLineCost(index, it) },
                        onWarranty            = { vm.setLineWarranty(index, it) },
                        onSerialChange        = { sIdx, v -> vm.setLineSerial(index, sIdx, v) },
                        onConditionChange     = { sIdx, v -> vm.setLineCondition(index, sIdx, v) },
                        onWarrantyItem        = { sIdx, v -> vm.setLineWarrantyItem(index, sIdx, v) },
                        onSerialPhoto         = { sIdx, b64 -> vm.setLineSerialPhoto(index, sIdx, b64) },
                        onAutoAssign          = { vm.autoAssignSerials(index) },
                        onToggleAssignSerials = { vm.toggleAssignSerials(index) },
                        onRemove              = { vm.removeLine(index) }
                    )
                }
            }

            item {
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val gross = totalAmount(state.lines)
                        val net   = netAmount(state.lines, state.discountAmount, state.taxAmount, state.otherCharges)
                        val paid  = state.paidAmount.toDoubleOrNull() ?: 0.0
                        val due   = maxOf(0.0, net - paid)

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("မူလစုစုပေါင်း", color = TextMuted, fontSize = 12.sp)
                            Text(money(gross), color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        OutlinedTextField(
                            value           = state.discountAmount,
                            onValueChange   = vm::setDiscountAmount,
                            modifier        = Modifier.fillMaxWidth(),
                            label           = { Text("လျှော့ငွေ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine      = true,
                            shape           = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value           = state.taxAmount,
                            onValueChange   = vm::setTaxAmount,
                            modifier        = Modifier.fillMaxWidth(),
                            label           = { Text("အခွန် / VAT") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine      = true,
                            shape           = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value           = state.otherCharges,
                            onValueChange   = vm::setOtherCharges,
                            modifier        = Modifier.fillMaxWidth(),
                            label           = { Text("အခြားကုန်ကျစရိတ်") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine      = true,
                            shape           = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value         = state.supplierInvoiceNo,
                            onValueChange = vm::setSupplierInvoiceNo,
                            modifier      = Modifier.fillMaxWidth(),
                            label         = { Text("ပေးသွင်းသူ Invoice No.") },
                            singleLine    = true,
                            shape         = RoundedCornerShape(10.dp)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ကျသင့်ငွေ", color = PurchaseColor, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            Text(money(net),   color = PurchaseColor, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        }
                        OutlinedTextField(
                            value           = state.paidAmount,
                            onValueChange   = vm::setPaidAmount,
                            modifier        = Modifier.fillMaxWidth(),
                            label           = { Text("ပေးပြီးငွေ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine      = true,
                            shape           = RoundedCornerShape(10.dp)
                        )
                        PickerField(
                            "ငွေပေးချေနည်း",
                            state.selectedPaymentMethod?.methodName ?: "ပေးပြီးငွေရှိလျှင် ရွေးရန်",
                            Icons.Outlined.Payments,
                            vm::openPaymentPicker
                        )
                        OutlinedTextField(
                            value         = state.paymentTransactionNo,
                            onValueChange = vm::setPaymentTransactionNo,
                            modifier      = Modifier.fillMaxWidth(),
                            label         = { Text("ငွေလွှဲနံပါတ်") },
                            singleLine    = true,
                            shape         = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick  = vm::addSplitPayment,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(containerColor = PurchaseColor),
                            shape    = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ခွဲပေးငွေ ထည့်ရန်")
                        }
                        state.splitPayments.forEachIndexed { i, payment ->
                            Surface(
                                color  = PurchaseBg,
                                shape  = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF99F6E4))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(payment.paymentMethodName ?: "ငွေပေးချေနည်း", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(money(payment.amount ?: 0.0), color = PurchaseColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    IconButton(onClick = { vm.removeSplitPayment(i) }) {
                                        Icon(Icons.Outlined.Delete, null, tint = Danger)
                                    }
                                }
                            }
                        }
                        Surface(
                            color = if (due > 0) DangerBg else PurchaseBg,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    if (due > 0) "ပေးရန်ကျန်" else "အပြည့်ပေးပြီး",
                                    color = if (due > 0) Danger else PurchaseColor,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    money(due),
                                    color = if (due > 0) Danger else PurchaseColor,
                                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ─── Purchase line card ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseLineCard(
    index:                 Int,
    line:                  PurchaseFormViewModel.PurchaseLine,
    isAutoAssigning:       Boolean,
    onQty:                 (String) -> Unit,
    onCost:                (String) -> Unit,
    onWarranty:            (String) -> Unit,
    onSerialChange:        (Int, String) -> Unit,
    onConditionChange:     (Int, String) -> Unit,
    onWarrantyItem:        (Int, String) -> Unit,
    onSerialPhoto:         (Int, String) -> Unit,
    onAutoAssign:          () -> Unit,
    onToggleAssignSerials: () -> Unit,
    onRemove:              () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var photoTargetIdx by remember { mutableStateOf(-1) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && photoTargetIdx >= 0) {
            val target = photoTargetIdx
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes  = stream.readBytes()
                        val bmp    = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                        val maxW   = 800
                        val scaled = if (bmp.width > maxW) {
                            val ratio = maxW.toFloat() / bmp.width
                            Bitmap.createScaledBitmap(bmp, maxW, (bmp.height * ratio).toInt(), true)
                        } else bmp
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        withContext(Dispatchers.Main) { onSerialPhoto(target, b64) }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Product name + remove button
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${index + 1}. ${line.productName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMain)
                    Text(
                        line.productCode + when {
                            line.hasSerial     -> " • စီရီယယ်ခြေရာခံ"
                            line.assignSerials -> " • စီရီယယ်ထည့်ထား"
                            else               -> ""
                        },
                        fontSize = 11.sp, color = TextMuted
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.RemoveCircleOutline, "ဖယ်ရန်", tint = Danger, modifier = Modifier.size(18.dp))
                }
            }

            // Qty + Unit cost
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value           = line.qty,
                    onValueChange   = onQty,
                    modifier        = Modifier.weight(1f),
                    label           = { Text("အရေအတွက်") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    shape           = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value           = line.unitCost,
                    onValueChange   = onCost,
                    modifier        = Modifier.weight(1f),
                    label           = { Text("တစ်ခုဝယ်ဈေး") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine      = true,
                    shape           = RoundedCornerShape(10.dp)
                )
            }

            // Warranty — single field only for non-serial items; serial items use per-row warranty
            if (!line.hasSerial && !line.assignSerials) {
                OutlinedTextField(
                    value           = line.warrantyMonths,
                    onValueChange   = onWarranty,
                    modifier        = Modifier.fillMaxWidth(),
                    label           = { Text("အာမခံလ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    shape           = RoundedCornerShape(10.dp)
                )
            }

            // ── Orphaned stock warning ─────────────────────────────────────────
            if (line.hasSerial && line.orphanedQty > 0) {
                Surface(
                    color  = Color(0xFFFFFBEB),
                    shape  = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                        Text(
                            "လက်ကျန် ${line.orphanedQty} ခု စီရီယယ်နဲ့ မချိတ်ဆက်ထားသေးပါ",
                            fontSize = 11.sp, color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // ── Assign-serials toggle (non-serial products only) ───────────────
            if (!line.hasSerial) {
                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("စီရီယယ်ခြေရာခံမည်", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                        Text("ဤပစ္စည်းကို စီရီယယ်နံပါတ်ဖြင့် မှတ်တမ်းတင်မည်", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked         = line.assignSerials,
                        onCheckedChange = { onToggleAssignSerials() },
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor  = Color.White,
                            checkedTrackColor  = PurchaseColor
                        )
                    )
                }
            }

            // ── Serial + Condition section ─────────────────────────────────────
            if (line.hasSerial || line.assignSerials) {
                val qty         = line.qty.toIntOrNull() ?: 0
                val filledCount = line.serials.count { it.isNotBlank() }
                val allFilled   = qty > 0 && filledCount == qty

                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

                // Header row: status + auto-assign button
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(
                            Icons.Outlined.Tag,
                            null,
                            tint     = if (allFilled) Color(0xFF059669) else Color(0xFFDC2626),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "စီရီယယ်နံပါတ်များ ($filledCount / $qty)",
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color      = if (allFilled) Color(0xFF059669) else Color(0xFFDC2626)
                        )
                    }
                    Button(
                        onClick        = onAutoAssign,
                        enabled        = !isAutoAssigning,
                        colors         = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEEF2FF),
                            contentColor   = Color(0xFF4F46E5)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier       = Modifier.height(30.dp)
                    ) {
                        if (isAutoAssigning) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFF4F46E5))
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("အလိုအလျောက်", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Column labels
                if (qty > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Spacer(Modifier.width(26.dp))
                        Text("စီရီယယ်နံပါတ်",  fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.6f))
                        Text("အခြေအနေ",  fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                }

                // Per-serial input rows
                repeat(qty) { sIdx ->
                    val sn       = line.serials.getOrElse(sIdx)       { "" }
                    val cond     = line.conditions.getOrElse(sIdx)    { "" }
                    val warItem  = line.warranties.getOrElse(sIdx)    { "" }
                    val hasPhoto = line.serialPhotos.getOrElse(sIdx)  { "" }.isNotBlank()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Row 1: serial + condition
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                "#${sIdx + 1}",
                                fontSize   = 10.sp,
                                color      = TextMuted,
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.width(26.dp)
                            )
                            OutlinedTextField(
                                value         = sn,
                                onValueChange = { onSerialChange(sIdx, it) },
                                modifier      = Modifier.weight(1.6f),
                                label         = { Text("စီရီယယ်", fontSize = 10.sp) },
                                singleLine    = true,
                                shape         = RoundedCornerShape(8.dp),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Color(0xFF4F46E5),
                                    unfocusedBorderColor = if (sn.isBlank()) Color(0xFFEF4444) else BorderColor
                                )
                            )
                            OutlinedTextField(
                                value         = cond,
                                onValueChange = { onConditionChange(sIdx, it) },
                                modifier      = Modifier.weight(1f),
                                label         = { Text("အခြေအနေ", fontSize = 10.sp) },
                                placeholder   = { Text("အသစ် / သုံးပြီး", fontSize = 9.sp, color = TextMuted) },
                                singleLine    = true,
                                shape         = RoundedCornerShape(8.dp),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Color(0xFFF59E0B),
                                    unfocusedBorderColor = Color(0xFFFDE68A)
                                )
                            )
                        }
                        // Row 2: warranty + photo
                        Row(
                            Modifier.fillMaxWidth().padding(start = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value           = warItem,
                                onValueChange   = { onWarrantyItem(sIdx, it) },
                                modifier        = Modifier.weight(1f),
                                label           = { Text("အာမခံ (လ)", fontSize = 9.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine      = true,
                                shape           = RoundedCornerShape(8.dp)
                            )
                            IconButton(
                                onClick  = { photoTargetIdx = sIdx; photoPicker.launch("image/*") },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    if (hasPhoto) Icons.Outlined.PhotoCamera else Icons.Outlined.CameraAlt,
                                    contentDescription = "ဓာတ်ပုံ",
                                    tint     = if (hasPhoto) Color(0xFF059669) else TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ပစ္စည်းတန်ဖိုး", color = TextMuted, fontSize = 12.sp)
                Text(
                    money((line.qty.toIntOrNull() ?: 0) * (line.unitCost.toDoubleOrNull() ?: 0.0)),
                    color = PurchaseColor, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── Picker field ─────────────────────────────────────────────────────────────

@Composable
private fun PickerField(
    label:   String,
    value:   String,
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape    = RoundedCornerShape(10.dp),
        color    = Color.White,
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = PurchaseColor, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 10.sp, color = TextMuted)
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = TextMuted)
        }
    }
}

@Composable
private fun DateField(
    label:    String,
    value:    String,
    onChange: (String) -> Unit,
    optional: Boolean = false
) {
    val context = LocalContext.current
    val parsed  = runCatching {
        if (value.isBlank()) LocalDate.now() else LocalDate.parse(value)
    }.getOrDefault(LocalDate.now())
    val picker  = remember(value) {
        DatePickerDialog(
            context,
            { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d).toString()) },
            parsed.year, parsed.monthValue - 1, parsed.dayOfMonth
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { picker.show() },
        shape    = RoundedCornerShape(10.dp),
        color    = Color.White,
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = PurchaseColor, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 10.sp, color = TextMuted)
                Text(
                    value.ifBlank { if (optional) "မထားလည်းရ" else LocalDate.now().toString() },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMain
                )
            }
            if (optional && value.isNotBlank()) {
                IconButton(onClick = { onChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = TextMuted)
            }
        }
    }
}

// ─── Bottom sheet pickers ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierPickerSheet(
    query: String, suppliers: List<SupplierDTO>,
    onQuery: (String) -> Unit, onSelect: (SupplierDTO) -> Unit, onDismiss: () -> Unit
) {
    PickerSheet("ပေးသွင်းသူ ရွေးရန်", query, onQuery, onDismiss) {
        suppliers
            .filter { it.name.contains(query, true) || (it.phone ?: "").contains(query, true) }
            .forEach { SheetRow(it.name, it.phone ?: "") { onSelect(it) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffPickerSheet(
    query: String, staff: List<StaffDTO>,
    onQuery: (String) -> Unit, onSelect: (StaffDTO) -> Unit, onDismiss: () -> Unit
) {
    PickerSheet("ဝန်ထမ်း ရွေးရန်", query, onQuery, onDismiss) {
        staff
            .filter { it.name.contains(query, true) || it.role.contains(query, true) }
            .forEach { SheetRow(it.name, it.role) { onSelect(it) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentPickerSheet(
    query: String, methods: List<PaymentMethodDTO>,
    onQuery: (String) -> Unit, onSelect: (PaymentMethodDTO) -> Unit, onDismiss: () -> Unit
) {
    PickerSheet("ငွေပေးချေနည်း ရွေးရန်", query, onQuery, onDismiss) {
        methods
            .filter { it.methodName.contains(query, true) }
            .forEach { SheetRow(it.methodName, "") { onSelect(it) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseProductPickerSheet(
    query: String, products: List<ProductDTO>,
    onQuery: (String) -> Unit, onSelect: (ProductDTO) -> Unit, onDismiss: () -> Unit
) {
    PickerSheet("ပစ္စည်း ရွေးရန်", query, onQuery, onDismiss) {
        products
            .filter { it.name.contains(query, true) || it.productCode.contains(query, true) }
            .take(30)
            .forEach { SheetRow(it.name, "${it.productCode} • လက်ကျန် ${it.stockQty}") { onSelect(it) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title:    String,
    query:    String,
    onQuery:  (String) -> Unit,
    onDismiss: () -> Unit,
    rows:     @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = TextMain)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value         = query,
                onValueChange = onQuery,
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("ရှာပါ...") },
                leadingIcon   = { Icon(Icons.Outlined.Search, null, tint = TextMuted) },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            rows()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = TextMuted)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = BorderColor, modifier = Modifier.size(16.dp))
    }
    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
}

private fun money(v: Double): String = "%,.0f Ks".format(v)
