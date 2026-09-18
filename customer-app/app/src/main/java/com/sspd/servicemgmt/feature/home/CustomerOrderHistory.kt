package com.sspd.servicemgmt.feature.home

import com.sspd.servicemgmt.core.util.formatWarranty
import com.sspd.servicemgmt.core.util.formatWarrantyState

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.Dp
import com.sspd.servicemgmt.core.feature.CustomerAppFeatures
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.OrderRatingRequest
import com.sspd.servicemgmt.core.network.CustomerOrder
import com.sspd.servicemgmt.core.network.CustomerPurchase
import com.sspd.servicemgmt.core.network.OrderDeliveryMilestone
import com.sspd.servicemgmt.core.network.OrderLine
import com.sspd.servicemgmt.core.network.OrderRating
import com.sspd.servicemgmt.core.network.PurchaseLine
import com.sspd.servicemgmt.core.network.ReceiptConfirmRequest
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.DangerBg
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
import com.sspd.servicemgmt.core.ui.component.SaleInvoiceViewerDialog
import com.sspd.servicemgmt.core.util.PreferenceManager
import com.sspd.servicemgmt.core.util.SaleInvoiceOpener
import kotlinx.coroutines.launch
import kotlin.math.abs

data class OrderStatusUi(
    val label: String,
    val fg: Color,
    val bg: Color
)

@Composable
fun OrderProgressStepper(order: CustomerOrder) {
    val pay = order.paymentState?.trim()?.uppercase().orEmpty()
    val status = order.status?.trim()?.uppercase().orEmpty()
    val delivery = order.deliveryStatus?.trim()?.uppercase().orEmpty()

    val receiptDone = order.customerReceiptState.equals("CONFIRMED", true) || status == "COMPLETED"

    val paymentDone = pay in setOf("PAID", "DEPOSIT_PAID", "FULFILLED", "CONFIRMED", "APPROVED", "COMPLETED")
            || order.completedSaleId != null
            || order.completedSale != null
            || status == "COMPLETED"

    val paymentStarted = paymentDone
            || pay in setOf("PROOF_SUBMITTED", "CHECKING", "REVIEW", "LATE_REVIEW", "AWAITING_PAYMENT")
            || order.latestProofId != null
            || order.collectionProofId != null

    val shippingStarted = delivery in setOf("PACKING", "PACKED", "HANDED_TO_RIDER", "OUT_FOR_DELIVERY", "IN_TRANSIT", "DELIVERED")

    val activeStage = when {
        receiptDone -> 3
        shippingStarted -> 2
        paymentDone -> 2
        paymentStarted -> 1
        else -> 0
    }

    val steps = listOf(
        "အော်ဒါတင်" to 0,
        "ငွေပေးချေ" to 1,
        (if (order.orderType.equals("PICKUP", true)) "ဆိုင်လာယူ" else "ပို့ဆောင်") to 2,
        "လက်ခံပြီး" to 3
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val reached = activeStage >= step.second
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(if (activeStage == index) 14.dp else 11.dp)
                        .clip(CircleShape)
                        .background(if (reached) Primary else BorderColor)
                )
                Text(
                    text = step.first,
                    fontSize = 10.sp,
                    color = if (reached) PrimaryDark else TextMuted,
                    fontWeight = if (activeStage == index) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (activeStage > index) Primary else BorderColor)
                )
            }
        }
    }
}
@Composable
fun orderStatusUi(status: String?, paymentState: String? = null): OrderStatusUi = when {
    paymentState.equals("FULFILLED", ignoreCase = true) -> OrderStatusUi("Invoice ထုတ်ပြီး", Success, SuccessBg)
    status?.trim()?.uppercase() in setOf("CONFIRMED", "APPROVED") -> OrderStatusUi("Approved", Success, SuccessBg)
    status?.trim()?.uppercase() in setOf("CANCELLED", "CANCELED", "REJECTED") -> OrderStatusUi("ပယ်ဖျက်ပြီး", Danger, DangerBg)
    status?.trim()?.uppercase() == "PENDING" -> OrderStatusUi("စောင့်ဆိုင်း", Warning, WarningBg)
    status?.trim()?.uppercase() == "COMPLETED" -> OrderStatusUi("ပြီးစီး", Primary, PrimaryLight)
    else -> OrderStatusUi(status?.replace('_', ' ')?.ifBlank { "—" } ?: "—", TextMuted, SurfaceSoft)
}

fun orderNeedsAttention(order: CustomerOrder): Boolean {
    val shipping = order.shippingState?.trim()?.uppercase()
    if ("DELIVERY".equals(order.orderType, ignoreCase = true)
        && !shipping.isNullOrBlank()
        && shipping !in setOf("LEGACY", "ACCEPTED")
    ) {
        return true
    }
    val pay = order.paymentState?.trim()?.uppercase()
    if (pay in setOf("PROOF_SUBMITTED", "CHECKING", "REVIEW", "LATE_REVIEW")) return true
    if (order.awaitingCustomerReceipt == true || order.customerReceiptState.equals("NOT_RECEIVED", true)) return true
    if ((pay.isNullOrBlank() || pay == "NONE") && order.status.equals("PENDING", ignoreCase = true)) return true
    return pay in setOf("AWAITING_PAYMENT", "EXPIRED", "REJECTED")
}

private fun fulfillmentShort(order: CustomerOrder): String =
    when (order.orderType?.trim()?.uppercase()) {
        "PICKUP" -> "ဆိုင်လာယူ"
        "DELIVERY" -> "သွားပို့ · ${deliveryStatusLabel(order.deliveryStatus, order.customerReceiptState)}"
        else -> "—"
    }

