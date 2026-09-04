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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.sspd.servicemgmt.core.network.AssignmentDTO
import com.sspd.servicemgmt.core.network.TeamSnapshotDTO
import com.sspd.servicemgmt.core.network.TechnicianVisitDTO
import com.sspd.servicemgmt.core.tracking.LocationPermission
import com.sspd.servicemgmt.core.tracking.VisitTracker
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.network.PaymentTransactionDTO
import com.sspd.servicemgmt.core.network.ProductSerialDTO
import com.sspd.servicemgmt.core.network.ReworkRequestDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.network.ServiceJobLineDTO
import com.sspd.servicemgmt.core.network.ServiceJobPartDTO
import com.sspd.servicemgmt.core.network.StaffDTO
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.ui.component.AppLoading

import com.sspd.servicemgmt.core.util.PreferenceManager
import com.sspd.servicemgmt.core.util.fmtWarranty
import kotlinx.coroutines.launch
import org.json.JSONArray

private fun formatPartRequests(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return try {
        val json = JSONArray(value)
        buildList {
            for (index in 0 until json.length()) {
                val row = json.optJSONObject(index) ?: continue
                val name = row.optString("partName").trim()
                if (name.isBlank()) continue
                val action = when (row.optString("action")) {
                    "REPLACE" -> "လဲရန်"
                    "REPAIR" -> "ပြုပြင်ရန်"
                    "CHECK" -> "စစ်ဆေးရန်"
                    else -> row.optString("action")
                }
                val notice = row.optString("notice").trim()
                add(buildString {
                    append(name)
                    if (action.isNotBlank()) append(" — ").append(action)
                    append(" × ").append(row.optInt("qty", 1).coerceAtLeast(1))
                    if (notice.isNotBlank()) append(" (").append(notice).append(")")
                })
            }
        }.joinToString("\n")
    } catch (_: Exception) {
        value
    }
}

private val TECHNICIAN_JOB_STEPS = listOf("RECEIVED", "INSPECTING", "IN_PROGRESS", "COMPLETED", "DELIVERED")

private fun allowedNextJobStatuses(current: String): Set<String> = when (current) {
    "RECEIVED" -> setOf("INSPECTING")
    "INSPECTING" -> setOf("IN_PROGRESS", "WAITING_PARTS")
    "WAITING_PARTS" -> setOf("IN_PROGRESS")
    "IN_PROGRESS" -> setOf("WAITING_PARTS", "COMPLETED")
    "COMPLETED" -> setOf("DELIVERED")
    else -> emptySet()
}

private fun technicianNextAction(job: ServiceJobDTO): String = when (job.status?.uppercase()) {
    "RECEIVED" -> "ပစ္စည်းနှင့် ပြဿနာကို စစ်ဆေးပြီး ‘စစ်ဆေးဆဲ’ သို့ ပြောင်းပါ"
    "INSPECTING" -> when {
        job.lines.orEmpty().any { it.confirmationStatus == "CUSTOMER_HOLD" } ->
            "Customer ဆုံးဖြတ်ချက် စောင့်ဆိုင်းနေသည် — အတည်ပြု / ငြင်းပယ် ရွေးပါ"
        (job.estimatedCost ?: 0.0) > 0 && job.estimateApproved != true ->
            "Service/Parts နှင့် ခန့်မှန်းဈေးစစ်ပါ။ Customer Estimate အတည်ပြုပြီးမှ ပြင်ဆင်မှုစတင်ပါ"
        else -> "Customer အတည်ပြုထားသော Service များစစ်ပြီး ‘လုပ်ဆောင်ဆဲ’ သို့ ပြောင်းပါ"
    }
    "WAITING_PARTS" -> "Parts ရောက်ပါက Qty/Serial ဖြည့်ပြီး ‘လုပ်ဆောင်ဆဲ’ သို့ ပြောင်းပါ"
    "IN_PROGRESS" -> "Service များပြီးစီးကြောင်းနှင့် ကောက်ခံဈေးစစ်ပြီး ‘ပြီးဆုံး’ သို့ ပြောင်းပါ"
    "COMPLETED" -> "ရုံး/ကောင်တာမှ ငွေရှင်းပြီးမှ ပစ္စည်းပြန်ပေးနိုင်ပါမယ်"
    "DELIVERED" -> "Job ပြီးဆုံးပြီး ပစ္စည်းပြန်ပေးထားပါပြီ"
    "CANCELLED" -> "Job ပယ်ဖျက်ထားပါသည်"
    else -> "Job အချက်အလက်ကို စစ်ဆေးပါ"
}

