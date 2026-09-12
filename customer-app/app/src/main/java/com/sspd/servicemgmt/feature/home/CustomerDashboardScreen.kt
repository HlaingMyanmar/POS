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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
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
            QuickAction("ပစ္စည်း\nဝယ်မည်", Icons.Outlined.ShoppingCart, Modifier.weight(1f), onProducts)
            QuickAction("Service\nခေါ်မည်", Icons.Outlined.Handyman, Modifier.weight(1f), onServices)
            QuickAction("ခြင်းတောင်း\nကြည့်မည်", Icons.AutoMirrored.Outlined.ReceiptLong, Modifier.weight(1f), onCart)
            QuickAction("မှတ်တမ်း\nကြည့်မည်", Icons.Outlined.History, Modifier.weight(1f), onHistory)
        }

        jobs.firstOrNull { it.status !in listOf("DELIVERED", "CANCELLED") }?.let { job ->
            DashboardSectionTitle("လတ်တလော အခြေအနေ", action = "အားလုံးကြည့်မည်", onAction = onHistory)
            ActiveJobCard(job)
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
        Spacer(Modifier.height(18.dp))
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
            .aspectRatio(0.82f)
            .clickable(onClick = onClick),
        shape = PanelShape,
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextMain,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun ActiveJobCard(job: CustomerJob) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                        job.status.orEmpty().replace("_", " "),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = Primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            JobProgress(job.status)
        }
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