private fun productSummary(lines: List<OrderLine>): String {
    if (lines.isEmpty()) return "ပစ္စည်းစာရင်း မရှိပါ"
    val first = lines.first().productName.orEmpty().ifBlank { "ပစ္စည်း" }
    val extra = lines.size - 1
    return if (extra > 0) "$first · နောက်ထပ် $extra မျိုး" else first
}

fun deliveryStatusLabel(status: String?, receiptState: String? = null): String {
    if (receiptState.equals("CONFIRMED", ignoreCase = true)) return "လက်ထဲ ရောက်ပြီး"
    if (receiptState.equals("NOT_RECEIVED", ignoreCase = true)) return "မရောက်သေး"
    return when (status?.trim()?.uppercase()) {
    "PACKING" -> "ထုပ်ပိုးနေသည်"
    "PACKED" -> "ထုပ်ပိုးပြီး"
    "HANDED_TO_RIDER" -> "Rider ထံ အပ်ပြီး"
    "OUT_FOR_DELIVERY" -> "လာပို့နေပြီ"
    "IN_TRANSIT" -> "လမ်းမှာ"
    "DELIVERED" -> "ဆိုင်က ရောက်သည်ဟု မှတ်ထား"
    "PENDING", null, "" -> "မပို့သေး"
    else -> status.replace('_', ' ')
    }
}

private fun money(amount: Double?): String {
    val safe = amount?.takeIf { it.isFinite() } ?: 0.0
    val absValue = abs(safe).toLong()
    val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
}

private fun formatOrderDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return raw.replace('T', ' ').take(16)
}

