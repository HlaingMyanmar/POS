package com.sspd.servicemgmt.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sspd.servicemgmt.core.ui.component.PaperSizeSelectionDialog
import com.sspd.servicemgmt.core.ui.component.SaleInvoiceViewerDialog
import com.sspd.servicemgmt.core.util.SaleInvoiceOpener
import com.sspd.servicemgmt.core.util.formatWarranty
import com.sspd.servicemgmt.core.util.formatWarrantyState
import com.sspd.servicemgmt.core.util.jobStatusMm
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.CatalogProduct
import com.sspd.servicemgmt.core.network.CustomerAuthResponse
import com.sspd.servicemgmt.core.network.CustomerJob
import com.sspd.servicemgmt.core.network.CustomerNotification
import com.sspd.servicemgmt.core.network.JobPartLine
import com.sspd.servicemgmt.core.network.JobServiceLine
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
import kotlin.math.abs

private val PanelShape = RoundedCornerShape(16.dp)

@Composable
fun CustomerDashboardScreen(
    profile: CustomerAuthResponse,
    products: List<CatalogProduct>,
    jobs: List<CustomerJob>,
    notifications: List<CustomerNotification>,
    unreadNotificationCount: Int = notifications.size,
    orderCount: Int,
    serviceCount: Int,
    completedCount: Int,
    onProducts: () -> Unit,
    onServices: () -> Unit,
    onCart: () -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onNotifications: () -> Unit,
    onAddToCart: (CatalogProduct) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Spacer(Modifier.height(10.dp))
        DashboardHeader(
            profile = profile,
            notificationCount = unreadNotificationCount,
            onProfile = onProfile,
            onNotifications = onNotifications
        )
        SummaryRow(orderCount, serviceCount, completedCount)

        DashboardSectionTitle("ဘာလုပ်ချင်ပါသလဲ?")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("ပစ္စည်း", Icons.Outlined.ShoppingCart, Modifier.weight(1f), onProducts)
            QuickAction("Service", Icons.Outlined.Handyman, Modifier.weight(1f), onServices)
            QuickAction("ခြင်း", Icons.AutoMirrored.Outlined.ReceiptLong, Modifier.weight(1f), onCart)
            QuickAction("မှတ်တမ်း", Icons.Outlined.History, Modifier.weight(1f), onHistory)
        }

        var selectedJob by remember { mutableStateOf<CustomerJob?>(null) }

        jobs.firstOrNull { it.status !in listOf("DELIVERED", "CANCELLED") }?.let { job ->
            DashboardSectionTitle("လတ်တလော Service", action = "အားလုံးကြည့်မည်", onAction = onServices)
            ActiveJobCard(job, onClick = { selectedJob = job })
        }

        selectedJob?.let { job ->
            JobDetailDashboardDialog(job = job, onDismiss = { selectedJob = null })
        }

        if (products.isNotEmpty()) {
            DashboardSectionTitle("အကြံပြုပစ္စည်းများ", action = "အားလုံးကြည့်မည်", onAction = onProducts)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                products
                    .sortedWith(
                        compareBy<CatalogProduct> { if ((it.stockQty ?: 0) > 0) 0 else 1 }
                            .thenByDescending { it.stockQty ?: 0 }
                    )
                    .take(6)
                    .forEach { product ->
                        RecommendedProductCard(product, onAddToCart)
                    }
            }
        }
        Spacer(Modifier.height(84.dp))
        }
    }
}

