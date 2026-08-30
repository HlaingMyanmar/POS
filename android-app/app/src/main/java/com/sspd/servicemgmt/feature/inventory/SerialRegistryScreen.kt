package com.sspd.servicemgmt.feature.inventory

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.ProductSerialDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppSearchField
import com.sspd.servicemgmt.core.ui.theme.*

import com.sspd.servicemgmt.core.util.fmtWarranty
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val RegistryColor = Color(0xFF7C3AED)
private val RegistryBg    = Color(0xFFF5F3FF)

private data class StatusInfo(val label: String, val color: Color, val bg: Color)

private fun statusInfo(status: String?): StatusInfo = when (status) {
    "AVAILABLE"  -> StatusInfo("ရရှိနိုင်",   Color(0xFF16A34A), Color(0xFFF0FDF4))
    "SOLD"       -> StatusInfo("ရောင်းပြီး",  Primary,           PrimaryLight)
    "IN_SERVICE" -> StatusInfo("ဝန်ဆောင်မှု", Warning,           WarningBg)
    "RETURNED"   -> StatusInfo("ပြန်လည်ရောက်", Color(0xFF0891B2), Color(0xFFECFEFF))
    "DAMAGED"    -> StatusInfo("ပျက်စီး",     Danger,            DangerBg)
    else         -> StatusInfo(status ?: "—", TextMuted,         Color(0xFFF1F5F9))
}

