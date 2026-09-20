package com.sspd.servicemgmt.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sspd.servicemgmt.core.network.CustomerJob
import com.sspd.servicemgmt.core.network.JobPartLine
import com.sspd.servicemgmt.core.network.JobServiceLine
import com.sspd.servicemgmt.core.ui.component.ErrorRetryBanner
import com.sspd.servicemgmt.core.ui.component.PaperSizeSelectionDialog
import com.sspd.servicemgmt.core.ui.component.SaleInvoiceViewerDialog
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.Success
import com.sspd.servicemgmt.core.ui.theme.SuccessBg
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.ui.theme.Warning
import com.sspd.servicemgmt.core.ui.theme.WarningBg
import com.sspd.servicemgmt.core.util.SaleInvoiceOpener
import com.sspd.servicemgmt.core.util.formatKsOrDash
import com.sspd.servicemgmt.core.util.formatWarranty
import com.sspd.servicemgmt.core.util.formatWarrantyState
import com.sspd.servicemgmt.core.util.jobStatusMm
import com.sspd.servicemgmt.core.util.paymentStatusMm
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun money(amount: Double): String {
    val safe = if (amount.isFinite()) amount else 0.0
    val absValue = abs(safe).toLong()
    val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
}

@Composable
fun CustomerServiceJobsScreen(
    jobs: List<CustomerJob>,
    loading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    onRequestNewService: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") }
    var selectedJob by remember { mutableStateOf<CustomerJob?>(null) }

    val activeStates = setOf("IN_PROGRESS", "CHECKING", "BOOKED", "WAITING_PARTS", "INSPECTING", "DIAGNOSED", "REPAIRING")
    val completedStates = setOf("COMPLETED", "DELIVERED", "READY_FOR_DELIVERY")

    val activeCount = jobs.count { it.status?.uppercase() in activeStates }
    val doneCount = jobs.count { it.status?.uppercase() in completedStates }
    val totalSpent = jobs.sumOf { it.netAmount ?: 0.0 }

    val filteredJobs = jobs.filter { job ->
        val q = searchQuery.trim().lowercase()
        val matchesSearch = q.isBlank() || listOf(
            job.jobNo,
            job.itemName,
            job.deviceType,
            job.problemDesc
        ).any { it.orEmpty().lowercase().contains(q) }

        val matchesFilter = when (filterMode) {
            "ACTIVE" -> job.status?.uppercase() in activeStates
            "DONE" -> job.status?.uppercase() in completedStates
            else -> true
        }
        matchesSearch && matchesFilter
    }.sortedByDescending { it.id }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        // Portal Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryDark)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OnPrimary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Handyman, null, tint = OnPrimary, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    "Service မှတ်တမ်း",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OnPrimary
                                )
                                Text(
                                    "ပြုပြင်ရေး job များကို ဤနေရာတွင် ကြည့်ပါ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnPrimary.copy(alpha = 0.75f),
                                    maxLines = 1
                                )
                            }
                        }

                        Button(
                            onClick = onRequestNewService,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight, contentColor = PrimaryDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("+ Service တောင်းမည်", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    HorizontalDivider(color = OnPrimary.copy(alpha = 0.15f))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ဆောင်ရွက်ဆဲ", style = MaterialTheme.typography.labelSmall, color = OnPrimary.copy(alpha = 0.7f), fontSize = 10.sp)
                            Text("$activeCount ခု", fontWeight = FontWeight.Bold, color = OnPrimary, fontSize = 14.sp)
                        }
                        Column {
                            Text("ပြီးစီးပြီး", style = MaterialTheme.typography.labelSmall, color = OnPrimary.copy(alpha = 0.7f), fontSize = 10.sp)
                            Text("$doneCount ခု", fontWeight = FontWeight.Bold, color = OnPrimary, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("စုစုပေါင်း ကုန်ကျစရိတ်", style = MaterialTheme.typography.labelSmall, color = OnPrimary.copy(alpha = 0.7f), fontSize = 10.sp)
                            Text(money(totalSpent), fontWeight = FontWeight.Bold, color = OnPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Job No / စက်အမည် / ပြဿနာ ရှာရန်", maxLines = 1, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "ရှင်းမည်", tint = TextMuted)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Filter Tabs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    "ALL" to "အားလုံး (${jobs.size})",
                    "ACTIVE" to "ဆောင်ရွက်ဆဲ (${activeCount})",
                    "DONE" to "ပြီးစီး (${doneCount})"
                )
                tabs.forEach { (id, label) ->
                    if (filterMode == id) {
                        Button(
                            onClick = { filterMode = id },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { filterMode = id },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = TextMain, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Job Items
        if (error != null) {
            item { ErrorRetryBanner(error, onRetry = onRetry) }
        } else if (jobs.isEmpty()) {
            item { CustomerServiceJobEmpty(onRequestNewService = onRequestNewService) }
        } else if (filteredJobs.isEmpty()) {
            item {
                val emptyMsg = when (filterMode) {
                    "ACTIVE" -> "ဆောင်ရွက်ဆဲ Service Job မရှိပါ"
                    "DONE" -> "ပြီးစီးထားသော Service Job မရှိပါ"
                    else -> "ကိုက်ညီသော Service Job မရှိပါ"
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.Handyman, null, tint = TextMuted, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(emptyMsg, fontWeight = FontWeight.Bold, color = TextMain)
                        Spacer(Modifier.height(4.dp))
                        Text("Filter သို့မဟုတ် ရှာဖွေစာကို ပြောင်းကြည့်ပါ", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        } else {
            itemsIndexed(filteredJobs, key = { index, job -> "j-${job.id}-$index" }) { _, job ->
                ServiceJobCard(job = job, onClick = { selectedJob = job })
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    selectedJob?.let { job ->
        JobDetailFullDialog(job = job, onDismiss = { selectedJob = null })
    }
}
}

@Composable
fun ServiceJobCard(
    job: CustomerJob,
    onClick: () -> Unit
) {
    val (statusText, statusFg, statusBg) = when (job.status?.uppercase()) {
        "COMPLETED", "DELIVERED", "READY_FOR_DELIVERY" -> Triple("ပြီးစီးပါပြီ", Success, SuccessBg)
        "IN_PROGRESS", "REPAIRING", "WAITING_PARTS" -> Triple("ပြုပြင်နေသည်", Primary, PrimaryLight)
        "CHECKING", "INSPECTING", "DIAGNOSED" -> Triple("စစ်ဆေးနေသည်", Warning, WarningBg)
        else -> Triple(jobStatusMm(job.status).first, TextMuted, SurfaceSoft)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Handyman, null, tint = Primary, modifier = Modifier.size(20.dp))
                    }
                    Column {
                            Text(
                                job.jobNo.orEmpty().ifBlank { "Service Job" },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = PrimaryDark
                            )
                            job.bookingNo?.takeIf { it.isNotBlank() }?.let { bookingNo ->
                                Text(
                                    "Booking $bookingNo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Success,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            job.receivedDate?.takeIf { it.isNotBlank() }?.let {
                            Text(it.replace('T', ' ').take(16), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, statusFg.copy(alpha = 0.2f))
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = statusFg,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            JobServiceModeChip(job)
            JobBookingNoChip(job)

            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

            Column {
                Text(
                    job.itemName.orEmpty().ifBlank { "—" },
                    fontWeight = FontWeight.Bold,
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!job.deviceType.isNullOrBlank()) {
                    Text(job.deviceType, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }

            if (!job.problemDesc.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceSoft
                ) {
                    Text(
                        "ပြဿနာ · ${job.problemDesc}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMain,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "အသေးစိတ် ကြည့်မည် ➔",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    formatKsOrDash(job.netAmount),
                    fontWeight = FontWeight.ExtraBold,
                    color = Primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun CustomerServiceJobEmpty(
    onRequestNewService: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.Handyman, null, tint = TextMuted, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text("Service Job မရှိသေးပါ", fontWeight = FontWeight.Bold, color = TextMain, style = MaterialTheme.typography.titleSmall)
            Text(
                "Service ခေါ်ယူထားပါက ဤနေရာတွင် တစ်စုတစ်ဝေးတည်း စနစ်တကျ ကြည့်နိုင်ပါသည်",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onRequestNewService,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Service ခေါ်မည်", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun JobDetailFullDialog(
    job: CustomerJob,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showInAppInvoiceDialog by remember { mutableStateOf(false) }
    var showPaperSizePicker by remember { mutableStateOf(false) }
    var openingInvoice by remember { mutableStateOf(false) }
    var invoiceError by remember { mutableStateOf<String?>(null) }

    val canOpenInvoice = job.canOpenServiceInvoice()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BorderColor),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Handyman, null, tint = Primary, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(
                                "Service Job အသေးစိတ်",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryDark
                            )
                            Text(
                                job.jobNo.orEmpty().ifBlank { "Service Job" },
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                            job.bookingNo?.takeIf { it.isNotBlank() }?.let { bookingNo ->
                                Text(
                                    "Booking $bookingNo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Success,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceSoft)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "ပိတ်မည်", tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                }

                HorizontalDivider(color = BorderColor.copy(alpha = 0.6f))

                // Invoice
                if (canOpenInvoice) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showInAppInvoiceDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = SuccessBg,
                        border = BorderStroke(1.dp, Success.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ReceiptLong,
                                    contentDescription = null,
                                    tint = Success,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "ဘောင်ချာ ကြည့်ရန်",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Success,
                                    modifier = Modifier.weight(1f)
                                )
                                job.jobNo?.takeIf { it.isNotBlank() }?.let { jobNo ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Success.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            jobNo,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Success,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showInAppInvoiceDialog = true },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("ကြည့်မည်", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        showPaperSizePicker = true
                                    },
                                    enabled = !openingInvoice,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("ပို့မည်", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            invoiceError?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
                            }
                        }
                    }
                }

                // Status Badge
                val (statusText, statusFg, statusBg) = when (job.status?.uppercase()) {
                    "COMPLETED", "DELIVERED", "READY_FOR_DELIVERY" -> Triple("✓ ပြီးစီးပါပြီ (${job.status})", Success, SuccessBg)
                    "IN_PROGRESS", "REPAIRING", "WAITING_PARTS" -> Triple("⚙ ပြုပြင်နေသည် (${job.status})", Primary, PrimaryLight)
                    "CHECKING", "INSPECTING", "DIAGNOSED" -> Triple("🔍 စစ်ဆေးနေသည် (${job.status})", Warning, WarningBg)
                    else -> Triple(job.status?.replace('_', ' ') ?: "—", TextMuted, SurfaceSoft)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusBg,
                        border = BorderStroke(1.dp, statusFg.copy(alpha = 0.2f))
                    ) {
                        Text(
                            statusText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = statusFg,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    JobServiceModeChip(job)
                    JobBookingNoChip(job)
                }

                // Device & Issue Details Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceSoft,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column {
                            Text("ပစ္စည်း / စက်အမည်", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                job.itemName.orEmpty().ifBlank { "—" },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextMain
                            )
                        }

                        if (!job.deviceType.isNullOrBlank()) {
                            Column {
                                Text("စက်အမျိုးအစား", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Spacer(Modifier.height(2.dp))
                                Text(job.deviceType, style = MaterialTheme.typography.bodyMedium, color = TextMain)
                            }
                        }

                        if (!job.problemDesc.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
                            Column {
                                Text("ဖြစ်ပွားသည့် ပြဿနာ / တောင်းဆိုချက်", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    job.problemDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMain,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                JobTimelineAndCrew(job)

                // Services Rendered
                val services = job.services.orEmpty()
                var servicesSum = 0.0
                if (services.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ဝန်ဆောင်မှုများ ကျသင့်ငွေ", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = PrimaryDark)
                            Text("${services.size} မျိုး", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        services.forEach { service ->
                            val unitP = service.chargedUnit()
                            val qty = service.qty ?: 1
                            val lineSubtotal = service.chargedAmount()
                            servicesSum += lineSubtotal

                            val warrantyStr = formatWarranty(service.warrantyMonths, service.warrantyStartDate, service.warrantyExpiryDate)
                            val warrantyState = formatWarrantyState(service.warrantyStatus, null)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceSoft,
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Outlined.Build, null, tint = Primary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                service.name.orEmpty().ifBlank { "Service" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextMain
                                            )
                                        }
                                        Text(
                                            formatKsOrDash(lineSubtotal, free = service.warrantyCovered == true),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryDark
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "× $qty ${if (unitP > 0) "· " + money(unitP) + " / ခု" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )

                                        if (service.lineDiscount() > 0.0) {
                                            Text(
                                                "လိုင်းလျှော့ · -${money(service.lineDiscount())}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Danger
                                            )
                                        }

                                        if (service.warrantyCovered == true) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = SuccessBg) {
                                                Text("✓ အာမခံ ခွင့်ပြုပြီး", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Success, fontWeight = FontWeight.Bold)
                                            }
                                        } else if (warrantyStr.isNotBlank()) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = SuccessBg) {
                                                Text("အာမခံ $warrantyStr ${if (warrantyState.isNotBlank()) "· $warrantyState" else ""}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Success, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Replacement Parts Used
                val parts = job.parts.orEmpty()
                var partsSum = 0.0
                if (parts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("လဲလှယ်/အသုံးပြုခဲ့သော အပိုပစ္စည်းများ ကျသင့်ငွေ", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = PrimaryDark)
                            Text("${parts.size} မျိုး", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        parts.forEach { part ->
                            val unitP = part.chargedUnit()
                            val qty = part.qty ?: 1
                            val lineSubtotal = part.chargedAmount()
                            partsSum += lineSubtotal

                            val warrantyStr = formatWarranty(part.warrantyMonths, part.warrantyStartDate, part.warrantyExpiryDate)
                            val warrantyState = formatWarrantyState(part.warrantyStatus, null)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceSoft,
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Outlined.Inventory2, null, tint = Primary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                part.productName.orEmpty().ifBlank { "Part" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextMain
                                            )
                                        }
                                        Text(
                                            formatKsOrDash(lineSubtotal, free = part.warrantyCovered == true),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryDark
                                        )
                                    }

                                    part.serialNumber?.takeIf { it.isNotBlank() }?.let { sn ->
                                        Text("S/N · $sn", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "× $qty ${if (unitP > 0) "· " + money(unitP) + " / ခု" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )

                                        if (part.lineDiscount() > 0.0) {
                                            Text(
                                                "လိုင်းလျှော့ · -${money(part.lineDiscount())}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Danger
                                            )
                                        }

                                        if (part.warrantyCovered == true) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = SuccessBg) {
                                                Text("✓ အာမခံ ခွင့်ပြုပြီး", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Success, fontWeight = FontWeight.Bold)
                                            }
                                        } else if (warrantyStr.isNotBlank()) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = SuccessBg) {
                                                Text("အာမခံ $warrantyStr ${if (warrantyState.isNotBlank()) "· $warrantyState" else ""}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Success, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Discount & Calculation Summary Card
                val rawTotal = (job.totalAmount ?: (servicesSum + partsSum)).let { if (it > 0) it else (servicesSum + partsSum) }
                val lineDiscTotal = job.lineDiscountTotal()
                val discount = job.overallDiscount()
                val finalNet = job.netAmount ?: (rawTotal - discount)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryLight.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("ကျသင့်ငွေ အသေးစိတ် စာရင်း", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = PrimaryDark)

                        if (servicesSum > 0.0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ဝန်ဆောင်မှု စုစုပေါင်း", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                Text(money(servicesSum), style = MaterialTheme.typography.bodySmall, color = TextMain)
                            }
                        }

                        if (partsSum > 0.0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("အပိုပစ္စည်း စုစုပေါင်း", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                Text(money(partsSum), style = MaterialTheme.typography.bodySmall, color = TextMain)
                            }
                        }

                        if (rawTotal > 0.0 && (servicesSum > 0.0 || partsSum > 0.0)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("စုစုပေါင်း ကျသင့်ငွေ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextMain)
                                Text(money(rawTotal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextMain)
                            }
                        }

                        if (lineDiscTotal > 0.0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("လိုင်းလျှော့ (Line discount)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Danger)
                                Text("-${money(lineDiscTotal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Danger)
                            }
                        }

                        if (discount > 0.0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Overall လျှော့", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Danger)
                                Text("-${money(discount)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Danger)
                            }
                        }

                        HorizontalDivider(color = BorderColor)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ငွေပေးချေမှု အခြေအနေ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    paymentStatusMm(job.paymentStatus),
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryDark,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("ပေးရန် ကျသင့်ငွေ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    money(finalNet),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Primary,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("ပိတ်မည်", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    if (showInAppInvoiceDialog) {
        SaleInvoiceViewerDialog(
            serviceJobId = job.id,
            saleCode = job.jobNo,
            onDismiss = { showInAppInvoiceDialog = false }
        )
    }

    if (showPaperSizePicker) {
        PaperSizeSelectionDialog(
            title = "Paper Size ရွေးပါ",
            subtitle = "Service Job ဘောက်ချာ ပို့လိုသော Paper Size ရွေးပါ",
            onSelect = { paperSize ->
                showPaperSizePicker = false
                openingInvoice = true
                invoiceError = null
                scope.launch {
                    invoiceError = SaleInvoiceOpener.shareForServiceJob(context, job.id, job.jobNo, paperSize)
                    openingInvoice = false
                }
            },
            onDismiss = { showPaperSizePicker = false }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerServiceJobsScreenPreview() {
    AppTheme {
        CustomerServiceJobsScreen(
            jobs = listOf(
                CustomerJob(
                    id = 45,
                    jobNo = "SJ-000045",
                    status = "COMPLETED",
                    itemName = "IMO 360 Camera 3Mp",
                    deviceType = "CCTV Camera",
                    problemDesc = "IMO 360 Camera 3Mp တပ်ဆင်ပေးရန်။",
                    receivedDate = "2026-09-18T00:55:00",
                    completedDate = "2026-09-18T14:55:00",
                    netAmount = 315_000.0
                ),
                CustomerJob(
                    id = 42,
                    jobNo = "JOB-000042",
                    status = "IN_PROGRESS",
                    itemName = "Dell Inspiron 15 Laptop",
                    deviceType = "Laptop",
                    problemDesc = "Screen အက်ကွဲ ပျက်စီးနေခြင်း",
                    receivedDate = "2026-09-17T10:20:00",
                    netAmount = 145_000.0
                )
            )
        )
    }
}

private fun sampleCompletedJob(): CustomerJob = CustomerJob(
    id = 45,
    jobNo = "SJ-000045",
    status = "COMPLETED",
    itemName = "IMO 360 Camera 3Mp",
    deviceType = "CCTV Camera",
    problemDesc = "IMO 360 Camera 3Mp တပ်ဆင်ပေးရန်။",
    receivedDate = "2026-09-18T00:55:00",
    completedDate = "2026-09-18T14:55:00",
    deliveredDate = "2026-09-18T16:00:00",
    totalAmount = 325_000.0,
    discountAmount = 10_000.0,
    netAmount = 315_000.0,
    paymentStatus = "PAID",
    completedSaleId = 128,
    saleCode = "SALE-000128",
    services = listOf(
        JobServiceLine(
            name = "Transportation charge",
            qty = 1,
            unitPrice = 15_000.0,
            subtotal = 15_000.0,
            warrantyMonths = 0
        ),
        JobServiceLine(
            name = "Computer General Diagnosis & Installation",
            qty = 1,
            unitPrice = 30_000.0,
            subtotal = 30_000.0,
            warrantyMonths = 6,
            warrantyStatus = "ACTIVE"
        )
    ),
    parts = listOf(
        JobPartLine(
            productName = "Micro SD Card 32GB",
            qty = 1,
            unitPrice = 20_000.0,
            subtotal = 20_000.0,
            warrantyMonths = 12,
            warrantyStatus = "ACTIVE",
            serialNumber = "SN-SD32-9901"
        ),
        JobPartLine(
            productName = "CCTV IMOU Camera Ranger 2 Indoor Smart Security Camera 3MP",
            qty = 1,
            unitPrice = 260_000.0,
            subtotal = 260_000.0,
            warrantyMonths = 12,
            warrantyStatus = "ACTIVE",
            serialNumber = "SN-IMOU-8821"
        )
    )
)

private fun sampleInProgressJob(): CustomerJob = CustomerJob(
    id = 42,
    jobNo = "JOB-000042",
    status = "IN_PROGRESS",
    itemName = "Dell Inspiron 15 Laptop",
    deviceType = "Laptop",
    problemDesc = "Screen အက်ကွဲ ပျက်စီးနေခြင်း",
    receivedDate = "2026-09-17T10:20:00",
    netAmount = 145_000.0,
    services = listOf(
        JobServiceLine(
            name = "Display Screen Replacement Service",
            qty = 1,
            unitPrice = 25_000.0,
            subtotal = 25_000.0,
            warrantyMonths = 3
        )
    ),
    parts = listOf(
        JobPartLine(
            productName = "Dell 15.6 FHD LED Screen Panel",
            qty = 1,
            unitPrice = 120_000.0,
            subtotal = 120_000.0,
            warrantyMonths = 6,
            serialNumber = "SN-SCR-5542"
        )
    )
)

@Preview(name = "Job Detail — Completed", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ServiceJobDetailCompletedPreview() {
    AppTheme {
        JobDetailFullDialog(
            job = sampleCompletedJob(),
            onDismiss = {}
        )
    }
}

@Preview(name = "Job Detail — In Progress", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ServiceJobDetailInProgressPreview() {
    AppTheme {
        JobDetailFullDialog(
            job = sampleInProgressJob(),
            onDismiss = {}
        )
    }
}