@Composable
private fun DashboardHeader(
    profile: CustomerAuthResponse,
    notificationCount: Int,
    onProfile: () -> Unit,
    onNotifications: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(PrimaryDark, Primary)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(OnPrimary.copy(alpha = 0.18f))
                        .border(1.dp, OnPrimary.copy(alpha = 0.28f), CircleShape)
                        .clickable(onClick = onProfile),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        profile.name.initials(),
                        color = OnPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "မင်္ဂလာပါ",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnPrimary.copy(alpha = 0.82f)
                    )
                    Text(
                        profile.name.orEmpty().ifBlank { "SSPD Customer" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(
                        onClick = onNotifications,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(OnPrimary.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsNone,
                            contentDescription = "အသိပေးချက်",
                            tint = OnPrimary
                        )
                    }
                    if (notificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                                .clip(CircleShape)
                                .background(CardBg)
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (notificationCount > 9) "9+" else notificationCount.toString(),
                                color = Primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerNotificationsDialog(
    notifications: List<CustomerNotification>,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("အသိပေးချက်များ", fontWeight = FontWeight.Bold, color = TextMain)
        },
        text = {
            if (notifications.isEmpty()) {
                Text("အသိပေးချက် မရှိသေးပါ", color = TextMuted)
            } else {
                LazyColumn(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(notifications.take(20), key = { index, n -> "${n.id}-$index" }) { _, notification ->
                        Surface(
                            color = SurfaceSoft,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(PrimaryLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.NotificationsNone,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        (notification.orderNo ?: notification.jobNo).orEmpty()
                                            .ifBlank { "Service Update" },
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextMain
                                    )
                                    Text(
                                        notification.note.orEmpty()
                                            .ifBlank { "Service အခြေအနေ အသစ်ရှိပါသည်" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                    notification.notifiedAt?.let {
                                        Text(
                                            it.replace("T", " ").take(16),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenHistory) {
                Text("မှတ်တမ်းကြည့်မည်", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ပိတ်မည်") }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = CardBg
    )
}

@Composable
private fun SummaryRow(orderCount: Int, serviceCount: Int, completedCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Row(modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp)) {
            SummaryItem(orderCount, "အော်ဒါ", Icons.Outlined.Inventory2, Modifier.weight(1f))
            SummaryItem(serviceCount, "Service", Icons.Outlined.Build, Modifier.weight(1f))
            SummaryItem(completedCount, "ပြီးစီး", Icons.Outlined.CheckCircle, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryItem(
    value: Int,
    label: String,
    icon: ImageVector,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Text(
            value.toString(),
            color = TextMain,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun DashboardSectionTitle(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextMain
        )
        action?.let {
            Text(
                it,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                color = Primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 86.dp)
            .clickable(onClick = onClick),
        shape = PanelShape,
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMain,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActiveJobCard(
    job: CustomerJob,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = PanelShape,
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Handyman, null, tint = Primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        job.jobNo.orEmpty().ifBlank { "Service Job" },
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                    job.bookingNo?.takeIf { it.isNotBlank() }?.let { bookingNo ->
                        Text(
                            "Booking $bookingNo",
                            style = MaterialTheme.typography.bodySmall,
                            color = Success,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        listOfNotNull(job.itemName, job.deviceType).joinToString(" • ")
                            .ifBlank { "Service" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Surface(
                    color = PrimaryLight,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.12f))
                ) {
                    Text(
                        jobStatusMm(job.status).first,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = Primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (!job.problemDesc.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "ပြဿနာ · ${job.problemDesc}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(16.dp))
            JobProgress(job.status)
        }
    }
}

@Composable
private fun JobDetailDashboardDialog(
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
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BorderColor),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
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

                // 1. Invoice (ဆိုင်ဘောင်ချာ / PDF Voucher)
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
                                    "Service Job Invoice (ဝန်ဆောင်မှု + Part)",
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
                                    Text("PDF ဖွင့်", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        showPaperSizePicker = true
                                    },
                                    enabled = !openingInvoice,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Send", fontWeight = FontWeight.SemiBold)
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

                // Progress Stepper
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceSoft,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        JobProgress(job.status)
                    }
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

                // 2 & 3. Services Rendered & Pricing & Warranty
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
                                            money(lineSubtotal),
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

                // 2 & 4. Replacement Parts Used & Pricing & Warranty
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
                                            money(lineSubtotal),
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

                // 5. Discount & Calculation Summary Card
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
                                    job.paymentStatus?.ifBlank { "PAID" } ?: "PAID",
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

@Composable
private fun JobProgress(status: String?) {
    val current = when (status?.uppercase()) {
        "RECEIVED", "BOOKED" -> 0
        "CHECKING", "INSPECTING", "DIAGNOSED" -> 1
        "IN_PROGRESS", "REPAIRING", "WAITING_PARTS" -> 2
        "COMPLETED", "READY_FOR_DELIVERY", "DELIVERED" -> 3
        else -> 1
    }
    val labels = listOf("လက်ခံ", "စစ်ဆေး", "ပြုပြင်", "ပြီးစီး")
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        labels.forEachIndexed { index, label ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < labels.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .align(Alignment.CenterEnd)
                                .height(2.dp)
                                .background(if (index < current) Primary else BorderColor)
                        )
                    }
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .align(Alignment.CenterStart)
                                .height(2.dp)
                                .background(if (index <= current) Primary else BorderColor)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (index <= current) Primary else SurfaceSoft)
                            .then(
                                if (index > current) Modifier.border(1.dp, BorderColor, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index < current) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                null,
                                tint = OnPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (index == current) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(OnPrimary)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index <= current) Primary else TextMuted,
                    fontWeight = if (index <= current) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RecommendedProductCard(product: CatalogProduct, onAddToCart: (CatalogProduct) -> Unit) {
    val inStock = (product.stockQty ?: 0) > 0 && product.inStock != false
    Surface(
        modifier = Modifier.width(188.dp),
        shape = PanelShape,
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .background(SurfaceSoft),
                contentAlignment = Alignment.Center
            ) {
                val url = product.photoUrls.orEmpty().firstOrNull()?.toAssetUrl()
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Outlined.Inventory2,
                        null,
                        tint = Primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    product.name.orEmpty().ifBlank { "ပစ္စည်း" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMain
                )
                Text(
                    listOfNotNull(product.brandName, product.categoryName)
                        .joinToString(" • ")
                        .ifBlank { "SSPD" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (!inStock) "ကုန်နေသည်" else "ကျန် ${product.stockQty} ခု",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!inStock) Danger else Success,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        money(product.sellingPrice ?: 0.0),
                        modifier = Modifier.weight(1f),
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { onAddToCart(product) },
                        enabled = inStock,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (inStock) Primary else SurfaceSoft)
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "ခြင်းထည့်ရန်",
                            tint = if (inStock) OnPrimary else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun String?.initials(): String {
    val words = this.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "C"
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}

private fun String.toAssetUrl(): String =
    if (startsWith("http://") || startsWith("https://")) this
    else BuildConfig.DEFAULT_BASE_URL.trimEnd('/') + "/" + trimStart('/')

private fun money(amount: Double): String {
    val safe = if (amount.isFinite()) amount else 0.0
    val absValue = abs(safe).toLong()
    val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerDashboardPreview() {
    AppTheme {
        CustomerDashboardScreen(
            profile = CustomerAuthResponse(
                customerId = 128,
                name = "Aung Kyaw Moe",
                email = "aungkyaw@example.com",
                creditAllowed = true,
                creditLimit = 1_500_000.0,
                creditDays = 30
            ),
            products = listOf(
                CatalogProduct(
                    id = 1,
                    name = "Dell Inspiron 15 3530",
                    productCode = "DELL-3530",
                    categoryName = "Laptop",
                    brandName = "Dell",
                    productType = "New",
                    sellingPrice = 1_350_000.0,
                    warrantyMonths = 12,
                    inStock = true,
                    stockQty = 5
                ),
                CatalogProduct(
                    id = 2,
                    name = "HP 15.6 Backpack",
                    productCode = "HP-BAG",
                    categoryName = "Accessories",
                    brandName = "HP",
                    productType = "New",
                    sellingPrice = 48_000.0,
                    warrantyMonths = 0,
                    inStock = true,
                    stockQty = 12
                )
            ),
            jobs = listOf(
                CustomerJob(id = 42, jobNo = "JOB-000042", status = "IN_PROGRESS", itemName = "Laptop")
            ),
            notifications = listOf(
                CustomerNotification(
                    1,
                    42,
                    "JOB-000042",
                    "APP",
                    "သင့်ပစ္စည်းကို စစ်ဆေးပြီး ပြုပြင်နေပါသည်",
                    "2026-09-07T13:30:00"
                )
            ),
            orderCount = 2,
            serviceCount = 1,
            completedCount = 12,
            onProducts = {},
            onServices = {},
            onCart = {},
            onHistory = {},
            onProfile = {},
            onNotifications = {},
            onAddToCart = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 900)
@Composable
private fun JobDetailDashboardDialogPreview() {
    AppTheme {
        JobDetailDashboardDialog(
            job = CustomerJob(
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
            ),
            onDismiss = {}
        )
    }
}