private val STATUS_FILTERS = listOf(
    null         to "အားလုံး",
    "AVAILABLE"  to "ရရှိနိုင်",
    "SOLD"       to "ရောင်းပြီး",
    "IN_SERVICE" to "ဝန်ဆောင်မှု",
    "RETURNED"   to "ပြန်ရောက်",
    "DAMAGED"    to "ပျက်စီး"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialRegistryScreen(
    onBack:          () -> Unit,
    onProductClick:  (Int, String) -> Unit = { _, _ -> }
) {
    val vm: SerialRegistryViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    var editingSerial by remember { mutableStateOf<ProductSerialDTO?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Serial Registry", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = RegistryColor,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)
        ) {
            // ── Search bar ─────────────────────────────────────────────────
            AppSearchField(
                value = searchText,
                onValueChange = { searchText = it; vm.setSearch(it) },
                placeholder = "စီးရီး / ကုန်ပစ္စည်း ရှာပါ...",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // ── Status filter chips ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                STATUS_FILTERS.forEach { (status, label) ->
                    val selected = state.statusFilter == status
                    FilterChip(
                        selected = selected,
                        onClick  = { vm.setStatusFilter(status) },
                        label    = { Text(label, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RegistryColor.copy(0.12f),
                            selectedLabelColor     = RegistryColor
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Count summary ──────────────────────────────────────────────
            if (!state.loading) {
                Text(
                    "${state.filtered.size} ခု တွေ့သည်",
                    fontSize = 11.sp,
                    color    = TextMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            // ── List ───────────────────────────────────────────────────────
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
            } else if (state.filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.QrCode2, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Text("Serial မတွေ့ပါ", color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.filtered, key = { index, it -> it.id ?: "sn-$index-${it.serialNumber}" }) { _, serial ->
                        SerialCard(
                            serial   = serial,
                            onClick  = { editingSerial = serial }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    editingSerial?.let { serial ->
        SerialEditSheet(
            serial = serial,
            saving = state.saving,
            onDismiss = { editingSerial = null },
            onProductClick = {
                val pid = serial.productId
                if (pid != null) {
                    editingSerial = null
                    onProductClick(pid, serial.serialNumber)
                }
            },
            onSave = { serialNumber, status, condition, warrantyMonths, warrantyStartDate ->
                vm.updateSerial(serial, serialNumber, status, condition, warrantyMonths, warrantyStartDate) { err ->
                    if (err == null) {
                        editingSerial = null
                    } else {
                        scope.launch {
                            snackbar.showSnackbar(err)
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SerialEditSheet(
    serial: ProductSerialDTO,
    saving: Boolean,
    onDismiss: () -> Unit,
    onProductClick: () -> Unit,
    onSave: (String, String, String, Int, String?) -> Unit
) {
    var serialNumber by rememberSaveable(serial.id) { mutableStateOf(serial.serialNumber) }
    var status by rememberSaveable(serial.id) { mutableStateOf(serial.status ?: "AVAILABLE") }
    var condition by rememberSaveable(serial.id) { mutableStateOf(serial.condition ?: "") }
    var warrantyValue by rememberSaveable(serial.id) { mutableStateOf("${serial.warrantyMonths ?: 0}") }
    var warrantyUnit by rememberSaveable(serial.id) { mutableStateOf("လ") }
    var warrantyStart by rememberSaveable(serial.id) { mutableStateOf(serial.warrantyStartDate?.take(10) ?: todayText()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val months = warrantyToMonths(warrantyValue.toIntOrNull() ?: 0, warrantyUnit)

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateToMillis(warrantyStart))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { warrantyStart = millisToDate(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = pickerState) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Update Serial Entry", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                    Text(serial.productName ?: "Inventory Assignment", fontSize = 11.sp, color = TextMuted)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, null, tint = TextMuted) }
            }

            OutlinedTextField(
                value = serialNumber,
                onValueChange = { serialNumber = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Serial Number") },
                leadingIcon = { Icon(Icons.Outlined.Tag, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { onProductClick() },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Product", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Text(serial.productName ?: "-", fontSize = 13.sp, color = TextMain, fontWeight = FontWeight.ExtraBold)
                        Text(serial.productCode ?: "", fontSize = 10.sp, color = TextMuted)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted)
                }
            }

            Text("Status", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AVAILABLE", "SOLD", "IN_SERVICE", "RETURNED", "DAMAGED").forEach { option ->
                    FilterChip(
                        selected = status == option,
                        onClick = { status = option },
                        label = { Text(option.replace("_", " "), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            OutlinedTextField(
                value = condition,
                onValueChange = { condition = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Condition / Note") },
                leadingIcon = { Icon(Icons.Outlined.Notes, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Text("အာမခံကာလ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = warrantyValue,
                    onValueChange = { warrantyValue = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.weight(1f),
                    label = { Text("တန်ဖိုး") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                WarrantyUnitButton("ရက်", warrantyUnit == "ရက်") { warrantyUnit = "ရက်" }
                WarrantyUnitButton("လ", warrantyUnit == "လ") { warrantyUnit = "လ" }
                WarrantyUnitButton("နှစ်", warrantyUnit == "နှစ်") { warrantyUnit = "နှစ်" }
            }
            Surface(color = RegistryBg, shape = RoundedCornerShape(10.dp)) {
                Text(
                    "= $months လ (backend တွက်ချက်မှု)",
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    fontSize = 12.sp,
                    color = RegistryColor,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarToday, null, tint = RegistryColor, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Warranty Start", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(warrantyStart, fontSize = 13.sp, color = TextMain, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Icon(Icons.Outlined.KeyboardArrowDown, null, tint = TextMuted)
                }
            }

            Button(
                onClick = { onSave(serialNumber, status, condition, months, warrantyStart.takeIf { months > 0 }) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RegistryColor)
            ) {
                if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Serial Entry သိမ်းမည်", fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WarrantyUnitButton(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = RegistryColor.copy(0.12f),
            selectedLabelColor = RegistryColor
        )
    )
}

private fun warrantyToMonths(value: Int, unit: String): Int = when (unit) {
    "ရက်" -> kotlin.math.ceil(value / 30.0).toInt()
    "နှစ်" -> value * 12
    else -> value
}.coerceAtLeast(0)

private fun todayText(): String = LocalDate.now().toString()

private fun millisToDate(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(millis))
}

private fun dateToMillis(date: String): Long {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(date)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}

@Composable
private fun SerialCard(
    serial:  ProductSerialDTO,
    onClick: () -> Unit
) {
    val info     = statusInfo(serial.status)
    val wLabel   = fmtWarranty(serial.warrantyMonths)

    Card(
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        border   = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            // Left: icon + serial info
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.Top,
                modifier              = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(RegistryBg, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.QrCode2, null, tint = RegistryColor, modifier = Modifier.size(20.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        serial.serialNumber,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 13.sp,
                        color      = RegistryColor,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    if (!serial.productName.isNullOrBlank()) {
                        Text(
                            serial.productName,
                            fontSize  = 12.sp,
                            color     = TextMain,
                            maxLines  = 1,
                            overflow  = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!serial.condition.isNullOrBlank()) {
                            Text(serial.condition, fontSize = 10.sp, color = TextMuted)
                        }
                        if (wLabel.isNotEmpty()) {
                            Text("🛡 $wLabel", fontSize = 10.sp, color = Color(0xFF0891B2))
                        }
                    }
                }
            }

            // Right: status badge + chevron
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(color = info.bg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        info.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = info.color
                    )
                }
                if (serial.productId != null) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = BorderColor, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
