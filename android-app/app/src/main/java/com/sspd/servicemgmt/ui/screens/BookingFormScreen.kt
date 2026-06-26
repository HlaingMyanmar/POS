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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.utils.rememberIsTablet
import com.sspd.servicemgmt.ui.viewmodel.BookingFormViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingFormScreen(onBack: () -> Unit, onSuccess: () -> Unit) {
    val vm: BookingFormViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    var showShelfSheet by rememberSaveable { mutableStateOf(false) }

    // ── New Customer Dialog ───────────────────────────────────────────────────
    if (state.showNewCustomerDialog) {
        AlertDialog(
            onDismissRequest = { vm.dismissNewCustomerDialog() },
            icon  = { Icon(Icons.Outlined.PersonAdd, null, tint = Primary) },
            title = { Text("ဖောက်သည်အသစ် ထည့်ရန်", fontWeight = FontWeight.ExtraBold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = state.newCustomerName,
                        onValueChange = { vm.setNewCustomerName(it) },
                        label         = { Text("အမည် *") },
                        leadingIcon   = { Icon(Icons.Outlined.Person, null) },
                        singleLine    = true,
                        shape         = RoundedCornerShape(10.dp),
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value         = state.newCustomerPhone,
                        onValueChange = { vm.setNewCustomerPhone(it) },
                        label         = { Text("ဖုန်းနံပါတ်") },
                        leadingIcon   = { Icon(Icons.Outlined.Phone, null) },
                        singleLine    = true,
                        shape         = RoundedCornerShape(10.dp),
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction    = ImeAction.Done
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick  = { vm.createCustomer() },
                    enabled  = state.newCustomerName.isNotBlank() && !state.creatingCustomer,
                    colors   = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    if (state.creatingCustomer)
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else
                        Text("သိမ်းဆည်းမည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissNewCustomerDialog() }, enabled = !state.creatingCustomer) {
                    Text("ပယ်ဖျက်")
                }
            }
        )
    }

    if (showShelfSheet) {
        ModalBottomSheet(onDismissRequest = { showShelfSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("ကန့်တည်နေရာ ရွေးပါ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.selectShelf(null); showShelfSheet = false }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("— မသတ်မှတ်ပါ —", fontSize = 14.sp, color = TextMuted)
                    if (state.selectedShelf == null)
                        Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = BorderColor)
                state.shelfLocations.forEach { shelf ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectShelf(shelf); showShelfSheet = false }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(shelf.code, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            if (!shelf.label.isNullOrBlank())
                                Text(shelf.label, fontSize = 11.sp, color = TextMuted)
                        }
                        if (state.selectedShelf?.id == shelf.id)
                            Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BorderColor)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (vm.isEdit) "လက်ခံမှု ပြင်ဆင်ရန်" else "လက်ခံမှု အသစ်",
                        fontWeight = FontWeight.ExtraBold
                    )
                },
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

        val isTablet = rememberIsTablet()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTablet) 64.dp else 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── ဖောက်သည် ──────────────────────────────────────────────────────
            SectionHeader(Icons.Outlined.Person, "ဖောက်သည် အချက်အလက်")

            Column {
                OutlinedTextField(
                    value         = state.customerQuery,
                    onValueChange = { vm.setCustomerQuery(it) },
                    label         = { Text("ဖောက်သည် အမည် *") },
                    placeholder   = { Text("နာမည် ရှာပါ...") },
                    leadingIcon   = { Icon(Icons.Outlined.PersonSearch, null) },
                    trailingIcon  = {
                        if (state.selectedCustomer != null)
                            Icon(Icons.Outlined.CheckCircle, null, tint = Success, modifier = Modifier.size(20.dp))
                    },
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    shape           = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                val suggestions = if (state.customerQuery.length >= 1)
                    state.customers.filter { it.name.contains(state.customerQuery, true) }.take(5)
                else emptyList()

                if (state.customerQuery.isNotBlank() && state.selectedCustomer == null) {
                    Card(
                        shape  = RoundedCornerShape(0.dp, 0.dp, 12.dp, 12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        suggestions.forEach { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.selectCustomer(c) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Person, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                Column {
                                    Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                    if (!c.phone.isNullOrBlank())
                                        Text(c.phone, fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            HorizontalDivider(color = BorderColor)
                        }
                        // ── New Customer option ───────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.showNewCustomerDialog() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.PersonAdd, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Text(
                                "\"${state.customerQuery}\" ကို ဖောက်သည်အသစ် အဖြစ် ထည့်မည်",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary
                            )
                        }
                    }
                }
            }

            // ── ပစ္စည်းများ (Multiple Devices) ────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Devices, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text("ပစ္စည်းများ", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                    Surface(color = Primary.copy(0.12f), shape = RoundedCornerShape(10.dp)) {
                        Text(
                            "${state.devices.size} ခု",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Primary
                        )
                    }
                }
                OutlinedButton(
                    onClick       = { vm.addDevice() },
                    shape         = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border        = BorderStroke(1.dp, Primary)
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(14.dp), tint = Primary)
                    Spacer(Modifier.width(4.dp))
                    Text("ပစ္စည်း ထပ်ထည့်", fontSize = 12.sp, color = Primary)
                }
            }

            state.devices.forEachIndexed { index, device ->
                DeviceCard(
                    index    = index,
                    device   = device,
                    canRemove = state.devices.size > 1,
                    onRemove = { vm.removeDevice(index) },
                    onChange = { updated -> vm.updateDevice(index, updated) }
                )
            }

            // ── သိမ်းဆည်းနေရာ & ကုန်ကျငွေ ─────────────────────────────────────
            SectionHeader(Icons.Outlined.LocationOn, "သိမ်းဆည်းနေရာ & ကုန်ကျငွေ")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showShelfSheet = true },
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val shelf = state.selectedShelf
                        Icon(Icons.Outlined.LocationOn, null, tint = if (shelf != null) Violet else TextMuted, modifier = Modifier.size(18.dp))
                        if (shelf != null) {
                            Column {
                                Text(shelf.code, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Violet)
                                if (!shelf.label.isNullOrBlank())
                                    Text(shelf.label, fontSize = 11.sp, color = TextMuted)
                            }
                        } else {
                            Text("ကန့်တည်နေရာ ရွေးပါ (optional)", fontSize = 13.sp, color = TextMuted)
                        }
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            OutlinedTextField(
                value           = state.totalAmount,
                onValueChange   = { vm.setTotalAmount(it) },
                label           = { Text("ခန့်မှန်းကုန်ကျငွေ (Ks)") },
                leadingIcon     = { Icon(Icons.Outlined.Payments, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier        = Modifier.fillMaxWidth(),
                singleLine      = true,
                shape           = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value           = state.remark,
                onValueChange   = { vm.setRemark(it) },
                label           = { Text("မှတ်ချက်") },
                modifier        = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                maxLines        = 4,
                shape           = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            // ── Error ──────────────────────────────────────────────────────────
            val saveError = state.saveError
            if (!saveError.isNullOrBlank()) {
                Surface(color = DangerBg, shape = RoundedCornerShape(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(18.dp))
                        Text(saveError, fontSize = 13.sp, color = Danger, modifier = Modifier.weight(1f))
                    }
                }
            }

            // ── Save ───────────────────────────────────────────────────────────
            Button(
                onClick  = { vm.save { onSuccess() } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled  = !state.saving
            ) {
                if (state.saving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (vm.isEdit) "ပြင်ဆင်မှု သိမ်းဆည်းမည်" else "လက်ခံမှု သိမ်းဆည်းမည်",
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Device Card ───────────────────────────────────────────────────────────────

@Composable
private fun DeviceCard(
    index:     Int,
    device:    BookingFormViewModel.DeviceDraft,
    canRemove: Boolean,
    onRemove:  () -> Unit,
    onChange:  (BookingFormViewModel.DeviceDraft) -> Unit
) {
    val deviceTypes = listOf("Phone", "Laptop", "Computer", "Tablet", "Printer", "Other")

    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.5.dp, if (index == 0) Primary.copy(0.4f) else BorderColor)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Primary.copy(0.12f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                    }
                    Text(
                        device.brand.ifBlank { "ပစ္စည်း ${index + 1}" },
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextMain
                    )
                }
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.RemoveCircleOutline, "ဖယ်ရှားရန်", tint = Danger, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Device type chips
            Column {
                Text("ပစ္စည်းအမျိုးအစား", fontSize = 11.sp, color = TextMuted)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    deviceTypes.take(3).forEach { t ->
                        FilterChip(
                            selected = device.deviceType == t,
                            onClick  = { onChange(device.copy(deviceType = if (device.deviceType == t) "" else t)) },
                            label    = { Text(t, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    deviceTypes.drop(3).forEach { t ->
                        FilterChip(
                            selected = device.deviceType == t,
                            onClick  = { onChange(device.copy(deviceType = if (device.deviceType == t) "" else t)) },
                            label    = { Text(t, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceField(
                    value    = device.brand,
                    onChange = { onChange(device.copy(brand = it)) },
                    label    = "Brand *",
                    hint     = "Apple, Samsung...",
                    modifier = Modifier.weight(1f)
                )
                DeviceField(
                    value    = device.model,
                    onChange = { onChange(device.copy(model = it)) },
                    label    = "Model",
                    hint     = "iPhone 14...",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceField(
                    value    = device.serialNumber,
                    onChange = { onChange(device.copy(serialNumber = it)) },
                    label    = "Serial No",
                    modifier = Modifier.weight(1f)
                )
                DeviceField(
                    value    = device.color,
                    onChange = { onChange(device.copy(color = it)) },
                    label    = "အရောင်",
                    modifier = Modifier.weight(1f)
                )
            }

            DeviceField(
                value    = device.accessories,
                onChange = { onChange(device.copy(accessories = it)) },
                label    = "ပါပစ္စည်းများ",
                hint     = "Charger, Case...",
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value         = device.problemDesc,
                onValueChange = { onChange(device.copy(problemDesc = it)) },
                label         = { Text("ပြဿနာ ဖော်ပြချက်") },
                placeholder   = { Text("ဖောက်သည် တင်ပြသည့် ပြဿနာ...") },
                modifier      = Modifier.fillMaxWidth().heightIn(min = 70.dp),
                maxLines      = 3,
                shape         = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.padding(top = 4.dp)
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
    }
}

@Composable
private fun DeviceField(
    value: String, onChange: (String) -> Unit,
    label: String, hint: String = "", modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label, fontSize = 12.sp) },
        placeholder     = if (hint.isNotBlank()) {{ Text(hint, fontSize = 12.sp) }} else null,
        modifier        = modifier,
        singleLine      = true,
        shape           = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        textStyle       = LocalTextStyle.current.copy(fontSize = 13.sp)
    )
}
