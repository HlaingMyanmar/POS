package com.sspd.servicemgmt.feature.service.job

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.sspd.servicemgmt.core.network.HandoverDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.ui.component.AppLoading

import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceJobListScreen(
    onBack:     () -> Unit,
    onJobClick: (Int) -> Unit = {},
    onNewJob:   () -> Unit    = {}
) {
    val vm: ServiceJobListViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val canOutdoorVisit = remember(context) {
        PreferenceManager(context).hasPermission("CAN_ACCESS_TECHNICIAN_VISIT_START")
    }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) { vm.load(); delay(30_000) }
    }

    LaunchedEffect(state.deleteSuccess) {
        state.deleteSuccess?.let { snackbar.showSnackbar(it); vm.clearDeleteSuccess() }
    }

    // ── Date pickers ──────────────────────────────────────────────────────────
    if (showFromPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = state.fromDate?.parseDateToMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setFromDate(it.formatMillisToDate()) }
                    showFromPicker = false
                }) { Text("အိုကေ") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("ပယ်ဖျက်") }
            }
        ) { DatePicker(state = dpState) }
    }

    if (showToPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = state.toDate?.parseDateToMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setToDate(it.formatMillisToDate()) }
                    showToPicker = false
                }) { Text("အိုကေ") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("ပယ်ဖျက်") }
            }
        ) { DatePicker(state = dpState) }
    }

    // ── Delete confirm dialog ─────────────────────────────────────────────────
    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!state.deleting) vm.cancelDelete() },
            icon  = { Icon(Icons.Outlined.DeleteForever, null, tint = Danger) },
            title = { Text("Job ဖျက်မည်", fontWeight = FontWeight.ExtraBold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Job \"${target.jobNo ?: "#${target.id}"}\" ကို ဖျက်မည်။ ဆက်လက်မည်လား?",
                        fontSize = 14.sp
                    )
                    state.deleteError?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 12.sp, color = Danger)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick  = { vm.delete() },
                    colors   = ButtonDefaults.buttonColors(containerColor = Danger),
                    enabled  = !state.deleting
                ) {
                    if (state.deleting)
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else
                        Text("ဖျက်မည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDelete() }, enabled = !state.deleting) {
                    Text("မဖျက်ပါ")
                }
            }
        )
    }

    val counts = workQueueCounts(state.items, state.sentPendingCount)
    val tabFiltered = filterByWorkTab(state.items, state.workTab)
    val filtered = when (state.filter) {
        "ALL"    -> tabFiltered
        "CREDIT" -> tabFiltered.filter { (it.dueAmount ?: 0.0) > 0 }
        "OVERDUE" -> tabFiltered.filter { it.overdue == true }
        else     -> tabFiltered.filter { it.status?.uppercase() == state.filter }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("ဝန်ဆောင်မှုအလုပ်များ", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewJob,
                containerColor = Primary,
                contentColor = Color.White,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Job အသစ်", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Job နံပါတ် / ဖောက်သည် ရှာပါ", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp), tint = TextMuted) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            val summaryMetrics = listOf(
                "Total" to state.items.size.toString(),
                "Active" to (counts[WORK_TAB_ACTIVE] ?: 0).toString(),
                "Payment" to (counts[WORK_TAB_PAYMENT] ?: 0).toString(),
                "Handover" to (counts[WORK_TAB_HANDOVER] ?: 0).toString(),
                "Transfer" to (counts[WORK_TAB_TRANSFER] ?: 0).toString(),
                "Sent" to (counts[WORK_TAB_SENT] ?: 0).toString()
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                summaryMetrics.forEach { (label, value) ->
                    SummaryMetricCard(label = label, value = value)
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    WORK_TAB_TRANSFER to "Hand Over",
                    WORK_TAB_SENT to "ပို့ထား",
                    WORK_TAB_ACTIVE to "လုပ်ဆောင်ဆဲ",
                    WORK_TAB_PAYMENT to "ငွေရှင်းရန်",
                    WORK_TAB_HANDOVER to "ပေးအပ်ရန်",
                    WORK_TAB_CLOSED to "ပိတ်ပြီး",
                    WORK_TAB_ALL to "အားလုံး"
                ).forEach { (k, v) ->
                    val count = counts[k]
                    FilterChip(
                        selected = state.workTab == k,
                        onClick  = { vm.setWorkTab(k) },
                        label    = {
                            Text(
                                if (count != null && k != WORK_TAB_ALL && k != WORK_TAB_CLOSED) "$v ($count)" else v,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            // ── Date filter row ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.DateRange, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                OutlinedCard(
                    modifier = Modifier.weight(1f).clickable { showFromPicker = true },
                    shape    = RoundedCornerShape(8.dp),
                    border   = BorderStroke(1.dp, BorderColor)
                ) {
                    Text(
                        text     = state.fromDate ?: "ရက်ရွေး",
                        fontSize = 12.sp,
                        color    = if (state.fromDate != null) TextMain else TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Text("—", fontSize = 12.sp, color = TextMuted)
                OutlinedCard(
                    modifier = Modifier.weight(1f).clickable { showToPicker = true },
                    shape    = RoundedCornerShape(8.dp),
                    border   = BorderStroke(1.dp, BorderColor)
                ) {
                    Text(
                        text     = state.toDate ?: "ရက်ရွေး",
                        fontSize = 12.sp,
                        color    = if (state.toDate != null) TextMain else TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                if (state.fromDate != null || state.toDate != null) {
                    IconButton(
                        onClick  = { vm.clearDateFilter() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Outlined.Close, null, tint = Danger, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── Status filter chips ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL"         to "အားလုံး",
                    "RECEIVED"    to "လက်ခံပြီး",
                    "INSPECTING"  to "စစ်ဆေးဆဲ",
                    "IN_PROGRESS" to "လုပ်ဆဲ",
                    "WAITING_PARTS" to "ပစ္စည်းစောင့်",
                    "COMPLETED"   to "ပြီး",
                    "DELIVERED"   to "ပြန်ပေးပြီး",
                    "CREDIT"      to "ကြွေးကျန်",
                    "OVERDUE"     to "SLA ကျော်"
                ).forEach { (k, v) ->
                    FilterChip(
                        selected = state.filter == k,
                        onClick  = { vm.setFilter(k) },
                        label    = { Text(v, fontSize = 12.sp) },
                        colors   = if (k == "CREDIT") FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DangerBg,
                            selectedLabelColor     = Danger
                        ) else FilterChipDefaults.filterChipColors()
                    )
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppLoading()
                }
            } else if (state.workTab == WORK_TAB_SENT) {
                if (state.sentHandovers.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Hand Over ပို့ထားသော Job မရှိသေးပါ", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text("ပို့ထားပြီးပါက လက်ခံ/ငြင်းပယ် status ကို ဒီမှာ ကြည့်နိုင်ပါသည်", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.sentHandovers, key = { it.id ?: it.hashCode() }) { handover ->
                            SentHandoverListCard(handover = handover, onOpen = {
                                handover.serviceJobId?.let(onJobClick)
                            })
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = PrimaryLight, shape = RoundedCornerShape(18.dp)) {
                            Icon(
                                Icons.Outlined.Build,
                                null,
                                tint = Primary,
                                modifier = Modifier.padding(18.dp).size(34.dp)
                            )
                        }
                        Text("Job မရှိသေးပါ", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "ဖုန်း၊ Laptop၊ Printer စတဲ့ပြင်ဆင်မှုအသစ်တွေကို ဒီနေရာကနေ မှတ်တမ်းတင်ပါ။",
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                        Button(
                            onClick = onNewJob,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Job အသစ်ယူမည်", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("အပြင်ထွက် Visit", fontWeight = FontWeight.ExtraBold, color = Color(0xFF047857))
                                Text(
                                    if (canOutdoorVisit) "Job တစ်ခုဖွင့်ပြီး «ထွက်ခွာပြီ» နှိပ်ပါ"
                                    else "Admin မှ CAN_ACCESS_TECHNICIAN_VISIT_START ပေးပြီး logout/login ပြန်လုပ်ပါ",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                    items(filtered) { job ->
                        Card(
                            shape    = RoundedCornerShape(12.dp),
                            colors   = CardDefaults.cardColors(containerColor = CardBg),
                            border   = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth().clickable { job.id?.let { onJobClick(it) } }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(job.jobNo ?: "-", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Violet)
                                            ServiceModeChip(job.serviceMode)
                                        }
                                        Text(job.customerName ?: "-", fontSize = 13.sp, color = TextMain)
                                    }
                                    JobStatusBadge(job.status)
                                }
                                Spacer(Modifier.height(6.dp))
                                if (!job.itemName.isNullOrBlank()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Outlined.Devices, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                        Text(job.itemName, fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                                if (!job.shelfLocationCode.isNullOrBlank()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Inventory2, null, tint = Primary, modifier = Modifier.size(12.dp))
                                        Text(
                                            listOfNotNull(
                                                job.shelfLocationCode,
                                                job.shelfLocationLabel?.takeIf { it.isNotBlank() }
                                            ).joinToString(" - "),
                                            fontSize = 11.sp,
                                            color = Primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (!job.problemDesc.isNullOrBlank()) {
                                    Text(job.problemDesc, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Person, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                        Text(job.assignedStaffName ?: "-", fontSize = 11.sp, color = TextMuted)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if ((job.dueAmount ?: 0.0) > 0) {
                                            Surface(color = DangerBg, shape = RoundedCornerShape(6.dp)) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Outlined.CreditCard, null, tint = Danger, modifier = Modifier.size(10.dp))
                                                    Text("ကြွေး ${String.format("%,.0f", job.dueAmount)} Ks", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Danger)
                                                }
                                            }
                                        }
                                        Text(
                                            "${String.format("%,.0f", job.netAmount ?: 0.0)} Ks",
                                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(label: String, value: String) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderColor),
        tonalElevation = 1.dp,
        modifier = Modifier.defaultMinSize(minWidth = 118.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Violet)
        }
    }
}

@Composable
private fun ServiceModeChip(mode: String?) {
    val outdoor = mode.equals("OUTDOOR", ignoreCase = true)
    Surface(
        color = if (outdoor) Color(0xFFD1FAE5) else BorderColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            if (outdoor) "OUTDOOR" else "INDOOR",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (outdoor) Color(0xFF047857) else TextMuted
        )
    }
}

@Composable
private fun JobStatusBadge(status: String?) {
    val (bg, color, label) = when (status?.uppercase()) {
        "COMPLETED"   -> Triple(SuccessBg, Success, "ပြီးဆုံး")
        "DELIVERED"   -> Triple(SuccessBg, Success, "ပြန်ပေးပြီး")
        "RECEIVED"    -> Triple(WarningBg, Warning, "လက်ခံပြီး")
        "INSPECTING"  -> Triple(WarningBg, Warning, "စစ်ဆေးဆဲ")
        "IN_PROGRESS" -> Triple(VioletBg,  Violet,  "လုပ်ဆဲ")
        "WAITING_PARTS" -> Triple(WarningBg, Warning, "ပစ္စည်းစောင့်")
        "CANCELLED"   -> Triple(DangerBg,  Danger,  "ပယ်ဖျက်")
        else          -> Triple(BorderColor, TextMuted, status ?: "-")
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize   = 10.sp,
            fontWeight = FontWeight.Bold,
            color      = color
        )
    }
}

private fun String.parseDateToMillis(): Long? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .parse(this)?.time
} catch (_: Exception) { null }

private fun Long.formatMillisToDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(this))

@Composable
private fun SentHandoverListCard(handover: HandoverDTO, onOpen: () -> Unit) {
    val status = handover.status?.uppercase().orEmpty()
    val (bg, fg, label) = when (status) {
        "ACCEPTED" -> Triple(Color(0xFFD1FAE5), Color(0xFF065F46), "လက်ခံပြီး")
        "REJECTED" -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), "ငြင်းပယ်ပြီး")
        else -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "စောင့်ဆိုင်းနေ")
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(handover.jobNo ?: "-", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Violet)
                Surface(color = bg, shape = RoundedCornerShape(50)) {
                    Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
                }
            }
            Text("${handover.fromStaffName} → ${handover.toStaffName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            handover.remainingWork?.takeIf { it.isNotBlank() }?.let {
                Text("ကျန်ရှိ: $it", fontSize = 11.sp, color = TextMuted, maxLines = 2)
            }
            handover.rejectionReason?.takeIf { it.isNotBlank() }?.let {
                Text("ငြင်းပယ်ရသည့်အကြောင်း: $it", fontSize = 11.sp, color = Danger)
            }
            Text("Hand Over History ကြည့်ရန်", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}