@Composable
fun CustomerOrderHistoryCard(
    order: CustomerOrder,
    onOrderUpdated: (CustomerOrder) -> Unit = {},
    onReorder: (CustomerOrder) -> Unit = {},
    expanded: Boolean = false,
    pickedImageUri: Uri? = null,
    onPickImage: () -> Unit = {},
    onCardClick: (() -> Unit)? = null
) {
    val needsAttention = orderNeedsAttention(order)
    val lines = order.lines.orEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openingInvoice by remember(order.id) { mutableStateOf(false) }
    var invoiceError by remember(order.id) { mutableStateOf<String?>(null) }
    var showInAppInvoiceDialog by remember(order.id) { mutableStateOf(false) }
    var receiptBusy by remember(order.id) { mutableStateOf(false) }
    var receiptError by remember(order.id) { mutableStateOf<String?>(null) }
    var showAllItems by remember(order.id) { mutableStateOf(false) }
    val canOpenInvoice = order.completedSaleId != null || order.completedSale != null

    val orderStatus = orderStatusUi(order.status, order.paymentState)
    val payState = order.paymentState?.trim()?.uppercase().orEmpty()
    val showPaymentBadge = payState.isNotBlank() && payState != "NONE" && payState != "FULFILLED"
    val pay = paymentStatusUi(order.paymentState)
    val payNow = orderPayNowAmount(order)
    val amountLabel = if (payNow > 0.0) "ပေးရန်" else "စုစုပေါင်း"

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(
            1.dp,
            if (needsAttention) Warning.copy(alpha = 0.45f) else BorderColor
        ),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCardClick?.invoke() },
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.ShoppingBag, null, tint = Primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        order.orderNo.orEmpty().ifBlank { "App Order" },
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${formatOrderDate(order.createdAt)} · ${fulfillmentShort(order)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        productSummary(lines),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = orderStatus.bg,
                        border = BorderStroke(1.dp, orderStatus.fg.copy(alpha = 0.15f))
                    ) {
                        Text(
                            orderStatus.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = orderStatus.fg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    if (showPaymentBadge) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = pay.bg,
                            border = BorderStroke(1.dp, pay.fg.copy(alpha = 0.15f))
                        ) {
                            Text(
                                pay.label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = pay.fg,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        amountLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        money(payNow.takeIf { it > 0.0 } ?: order.total),
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "အသေးစိတ်",
                        tint = TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (expanded) OrderProgressStepper(order)

            if (!expanded) {
                Text(
                    if (needsAttention) "နှိပ်ပြီး ငွေလွှဲ / အတည်ပြု ဆက်လုပ်ပါ" else "အသေးစိတ် ကြည့်ရန် နှိပ်ပါ",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (needsAttention) Warning else TextMuted
                )
            } else {
            HorizontalDivider(color = BorderColor.copy(alpha = 0.8f))

            if (needsAttention) {
                PrimaryActionBanner(order)
            }

            OrderLifecycleTimeline(order)

            PaymentSummaryCard(order)

            SectionHeader("အချက်အလက်")

            Text(
                "ပါဝင်သော ပစ္စည်းများ",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
            )

            if (lines.isEmpty()) {
                Text("ပစ္စည်းစာရင်း မရှိပါ", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            } else {
                val visibleLines = if (showAllItems) lines else lines.take(3)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleLines.forEach { line ->
                        OrderLineRow(line)
                    }
                }
                if (lines.size > 3) {
                    TextButton(
                        onClick = { showAllItems = !showAllItems },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            if (showAllItems) "ခေါက်သိမ်းမည်" else "အားလုံး ပြရန် (${lines.size})",
                            color = Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!order.note.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceSoft,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("မှတ်ချက်", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(order.note, style = MaterialTheme.typography.bodySmall, color = TextMain)
                    }
                }
            }

            SectionHeader("ငွေပေးချေမှု")

                CustomerOrderPaymentCard(
                    order,
                    onOrderUpdated = onOrderUpdated,
                    onReorder = onReorder,
                    pickedImageUri = pickedImageUri,
                    onPickImage = onPickImage
                )

                order.completedSale?.let { invoice ->
                    Surface(
                        modifier = Modifier.clickable { showInAppInvoiceDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = SuccessBg,
                        border = BorderStroke(1.dp, Success.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Sale Invoice (ဆိုင်ဘောင်ချာ)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Success
                            )
                            Text(
                                invoice.saleCode.orEmpty().ifBlank { "Sale #${invoice.id}" },
                                fontWeight = FontWeight.Bold,
                                color = TextMain,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                listOfNotNull(
                                    invoice.saleDate?.replace('T', ' ')?.take(16),
                                    invoice.paymentStatus
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                            Text(
                                money(invoice.netAmount),
                                fontWeight = FontWeight.Bold,
                                color = Success,
                                fontSize = 16.sp
                            )
                            invoice.lines.orEmpty().forEach { line ->
                                val warranty = formatWarranty(line.warrantyMonths, line.warrantyStartDate, line.warrantyExpiryDate)
                                val stateLabel = formatWarrantyState(line.warrantyStatus, line.warrantyDaysRemaining)
                                Text(
                                    buildString {
                                        append(line.productName.orEmpty().ifBlank { "Product" })
                                        append(" · Qty ${line.qty ?: 1}")
                                        line.serialNumber?.takeIf { it.isNotBlank() }?.let { append(" · S/N $it") }
                                        if (warranty.isNotBlank()) append(" · အာမခံ $warranty")
                                        if (stateLabel.isNotBlank()) append(" · $stateLabel")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (line.warrantyStatus == "EXPIRED") Danger else TextMain
                                )
                            }
                            if ((order.depositAmount ?: 0.0) > 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CardBg,
                                    border = BorderStroke(1.dp, BorderColor)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("Sale ငွေပေးချေမှု", fontWeight = FontWeight.Bold, color = TextMain)
                                        Text("စရံ · ${money(order.depositAmount)} · ${order.paymentMethodName ?: "Channel"} ✓", style = MaterialTheme.typography.bodySmall, color = Success)
                                        Text("ကျန်ငွေ · ${money(order.collectionAmount ?: order.remainingAmount)} · ${order.collectionPaymentMethodName ?: "Channel"} ✓", style = MaterialTheme.typography.bodySmall, color = Success)
                                        HorizontalDivider(color = BorderColor)
                                        Text("ပေးပြီးစုစုပေါင်း · ${money(invoice.netAmount)}", fontWeight = FontWeight.SemiBold, color = TextMain)
                                        Text("ပေးရန်ကျန် · ${money(0.0)}", fontWeight = FontWeight.Bold, color = Success)
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        showInAppInvoiceDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text(
                                        "PDF ဖွင့်",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        openingInvoice = true
                                        invoiceError = null
                                        scope.launch {
                                            invoiceError = SaleInvoiceOpener.shareForOrder(
                                                context,
                                                order.id,
                                                invoice.saleCode
                                            )
                                            openingInvoice = false
                                        }
                                    },
                                    enabled = !openingInvoice,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Send", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text(
                                "ဆိုင် POS ဘောင်ချာ template အတိုင်း server က ထုတ်ပေးသည်။",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            invoiceError?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
                            }
                        }
                    }
                } ?: run {
                    if (canOpenInvoice) {
                        Surface(
                            modifier = Modifier.clickable { showInAppInvoiceDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            color = SuccessBg,
                            border = BorderStroke(1.dp, Success.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Sale #${order.completedSaleId} — ဘောင်ချာ ထုတ်ပြီးပါပြီ",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Success
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            showInAppInvoiceDialog = true
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                    ) {
                                        Text(
                                            "PDF ဖွင့်",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            openingInvoice = true
                                            invoiceError = null
                                            scope.launch {
                                                invoiceError = SaleInvoiceOpener.shareForOrder(
                                                    context,
                                                    order.id,
                                                    null
                                                )
                                                openingInvoice = false
                                            }
                                        },
                                        enabled = !openingInvoice,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
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
                }

            if ("DELIVERY".equals(order.orderType, ignoreCase = true)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryLight.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "ပို့ဆောင်မှု · ${deliveryStatusLabel(order.deliveryStatus, order.customerReceiptState)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryDark
                        )
                        Text(
                            when (order.deliveryHandler) {
                                "HANDOFF" -> "အပြင်ပို့ အပ် · ဆိုင်ပို့ခ မကောက် / ဘောင်ချာမထည့်"
                                "OWN" -> "ဆိုင်ကပို့ · ပို့ခ ဘောင်ချာထည့်"
                                else -> "ပို့ပုံကို ဆိုင်က ဆုံးဖြတ်မည်"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMain
                        )
                        val townshipLine = listOfNotNull(
                            order.townshipName?.takeIf { it.isNotBlank() },
                            order.wardName?.takeIf { it.isNotBlank() },
                            if (order.deliveryHandler == "HANDOFF") "ဆိုင်ပို့ခ ၀"
                            else order.deliveryCharge?.takeIf { it > 0 }?.let { "charge ${money(it)}" }
                        ).joinToString(" · ")
                        if (townshipLine.isNotBlank()) {
                            Text(townshipLine, style = MaterialTheme.typography.bodySmall, color = TextMain)
                        }
                        order.deliveryAddress?.takeIf { it.isNotBlank() }?.let {
                            Text("လိပ်စာ · $it", style = MaterialTheme.typography.bodySmall, color = TextMain)
                        }
                        order.deliveryCurrentLocation?.takeIf { it.isNotBlank() }?.let {
                            Text("လက်ရှိနေရာ · $it", style = MaterialTheme.typography.bodySmall, color = TextMain)
                        }
                        order.deliveryScheduledAt?.takeIf { it.isNotBlank() }?.let { scheduled ->
                            val req = order.requestedDeliveryAt?.replace('T', ' ')?.take(16) ?: ""
                            val sch = scheduled.replace('T', ' ').take(16)
                            val changed = req.isNotBlank() && req != sch

                            if (changed) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = WarningBg,
                                    border = BorderStroke(1.dp, Warning.copy(alpha = 0.25f))
                                ) {
                                    Text(
                                        "ပို့မည့်အချိန် (ဆိုင်ပြောင်းထားသည်) · ${formatOrderDate(scheduled)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Warning,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Text("ပို့မည့်အချိန် · ${formatOrderDate(scheduled)}", style = MaterialTheme.typography.bodySmall, color = TextMain)
                            }
                        }
                        order.deliveryPersonPhone?.takeIf { it.isNotBlank() }?.let {
                            Text("ပို့သူ ဖုန်း · $it", style = MaterialTheme.typography.bodySmall, color = Primary)
                        }
                        order.deliveredAt?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                if (order.customerReceiptState.equals("CONFIRMED", true))
                                    "ဆိုင်မှတ်တမ်း · ${formatOrderDate(it)}"
                                else "ဆိုင်က ရောက်သည်ဟု မှတ်ထား · ${formatOrderDate(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (order.customerReceiptState.equals("CONFIRMED", true)) Success else Warning
                            )
                        }
                        DeliveryProgressTimeline(order)
                    }
                }
            } else if ("PICKUP".equals(order.orderType, ignoreCase = true)) {
                Text(
                    "ဆိုင်မှာ လာယူမည်",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }

            SectionHeader("လုပ်ဆောင်ရန်")

            CustomerReceiptConfirmBlock(
                order = order,
                busy = receiptBusy,
                error = receiptError,
                onConfirm = { received ->
                    receiptBusy = true
                    receiptError = null
                    scope.launch {
                        try {
                            val prefs = PreferenceManager(context)
                            val response = ApiClient.service.confirmReceipt(
                                ApiClient.bearer(prefs.authToken),
                                order.id,
                                ReceiptConfirmRequest(received)
                            )
                            val updated = response.body()?.data
                            if (response.isSuccessful && updated != null) {
                                onOrderUpdated(updated)
                            } else {
                                receiptError = response.body()?.message ?: "အတည်ပြုမရပါ"
                            }
                        } catch (e: Exception) {
                            receiptError = e.message ?: "ချိတ်ဆက်မှု ပြန်စစ်ပါ"
                        } finally {
                            receiptBusy = false
                        }
                    }
                }
            )



            OrderRatingBlock(order = order, onOrderUpdated = onOrderUpdated)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${lines.sumOf { it.qty ?: 0 }} ခု · ${lines.size} မျိုး",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if ((order.discountAmount ?: 0.0) > 0.0) {
                        Text(
                            "Overall လျှော့ -${money(order.discountAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Danger
                        )
                    }
                    Text("စုစုပေါင်း", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        money(order.total),
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            if (showInAppInvoiceDialog) {
                SaleInvoiceViewerDialog(
                    orderId = order.id,
                    purchaseSaleId = order.completedSaleId ?: order.completedSale?.id,
                    saleCode = order.completedSale?.saleCode,
                    onDismiss = { showInAppInvoiceDialog = false }
                )
            }
            }
        }
    }
}

@Composable
private fun DeliveryProgressTimeline(order: CustomerOrder) {
    val milestones = order.deliveryMilestones.orEmpty()
    if (milestones.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
        Text(
            "ပို့ဆောင်မှု ဖြစ်စဉ် (Progress Tracker)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryDark,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val reversedMilestones = milestones.asReversed().take(8)
        reversedMilestones.forEachIndexed { index, row ->
            val isLatest = index == 0
            val isLast = index == reversedMilestones.size - 1
            val stamp = row.at?.takeIf { it.isNotBlank() }?.let { formatOrderDate(it) }
            val displayActor = row.actor?.takeIf { it.isNotBlank() && !it.contains("@") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(if (isLatest) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (isLatest) Primary else TextMuted.copy(alpha = 0.4f))
                    )
                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(if (isLatest) Primary.copy(alpha = 0.3f) else BorderColor)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (!isLast) 6.dp else 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = deliveryStatusLabel(row.toStatus),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLatest) Primary else TextMain
                        )
                        stamp?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    displayActor?.let { actor ->
                        Text(
                            text = actor,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    row.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StarRatingBar(
    rating: Int,
    maxStars: Int = 5,
    onRatingChanged: ((Int) -> Unit)? = null,
    starSize: Dp = 22.dp
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..maxStars) {
            val isSelected = i <= rating
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = "ကြယ် $i ပွင့်",
                tint = if (isSelected) Color(0xFFFFB800) else Color(0xFFD1D5DB),
                modifier = Modifier
                    .size(starSize)
                    .then(
                        if (onRatingChanged != null) {
                            Modifier.clickable { onRatingChanged(i) }
                        } else Modifier
                    )
            )
        }
    }
}


@Composable
private fun OrderRatingBlock(
    order: CustomerOrder,
    onOrderUpdated: (CustomerOrder) -> Unit = {}
) {
    if (!CustomerAppFeatures.ORDER_RATINGS) return
    val receiptOk = order.customerReceiptState.equals("CONFIRMED", ignoreCase = true)
    if (!receiptOk && order.rating == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentRating by remember(order.id, order.rating) { mutableStateOf(order.rating) }
    var review by remember(order.id) { mutableStateOf("") }
    var message by remember(order.id) { mutableStateOf<String?>(null) }
    var busy by remember(order.id) { mutableStateOf(false) }
    val serviceLabel = if (order.orderType.equals("PICKUP", ignoreCase = true)) "ဆိုင်ဝန်ဆောင်မှု" else "ပို့ဆောင်မှု"

    // Once rated (currentRating != null), cannot rate or edit again
    val canRate = receiptOk && currentRating == null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = SurfaceSoft,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    if (currentRating != null) "အဆင့်သတ်မှတ်ချက်" else "အဆင့်သတ်မှတ်ရန်",
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
            }

            currentRating?.let { rate ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("ပစ္စည်းအဆင့်:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        StarRatingBar(rating = rate.productRating ?: rate.rating ?: 5, starSize = 18.dp)
                    }
                    val sRating = rate.serviceRating
                    if (sRating != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("$serviceLabel:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            StarRatingBar(rating = sRating, starSize = 18.dp)
                        }
                    }
                    val comment = rate.comment ?: rate.review
                    if (!comment.isNullOrBlank()) {
                        Text("မှတ်ချက်: $comment", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }

            if (!receiptOk && currentRating == null) {
                Text("လက်ခံအတည်ပြုပြီးမှ အဆင့်ပေးနိုင်သည်။", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }

            if (canRate) {
                var pStars by remember(order.id) { mutableIntStateOf(5) }
                var sStars by remember(order.id) { mutableIntStateOf(5) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ပစ္စည်းအဆင့် ပေးပါ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        StarRatingBar(
                            rating = pStars,
                            onRatingChanged = { pStars = it },
                            starSize = 26.dp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$serviceLabel အဆင့် ပေးပါ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        StarRatingBar(
                            rating = sStars,
                            onRatingChanged = { sStars = it },
                            starSize = 26.dp
                        )
                    }

                    OutlinedTextField(
                        value = review,
                        onValueChange = { review = it },
                        label = { Text("မှတ်ချက် (ဆန္ဒရှိပါက ထည့်ပါ)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            val productStars = pStars
                            val serviceStars = sStars
                            if (productStars !in 1..5 || serviceStars !in 1..5) {
                                message = "အဆင့် 1 မှ 5"
                                return@OutlinedButton
                            }
                            busy = true
                            message = null
                            scope.launch {
                                try {
                                    val prefs = PreferenceManager(context)
                                    val res = ApiClient.service.rateOrder(
                                        ApiClient.bearer(prefs.authToken),
                                        order.id,
                                        OrderRatingRequest(productStars, serviceStars, review.trim().ifBlank { null })
                                    )
                                    if (res.isSuccessful) {
                                        val newRating = OrderRating(
                                            productRating = productStars,
                                            serviceRating = serviceStars,
                                            comment = review.trim().ifBlank { null }
                                        )
                                        currentRating = newRating
                                        val updatedOrder = order.copy(
                                            rating = newRating,
                                            canRate = false,
                                            canEditRating = false
                                        )
                                        onOrderUpdated(updatedOrder)
                                        message = "အဆင့်ပေးပြီးပါပြီ"
                                    } else {
                                        message = res.body()?.message ?: "မတင်နိုင်ပါ"
                                    }
                                } catch (e: Exception) {
                                    message = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) { Text("အဆင့်ပေးမည်", fontWeight = FontWeight.SemiBold) }
                }
            }

            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Primary) }
        }
    }
}

@Composable
private fun CustomerReceiptConfirmBlock(
    order: CustomerOrder,
    busy: Boolean,
    error: String?,
    onConfirm: (Boolean) -> Unit
) {
    val receipt = order.customerReceiptState?.trim()?.uppercase().orEmpty()
    val awaiting = order.awaitingCustomerReceipt == true || receipt == "NOT_RECEIVED"
    if (!awaiting && receipt != "CONFIRMED") return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = when (receipt) {
            "CONFIRMED" -> SuccessBg
            "NOT_RECEIVED" -> DangerBg
            else -> WarningBg
        },
        border = BorderStroke(
            1.dp,
            when (receipt) {
                "CONFIRMED" -> Success.copy(alpha = 0.25f)
                "NOT_RECEIVED" -> Danger.copy(alpha = 0.25f)
                else -> Warning.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (receipt) {
                    "CONFIRMED" -> "ပစ္စည်း လက်ထဲ ရောက်ကြောင်း အတည်ပြုပြီး"
                    "NOT_RECEIVED" -> "ပစ္စည်း မရောက်သေးဟု ပြောထားသည်"
                    else -> "ပစ္စည်း လက်ထဲ ရောက်ပါသလား?"
                },
                fontWeight = FontWeight.Bold,
                color = when (receipt) {
                    "CONFIRMED" -> Success
                    "NOT_RECEIVED" -> Danger
                    else -> Warning
                }
            )
            Text(
                when (receipt) {
                    "CONFIRMED" -> "ဆိုင်သို့ အသိပေးပြီးပါပြီ။"
                    "NOT_RECEIVED" -> "ဆိုင်က ပြန်စစ်ပြီး ပို့ပေးပါမည်။ ရောက်ရင် အောက်က ခလုတ်နှိပ်ပါ။"
                    else -> "ဆိုင်က ပို့ပြီး / ရောက်သည်ဟု မှတ်ထားနိုင်ပါတယ်။ လက်ထဲ အမှန်တကယ် ရောက်မှသာ အတည်ပြုပါ။"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMain
            )
            order.customerReceivedAt?.takeIf { it.isNotBlank() }?.let {
                Text("အတည်ပြုချိန် · ${formatOrderDate(it)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
            if (receipt != "CONFIRMED") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = { onConfirm(true) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Success)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnPrimary)
                        else Text("ရောက်ပါပြီ", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { onConfirm(false) },
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("မရောက်သေး", fontWeight = FontWeight.SemiBold, color = Danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderLineRow(line: OrderLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceSoft)
            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Inventory2,
            null,
            tint = Primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                line.productName.orEmpty().ifBlank { "ပစ္စည်း" },
                fontWeight = FontWeight.SemiBold,
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "× ${line.qty ?: 0} · ${money(line.unitPrice)} / ခု",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            if (line.lineDiscount() > 0.0) {
                Text(
                    "လိုင်းလျှော့ · -${money(line.lineDiscount())}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Danger
                )
            }
        }
        Text(
            money(line.subtotal ?: ((line.unitPrice ?: 0.0) * (line.qty ?: 0))),
            fontWeight = FontWeight.Bold,
            color = TextMain
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = PrimaryDark
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(color = BorderColor.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PrimaryActionBanner(order: CustomerOrder) {
    val pay = order.paymentState?.trim()?.uppercase().orEmpty()
    val awaitingReceipt = order.awaitingCustomerReceipt == true || order.customerReceiptState.equals("NOT_RECEIVED", true)
    val needsPayment = pay in setOf("AWAITING_PAYMENT", "EXPIRED", "REJECTED") ||
        ((pay.isBlank() || pay == "NONE") && order.status.equals("PENDING", true))
    val (title, desc) = when {
        awaitingReceipt -> "ပစ္စည်း လက်ထဲ ရောက်ပြီလား?" to "ရောက်ပြီဆိုရင် အောက်မှာ အတည်ပြုပေးပါ"
        needsPayment -> "ငွေလွှဲရန် ကျန်နေပါသည်" to "အောက်က ငွေပေးချေမှု အပိုင်းမှာ ဆက်လုပ်ပါ"
        else -> "ဆက်လုပ်ဆောင်ရန် ကျန်ပါသည်" to "အောက်မှာ အသေးစိတ် ကြည့်ပါ"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = WarningBg,
        border = BorderStroke(1.dp, Warning.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Warning, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = TextMain)
        }
    }
}

@Composable
private fun OrderLifecycleTimeline(order: CustomerOrder) {
    val events = order.timeline.orEmpty()
    if (events.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            "အော်ဒါ ဖြစ်စဉ်",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryDark,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val reversed = events.asReversed().take(8)
        reversed.forEachIndexed { index, ev ->
            val isLatest = index == 0
            val isLast = index == reversed.size - 1
            val stamp = ev.at?.takeIf { it.isNotBlank() }?.let { formatOrderDate(it) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(if (isLatest) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (isLatest) Primary else TextMuted.copy(alpha = 0.4f))
                    )
                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(if (isLatest) Primary.copy(alpha = 0.3f) else BorderColor)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).padding(bottom = if (!isLast) 6.dp else 0.dp)) {
                    Text(
                        ev.action?.replace('_', ' ') ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLatest) Primary else TextMain
                    )
                    stamp?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 11.sp)
                    }
                    ev.details?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentSummaryCard(order: CustomerOrder) {
    val total = order.total ?: 0.0
    val deposit = order.depositAmount ?: 0.0
    val remaining = order.remainingAmount ?: (total - deposit)
    val payNow = orderPayNowAmount(order)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceSoft,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "ငွေပေးချေမှု အကျဉ်း",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("စုစုပေါင်း", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text(money(total), fontWeight = FontWeight.Bold, color = TextMain)
            }
            if (deposit > 0.0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("စရံ", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text(money(deposit), style = MaterialTheme.typography.bodySmall, color = TextMain)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ကျန်ငွေ", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text(money(remaining), style = MaterialTheme.typography.bodySmall, color = TextMain)
                }
            }
            if (payNow > 0.0) {
                HorizontalDivider(color = BorderColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ပေးရန်", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PrimaryDark)
                    Text(money(payNow), fontWeight = FontWeight.Bold, color = Primary)
                }
            }
        }
    }
}

@Composable
fun CustomerOrderHistoryEmpty() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.ShoppingBag, null, tint = TextMuted, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text("App အော်ဒါ မရှိသေးပါ", fontWeight = FontWeight.SemiBold, color = TextMain)
            Text(
                "ခြင်းတောင်းမှ အော်ဒါတင်ပြီးရင် ဤနေရာမှာ မြင်ရပါမည်",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
fun CustomerOrderHistoryList(
    modifier: Modifier = Modifier,
    orders: List<CustomerOrder>,
    onOrderUpdated: (CustomerOrder) -> Unit = {}
) {
    var selectedOrderId by remember { mutableStateOf<Int?>(null) }
    var filter by remember { mutableStateOf("ACTION") }
    var query by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    val selectedOrder = orders.find { it.id == selectedOrderId }
    if (selectedOrderId != null && selectedOrder == null) selectedOrderId = null

    val completedStates = setOf("COMPLETED", "CANCELLED", "CANCELED", "REJECTED")
    fun isFinished(order: CustomerOrder) =
        order.status?.uppercase() in completedStates || order.paymentState.equals("FULFILLED", true)
    val actionCount = orders.count { orderNeedsAttention(it) }
    val activeCount = orders.count { !isFinished(it) }
    val doneCount = orders.count { isFinished(it) }
    val visibleOrders = orders.filter { order ->
        val q = query.trim().lowercase()
        val matchesSearch = q.isBlank() || listOf(order.orderNo, productSummary(order.lines.orEmpty()), fulfillmentShort(order)).any { it.orEmpty().lowercase().contains(q) }
        val matchesFilter = when (filter) {
            "ACTION" -> orderNeedsAttention(order)
            "ACTIVE" -> !isFinished(order)
            "DONE" -> isFinished(order)
            else -> true
        }
        matchesSearch && matchesFilter
    }.sortedWith(
        compareByDescending<CustomerOrder> { orderNeedsAttention(it) }
            .then(
                if (newestFirst) compareByDescending<CustomerOrder> { it.id }
                else compareBy<CustomerOrder> { it.id }
            )
    )

    Column(modifier = modifier.fillMaxWidth().background(ScreenBg).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selectedOrder != null) {
            Surface(onClick = { selectedOrderId = null }, shape = RoundedCornerShape(12.dp), color = PrimaryLight, border = BorderStroke(1.dp, Primary.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("အော်ဒါစာရင်းသို့ ပြန်မည်", fontWeight = FontWeight.Bold, color = PrimaryDark)
                        Text(selectedOrder.orderNo.orEmpty(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
            CustomerOrderHistoryCard(order = selectedOrder, onOrderUpdated = onOrderUpdated, expanded = true)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("ကျွန်ုပ်၏ အော်ဒါများ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = PrimaryDark)
                    Text("စုစုပေါင်း ${orders.size} ခု", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                TextButton(onClick = { newestFirst = !newestFirst }) {
                    Icon(Icons.Outlined.Sort, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (newestFirst) "နောက်ဆုံး" else "အဟောင်း", color = TextMuted, fontSize = 12.sp)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Order No. သို့မဟုတ် ပစ္စည်းအမည် ရှာရန်") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "ရှာဖွေမှု ရှင်းမည်", tint = TextMuted)
                        }
                    }
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val tabs = listOf(
                    "ACTION" to "လုပ်ရန် (${actionCount})",
                    "ACTIVE" to "ဆောင်ရွက်ဆဲ (${activeCount})",
                    "DONE" to "ပြီးဆုံး (${doneCount})",
                    "ALL" to "အားလုံး (${orders.size})"
                )
                tabs.forEach { (id, label) ->
                    if (filter == id) Button(onClick = { filter = id }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text(label, fontSize = 11.sp, maxLines = 1) }
                    else OutlinedButton(onClick = { filter = id }, modifier = Modifier.weight(1f)) { Text(label, fontSize = 11.sp, maxLines = 1) }
                }
            }
            if (orders.isEmpty()) CustomerOrderHistoryEmpty()
            else if (visibleOrders.isEmpty()) Surface(shape = RoundedCornerShape(14.dp), color = CardBg, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ကိုက်ညီသော အော်ဒါ မရှိပါ", fontWeight = FontWeight.Bold, color = TextMain)
                    Text("Filter သို့မဟုတ် ရှာဖွေစာကို ပြောင်းကြည့်ပါ", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { query = ""; filter = "ALL" }) {
                        Text("ရှာဖွေမှု ရှင်းမည်", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            else visibleOrders.forEach { order ->
                CustomerOrderHistoryCard(order = order, onOrderUpdated = onOrderUpdated, expanded = false, onCardClick = { selectedOrderId = order.id })
            }
        }
    }
}
private fun sampleOrders(): List<CustomerOrder> {
    val now = System.currentTimeMillis()
    return listOf(
        CustomerOrder(
            id = 1,
            orderNo = "CA-000041",
            status = "PENDING",
            paymentState = "NONE",
            paymentChoice = "TRANSFER",
            note = "ဆိုင်မှာ လာယူမည်",
            total = 1_398_000.0,
            createdAt = "2026-09-07T14:20:00",
            orderType = "PICKUP",
            lines = listOf(
                OrderLine(1, "Dell Inspiron 15 3530", 1, 1_350_000.0, 1_350_000.0),
                OrderLine(2, "HP 15.6 Backpack", 1, 48_000.0, 48_000.0)
            )
        ),
        CustomerOrder(
            id = 2,
            orderNo = "CA-000055",
            status = "CONFIRMED",
            paymentState = "AWAITING_PAYMENT",
            paymentChoice = "TRANSFER",
            paymentMethodId = 3,
            paymentMethodName = "KBZPay",
            payeeName = "ဦးမောင်",
            payeeAccountNo = "09xxxxxxxx",
            paymentInstructions = "KBZPay · ဦးမောင် · 09xxxxxxxx\nအကောင့်အမည်: ဦးမောင်\nအကောင့်နံပါတ်: 09xxxxxxxx",
            reservationExpiresAtEpochMillis = now + 12 * 60_000L,
            total = 450_000.0,
            itemsTotal = 420_000.0,
            deliveryCharge = 30_000.0,
            createdAt = "2026-09-07T22:10:00",
            orderType = "DELIVERY",
            townshipName = "စမ်းချောင်း",
            deliveryStatus = "PENDING",
            deliveryAddress = "အမှတ် ၁၂၊ ဗိုလ်ချုပ်လမ်း",
            lines = listOf(
                OrderLine(5, "Keychron K2 Keyboard", 1, 420_000.0, 420_000.0)
            ),
            deliveryMilestones = listOf(
                OrderDeliveryMilestone(1, "PENDING", "PACKING", "hlainghtun2018@gmail.com", at = "2026-09-11T12:44:00"),
                OrderDeliveryMilestone(2, "PACKING", "HANDED_TO_RIDER", "hlainghtun2018@gmail.com", at = "2026-09-11T12:45:00"),
                OrderDeliveryMilestone(3, "HANDED_TO_RIDER", "OUT_FOR_DELIVERY", "hlainghtun2018@gmail.com", at = "2026-09-11T12:45:00")
            )
        ),
        CustomerOrder(
            id = 3,
            orderNo = "CA-000048",
            status = "CONFIRMED",
            paymentState = "REVIEW",
            paymentChoice = "TRANSFER",
            paymentMethodId = 3,
            paymentMethodName = "KBZPay",
            payeeName = "ဦးမောင်",
            payeeAccountNo = "09xxxxxxxx",
            paymentInstructions = "KBZPay · ဦးမောင် · 09xxxxxxxx",
            total = 185_000.0,
            createdAt = "2026-09-07T18:00:00",
            orderType = "PICKUP",
            lines = listOf(
                OrderLine(6, "Logitech MX Master 3S", 1, 185_000.0, 185_000.0)
            )
        ),
        CustomerOrder(
            id = 4,
            orderNo = "CA-000038",
            status = "CONFIRMED",
            paymentState = "FULFILLED",
            customerReceiptState = "CONFIRMED",
            canRate = true,
            paymentChoice = "TRANSFER",
            paymentMethodId = 2,
            paymentMethodName = "WavePay",
            completedSaleId = 128,
            total = 45_000.0,
            createdAt = "2026-09-06T10:05:00",
            orderType = "PICKUP",
            lines = listOf(
                OrderLine(3, "Logitech Wireless Mouse M331", 1, 45_000.0, 45_000.0)
            ),
            completedSale = CustomerPurchase(
                id = 128,
                saleCode = "SALE-000128",
                saleDate = "2026-09-06T11:30:00",
                netAmount = 45_000.0,
                paymentStatus = "PAID",
                lines = listOf(
                    PurchaseLine(
                        productName = "Logitech Wireless Mouse M331",
                        qty = 1,
                        unitPrice = 45_000.0,
                        subtotal = 45_000.0,
                        serialNumber = "SN-M331-88991"
                    )
                )
            )
        ),
        CustomerOrder(
            id = 5,
            orderNo = "CA-000030",
            status = "CANCELLED",
            paymentState = "REFUNDED",
            paymentChoice = "TRANSFER",
            paymentReviewNote = "Stock မရှိ — ငွေပြန်အမ်းပြီး",
            note = "ပစ္စည်း မလိုတော့ပါ",
            total = 280_000.0,
            createdAt = "2026-09-05T18:40:00",
            orderType = "DELIVERY",
            lines = listOf(
                OrderLine(4, "Samsung 24 inch Monitor", 1, 280_000.0, 280_000.0)
            )
        )
    )
}

@Composable
private fun OrderHistoryPreviewHost(orders: List<CustomerOrder>) {
    AppTheme {
        Surface(color = ScreenBg, modifier = Modifier.fillMaxWidth()) {
            CustomerOrderHistoryList(orders = orders)
        }
    }
}

@Preview(
    name = "1 · Order History — full list",
    showBackground = true,
    backgroundColor = 0xFFF4F7FA,
    widthDp = 390,
    heightDp = 1200
)
@Preview(name = "1b · Order History — dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 390, heightDp = 1200)
@Composable
private fun OrderHistoryListPreview() {
    OrderHistoryPreviewHost(sampleOrders())
}

@Preview(
    name = "2 · Order History — empty",
    showBackground = true,
    backgroundColor = 0xFFF4F7FA,
    widthDp = 390,
    heightDp = 400
)
@Composable
private fun OrderHistoryEmptyPreview() {
    OrderHistoryPreviewHost(emptyList())
}

@Preview(
    name = "3 · Pending — ငွေလွှဲမပြသေး",
    showBackground = true,
    backgroundColor = 0xFFF4F7FA,
    widthDp = 390,
    heightDp = 420
)
@Composable
private fun OrderPendingPreview() {
    AppTheme {
        Surface(color = ScreenBg, modifier = Modifier.padding(12.dp)) {
            CustomerOrderHistoryCard(sampleOrders()[0])
        }
    }
}

@Preview(
    name = "4 · Awaiting payment — countdown",
    showBackground = true,
    backgroundColor = 0xFFF4F7FA,
    widthDp = 390,
    heightDp = 640
)
@Composable
private fun OrderAwaitingPaymentPreview() {
    AppTheme {
        Surface(color = ScreenBg, modifier = Modifier.padding(12.dp)) {
            CustomerOrderHistoryCard(sampleOrders()[1], expanded = true)
        }
    }
}

@Preview(
    name = "5 · Review — စစ်ဆေးဆဲ",
    showBackground = true,
    backgroundColor = 0xFFF4F7FA,
    widthDp = 390,
    heightDp = 480
)
@Composable
private fun OrderReviewPreview() {
    AppTheme {
        Surface(color = ScreenBg, modifier = Modifier.padding(12.dp)) {
            CustomerOrderHistoryCard(sampleOrders()[2], expanded = true)
        }
    }
}

@Preview(
    name = "6 · Fulfilled — Sale Invoice",
    showBackground = true,
    backgroundColor = 0xFFF4F7FA,
    widthDp = 390,
    heightDp = 900
)
@Composable
private fun OrderInvoicePreview() {
    AppTheme {
        Surface(color = ScreenBg, modifier = Modifier.padding(12.dp)) {
            CustomerOrderHistoryCard(sampleOrders()[3], expanded = true)
        }
    }
}

@Preview(
    name = "7 · Cancelled / Refunded",
    showBackground = true,
    backgroundColor = 0xFFF4F7FA,
    widthDp = 390,
    heightDp = 420
)
@Composable
private fun OrderCancelledPreview() {
    AppTheme {
        Surface(color = ScreenBg, modifier = Modifier.padding(12.dp)) {
            CustomerOrderHistoryCard(sampleOrders()[4], expanded = true)
        }
    }
}



@Preview(
    name = "9 · Rating Block Card",
    showBackground = true,
    backgroundColor = 0xFFF4F7FA,
    widthDp = 390
)
@Composable
private fun OrderRatingBlockPreview() {
    AppTheme {
        Surface(color = ScreenBg, modifier = Modifier.padding(12.dp)) {
            OrderRatingBlock(sampleOrders()[3])
        }
    }
}