@Composable
private fun TechnicianJobWorkflowGuide(
    job: ServiceJobDTO,
    loading: Boolean,
    onApproveEstimate: () -> Unit,
    onHoldEstimate: () -> Unit,
    onRejectEstimate: () -> Unit
) {
    val current = job.status?.uppercase().orEmpty()
    val currentIndex = TECHNICIAN_JOB_STEPS.indexOf(current)
    val displayIndex = if (current == "WAITING_PARTS") TECHNICIAN_JOB_STEPS.indexOf("IN_PROGRESS") else currentIndex
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
        border = BorderStroke(1.dp, Color(0xFFC7D2FE))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Job လုပ်ငန်းစဉ်", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF312E81))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TECHNICIAN_JOB_STEPS.forEachIndexed { index, status ->
                    val done = displayIndex >= 0 && index < displayIndex
                    val active = status == current || (current == "WAITING_PARTS" && status == "IN_PROGRESS")
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (done) Success else if (active) Primary else Color.White,
                            border = if (!done && !active) BorderStroke(1.dp, BorderColor) else null,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (done) "✓" else "${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = if (done || active) Color.White else TextMuted)
                            }
                        }
                        Text(
                            when (status) { "RECEIVED" -> "လက်ခံ"; "INSPECTING" -> "စစ်ဆေး"; "IN_PROGRESS" -> "ပြင်ဆင်"; "COMPLETED" -> "ပြီးစီး"; else -> "ပြန်ပေး" },
                            fontSize = 9.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (active) Primary else TextMuted
                        )
                    }
                }
            }
            Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("နောက်လုပ်ရန်", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                    Text(technicianNextAction(job), fontSize = 12.sp, color = TextMain)
                    if ((job.estimatedCost ?: 0.0) > 0 && job.estimateApproved != true
                        && job.status?.uppercase() !in listOf("DELIVERED", "CANCELLED")) {
                        EstimateActionSection(
                            isOnHold = jobHasEstimateHold(job),
                            loading = loading,
                            onApprove = onApproveEstimate,
                            onHoldEstimate = onHoldEstimate,
                            onRejectEstimate = onRejectEstimate
                        )
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceJobDetailScreen(
    onBack:    () -> Unit,
    onEdit:    () -> Unit = {},
    onPrint:   () -> Unit = {},
    onDeleted: () -> Unit = {},
    onOpenActiveVisit: (Int) -> Unit = {}
) {
    val vm: ServiceJobDetailViewModel = viewModel()
    val visitVm: TechnicianVisitViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val visit by visitVm.visit.collectAsStateWithLifecycle()
    val visitBusy by visitVm.busy.collectAsStateWithLifecycle()
    val visitMessage by visitVm.message.collectAsStateWithLifecycle()
    val pendingResume by VisitTracker.pendingResume.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canAssignTechnician = remember {
        PreferenceManager(context).hasPermission("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN")
    }
    var pendingVisitAction by remember { mutableStateOf<String?>(null) }
    var showReasonDialog by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!ok) return@rememberLauncherForActivityResult
        when (pendingVisitAction) {
            "start:SERVICE", "start:PICKUP", "start:DELIVERY", "start:FOLLOW_UP" ->
                pendingVisitAction?.let { action -> state.job?.id?.let { visitVm.start(it, action.substringAfter(":")) } }
            "arrive" -> visitVm.arrive()
            "depart:FIXED_ON_SITE", "depart:BROUGHT_TO_SHOP", "depart:PARTS_REQUIRED",
            "depart:RESCHEDULED", "depart:CUSTOMER_UNAVAILABLE" ->
                pendingVisitAction?.let { visitVm.departCustomer(it.substringAfter(":")) }
            "end" -> visitVm.end()
            "journeyResume" -> visitVm.resumeJourney()
            "resume" -> visitVm.resumeTracking()
        }
        pendingVisitAction = null
    }

    fun runVisit(action: String) {
        if (LocationPermission.granted(context)) {
            when (action) {
                "start:SERVICE", "start:PICKUP", "start:DELIVERY", "start:FOLLOW_UP" ->
                    state.job?.id?.let { visitVm.start(it, action.substringAfter(":")) }
                "arrive" -> visitVm.arrive()
                "depart:FIXED_ON_SITE", "depart:BROUGHT_TO_SHOP", "depart:PARTS_REQUIRED",
                "depart:RESCHEDULED", "depart:CUSTOMER_UNAVAILABLE" ->
                    visitVm.departCustomer(action.substringAfter(":"))
                "end" -> visitVm.end()
                "journeyResume" -> visitVm.resumeJourney()
                "resume" -> visitVm.resumeTracking()
            }
        } else {
            pendingVisitAction = action
            locationPermission.launch(LocationPermission.required)
        }
    }

    LaunchedEffect(visitMessage) {
        visitMessage?.let { snackbar.showSnackbar(it); visitVm.clearMessage() }
    }
    LaunchedEffect(visit?.needsReason, visit?.id) {
        if (visit?.needsReason == true && visit?.status in listOf("EN_ROUTE", "RETURNING")) {
            showReasonDialog = true
        }
    }

    LaunchedEffect(state.actionSuccess) {
        state.actionSuccess?.let { snackbar.showSnackbar(it); vm.clearActionSuccess() }
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let { snackbar.showSnackbar(it); vm.clearActionError() }
    }

    if (showReasonDialog && visit?.status in listOf("EN_ROUTE", "RETURNING")) {
        LongStopReasonDialog(
            onDismiss = { showReasonDialog = false },
            onSubmit = { code, note ->
                visitVm.addReason(code, note)
                showReasonDialog = false
            }
        )
    }

    // ── Delete confirm dialog ─────────────────────────────────────────────────
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.deleteLoading) vm.dismissDeleteDialog() },
            icon  = { Icon(Icons.Outlined.DeleteForever, null, tint = Danger) },
            title = { Text("Job ဖျက်မည်", fontWeight = FontWeight.ExtraBold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Job \"${state.job?.jobNo ?: "#${state.job?.id}"}\" ကို ဖျက်မည်။ ဆက်လက်မည်လား?",
                        fontSize = 14.sp
                    )
                    state.actionError?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 12.sp, color = Danger)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick  = { vm.delete { onDeleted() } },
                    colors   = ButtonDefaults.buttonColors(containerColor = Danger),
                    enabled  = !state.deleteLoading
                ) {
                    if (state.deleteLoading)
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else
                        Text("ဖျက်မည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissDeleteDialog() }, enabled = !state.deleteLoading) {
                    Text("မဖျက်ပါ")
                }
            }
        )
    }

    if (state.showSettleDialog) {
        SettleDialog(
            job            = state.job,
            paymentMethods = state.paymentMethods,
            loading        = state.actionLoading,
            onDismiss      = { vm.dismissSettleDialog() },
            onSettle       = { cost, disc, foc, paid, mid, txn, due, payments, alloc ->
                vm.settle(cost, disc, foc, paid, mid, txn, due, payments, alloc)
            }
        )
    }

    if (state.showPayDueDialog) {
        JobPayDueDialog(
            dueAmount      = state.job?.dueAmount ?: 0.0,
            paymentMethods = state.paymentMethods,
            loading        = state.actionLoading,
            onDismiss      = { vm.dismissPayDueDialog() },
            onPay          = { amt, mid, txn, note, payments, discount, discountNote ->
                vm.payDue(amt, mid, txn, note, payments, discount, discountNote)
            }
        )
    }

    if (state.showDueDeliveryDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.dismissDueDeliveryDialog() },
            title = { Text("Due Delivery Approval", fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Approval reason *") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.confirmDueDeliveryAndDeliver(reason) },
                    enabled = !state.actionLoading && reason.isNotBlank()
                ) { Text("အတည်ပြုပြီး ပေးအပ်မည်") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissDueDeliveryDialog() }) { Text("မလုပ်တော့ပါ") } }
        )
    }

    if (state.showHoldDialog) {
        var hold by remember { mutableStateOf(state.job?.holdReason ?: "") }
        AlertDialog(
            onDismissRequest = { vm.dismissHoldDialog() },
            title = { Text("ပစ္စည်းစောင့်", fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = hold, onValueChange = { hold = it },
                    label = { Text("အကြောင်းအရာ") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { vm.updateStatus("WAITING_PARTS", hold.ifBlank { "Waiting for parts" }) }, enabled = !state.actionLoading) {
                    Text("သိမ်းမည်")
                }
            },
            dismissButton = { TextButton(onClick = { vm.dismissHoldDialog() }) { Text("မလုပ်တော့ပါ") } }
        )
    }

    var showEstimateHoldDialog by remember { mutableStateOf(false) }
    var showEstimateRejectDialog by remember { mutableStateOf(false) }
    if (showEstimateHoldDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEstimateHoldDialog = false },
            title = { Text("Estimate Hold", fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("မှတ်ချက်") },
                    placeholder = { Text("Customer ပြန်ဆက်သွယ်ရန်") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.holdEstimate(reason); showEstimateHoldDialog = false },
                    enabled = !state.actionLoading
                ) { Text("Hold ထားမည်") }
            },
            dismissButton = { TextButton(onClick = { showEstimateHoldDialog = false }) { Text("မလုပ်တော့ပါ") } }
        )
    }
    if (showEstimateRejectDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEstimateRejectDialog = false },
            title = { Text("Estimate ငြင်းပယ်", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Customer မလုပ်ဘူးဟု ဆုံးဖြတ်ပါက Job ကို ပယ်ဖျက်မည်", fontSize = 12.sp, color = TextMuted)
                    OutlinedTextField(
                        value = reason, onValueChange = { reason = it },
                        label = { Text("အကြောင်းရင်း") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.rejectEstimate(reason); showEstimateRejectDialog = false },
                    enabled = !state.actionLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("ငြင်းပယ်မည်") }
            },
            dismissButton = { TextButton(onClick = { showEstimateRejectDialog = false }) { Text("မလုပ်တော့ပါ") } }
        )
    }

    if (state.showVoidDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.dismissVoidDialog() },
            title = { Text("Settlement ပြန်ဖျက်မည်", fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("အကြောင်းအရာ *") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { if (reason.isNotBlank()) vm.voidSettlement(reason.trim()) },
                    enabled = reason.isNotBlank() && !state.actionLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("Void") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissVoidDialog() }) { Text("မလုပ်တော့ပါ") } }
        )
    }

    if (state.showNotifyDialog) {
        var channel by remember { mutableStateOf("CALL") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.dismissNotifyDialog() },
            title = { Text("ဖောက်သည် အကြောင်းကြား", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CALL" to "ဖုန်း", "SMS" to "SMS", "NOTE" to "မှတ်ချက်").forEach { (k, l) ->
                            FilterChip(selected = channel == k, onClick = { channel = k }, label = { Text(l, fontSize = 11.sp) })
                        }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("မှတ်ချက်") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { vm.notifyCustomer(channel, note.ifBlank { "Ready for pickup" }) }, enabled = !state.actionLoading) {
                    Text("မှတ်တမ်းတင်မည်")
                }
            },
            dismissButton = { TextButton(onClick = { vm.dismissNotifyDialog() }) { Text("မလုပ်တော့ပါ") } }
        )
    }

    if (state.showCreditDialog) {
        var amount by remember { mutableStateOf("") }
        val due = state.job?.dueAmount ?: 0.0
        val max = minOf(due, state.creditBalance)
        AlertDialog(
            onDismissRequest = { vm.dismissCreditDialog() },
            title = { Text("ဖောက်သည် credit သုံးမည်", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Advance: ${String.format("%,.0f", state.creditBalance)} Ks", fontSize = 13.sp)
                    Text("ကျန်ငွေ: ${String.format("%,.0f", due)} Ks", fontSize = 13.sp, color = Danger)
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it },
                        label = { Text("သုံးမည့်ပမာဏ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val staffId = state.job?.assignedStaffId ?: state.staff.firstOrNull()?.id
                Button(
                    onClick = {
                        val v = amount.toDoubleOrNull() ?: 0.0
                        if (staffId != null && v > 0) vm.applyCredit(v.coerceAtMost(max), staffId, "Job credit apply")
                    },
                    enabled = !state.actionLoading && max > 0
                ) { Text("သုံးမည်") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissCreditDialog() }) { Text("မလုပ်တော့ပါ") } }
        )
    }

    if (state.showReworkDialog) {
        ReworkDialog(
            job = state.job,
            staff = state.staff,
            paymentMethods = state.paymentMethods,
            loading = state.actionLoading,
            onDismiss = { vm.dismissReworkDialog() },
            onSubmit = { vm.createRework(it) }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.job?.jobNo ?: "Job အသေးစိတ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White) }
                    IconButton(onClick = {
                        val job = state.job
                        if (!canAssignTechnician && (job?.canEditJob == false || job?.myAssignmentStatus.equals("PENDING", true))) {
                            scope.launch {
                                snackbar.showSnackbar("Assignment လက်ခံပြီးမှသာ Job ပြင်ဆင်နိုင်ပါသည်")
                            }
                        } else {
                            onEdit()
                        }
                    }) { Icon(Icons.Outlined.Edit, "ပြင်ရန်", tint = Color.White) }
                    IconButton(onClick = onPrint)      { Icon(Icons.Outlined.Print,   "ပရင့်", tint = Color.White) }
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

        val job = state.job ?: run {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("ဒေတာ မတွေ့ပါ", color = TextMuted)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Header card ───────────────────────────────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(job.jobNo ?: "#${job.id}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Violet)
                            JobDetailStatusBadge(job.status)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(job.receivedDate?.take(16)?.replace("T", "  ") ?: "—", fontSize = 12.sp, color = TextMuted)
                        if (job.rework == true) {
                            Spacer(Modifier.height(6.dp))
                            Surface(color = WarningBg, shape = RoundedCornerShape(6.dp)) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Replay, null, tint = Warning, modifier = Modifier.size(12.dp))
                                    Text("Rework Job", fontSize = 10.sp, color = Warning, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (job.serviceMode == "OUTDOOR") {
                item {
                    OutdoorVisitCard(
                        jobId = job.id,
                        visit = visit,
                        busy = visitBusy,
                        pendingResume = pendingResume && visit?.jobId == job.id,
                        canStart = visitVm.canStart,
                        onStart = { purpose -> runVisit("start:$purpose") },
                        onArrive = { runVisit("arrive") },
                        onDepartCustomer = { outcome -> runVisit("depart:$outcome") },
                        onEnd = { runVisit("end") },
                        onJourneyResume = { runVisit("journeyResume") },
                        onResume = { runVisit("resume") },
                        onCancel = { visitVm.cancel("WRONG_VISIT") },
                        onReason = { showReasonDialog = true },
                        onOpenActiveVisit = onOpenActiveVisit
                    )
                }

                item {
                    val thisJobVisit = visit?.takeIf { it.jobId == job.id }
                    CustomerRouteMap(
                        customerName = job.customerName ?: "Customer",
                        destinationLatitude = job.customerLatitude ?: thisJobVisit?.customerLatitude,
                        destinationLongitude = job.customerLongitude ?: thisJobVisit?.customerLongitude,
                        visit = thisJobVisit
                    )
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Text(
                            "Indoor Service Job — GPS Map နှင့် Outdoor Tracking မလိုအပ်ပါ",
                            modifier = Modifier.padding(14.dp),
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // ── Info card ─────────────────────────────────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        JobInfoRow(Icons.Outlined.Person,  "ဖောက်သည်",    job.customerName ?: "—")
                        if (!job.customerPhone.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            JobInfoRow(Icons.Outlined.Phone, "ဖုန်း",       job.customerPhone)
                        }
                        HorizontalDivider(color = BorderColor)
                        JobInfoRow(Icons.Outlined.Badge,   "နည်းပညာဆရာ", job.assignedStaffName ?: "—")
                        HorizontalDivider(color = BorderColor)
                        JobInfoRow(Icons.Outlined.Devices, "ပစ္စည်း",      job.itemName ?: "—")
                        if (!job.shelfLocationCode.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            JobInfoRow(
                                Icons.Outlined.Inventory2,
                                "ထားသည့်နေရာ",
                                listOfNotNull(job.shelfLocationCode, job.shelfLocationLabel?.takeIf { it.isNotBlank() }).joinToString(" - ")
                            )
                        }
                        if (!job.serialNo.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            JobInfoRow(Icons.Outlined.Numbers, "Serial No", job.serialNo)
                        }
                        if (!job.color.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            JobInfoRow(Icons.Outlined.Palette, "အရောင်", job.color)
                        }
                        if (!job.itemCondition.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            JobInfoRow(Icons.Outlined.Info, "အခြေအနေ",    job.itemCondition)
                        }
                        if (!job.partRequests.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            JobInfoRow(Icons.Outlined.Build, "Part လိုအပ်ချက်", formatPartRequests(job.partRequests))
                        }
                        if (!job.problemDesc.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            JobInfoRow(Icons.Outlined.ReportProblem, "ပြဿနာ", job.problemDesc)
                        }
                        if (!job.diagnosisNotes.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            JobInfoRow(Icons.Outlined.Description, "စစ်ဆေးချက်", job.diagnosisNotes)
                        }
                        if (!job.estimatedCompletion.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            JobInfoRow(Icons.Outlined.Schedule, "ပြီးမည့်ချိန်", job.estimatedCompletion.take(16).replace("T", "  "))
                        }
                        if (!job.bookingNo.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            JobInfoRow(Icons.Outlined.ConfirmationNumber, "Booking", job.bookingNo)
                        }
                    }
                }
            }

            // ── Status chips ──────────────────────────────────────────────────
            item {
                TechnicianJobWorkflowGuide(
                    job = job,
                    loading = state.actionLoading,
                    onApproveEstimate = { vm.approveEstimate() },
                    onHoldEstimate = { showEstimateHoldDialog = true },
                    onRejectEstimate = { showEstimateRejectDialog = true }
                )
            }
            item {
                TechnicianAssignmentSection(
                    team = state.team,
                    teamError = state.teamError,
                    myStaffId = vm.myStaffId,
                    loading = state.actionLoading,
                    onAccept = vm::acceptAssignment,
                    onReject = vm::rejectAssignment,
                    onWork = { id, action, work, service, parts, note ->
                        vm.recordWork(id, action, note, work, service, parts)
                    }
                )
            }
            item {
                ServiceJobHandoverSection(
                    team = state.team,
                    assignments = state.team?.assignments.orEmpty(),
                    staff = state.staff,
                    myStaffId = vm.myStaffId,
                    canSupervise = vm.canSupervise,
                    loading = state.actionLoading,
                    onAccept = vm::acceptHandover,
                    onReject = vm::rejectHandover,
                    onRequest = vm::requestHandover
                )
            }
            item {
                ServiceJobFinalCheckSection(
                    team = state.team,
                    loading = state.actionLoading,
                    canSupervise = vm.canSupervise,
                    onLeadFinalCheck = vm::submitLeadFinalCheck,
                    onApproveFinal = vm::approveFinal,
                    onReturnForRework = vm::returnFinalCheck
                )
            }
            item {
                Text("အဆင့် ပြောင်းရန်", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
            }
            item {
                val statuses = listOf(
                    "RECEIVED"      to "လက်ခံပြီး",
                    "INSPECTING"    to "စစ်ဆေးဆဲ",
                    "IN_PROGRESS"   to "လုပ်ဆဲ",
                    "WAITING_PARTS" to "ပစ္စည်းစောင့်",
                    "COMPLETED"     to "ပြီးဆုံး",
                    "DELIVERED"     to "ပြန်ပေးပြီး"
                )
                val canChange = job.status?.uppercase() !in listOf("DELIVERED", "CANCELLED")
                val currentStatus = job.status?.uppercase().orEmpty()
                val allowedStatuses = allowedNextJobStatuses(currentStatus)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statuses.filter { (key, _) -> key == currentStatus || key in allowedStatuses }.forEach { (key, label) ->
                        val isCurrent = currentStatus == key
                        val blockedByEstimate = key == "IN_PROGRESS" && (
                            ((job.estimatedCost ?: 0.0) > 0 && job.estimateApproved != true) ||
                            job.lines.orEmpty().any { it.confirmationStatus == "CUSTOMER_HOLD" }
                            )
                        val due = job.dueAmount ?: 0.0
                        val canDeliverWithDue = state.serviceAllowDeliveryWithDue && !job.dueDeliveryApprovedAt.isNullOrBlank()
                        val blockedByPayment = key == "DELIVERED" && job.foc != true && (
                            job.paymentStatus.isNullOrBlank() || (due > 0.0 && !canDeliverWithDue)
                            )
                        FilterChip(
                            selected = isCurrent,
                            onClick  = {
                                if (!isCurrent && canChange && !state.actionLoading) {
                                    when (key) {
                                        "WAITING_PARTS" -> vm.showHoldDialog()
                                        "DELIVERED" -> vm.deliver()
                                        else -> vm.updateStatus(key)
                                    }
                                }
                            },
                            enabled = canChange && !state.actionLoading && ((!blockedByEstimate && !blockedByPayment) || isCurrent),
                            label    = { Text(label, fontSize = 10.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (key) {
                                    "COMPLETED", "DELIVERED" -> SuccessBg
                                    "CANCELLED" -> DangerBg
                                    "WAITING_PARTS" -> WarningBg
                                    "IN_PROGRESS" -> VioletBg
                                    else -> WarningBg
                                },
                                selectedLabelColor = when (key) {
                                    "COMPLETED", "DELIVERED" -> Success
                                    "CANCELLED" -> Danger
                                    "WAITING_PARTS" -> Warning
                                    "IN_PROGRESS" -> Violet
                                    else -> Warning
                                }
                            )
                        )
                    }
                }
                if (allowedStatuses.contains("IN_PROGRESS") && job.lines.orEmpty().any { it.confirmationStatus == "CUSTOMER_HOLD" }) {
                    Text("Estimate Hold ဖြေရှင်းပြီးမှ ‘လုပ်ဆောင်ဆဲ’ ကို ရွေးနိုင်ပါမယ်", fontSize = 11.sp, color = Violet, fontWeight = FontWeight.Bold)
                } else if (allowedStatuses.contains("IN_PROGRESS") && (job.estimatedCost ?: 0.0) > 0 && job.estimateApproved != true) {
                    Text("Estimate အတည်ပြုပြီးမှ ‘လုပ်ဆောင်ဆဲ’ ကို ရွေးနိုင်ပါမယ်", fontSize = 11.sp, color = Warning, fontWeight = FontWeight.Bold)
                }
                val due = job.dueAmount ?: 0.0
                val canDeliverWithDue = state.serviceAllowDeliveryWithDue && !job.dueDeliveryApprovedAt.isNullOrBlank()
                if (allowedStatuses.contains("DELIVERED") && job.foc != true && (job.paymentStatus.isNullOrBlank() || (due > 0.0 && !canDeliverWithDue))) {
                    Text(
                        if (due > 0.0 && state.serviceAllowDeliveryWithDue) "Due delivery approval ရပြီးမှ ‘ပြန်ပေးပြီး’ ရွေးနိုင်ပါမယ်"
                        else "ငွေရှင်းပြီးမှ ‘ပြန်ပေးပြီး’ ကို ရွေးနိုင်ပါမယ်",
                        fontSize = 11.sp, color = Warning, fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Services ──────────────────────────────────────────────────────
            if (!job.lines.isNullOrEmpty()) {
                item {
                    Text("ဝန်ဆောင်မှုများ (${job.lines.size})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                }
                items(job.lines) { line -> ServiceLineCard(line) }
            }

            // ── Parts ─────────────────────────────────────────────────────────
            if (!job.productParts.isNullOrEmpty()) {
                item {
                    Text("အပိုပစ္စည်းများ (${job.productParts.size})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                }
                items(job.productParts) { part -> PartCard(part, state.serialWarrantyMap) }
            }

            if (!job.attachments.isNullOrEmpty()) {
                item {
                    Text("Attachments (${job.attachments.size})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                }
                items(job.attachments, key = { it.id ?: it.fileName.orEmpty() }) { attachment ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.AttachFile, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(attachment.fileName ?: "Attachment", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                Text(attachment.attachmentType ?: attachment.contentType ?: "File", fontSize = 11.sp, color = TextMuted)
                            }
                            attachment.id?.let { attachmentId ->
                                IconButton(onClick = { vm.deleteAttachment(attachmentId) }, enabled = !state.actionLoading) {
                                    Icon(Icons.Outlined.Delete, "Delete attachment", tint = Danger)
                                }
                            }
                        }
                    }
                }
            }

            // ── Summary ───────────────────────────────────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if ((job.estimatedCost ?: 0.0) > 0)
                            JobSummaryRow("ခန့်မှန်းကိုန်",  "${job.estimatedCost.fmtD()} Ks", TextMuted)
                        if ((job.finalCost ?: 0.0) > 0)
                            JobSummaryRow("နောက်ဆုံးကိုန်",  "${job.finalCost.fmtD()} Ks", TextMain)
                        if ((job.discountAmount ?: 0.0) > 0)
                            JobSummaryRow("လျှော့ငွေ",       "-${job.discountAmount.fmtD()} Ks", Warning)
                        HorizontalDivider(color = BorderColor)
                        JobSummaryRow("စုစုပေါင်း",          "${job.netAmount.fmtD()} Ks", Primary, bold = true)
                        JobSummaryRow("ပေးပြီး",             "${job.paidAmount.fmtD()} Ks", Success)
                        if ((job.dueAmount ?: 0.0) > 0)
                            JobSummaryRow("ကျန်ငွေ",          "${job.dueAmount.fmtD()} Ks", Danger, bold = true)
                        if ((job.paymentDiscountAmount ?: 0.0) > 0)
                            JobSummaryRow("Payment Discount", "-${job.paymentDiscountAmount.fmtD()} Ks", Warning)
                        if (!job.dueDeliveryApprovedAt.isNullOrBlank())
                            JobSummaryRow("Due Delivery OK", job.dueDeliveryApprovedBy ?: "Approved", Success)
                        if (job.foc == true) {
                            Surface(color = SuccessBg, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("FREE OF CHARGE", modifier = Modifier.padding(8.dp),
                                    fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Success)
                            }
                        }
                    }
                }
            }

            // ── Credit / Due info card ────────────────────────────────────────
            if ((job.dueAmount ?: 0.0) > 0) {
                item {
                    Card(
                        shape  = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DangerBg),
                        border = BorderStroke(1.5.dp, Danger.copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.CreditCard, null, tint = Danger, modifier = Modifier.size(18.dp))
                                Text("ကြွေးကျန် အချက်အလက်", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Danger, letterSpacing = 0.5.sp)
                            }
                            HorizontalDivider(color = Danger.copy(alpha = 0.2f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ကျန်ငွေ", fontSize = 13.sp, color = Danger, fontWeight = FontWeight.Bold)
                                Text("${job.dueAmount.fmtD()} Ks", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                            }
                            if (!job.dueDate.isNullOrBlank()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.CalendarMonth, null, tint = Danger, modifier = Modifier.size(14.dp))
                                        Text("ဆပ်ရမည့်ရက်", fontSize = 12.sp, color = Danger)
                                    }
                                    Text(job.dueDate.take(10), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Danger)
                                }
                            }
                            if (!job.paymentMethodName.isNullOrBlank()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.AccountBalance, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                        Text("ငွေပေးချေနည်း", fontSize = 12.sp, color = TextMuted)
                                    }
                                    Text(job.paymentMethodName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                }
                            }
                        }
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────────────────
            item {
                ServiceJobActivitySection(activities = job.activities)
            }
            // Settle button — only when not yet settled (no due amount means it's a fresh settle)
            if (job.status?.uppercase() == "COMPLETED"
                && (job.paymentStatus?.uppercase() != "PAID" || job.netAmount == null)
                && (job.dueAmount ?: 0.0) == 0.0) {
                item {
                    Button(
                        onClick = { vm.showSettleDialog() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        enabled = !state.actionLoading
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ငွေချေ / Settle လုပ်ရန်", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            if ((job.dueAmount ?: 0.0) > 0 && job.status?.uppercase() != "CANCELLED") {
                item {
                    Button(
                        onClick = { vm.showPayDueDialog() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                        enabled = !state.actionLoading
                    ) {
                        Icon(Icons.Outlined.Payment, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ကျန်ငွေ ${job.dueAmount.fmtD()} Ks ဆပ်မည်", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { vm.showCreditDialog() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !state.actionLoading
                    ) {
                        Icon(Icons.Outlined.AccountBalanceWallet, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ဖောက်သည် credit သုံးမည်", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (job.status?.uppercase() == "DELIVERED") {
                item {
                    Button(
                        onClick = { vm.showReworkDialog() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Warning),
                        enabled = !state.actionLoading
                    ) {
                        Icon(Icons.Outlined.Replay, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Rework / Warranty ပြန်ပြင်", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            if (job.paymentStatus != null && job.status?.uppercase() != "DELIVERED" && job.voided != true) {
                item {
                    OutlinedButton(
                        onClick = { vm.showVoidDialog() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                        enabled = !state.actionLoading
                    ) {
                        Text("Settlement ပြန်ဖျက် (Void)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (job.status?.uppercase() == "COMPLETED") {
                item {
                    OutlinedButton(
                        onClick = { vm.showNotifyDialog() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !state.actionLoading
                    ) { Text("ဖောက်သည် အကြောင်းကြားမှတ်တမ်း", fontWeight = FontWeight.Bold) }
                }
            }

            if (!job.remark.isNullOrBlank()) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("မှတ်ချက်", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Warning, letterSpacing = 0.5.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(job.remark, fontSize = 13.sp, color = TextMain)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun JobDetailStatusBadge(status: String?) {
    val (bg, color, label) = when (status?.uppercase()) {
        "RECEIVED"    -> Triple(WarningBg,  Warning, "လက်ခံပြီး")
        "INSPECTING"  -> Triple(VioletBg,   Violet,  "စစ်ဆေးဆဲ")
        "IN_PROGRESS" -> Triple(VioletBg,   Violet,  "လုပ်ဆဲ")
        "WAITING_PARTS" -> Triple(WarningBg, Warning, "ပစ္စည်းစောင့်")
        "COMPLETED"   -> Triple(SuccessBg,  Success, "ပြီးဆုံး")
        "DELIVERED"   -> Triple(SuccessBg,  Success, "ပြန်ပေးပြီး")
        "CANCELLED"   -> Triple(DangerBg,   Danger,  "ပယ်ဖျက်")
        else          -> Triple(BorderColor, TextMuted, status ?: "—")
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun JobInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(15.dp).padding(top = 1.dp))
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(90.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun JobSummaryRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = if (bold) color else TextMuted, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal)
        Text(value, fontSize = 13.sp, color = color, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold)
    }
}

@Composable
private fun ServiceLineCard(line: ServiceJobLineDTO) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.serviceItemName ?: "—", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Text("${line.qty ?: 1} × ${line.price.fmtD()} Ks", fontSize = 11.sp, color = TextMuted)
                lineConfirmationLabel(line.confirmationStatus)?.let {
                    Text(it, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lineConfirmationColor(line.confirmationStatus))
                }
                val lineWLabel = fmtWarranty(line.warrantyMonths)
                if (lineWLabel.isNotEmpty())
                    Text("အာမခံ: $lineWLabel", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color(0xFF0891B2))
            }
            Text(
                if (line.confirmationStatus == "CUSTOMER_REJECTED" || line.confirmationStatus == "CUSTOMER_HOLD") "0"
                else "${line.subtotal.fmtD()} Ks",
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = if (line.confirmationStatus == "CUSTOMER_REJECTED") Danger else Violet
            )
        }
    }
}

private fun jobHasEstimateHold(job: ServiceJobDTO) =
    job.lines.orEmpty().any { it.confirmationStatus == "CUSTOMER_HOLD" }

private fun lineConfirmationLabel(status: String?): String? = when (status) {
    "CUSTOMER_HOLD" -> "Customer စောင့်ဆိုင်း"
    "CUSTOMER_REJECTED" -> "Customer ငြင်းပယ်"
    "CUSTOMER_APPROVED" -> "Customer အတည်ပြုပြီး"
    "RECOMMENDED" -> "အကြံပြုထားသည်"
    "INSPECTING" -> "စစ်ဆေးဆဲ"
    "IN_PROGRESS" -> "လုပ်ဆောင်ဆဲ"
    "COMPLETED" -> "ပြီးစီး"
    else -> null
}

private fun lineConfirmationColor(status: String?) = when (status) {
    "CUSTOMER_HOLD" -> Violet
    "CUSTOMER_REJECTED" -> Danger
    "CUSTOMER_APPROVED" -> Success
    else -> TextMuted
}

@Composable
private fun EstimateActionSection(
    isOnHold: Boolean,
    loading: Boolean,
    onApprove: () -> Unit,
    onHoldEstimate: () -> Unit,
    onRejectEstimate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isOnHold) {
            Surface(color = VioletBg, shape = RoundedCornerShape(10.dp)) {
                Text(
                    "Customer ဆုံးဖြတ်ချက် စောင့်ဆိုင်းနေသည်",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Violet
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onApprove,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !loading
            ) { Text("အတည်ပြု", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            OutlinedButton(
                onClick = onHoldEstimate,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !loading
            ) { Text("Hold", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            OutlinedButton(
                onClick = onRejectEstimate,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !loading,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
            ) { Text("ငြင်းပယ်", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun PartCard(
    part:  ServiceJobPartDTO,
    snMap: Map<String, ProductSerialDTO> = emptyMap()
) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(part.productName ?: "—", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    if (!part.productCode.isNullOrBlank())
                        Text(part.productCode, fontSize = 10.sp, color = TextMuted)
                    Text("${part.qty ?: 1} × ${part.unitPrice.fmtD()} Ks", fontSize = 11.sp, color = TextMuted)
                }
                Text("${part.subtotal.fmtD()} Ks", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
            }
            if (!part.serialNumbers.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                part.serialNumbers.forEach { sn ->
                    val wLabel = fmtWarranty(snMap[sn]?.warrantyMonths)
                    Text("S/N: $sn", fontSize = 10.sp, color = Primary)
                    if (wLabel.isNotEmpty()) {
                        Text("🛡 $wLabel", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color(0xFF0891B2))
                    }
                }
            }
        }
    }
}

// ── Settle Dialog ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleDialog(
    job:            ServiceJobDTO?,
    paymentMethods: List<PaymentMethodDTO>,
    loading:        Boolean,
    onDismiss:      () -> Unit,
    onSettle:       (finalCost: Double, discount: Double, foc: Boolean, paid: Double, methodId: Int?, txnNo: String?, dueDate: String?, payments: List<PaymentTransactionDTO>?, discountAllocationMethod: String) -> Unit
) {
    val defaultCost = job?.estimatedCost ?: job?.netAmount ?: 0.0
    var costStr     by remember { mutableStateOf(String.format("%.0f", defaultCost)) }
    var discountStr by remember { mutableStateOf("0") }
    var allocMethod by remember { mutableStateOf(job?.discountAllocationMethod ?: "PRO_RATA") }
    var foc         by remember { mutableStateOf(false) }
    var paidStr     by remember { mutableStateOf("") }
    var txnNo       by remember { mutableStateOf("") }
    var selectedPm  by remember { mutableStateOf<PaymentMethodDTO?>(null) }
    var splitPayments by remember { mutableStateOf<List<PaymentTransactionDTO>>(emptyList()) }
    var showSheet   by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var dueDate     by remember { mutableStateOf("") }
    var error       by remember { mutableStateOf("") }

    val net     = ((costStr.toDoubleOrNull() ?: 0.0) - (discountStr.toDoubleOrNull() ?: 0.0)).coerceAtLeast(0.0)
    val splitPaid = splitPayments.sumOf { it.amount ?: 0.0 }
    val paid    = if (foc) 0.0 else if (splitPayments.isNotEmpty()) splitPaid else (paidStr.toDoubleOrNull() ?: 0.0)
    val balance = (net - paid).coerceAtLeast(0.0)

    // Due date picker
    if (showDuePicker) {
        val dpState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDate = dpState.selectedDateMillis?.let {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                            .format(java.util.Date(it))
                    } ?: ""
                    showDuePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDuePicker = false }) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = dpState) }
    }

    // Payment method sheet
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("ငွေပေးချေမှု နည်းလမ်း", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                paymentMethods.forEach { pm ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedPm = pm; showSheet = false }.padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pm.methodName, fontSize = 14.sp, color = TextMain)
                        if (selectedPm?.id == pm.id) Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BorderColor)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Primary, modifier = Modifier.size(22.dp))
                Text("ငွေချေ / Settle", fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // ── Cost & Discount ───────────────────────────────────────────
                OutlinedTextField(
                    value = costStr, onValueChange = { costStr = it; error = "" },
                    label = { Text("နောက်ဆုံးကိုန် (Ks)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    enabled = !foc
                )
                OutlinedTextField(
                    value = discountStr, onValueChange = { discountStr = it },
                    label = { Text("လျှော့ငွေ (Ks)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    enabled = !foc
                )
                if (!foc) {
                    Text("လျှော့ငွေ ခွဲဝေမှု", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("PRO_RATA" to "အချိုး", "LABOR_FIRST" to "လုပ်အား", "PARTS_FIRST" to "ပစ္စည်း").forEach { (value, label) ->
                            FilterChip(
                                selected = allocMethod == value,
                                onClick = { allocMethod = value },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                // ── Net display ───────────────────────────────────────────────
                Surface(color = if (foc) SuccessBg else ScreenBg, shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("စုစုပေါင်း", fontSize = 13.sp, color = if (foc) Success else TextMuted)
                        Text(if (foc) "FREE" else "${String.format("%,.0f", net)} Ks",
                            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (foc) Success else Primary)
                    }
                }

                // ── FOC ───────────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = foc, onCheckedChange = {
                        foc = it
                        if (it) { paidStr = "0"; selectedPm = null; txnNo = "" }
                    })
                    Text("FREE OF CHARGE (အခမဲ့)", fontSize = 13.sp, color = if (foc) Success else TextMain)
                }

                if (!foc) {
                    // ── Quick-fill buttons ────────────────────────────────────
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { paidStr = String.format("%.0f", net); error = "" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Success)
                        ) { Text("အပြည့်", fontSize = 12.sp, color = Success, fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { paidStr = "0"; selectedPm = null; txnNo = "" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Warning)
                        ) { Text("အကြွေး", fontSize = 12.sp, color = Warning, fontWeight = FontWeight.Bold) }
                    }

                    // ── Paid amount ───────────────────────────────────────────
                    OutlinedTextField(
                        value = paidStr, onValueChange = { paidStr = it; error = "" },
                        label = { Text("ပေးချေမည့် ပမာဏ (Ks)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(10.dp), isError = error.isNotBlank()
                    )

                    // Balance info
                    if (paid > 0 || paidStr.isNotBlank()) {
                        val balColor = if (balance > 0.01) Danger else Success
                        Surface(color = if (balance > 0.01) DangerBg else SuccessBg, shape = RoundedCornerShape(6.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (balance > 0.01) "ကျန်ငွေ (အကြွေး)" else "ငွေအပြည့်ပေး ✓",
                                    fontSize = 12.sp, color = balColor, fontWeight = FontWeight.Bold)
                                if (balance > 0.01)
                                    Text("${String.format("%,.0f", balance)} Ks",
                                        fontSize = 12.sp, color = balColor, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    // ── Payment method (only when paid > 0) ───────────────────
                    if (paid > 0.0) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().clickable { showSheet = true },
                            shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(selectedPm?.methodName ?: "ငွေပေးချေမှု နည်းလမ်း ရွေးပါ",
                                    color = if (selectedPm != null) TextMain else TextMuted, fontSize = 13.sp)
                                Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }

                        // ── Transaction No ────────────────────────────────────
                        OutlinedTextField(
                            value = txnNo, onValueChange = { txnNo = it },
                            label = { Text("Transaction No (optional)") },
                            leadingIcon = { Icon(Icons.Outlined.Receipt, null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick = {
                                val method = selectedPm ?: return@Button
                                val amount = paidStr.toDoubleOrNull() ?: 0.0
                                if (amount > 0.0) {
                                    val next = splitPayments + PaymentTransactionDTO(
                                        paymentMethodId = method.id,
                                        paymentMethodName = method.methodName,
                                        amount = amount,
                                        transactionNo = txnNo.ifBlank { null }
                                    )
                                    splitPayments = next
                                    paidStr = next.sumOf { it.amount ?: 0.0 }.formatDialogMoney()
                                    txnNo = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add split payment")
                        }
                        splitPayments.forEachIndexed { index, payment ->
                            SplitPaymentRow(payment = payment, onRemove = {
                                val next = splitPayments.filterIndexed { i, _ -> i != index }
                                splitPayments = next
                                paidStr = if (next.isEmpty()) "" else next.sumOf { it.amount ?: 0.0 }.formatDialogMoney()
                            })
                        }
                    }

                    // ── Due date (when balance > 0) ───────────────────────────
                    if (balance > 0.01) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().clickable { showDuePicker = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (dueDate.isNotBlank()) Warning else BorderColor)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CalendarMonth, null,
                                        tint = if (dueDate.isNotBlank()) Warning else TextMuted,
                                        modifier = Modifier.size(16.dp))
                                    Text(if (dueDate.isNotBlank()) "ကြွေးဆပ်ရမည့်ရက် : $dueDate" else "ကြွေးဆပ်ရမည့်ရက် ရွေးပါ",
                                        fontSize = 13.sp,
                                        color = if (dueDate.isNotBlank()) Warning else TextMuted)
                                }
                                Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (error.isNotBlank())
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cost    = costStr.toDoubleOrNull()
                    val disc    = discountStr.toDoubleOrNull() ?: 0.0
                    val paidVal = if (foc) 0.0 else if (splitPayments.isNotEmpty()) splitPayments.sumOf { it.amount ?: 0.0 } else paidStr.toDoubleOrNull()
                    val needPm  = !foc && (paidVal ?: 0.0) > 0
                    when {
                        cost == null || cost < 0     -> error = "ကိုန် မှန်ကန်စွာ ရိုက်ပါ"
                        !foc && paidVal == null      -> error = "ပမာဏ မှန်ကန်စွာ ရိုက်ပါ"
                        needPm && selectedPm == null -> error = "ငွေပေးချေမှု နည်းလမ်း ရွေးပါ"
                        else -> onSettle(
                            cost, disc, foc,
                            paidVal ?: 0.0,
                            selectedPm?.id,
                            txnNo.ifBlank { null },
                            dueDate.ifBlank { null },
                            splitPayments.ifEmpty { null },
                            allocMethod
                        )
                    }
                },
                enabled = !loading,
                colors  = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("အတည်ပြုရန်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("ပယ်ဖျက်") } }
    )
}

// ── Pay Due Dialog ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobPayDueDialog(
    dueAmount:      Double,
    paymentMethods: List<PaymentMethodDTO>,
    loading:        Boolean,
    onDismiss:      () -> Unit,
    onPay:          (amount: Double, methodId: Int, txnNo: String?, note: String?, payments: List<PaymentTransactionDTO>?, paymentDiscount: Double, paymentDiscountApprovalNote: String?) -> Unit
) {
    var amountStr  by remember { mutableStateOf(String.format("%.0f", dueAmount)) }
    var discountStr by remember { mutableStateOf("0") }
    var discountNote by remember { mutableStateOf("") }
    var selectedPm by remember { mutableStateOf<PaymentMethodDTO?>(null) }
    var splitPayments by remember { mutableStateOf<List<PaymentTransactionDTO>>(emptyList()) }
    var txnNo      by remember { mutableStateOf("") }
    var note       by remember { mutableStateOf("") }
    var showSheet  by remember { mutableStateOf(false) }
    var error      by remember { mutableStateOf("") }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("ငွေပေးချေမှု နည်းလမ်း", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                paymentMethods.forEach { pm ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedPm = pm; showSheet = false }.padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pm.methodName, fontSize = 14.sp, color = TextMain)
                        if (selectedPm?.id == pm.id) Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BorderColor)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Payment, null, tint = Danger, modifier = Modifier.size(22.dp))
                Text("ကျန်ငွေ ဆပ်မည်", fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ကျန်ငွေ", fontSize = 12.sp, color = Danger)
                        Text("${String.format("%,.0f", dueAmount)} Ks",
                            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                    }
                }
                // Quick-fill full button
                OutlinedButton(
                    onClick = { amountStr = String.format("%.0f", dueAmount) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Success)
                ) { Text("ကျန်ငွေ အပြည့်ဆပ်မည် (${String.format("%,.0f", dueAmount)} Ks)",
                    fontSize = 12.sp, color = Success, fontWeight = FontWeight.Bold) }

                OutlinedTextField(
                    value = amountStr, onValueChange = { amountStr = it; error = "" },
                    label = { Text("ဆပ်မည့် ပမာဏ (Ks)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp), isError = error.isNotBlank()
                )
                OutlinedTextField(
                    value = discountStr, onValueChange = { discountStr = it; error = "" },
                    label = { Text("Payment Discount (Ks)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = discountNote, onValueChange = { discountNote = it },
                    label = { Text("Discount Approval Note") },
                    enabled = (discountStr.toDoubleOrNull() ?: 0.0) > 0.0,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { showSheet = true },
                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedPm?.methodName ?: "ငွေပေးချေမှု နည်းလမ်း ရွေးပါ",
                            color = if (selectedPm != null) TextMain else TextMuted, fontSize = 13.sp)
                        Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                OutlinedTextField(
                    value = txnNo, onValueChange = { txnNo = it },
                    label = { Text("Transaction No (optional)") },
                    leadingIcon = { Icon(Icons.Outlined.Receipt, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
                Button(
                    onClick = {
                        val method = selectedPm ?: return@Button
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        if (amount > 0.0) {
                            val next = splitPayments + PaymentTransactionDTO(
                                paymentMethodId = method.id,
                                paymentMethodName = method.methodName,
                                amount = amount,
                                transactionNo = txnNo.ifBlank { null }
                            )
                            splitPayments = next
                            amountStr = next.sumOf { it.amount ?: 0.0 }.formatDialogMoney()
                            txnNo = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add split payment")
                }
                splitPayments.forEachIndexed { index, payment ->
                    SplitPaymentRow(payment = payment, onRemove = {
                        val next = splitPayments.filterIndexed { i, _ -> i != index }
                        splitPayments = next
                        amountStr = if (next.isEmpty()) "" else next.sumOf { it.amount ?: 0.0 }.formatDialogMoney()
                    })
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("မှတ်ချက် (optional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
                if (error.isNotBlank())
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val discount = discountStr.toDoubleOrNull() ?: 0.0
                    val amt = if (splitPayments.isNotEmpty()) splitPayments.sumOf { it.amount ?: 0.0 } else amountStr.toDoubleOrNull()
                    when {
                        amt == null || (amt <= 0 && discount <= 0) -> error = "ပေးချေငွေ သို့မဟုတ် discount ထည့်ပါ"
                        amt != null && amt + discount > dueAmount + 0.01 -> error = "ပေးချေငွေနှင့် discount သည် ကျန်ငွေထက် မကျော်ရပါ"
                        discount > 0 && discountNote.isBlank() -> error = "Discount approval note လိုအပ်သည်"
                        amt != null && amt > 0 && selectedPm == null && splitPayments.isEmpty() -> error = "ငွေပေးချေမှု နည်းလမ်း ရွေးပါ"
                        else -> {
                            val methodId = selectedPm?.id ?: splitPayments.firstOrNull()?.paymentMethodId ?: 0
                            onPay(amt ?: 0.0, methodId, txnNo.ifBlank { null }, note.ifBlank { null }, splitPayments.ifEmpty { null }, discount, discountNote.ifBlank { null })
                        }
                    }
                },
                enabled = !loading,
                colors  = ButtonDefaults.buttonColors(containerColor = Danger)
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("ဆပ်မည်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("ပယ်ဖျက်") } }
    )
}

@Composable
private fun SplitPaymentRow(payment: PaymentTransactionDTO, onRemove: () -> Unit) {
    Surface(color = CardBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(payment.paymentMethodName ?: "Payment", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${String.format("%,.0f", payment.amount ?: 0.0)} Ks", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, null, tint = Danger)
            }
        }
    }
}

private fun Double.formatDialogMoney(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun Double?.fmtD() = String.format("%,.0f", this ?: 0.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReworkDialog(
    job: ServiceJobDTO?,
    staff: List<StaffDTO>,
    paymentMethods: List<PaymentMethodDTO>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (ReworkRequestDTO) -> Unit
) {
    var reworkType by remember { mutableStateOf("WARRANTY") }
    var problem by remember { mutableStateOf("") }
    var staffId by remember { mutableStateOf(job?.assignedStaffId) }
    var mode by remember { mutableStateOf("SERVICE_ONLY") }
    var originalPartId by remember { mutableStateOf<Int?>(null) }
    var disposition by remember { mutableStateOf("QUARANTINE") }
    var refundAmt by remember { mutableStateOf("") }
    var refundMethodId by remember { mutableStateOf<Int?>(null) }
    var err by remember { mutableStateOf("") }
    val parts = job?.productParts ?: emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rework / Warranty Job", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("အမျိုးအစား", fontSize = 11.sp, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("WARRANTY" to "အာမခံ", "ADDITIONAL" to "ထပ်ဆောင်း", "REPLACEMENT" to "လဲပေး").forEach { (k, l) ->
                        FilterChip(selected = reworkType == k, onClick = { reworkType = k }, label = { Text(l, fontSize = 10.sp) })
                    }
                }
                OutlinedTextField(
                    value = problem, onValueChange = { problem = it; err = "" },
                    label = { Text("ပြန်လာသည့် ပြဿနာ *") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3
                )
                Text("နည်းပညာဆရာ", fontSize = 11.sp, color = TextMuted)
                staff.take(8).forEach { s ->
                    FilterChip(
                        selected = staffId == s.id,
                        onClick = { staffId = s.id },
                        label = { Text(s.name, fontSize = 11.sp) }
                    )
                }
                Text("ဖြေရှင်းနည်း", fontSize = 11.sp, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("SERVICE_ONLY" to "ပြင်ပေး", "REPLACE_SAME" to "လဲပေး", "REFUND" to "ပြန်အမ်း").forEach { (k, l) ->
                        FilterChip(selected = mode == k, onClick = { mode = k }, label = { Text(l, fontSize = 10.sp) })
                    }
                }
                if (mode != "SERVICE_ONLY") {
                    if (parts.isEmpty()) {
                        Text("မူလ Job တွင် part မရှိပါ — SERVICE_ONLY သုံးပါ", color = Danger, fontSize = 12.sp)
                    } else {
                        parts.forEach { p ->
                            FilterChip(
                                selected = originalPartId == p.id,
                                onClick = { originalPartId = p.id },
                                label = { Text(p.productName ?: "Part #${p.id}", fontSize = 11.sp) }
                            )
                        }
                        Text("အဟောင်းပစ္စည်း", fontSize = 11.sp, color = TextMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("QUARANTINE" to "သိမ်း", "REUSE" to "ပြန်သုံး", "DAMAGED" to "ပျက်").forEach { (k, l) ->
                                FilterChip(selected = disposition == k, onClick = { disposition = k }, label = { Text(l, fontSize = 10.sp) })
                            }
                        }
                    }
                }
                if (mode == "REFUND") {
                    OutlinedTextField(
                        value = refundAmt, onValueChange = { refundAmt = it },
                        label = { Text("ပြန်အမ်းငွေ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    paymentMethods.forEach { m ->
                        FilterChip(
                            selected = refundMethodId == m.id,
                            onClick = { refundMethodId = m.id },
                            label = { Text(m.methodName, fontSize = 11.sp) }
                        )
                    }
                }
                if (err.isNotBlank()) Text(err, color = Danger, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (problem.isBlank()) { err = "ပြဿနာ ဖြည့်ပါ"; return@Button }
                    if (mode != "SERVICE_ONLY" && originalPartId == null) { err = "မူလ part ရွေးပါ"; return@Button }
                    if (mode == "REPLACE_SAME") {
                        val part = parts.find { it.id == originalPartId }
                        if (part?.productId == null) { err = "Replacement ပစ္စည်း မရှိပါ"; return@Button }
                    }
                    if (mode == "REFUND" && (refundAmt.toDoubleOrNull() ?: 0.0) <= 0.0) {
                        err = "ပြန်အမ်းငွေ ဖြည့်ပါ"; return@Button
                    }
                    val part = parts.find { it.id == originalPartId }
                    onSubmit(
                        ReworkRequestDTO(
                            reworkType = reworkType,
                            problemDesc = problem.trim(),
                            assignedStaffId = staffId,
                            resolutionMode = mode,
                            originalPartId = originalPartId,
                            oldPartDisposition = if (mode == "SERVICE_ONLY") null else disposition,
                            replacementProductId = if (mode == "REPLACE_SAME") part?.productId else null,
                            replacementQty = if (mode == "REPLACE_SAME") (part?.qty ?: 1) else null,
                            replacementSerialNumbers = if (mode == "REPLACE_SAME") part?.serialNumbers else null,
                            refundAmount = refundAmt.toDoubleOrNull(),
                            refundPaymentMethodId = refundMethodId
                        )
                    )
                },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = Warning)
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Rework ဖန်တီးမည်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("ပယ်ဖျက်") } }
    )
}

@Composable
private fun OutdoorVisitCard(
    jobId: Int?,
    visit: TechnicianVisitDTO?,
    busy: Boolean,
    pendingResume: Boolean,
    canStart: Boolean,
    onStart: (String) -> Unit,
    onArrive: () -> Unit,
    onDepartCustomer: (String) -> Unit,
    onEnd: () -> Unit,
    onJourneyResume: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onReason: () -> Unit,
    onOpenActiveVisit: (Int) -> Unit
) {
    val forThisJob = visit != null && visit.jobId == jobId
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("အပြင်ထွက် Visit", fontWeight = FontWeight.ExtraBold, color = Violet)
            when {
                !canStart -> {
                    Text(
                        "Admin မှ CAN_ACCESS_TECHNICIAN_VISIT_START ပေးပြီး logout/login ပြန်လုပ်ပါ",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                pendingResume && forThisJob -> {
                    Text("Tracking ရပ်နေသည်။ ပြန်စရန် နှိပ်ပါ။", fontSize = 12.sp, color = TextMuted)
                    Button(onClick = onResume, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Tracking ပြန်စမည်")
                    }
                }
                visit != null && !forThisJob -> {
                    Text(
                        "တခြား Job (${visit.jobNo ?: visit.jobId}) သို့ သွားနေသည်",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    visit.jobId?.let { activeJobId ->
                        Button(
                            onClick = { onOpenActiveVisit(activeJobId) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("လက်ရှိ Job ကိုဖွင့်မည်")
                        }
                    }
                }
                visit == null -> {
                    Text("Visit ရည်ရွယ်ချက် ရွေးပါ", fontSize = 12.sp, color = TextMuted)
                    Button(onClick = { onStart("SERVICE") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("စစ်ဆေး/ပြင်ဆင်ရန် သွားမည်") }
                    OutlinedButton(onClick = { onStart("PICKUP") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ပစ္စည်းယူရန် သွားမည်") }
                    OutlinedButton(onClick = { onStart("DELIVERY") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ပစ္စည်းပြန်ပို့ရန် သွားမည်") }
                    OutlinedButton(onClick = { onStart("FOLLOW_UP") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("နောက်ဆက်တွဲ စစ်ဆေးရန်") }
                }
                visit.status == "EN_ROUTE" -> {
                    Text(
                        listOfNotNull(visit.motionStatus, visit.distanceMeters?.let { "${it.toInt()} m" })
                            .joinToString(" · ")
                            .ifBlank { "ခရီးစဉ် လမ်းတွင်" },
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Button(onClick = onArrive, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Customer ဆီရောက်ပြီ")
                    }
                    if (visit.motionStatus == "STOPPED" || visit.motionStatus == "LONG_STOP") {
                        OutlinedButton(
                            onClick = onJourneyResume,
                            enabled = !busy && visit.needsReason != true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (visit.needsReason == true) "အကြောင်းပြချက်အရင်သိမ်းပါ"
                                else "ခရီးဆက်ပြီ"
                            )
                        }
                    }
                    TextButton(onClick = onReason, enabled = !busy) { Text("ခဏရပ်သည့်အကြောင်း") }
                    TextButton(onClick = onCancel, enabled = !busy) { Text("Visit ပယ်ဖျက်") }
                }
                visit.status == "ON_SITE" -> {
                    Text("Customer နေရာက လုပ်ငန်းရလဒ် ရွေးပါ", fontSize = 12.sp, color = TextMuted)
                    Button(onClick = { onDepartCustomer("FIXED_ON_SITE") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("နေရာတွင် ပြင်ပြီး — ပြန်ထွက်မည်") }
                    OutlinedButton(onClick = { onDepartCustomer("BROUGHT_TO_SHOP") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ပစ္စည်းကို ဆိုင်သို့ယူလာမည်") }
                    OutlinedButton(onClick = { onDepartCustomer("PARTS_REQUIRED") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Parts လိုအပ်သည်") }
                    OutlinedButton(onClick = { onDepartCustomer("RESCHEDULED") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Customer နှင့် ပြန်ချိန်းထားသည်") }
                    OutlinedButton(onClick = { onDepartCustomer("CUSTOMER_UNAVAILABLE") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Customer မရှိ/ဆက်သွယ်မရ") }
                }
                visit.status == "RETURNING" -> {
                    Text("Customer ဆီမှ ပြန်လာနေသည်", fontSize = 12.sp, color = TextMuted)
                    Button(onClick = onEnd, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("ပြန်ရောက်ပြီ")
                    }
                    if (visit.motionStatus == "STOPPED" || visit.motionStatus == "LONG_STOP") {
                        OutlinedButton(
                            onClick = onJourneyResume,
                            enabled = !busy && visit.needsReason != true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (visit.needsReason == true) "အကြောင်းပြချက်အရင်သိမ်းပါ"
                                else "ခရီးဆက်ပြီ"
                            )
                        }
                    }
                    TextButton(onClick = onReason, enabled = !busy) { Text("ခဏရပ်သည့်အကြောင်း") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LongStopReasonDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String?) -> Unit
) {
    var code by remember { mutableStateOf("TRAFFIC") }
    var note by remember { mutableStateOf("") }
    val options = listOf(
        "TRAFFIC" to "Traffic",
        "FUEL" to "ဆီဖြည့်နေသည်",
        "PARTS" to "ပစ္စည်းဝယ်နေသည်",
        "BREAK" to "စားသောက်/ခဏနား",
        "EMERGENCY" to "အခြားအရေးပေါ်ကိစ္စ",
        "WRONG_VISIT" to "မှားပြီး Visit စခဲ့သည်",
        "OTHER" to "ကိုယ်တိုင်ရေးမည်"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ခရီးစဉ် ခဏရပ်နေသည်", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("အကြောင်းရင်းရွေးပါ", fontSize = 13.sp, color = TextMuted)
                options.forEach { (value, label) ->
                    FilterChip(
                        selected = code == value,
                        onClick = { code = value },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = {
                        Text(if (code == "OTHER") "အကြောင်းပြချက် *" else "ထပ်ဆောင်းမှတ်ချက်")
                    },
                    placeholder = {
                        if (code == "OTHER") Text("ဘာကြောင့် ရပ်နားခဲ့သည်ကို ရေးပါ")
                    },
                    minLines = 2,
                    maxLines = 4,
                    isError = code == "OTHER" && note.isBlank(),
                    supportingText = {
                        if (code == "OTHER" && note.isBlank()) {
                            Text("ကိုယ်တိုင်ရေးမည် ရွေးထားလျှင် အကြောင်းပြချက်ထည့်ပါ")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(code, note.trim().ifBlank { null }) },
                enabled = code != "OTHER" || note.isNotBlank()
            ) { Text("သိမ်းမည်") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("နောက်မှ") } }
    )
}
