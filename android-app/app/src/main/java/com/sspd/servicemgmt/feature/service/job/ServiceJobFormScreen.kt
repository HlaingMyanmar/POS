package com.sspd.servicemgmt.feature.service.job

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.ProductDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.network.StaffDTO
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.util.rememberIsTablet
import com.sspd.servicemgmt.core.ui.component.AppPickerSheet
import com.sspd.servicemgmt.feature.service.catalog.ServiceItemPickerContent
import com.sspd.servicemgmt.feature.service.catalog.rememberFilteredServiceItems

import com.sspd.servicemgmt.core.util.fmtWarranty
import com.sspd.servicemgmt.core.ui.scanner.BarcodeScannerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceJobFormScreen(onBack: () -> Unit, onSuccess: (ServiceJobDTO) -> Unit) {
    val vm: ServiceJobFormViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.loadCustomers() }

    var showStaffSheet    by rememberSaveable { mutableStateOf(false) }
    var showLocationSheet by rememberSaveable { mutableStateOf(false) }
    var showLineItemSheet by rememberSaveable { mutableStateOf(-1) }
    var showPartSheet     by rememberSaveable { mutableStateOf(-1) }
    var convertRowKey     by rememberSaveable { mutableStateOf("") }
    var serviceSearchQuery by rememberSaveable { mutableStateOf("") }
    var partSearchQuery   by rememberSaveable { mutableStateOf("") }
    // date-time picker for estimatedCompletion (date step → time step)
    var showDatePicker    by rememberSaveable { mutableStateOf(false) }
    var showTimePicker    by rememberSaveable { mutableStateOf(false) }
    var tempDateMillis    by rememberSaveable { mutableStateOf<Long?>(null) }
    val dpState           = rememberDatePickerState()
    val tpState           = rememberTimePickerState(is24Hour = true)

    LaunchedEffect(state.partScanError) {
        state.partScanError?.let { snackbar.showSnackbar(it); vm.clearPartScanError() }
    }
    LaunchedEffect(state.serialError) {
        state.serialError?.let { snackbar.showSnackbar(it); vm.clearSerialError() }
    }

    if (state.showNewCustomerDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.creatingCustomer) vm.dismissNewCustomerDialog() },
            icon = { Icon(Icons.Outlined.PersonAdd, null, tint = Primary, modifier = Modifier.size(28.dp)) },
            title = { Text("ဖောက်သည်အသစ်", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.newCustomerName,
                        onValueChange = { vm.setNewCustomerName(it) },
                        label = { Text("အမည် *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        isError = state.newCustomerError != null && state.newCustomerName.isBlank()
                    )
                    OutlinedTextField(
                        value = state.newCustomerPhone,
                        onValueChange = { vm.setNewCustomerPhone(it) },
                        label = { Text("ဖုန်း") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = state.newCustomerAddress,
                        onValueChange = { vm.setNewCustomerAddress(it) },
                        label = { Text("လိပ်စာ") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    state.newCustomerError?.let {
                        Text(it, color = Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.createCustomer() },
                    enabled = !state.creatingCustomer,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    if (state.creatingCustomer) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("သိမ်းမည်", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { vm.dismissNewCustomerDialog() },
                    enabled = !state.creatingCustomer
                ) { Text("မလုပ်တော့ပါ") }
            }
        )
    }

    if (showStaffSheet) {
        AppPickerSheet(
            title = "နည်းပညာဆရာ ရွေးပါ",
            items = state.staffList,
            label = { it.name },
            subtitle = { it.role },
            onSelect = { vm.selectStaff(it); showStaffSheet = false },
            onDismiss = { showStaffSheet = false }
        )
    }

    if (showLocationSheet) {
        AppPickerSheet(
            title = "ထားမည့်နေရာ ရွေးပါ",
            items = state.shelfLocations,
            label = { it.code },
            subtitle = { it.label },
            onSelect = { vm.selectShelfLocation(it); showLocationSheet = false },
            onDismiss = { showLocationSheet = false }
        )
    }

    // Service item picker sheet
    if (showLineItemSheet >= 0) {
        val lineIdx = showLineItemSheet
        val filteredServices = rememberFilteredServiceItems(state.serviceItems, serviceSearchQuery)
        ModalBottomSheet(
            onDismissRequest = { showLineItemSheet = -1; serviceSearchQuery = "" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ServiceItemPickerContent(
                items = filteredServices,
                search = serviceSearchQuery,
                onSearch = { serviceSearchQuery = it },
                onSelect = { si ->
                    vm.selectServiceItem(lineIdx, si)
                    showLineItemSheet = -1
                    serviceSearchQuery = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
            )
        }
    }

    // Product part picker sheet
    if (showPartSheet >= 0) {
        val partIdx = showPartSheet
        val filtered = if (partSearchQuery.isBlank()) state.productList
                       else state.productList.filter {
                           it.name.contains(partSearchQuery, true) || it.productCode.contains(partSearchQuery, true)
                       }
        ModalBottomSheet(
            onDismissRequest = { showPartSheet = -1; partSearchQuery = "" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ProductPartPickerContent(
                title = "ပစ္စည်း ရွေးပါ",
                items = filtered,
                search = partSearchQuery,
                onSearch = { partSearchQuery = it },
                onSelect = { prod ->
                    vm.selectPartProduct(partIdx, prod)
                    showPartSheet = -1
                    partSearchQuery = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
            )
        }
    }

    val convertRows = parsePartRequestRows(state.partRequests)
    val convertRow = convertRows.find { it.key == convertRowKey }
    if (convertRow != null) {
        val filtered = if (partSearchQuery.isBlank()) state.productList
        else state.productList.filter {
            it.name.contains(partSearchQuery, true) || it.productCode.contains(partSearchQuery, true)
        }.ifEmpty { state.productList }
        ModalBottomSheet(
            onDismissRequest = { convertRowKey = ""; partSearchQuery = "" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ProductPartPickerContent(
                title = "Stock Part အဖြစ်ပြောင်း",
                subtitle = convertRow.label,
                items = filtered,
                search = partSearchQuery,
                onSearch = { partSearchQuery = it },
                onSelect = { prod ->
                    vm.convertPartRequest(convertRow, prod)
                    convertRowKey = ""
                    partSearchQuery = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
            )
        }
    }

    if (state.serialSelectPartIdx != null && state.serialSelectProduct != null) {
        val serialProduct = state.serialSelectProduct!!
        val serialError = state.serialSelectError
        ModalBottomSheet(
            onDismissRequest = { vm.dismissSerialSelector() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                            state.serialSelectOptions.forEach { serial ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.selectSerialForPart(serial) }
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
                ) { Text("နောက်မှရွေးမည်", color = TextMuted) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Step 1 — Date picker
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    tempDateMillis = dpState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("နောက်တစ်ဆင့် →") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = dpState) }
    }

    // Step 2 — Time picker
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Schedule, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text("အချိန် ရွေးပါ", fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = tpState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dateStr = tempDateMillis?.let {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                            .format(java.util.Date(it))
                    } ?: ""
                    val timeStr = String.format("%02d:%02d", tpState.hour, tpState.minute)
                    vm.setEstimatedCompletion("${dateStr}T$timeStr")
                    showTimePicker = false
                }) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    showDatePicker = true
                }) { Text("← ပြန်") }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.loading) {
                Surface(
                    color = CardBg,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Button(
                        onClick = { vm.save { job -> onSuccess(job) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        enabled = !state.saving
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (vm.isEdit) "Job ပြင်ဆင်မှု သိမ်းဆည်းမည်" else "Job သိမ်းဆည်းမည်",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Job ပြင်ဆင်ရန်" else "Job အသစ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = OnPrimary)
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
                .fillMaxSize().padding(padding).background(ScreenBg)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTablet) 64.dp else 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── ဖောက်သည် ──────────────────────────────────────────────────────
            JobFormSection(Icons.Outlined.Person, "ဖောက်သည် *")
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderColor)
            ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.selectedCustomer?.let { customer ->
                    Surface(
                        color = SuccessBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Success.copy(0.22f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(38.dp).background(Success.copy(0.14f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Person, null, tint = Success, modifier = Modifier.size(20.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(customer.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                                if (!customer.phone.isNullOrBlank()) {
                                    Text(customer.phone, fontSize = 12.sp, color = TextMuted)
                                }
                                if (!customer.address.isNullOrBlank()) {
                                    Text(customer.address, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                                }
                            }
                            IconButton(onClick = { vm.setCustomerQuery("") }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Outlined.Close, "ဖောက်သည် ပြန်ရွေးရန်", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = state.customerQuery, onValueChange = { vm.setCustomerQuery(it) },
                    label = { Text("ဖောက်သည် ရှာပါ *") },
                    leadingIcon = { Icon(Icons.Outlined.PersonSearch, null) },
                    trailingIcon = {
                        if (state.selectedCustomer != null)
                            Icon(Icons.Outlined.CheckCircle, null, tint = Success, modifier = Modifier.size(20.dp))
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                val suggestions = if (state.customerQuery.length >= 1)
                    state.customers.filter { it.name.contains(state.customerQuery, true) }.take(5)
                else emptyList()
                if (suggestions.isNotEmpty() && state.selectedCustomer == null) {
                    Card(shape = RoundedCornerShape(0.dp,0.dp,12.dp,12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        suggestions.forEach { c ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { vm.selectCustomer(c) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Person, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                Column {
                                    Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                    if (!c.phone.isNullOrBlank()) Text(c.phone, fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            HorizontalDivider(color = BorderColor)
                        }
                    }
                }
                if (state.selectedCustomer == null) {
                    OutlinedButton(
                        onClick = { vm.showNewCustomerDialog() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Primary)
                    ) {
                        Icon(Icons.Outlined.PersonAdd, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ဖောက်သည်အသစ် ဖန်တီးမည်", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            }

            // ── နည်းပညာဆရာ ──────────────────────────────────────────────────
            JobFormSection(Icons.Outlined.Badge, "နည်းပညာဆရာ")
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showStaffSheet = true },
                shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val staff = state.selectedStaff
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Engineering, null, tint = if (staff != null) Primary else TextMuted, modifier = Modifier.size(18.dp))
                        if (staff != null) {
                            Column {
                                Text(staff.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                Text(staff.role, fontSize = 11.sp, color = TextMuted)
                            }
                        } else {
                            Text("နည်းပညာဆရာ ရွေးပါ (optional)", fontSize = 13.sp, color = TextMuted)
                        }
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            // ── ပစ္စည်း ───────────────────────────────────────────────────────
            JobFormSection(Icons.Outlined.Devices, "ပစ္စည်း အချက်အလက်")
            JobTextField(state.itemName, { vm.setItemName(it) }, "ပစ္စည်းအမည် *", "iPhone 14, Laptop Dell...")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                JobTextField(state.serialNo, { vm.setSerialNo(it) }, "Serial No", modifier = Modifier.weight(1f))
                JobTextField(state.color, { vm.setColor(it) }, "အရောင်", modifier = Modifier.weight(1f))
            }
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showLocationSheet = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (state.selectedShelfLocation != null) Primary else BorderColor)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val location = state.selectedShelfLocation
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Inventory2, null, tint = if (location != null) Primary else TextMuted, modifier = Modifier.size(18.dp))
                        if (location != null) {
                            Column {
                                Text(location.code, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                if (!location.label.isNullOrBlank()) Text(location.label, fontSize = 11.sp, color = TextMuted)
                            }
                        } else {
                            Text("ထားမည့်နေရာ ရွေးပါ (optional)", fontSize = 13.sp, color = TextMuted)
                        }
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
            JobTextField(state.accessories, { vm.setAccessories(it) }, "ပါပစ္စည်းများ", "Charger, Case...")
            JobTextField(state.itemCondition, { vm.setItemCondition(it) }, "ပစ္စည်းအခြေအနေ (ရောက်ချိန်)", "Cracked screen, Power off...")
            JobTextField(state.deviceConditions, { vm.setDeviceConditions(it) }, "အသေးစိတ် အခြေအနေ")

            // ── ပြဿနာ ────────────────────────────────────────────────────────
            JobFormSection(Icons.Outlined.ReportProblem, "ပြဿနာ & စစ်ဆေးချက်")
            OutlinedTextField(
                value = state.problemDesc, onValueChange = { vm.setProblemDesc(it) },
                label = { Text("ဖောက်သည် တင်ပြသည့် ပြဿနာ *") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
                maxLines = 4, shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = state.diagnosisNotes, onValueChange = { vm.setDiagnosisNotes(it) },
                label = { Text("စစ်ဆေးချက် (optional)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                maxLines = 3, shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = state.partRequests, onValueChange = { vm.setPartRequests(it) },
                label = { Text("Booking Part လိုအပ်ချက်") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                maxLines = 4, shape = RoundedCornerShape(12.dp)
            )

            val partRequestRows = parsePartRequestRows(state.partRequests)
            if (partRequestRows.isNotEmpty()) {
                Surface(
                    color = Warning.copy(0.10f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Warning.copy(0.35f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Booking မှ Part လိုအပ်ချက်", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Warning)
                        partRequestRows.forEach { row ->
                            val converted = state.convertedPartKeys.contains(row.key)
                            Surface(
                                color = CardBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Warning.copy(0.25f))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(row.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                                        if (converted) {
                                            Text("Stock Part အဖြစ်ပြောင်းပြီး", fontSize = 11.sp, color = Success, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (converted) {
                                        Surface(color = Success.copy(0.15f), shape = RoundedCornerShape(8.dp)) {
                                            Text("ပြောင်းပြီး", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Success)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                convertRowKey = row.key
                                                partSearchQuery = partRequestSearchText(row.partName)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            border = BorderStroke(1.dp, Primary)
                                        ) {
                                            Text("Stock Part အဖြစ်ပြောင်း", fontSize = 11.sp, color = Primary)
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "Technician စစ်ဆေးပြီးမှ Stock Part အဖြစ်ပြောင်းပါ။ Booking မူရင်းစာရင်း မပြောင်းပါ။",
                            fontSize = 11.sp, color = Warning
                        )
                    }
                }
            }

            // ── ဝန်ဆောင်မှုများ ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MiscellaneousServices, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text("ဝန်ဆောင်မှုများ", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                    if (state.lines.isNotEmpty()) {
                        Surface(color = Primary.copy(0.12f), shape = RoundedCornerShape(10.dp)) {
                            Text("${state.lines.size}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                        }
                    }
                }
                OutlinedButton(onClick = { vm.addLine() }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), border = BorderStroke(1.dp, Primary)) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(14.dp), tint = Primary)
                    Spacer(Modifier.width(4.dp))
                    Text("ထည့်ရန်", fontSize = 12.sp, color = Primary)
                }
            }

            state.lines.forEachIndexed { i, line ->
                ServiceLineDraftCard(
                    index      = i,
                    line       = line,
                    onPickItem = { showLineItemSheet = i },
                    onChange   = { vm.updateLine(i, it) },
                    onRemove   = { vm.removeLine(i) }
                )
            }

            // ── အသုံးပြုပစ္စည်းများ (Parts) ───────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Build, null, tint = Warning, modifier = Modifier.size(18.dp))
                    Text("အသုံးပြုပစ္စည်းများ", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Warning)
                    if (state.parts.isNotEmpty()) {
                        Surface(color = Warning.copy(0.15f), shape = RoundedCornerShape(10.dp)) {
                            Text("${state.parts.size}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Warning)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.partScanLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Warning)
                    } else {
                        Button(
                            onClick = { vm.showPartScanner() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Warning)
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, "ဘားကုဒ် ဖတ်ရန်", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = { vm.addPart() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, Warning)
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(14.dp), tint = Warning)
                        Spacer(Modifier.width(4.dp))
                        Text("ထည့်ရန်", fontSize = 12.sp, color = Warning)
                    }
                }
            }

            state.parts.forEachIndexed { i, part ->
                PartDraftCard(
                    index         = i,
                    part          = part,
                    onPickItem    = { showPartSheet = i },
                    onChange      = { vm.updatePart(i, it) },
                    onRemove      = { vm.removePart(i) },
                    onSerialScan  = { vm.showSerialScanner(i) },
                    onSerialAdd   = { sn -> vm.addSerialToPart(i, sn) },
                    onSerialRemove = { sn -> vm.removeSerialFromPart(i, sn) }
                )
            }

            // ── ကိုန်ခန့်မှန်း ────────────────────────────────────────────────
            JobFormSection(Icons.Outlined.Payments, "ကိုန်ခန့်မှန်း")
            OutlinedTextField(
                value = state.estimatedCost, onValueChange = { vm.setEstimatedCost(it) },
                label = { Text("ခန့်မှန်းကိုန် (Ks)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            // Single date-time picker card
            val hasDateTime = state.estimatedCompletion.isNotBlank()
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, if (hasDateTime) Primary else BorderColor)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null,
                            tint = if (hasDateTime) Primary else TextMuted,
                            modifier = Modifier.size(18.dp))
                        Column {
                            Text(
                                text = if (hasDateTime) "ပြီးမည့်ရက်/အချိန်" else "ပြီးမည့်ရက်/အချိန် ရွေးပါ",
                                fontSize = 11.sp,
                                color    = if (hasDateTime) Primary else TextMuted
                            )
                            if (hasDateTime) {
                                Text(
                                    text = state.estimatedCompletion.replace("T", "  "),
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain
                                )
                            }
                        }
                    }
                    if (hasDateTime) {
                        IconButton(onClick = { vm.setEstimatedCompletion("") }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Close, "ရက်ရှင်းရန်", tint = Danger, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── မှတ်ချက် ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.remark, onValueChange = { vm.setRemark(it) },
                label = { Text("မှတ်ချက်") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                maxLines = 3, shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            // ── Error ─────────────────────────────────────────────────────────
            val saveError = state.saveError
            if (!saveError.isNullOrBlank()) {
                Surface(color = DangerBg, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(18.dp))
                        Text(saveError, fontSize = 13.sp, color = Danger, modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(86.dp))
        }
    }

    // ── Part product scanner overlay ──────────────────────────────────────────
    if (state.showPartScanner) {
        BarcodeScannerView(
            onResult = { vm.onPartProductScan(it) },
            onClose  = { vm.dismissPartScanner() }
        )
    }

    // ── Serial scanner overlay ────────────────────────────────────────────────
    state.serialScanPartIdx?.let { partIdx ->
        BarcodeScannerView(
            onResult = { vm.onPartSerialScan(partIdx, it) },
            onClose  = { vm.dismissSerialScanner() }
        )
    }

    } // end Box
}

// ── Service Line Draft Card ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceLineDraftCard(
    index:      Int,
    line:       ServiceJobFormViewModel.LineDraft,
    onPickItem: () -> Unit,
    onChange:   (ServiceJobFormViewModel.LineDraft) -> Unit,
    onRemove:   () -> Unit
) {
    val qty = line.qty.toIntOrNull() ?: 1
    val chargeUnit = when {
        line.confirmationStatus == "CUSTOMER_REJECTED" || line.confirmationStatus == "CUSTOMER_HOLD" || line.warrantyCovered -> 0.0
        line.billedPrice.toDoubleOrNull() != null -> line.billedPrice.toDouble()
        line.confirmationStatus in setOf("CUSTOMER_APPROVED", "IN_PROGRESS", "COMPLETED") &&
            line.approvedPrice.toDoubleOrNull() != null -> line.approvedPrice.toDouble()
        else -> line.estimatedPrice.toDoubleOrNull() ?: line.catalogPrice.toDoubleOrNull() ?: 0.0
    }
    val rangeHint = listOfNotNull(
        line.minPrice?.let { "Min ${String.format("%,.0f", it)}" },
        line.maxPrice?.let { "Max ${String.format("%,.0f", it)}" }
    ).joinToString(" · ")
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (line.outsideMinMax()) Danger.copy(0.5f) else Primary.copy(0.3f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).background(Primary.copy(0.12f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.RemoveCircleOutline, "ဖယ်ရှားရန်", tint = Danger, modifier = Modifier.size(16.dp))
                }
            }
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { onPickItem() },
                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(line.serviceItem?.item ?: "ဝန်ဆောင်မှု ရွေးပါ *", color = if (line.serviceItem != null) TextMain else TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Text("အတည်ပြုအခြေအနေ", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LINE_CONFIRMATION_STATUS.forEach { (value, label) ->
                    FilterChip(
                        selected = line.confirmationStatus == value,
                        onClick = { onChange(line.copy(confirmationStatus = value)) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.qty, onValueChange = { onChange(line.copy(qty = it)) },
                    label = { Text("Qty", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = line.catalogPrice, onValueChange = {},
                    label = { Text("မူရင်းစျေး", fontSize = 11.sp) },
                    readOnly = true, enabled = false,
                    modifier = Modifier.weight(1.4f), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.estimatedPrice, onValueChange = { onChange(line.copy(estimatedPrice = it)) },
                    label = { Text("ခန့်မှန်းစျေး", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                    isError = line.outsideMinMax()
                )
                OutlinedTextField(
                    value = line.approvedPrice, onValueChange = { onChange(line.copy(approvedPrice = it)) },
                    label = { Text("အတည်ပြုစျေး", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = line.billedPrice, onValueChange = { onChange(line.copy(billedPrice = it)) },
                    label = { Text("ကောက်ခံစျေး", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = line.warrantyMonths, onValueChange = { onChange(line.copy(warrantyMonths = it)) },
                    label = { Text("အာမခံ (လ)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = line.warrantyCovered, onCheckedChange = { onChange(line.copy(warrantyCovered = it)) })
                Text("Warranty ဖြင့် အခမဲ့", fontSize = 12.sp, color = TextMain)
            }
            Surface(color = ScreenBg, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderColor)) {
                Text(
                    text = when {
                        line.confirmationStatus == "CUSTOMER_REJECTED" -> "ကောက်ခံမည်: ငြင်းပယ် — 0"
                        line.confirmationStatus == "CUSTOMER_HOLD" -> "ကောက်ခံမည်: Hold — 0"
                        line.warrantyCovered -> "ကောက်ခံမည်: FREE"
                        else -> "ကောက်ခံမည်: ${String.format("%,.0f", qty * chargeUnit)} Ks"
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = when {
                        line.confirmationStatus == "CUSTOMER_REJECTED" -> Danger
                        line.confirmationStatus == "CUSTOMER_HOLD" -> Violet
                        line.warrantyCovered -> Success
                        else -> TextMain
                    }
                )
            }
            if (rangeHint.isNotBlank()) {
                Text("$rangeHint Ks", fontSize = 10.sp, color = TextMuted)
            }
            if (line.outsideMinMax()) {
                Text("Min/Max ကျော်နေသည် — Manager approval လိုအပ်သည်", fontSize = 11.sp, color = Danger, fontWeight = FontWeight.Bold)
            }
            if (line.pricesDifferFromCatalog()) {
                OutlinedTextField(
                    value = line.priceChangeReason, onValueChange = { onChange(line.copy(priceChangeReason = it)) },
                    label = { Text("စျေးပြောင်းရသည့်အကြောင်းပြချက် *", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    isError = line.priceChangeReason.isBlank()
                )
            }
        }
    }
}

private val LINE_CONFIRMATION_STATUS = listOf(
    "RECOMMENDED" to "အကြံပြုထားသည်",
    "INSPECTING" to "စစ်ဆေးဆဲ",
    "CUSTOMER_HOLD" to "Customer စောင့်ဆိုင်း",
    "CUSTOMER_APPROVED" to "Customer အတည်ပြုပြီး",
    "CUSTOMER_REJECTED" to "Customer ငြင်းပယ်",
    "IN_PROGRESS" to "လုပ်ဆောင်ဆဲ",
    "COMPLETED" to "ပြီးစီး"
)

// ── Part Draft Card ───────────────────────────────────────────────────────────

@Composable
private fun PartDraftCard(
    index:          Int,
    part:           ServiceJobFormViewModel.PartDraft,
    onPickItem:     () -> Unit,
    onChange:       (ServiceJobFormViewModel.PartDraft) -> Unit,
    onRemove:       () -> Unit,
    onSerialScan:   () -> Unit,
    onSerialAdd:    (String) -> Unit,
    onSerialRemove: (String) -> Unit
) {
    var serialInput by rememberSaveable { mutableStateOf("") }
    val isSerialTracked = part.product?.hasSerial == true

    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Warning.copy(0.4f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Header row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).background(Warning.copy(0.15f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Warning)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.RemoveCircleOutline, "ဖယ်ရှားရန်", tint = Danger, modifier = Modifier.size(16.dp))
                }
            }

            // Product picker
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { onPickItem() },
                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            part.product?.name ?: "ပစ္စည်း ရွေးပါ *",
                            color = if (part.product != null) TextMain else TextMuted, fontSize = 13.sp
                        )
                        if (part.product != null)
                            Text(part.product.productCode, fontSize = 10.sp, color = TextMuted)
                    }
                    if (isSerialTracked) {
                        Surface(color = VioletBg, shape = RoundedCornerShape(5.dp)) {
                            Text("Serial", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Violet)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            // Qty + Price + Discount
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isSerialTracked) {
                    OutlinedTextField(
                        value = part.qty, onValueChange = { onChange(part.copy(qty = it)) },
                        label = { Text("Qty", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp)
                    )
                }
                OutlinedTextField(
                    value = part.unitPrice, onValueChange = { onChange(part.copy(unitPrice = it)) },
                    label = { Text("ဈေးနှုန်း (Ks)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(2f), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = part.discount, onValueChange = { onChange(part.copy(discount = it)) },
                    label = { Text("လျှော့ (Ks)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1.5f), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
            }

            // Subtotal
            val qty      = if (isSerialTracked) part.serialNumbers.size.coerceAtLeast(1) else part.qty.toIntOrNull() ?: 1
            val price    = part.unitPrice.toDoubleOrNull() ?: 0.0
            val disc     = part.discount.toDoubleOrNull() ?: 0.0
            val subtotal = (qty * price - disc).coerceAtLeast(0.0)
            if (price > 0) {
                Surface(color = WarningBg, shape = RoundedCornerShape(6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$qty × ${String.format("%,.0f", price)} Ks${if (disc > 0) " − ${String.format("%,.0f", disc)}" else ""}", fontSize = 11.sp, color = Warning)
                        Text("${String.format("%,.0f", subtotal)} Ks", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Warning)
                    }
                }
            }

            // Serial section (only for serial-tracked products)
            if (isSerialTracked) {
                Surface(color = ScreenBg, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SERIAL (${part.serialNumbers.size} ခု)", fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                            if (part.serialNumbers.isEmpty())
                                Text("⚠ Required", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Danger)
                        }
                        if (part.serialNumbers.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            part.serialNumbers.forEach { sn ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(color = SuccessBg, shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
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
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = serialInput, onValueChange = { serialInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Serial ရိုက်ထည့်", fontSize = 11.sp, color = TextMuted) },
                                singleLine = true, shape = RoundedCornerShape(8.dp),
                                leadingIcon = { Icon(Icons.Outlined.QrCode2, null, tint = TextMuted, modifier = Modifier.size(16.dp)) }
                            )
                            Button(
                                onClick = { val sn = serialInput.trim(); if (sn.isNotBlank()) { onSerialAdd(sn); serialInput = "" } },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            ) { Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onSerialScan,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Violet),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, "ဘားကုဒ် ဖတ်ရန်", tint = Violet, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("📷  Scan Serial Number", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Violet)
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun JobFormSection(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
    }
}

@Composable
private fun ProductPartPickerContent(
    title: String,
    items: List<ProductDTO>,
    search: String,
    onSearch: (String) -> Unit,
    onSelect: (ProductDTO) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.52f).dp
    Column(modifier.fillMaxWidth()) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, fontSize = 12.sp, color = TextMuted)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            placeholder = { Text("ရှာဖွေပါ (အမည် / ကုဒ်)", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Outlined.Clear, "ရှင်းရန်", modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                "ပစ္စည်း မတွေ့ပါ",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = listMaxHeight),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(items, key = { index, it -> if (it.id != 0) it.id else "prod-$index-${it.productCode}" }) { _, prod ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(prod) }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(prod.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                Text(prod.productCode, fontSize = 11.sp, color = TextMuted)
                                if (prod.stockQty > 0)
                                    Text("Stock: ${prod.stockQty}", fontSize = 10.sp, color = Success)
                            }
                            Text(
                                "${String.format("%,.0f", prod.sellingPrice.toDouble())} Ks",
                                fontSize = 12.sp,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = BorderColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun JobTextField(
    value:    String,
    onChange: (String) -> Unit,
    label:    String,
    hint:     String    = "",
    modifier: Modifier  = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = if (hint.isNotBlank()) {{ Text(hint, fontSize = 12.sp) }} else null,
        modifier = modifier, singleLine = true, shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
    )
}
