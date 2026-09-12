package com.sspd.servicemgmt.feature.home

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.CustomerOrder
import com.sspd.servicemgmt.core.network.CustomerPaymentChannel
import com.sspd.servicemgmt.core.network.PaymentChannelRequest
import com.sspd.servicemgmt.core.network.OrderPaymentProofs
import com.sspd.servicemgmt.core.network.ShippingDecision
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.DangerBg
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.Success
import com.sspd.servicemgmt.core.ui.theme.SuccessBg
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.ui.theme.Warning
import com.sspd.servicemgmt.core.ui.theme.WarningBg
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.math.abs

data class PaymentStatusUi(
    val label: String,
    val fg: androidx.compose.ui.graphics.Color,
    val bg: androidx.compose.ui.graphics.Color
)

@Composable
fun paymentStatusUi(state: String?, expired: Boolean = false): PaymentStatusUi = when (state?.trim()?.uppercase()) {
    "AWAITING_PAYMENT" -> if (expired) {
        PaymentStatusUi("ငွေလွှဲချိန်ကုန်ပြီ", Danger, DangerBg)
    } else {
        PaymentStatusUi("ငွေလွှဲရန်", Warning, WarningBg)
    }
    "AWAITING_COLLECTION" -> PaymentStatusUi("လက်ခံချိန် ငွေရှင်း", Primary, PrimaryLight)
    "PROOF_SUBMITTED" -> PaymentStatusUi("ငွေလွှဲအချက်အလက် ပို့ပြီး", Warning, WarningBg)
    "REMAINDER_PROOF_SUBMITTED" -> PaymentStatusUi("ကျန်ငွေလွှဲအထောက်အထား ပို့ပြီး", Warning, WarningBg)
    "REMAINDER_CHECKING" -> PaymentStatusUi("ကျန်ငွေလွှဲ စစ်ဆေးနေသည်", Primary, PrimaryLight)
    "CHECKING" -> PaymentStatusUi("စစ်ဆေးနေသည်", Primary, PrimaryLight)
    "REVIEW" -> PaymentStatusUi("ငွေလွှဲ စစ်ဆေးဆဲ", Primary, PrimaryLight)
    "LATE_REVIEW" -> PaymentStatusUi("နောက်ကျငွေလွှဲ — စစ်ဆေးရန်", Warning, WarningBg)
    "EXPIRED" -> PaymentStatusUi("အချိန်ကုန် · အထောက်အထားတင်နိုင်", Danger, DangerBg)
    "REJECTED" -> PaymentStatusUi("အထောက်အထား ပယ်ချ", Danger, DangerBg)
    "DEPOSIT_PAID" -> PaymentStatusUi("စရံရရှိပြီး · ကျန်ငွေ ပို့ရောက်ချိန် / ဆိုင်မှာ", Success, SuccessBg)
    "PAID" -> PaymentStatusUi("ငွေအပြည့် ရရှိပြီး", Success, SuccessBg)
    "FULFILLED" -> PaymentStatusUi("ဘောင်ချာထုတ်ပြီး", Success, SuccessBg)
    "REFUND_REQUIRED" -> PaymentStatusUi("ငွေပြန်အမ်းရန်", Warning, WarningBg)
    "REFUNDED" -> PaymentStatusUi("ငွေပြန်အမ်းပြီး", TextMuted, SurfaceSoft)
    "FORFEITED" -> PaymentStatusUi("စရံသိမ်းပြီး ပယ်ဖျက်", TextMuted, SurfaceSoft)
    "NONE", null, "" -> PaymentStatusUi("ဆိုင်အတည်ပြု စောင့်ဆိုင်း", TextMuted, SurfaceSoft)
    else -> PaymentStatusUi(state.replace('_', ' '), TextMuted, SurfaceSoft)
}

private fun moneyKs(amount: Double?): String {
    val safe = amount?.takeIf { it.isFinite() } ?: 0.0
    val absValue = abs(safe).toLong()
    val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
}

private fun formatTransferAmount(amount: Double?): String {
    val safe = amount?.takeIf { it.isFinite() } ?: 0.0
    val bd = java.math.BigDecimal.valueOf(safe).setScale(2, java.math.RoundingMode.HALF_UP)
    return if (bd.remainder(java.math.BigDecimal.ONE).compareTo(java.math.BigDecimal.ZERO) == 0) {
        bd.toBigInteger().toString()
    } else {
        bd.toPlainString()
    }
}

private fun formatCountdown(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

private fun moneyValue(amount: Double?): Double {
    val safe = amount?.takeIf { it.isFinite() } ?: 0.0
    return if (safe < 0) 0.0 else safe
}

private fun isRemainderReady(order: CustomerOrder, remaining: Double): Boolean {
    val state = order.paymentState?.trim()?.uppercase().orEmpty()
    return state == "DEPOSIT_PAID" &&
        remaining > 0.0 &&
        (order.orderType != "DELIVERY" || order.deliveryStatus in setOf(
            "HANDED_TO_RIDER", "OUT_FOR_DELIVERY", "IN_TRANSIT", "DELIVERED"
        ))
}

/** Amount the customer must transfer now: deposit only, or remaining after deposit is already sent. */
internal fun orderPayNowAmount(order: CustomerOrder): Double {
    val state = order.paymentState?.trim()?.uppercase().orEmpty()
    val total = moneyValue(order.total)
    val deposit = moneyValue(order.depositAmount)
    val remaining = moneyValue(order.remainingAmount ?: (total - deposit))
    val remainderSubmitted = state in setOf("REMAINDER_PROOF_SUBMITTED", "REMAINDER_CHECKING")
    val remainderConfirmed = state in setOf("PAID", "FULFILLED") && moneyValue(order.collectionAmount) > 0.0
    val depositTransferred = state in setOf(
        "DEPOSIT_PAID", "REMAINDER_PROOF_SUBMITTED", "REMAINDER_CHECKING",
        "PAID", "FULFILLED", "PROOF_SUBMITTED", "CHECKING", "REVIEW", "LATE_REVIEW"
    )
    return when {
        state in setOf("PAID", "FULFILLED") || remainderConfirmed || remainderSubmitted -> 0.0
        isRemainderReady(order, remaining) -> remaining
        depositTransferred && deposit > 0.0 -> remaining
        depositTransferred -> 0.0
        deposit > 0.0 -> deposit
        else -> total
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerOrderPaymentCard(
    order: CustomerOrder,
    onOrderUpdated: (CustomerOrder) -> Unit = {},
    onReorder: (CustomerOrder) -> Unit = {},
    pickedImageUri: Uri? = null,
    onPickImage: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var current by remember(order.id) { mutableStateOf(order) }
    LaunchedEffect(order) { current = order }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var image by remember(order.id) { mutableStateOf<Uri?>(null) }
    LaunchedEffect(pickedImageUri) {
        if (pickedImageUri != null) image = pickedImageUri
    }
    var reference by remember(order.id) { mutableStateOf("") }
    var savedProofs by remember(order.id) { mutableStateOf(OrderPaymentProofs()) }
    LaunchedEffect(current.latestProofId, current.collectionProofId, current.paymentState) {
        if (current.latestProofId == null && current.collectionProofId == null) {
            savedProofs = OrderPaymentProofs()
        } else {
            runCatching {
                val prefs = PreferenceManager(context)
                ApiClient.service.orderPaymentProofs(ApiClient.bearer(prefs.authToken), current.id)
            }.getOrNull()?.body()?.data?.let { savedProofs = it }
        }
    }
    val state = current.paymentState ?: "NONE"
    val depositAmount = moneyValue(current.depositAmount)
    val remainingAmount = moneyValue(current.remainingAmount ?: (moneyValue(current.total) - depositAmount))
    val depositConfirmed = state in setOf("DEPOSIT_PAID", "REMAINDER_PROOF_SUBMITTED", "REMAINDER_CHECKING", "PAID", "FULFILLED")
    val remainderSubmitted = state in setOf("REMAINDER_PROOF_SUBMITTED", "REMAINDER_CHECKING")
    val remainderConfirmed = state in setOf("PAID", "FULFILLED") && moneyValue(current.collectionAmount) > 0.0
    val depositTransferred = depositConfirmed || (
        depositAmount > 0.0 && state in setOf("PROOF_SUBMITTED", "CHECKING", "REVIEW", "LATE_REVIEW")
    )
    val paidSoFar = when {
        remainderConfirmed -> depositAmount + moneyValue(current.collectionAmount)
        remainderSubmitted -> depositAmount + remainingAmount
        depositTransferred -> depositAmount
        else -> 0.0
    }
    val balanceDue = (moneyValue(current.total) - paidSoFar).coerceAtLeast(0.0)
    val remainderReady = isRemainderReady(current, remainingAmount)
    val transferDue = orderPayNowAmount(current)
    var amount by remember(order.id) {
        mutableStateOf(formatTransferAmount(transferDue))
    }
    LaunchedEffect(order.id, transferDue) {
        amount = formatTransferAmount(transferDue)
    }
    var submitting by remember { mutableStateOf(false) }
    var choosingChannel by remember { mutableStateOf(false) }
    var choosingPay by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var form by remember(order.id) { mutableStateOf(false) }
    var copiedHint by remember { mutableStateOf<String?>(null) }
    var channels by remember(order.id) { mutableStateOf<List<CustomerPaymentChannel>>(emptyList()) }
    var selectedChannelId by remember(order.id) {
        mutableStateOf(order.collectionPaymentMethodId ?: order.paymentMethodId)
    }

    val isTransfer = current.paymentChoice != "PAY_ON_COLLECTION" || (current.depositAmount ?: 0.0) > 0.0
    val expiry = current.reservationExpiresAtEpochMillis
    val waitingPayment = state == "AWAITING_PAYMENT" || state == "AWAITING_COLLECTION"
    val tickNeeded = waitingPayment && expiry != null
    val holdExpired = state == "EXPIRED" || (state == "AWAITING_PAYMENT" && expiry != null && now >= expiry)
    val needsChannelPick = remainderReady ||
        (isTransfer && state in setOf("AWAITING_PAYMENT", "EXPIRED", "REJECTED"))

    var checkingHold by remember(order.id) { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state == "CHECKING") {
            checkingHold = true
            delay(3500)
            checkingHold = false
        } else {
            checkingHold = false
        }
    }

    LaunchedEffect(tickNeeded, expiry) {
        if (!tickNeeded) return@LaunchedEffect
        while (isActive) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    LaunchedEffect(holdExpired, current.id, state) {
        if (!holdExpired || state == "EXPIRED" || state == "LATE_REVIEW") return@LaunchedEffect
        try {
            val prefs = PreferenceManager(context)
            val response = ApiClient.service.myOrder(ApiClient.bearer(prefs.authToken), current.id)
            val updated = response.body()?.data
            if (response.isSuccessful && updated != null) {
                current = updated
                onOrderUpdated(updated)
            }
        } catch (_: Exception) { }
    }

    LaunchedEffect(order.id, needsChannelPick) {
        if (!needsChannelPick) return@LaunchedEffect
        selectedChannelId = if (remainderReady) current.collectionPaymentMethodId else current.paymentMethodId
        try {
            val prefs = PreferenceManager(context)
            val response = ApiClient.service.paymentChannels(ApiClient.bearer(prefs.authToken))
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                channels = body.data.orEmpty()
            }
        } catch (_: Exception) {
            // keep empty; UI shows retry via error on select
        }
    }

    LaunchedEffect(current.paymentMethodId, current.collectionPaymentMethodId, remainderReady) {
        selectedChannelId = if (remainderReady) current.collectionPaymentMethodId else current.paymentMethodId
    }

    val expired = expiry?.let { now >= it } ?: false
    val remainingSec = if (expiry != null && !expired) ((expiry - now).coerceAtLeast(0) / 1000) else 0L
    val statusUi = paymentStatusUi(state, expired && state == "AWAITING_PAYMENT")
    val selectableChannels = if (remainderReady) channels
        else channels.filterNot { it.methodName.equals("CASH", ignoreCase = true) }
    val selectedChannel = selectableChannels.firstOrNull { it.id == selectedChannelId }
        ?: channels.firstOrNull { it.id == current.paymentMethodId }
    val showAmount = (isTransfer && state in setOf(
        "AWAITING_PAYMENT", "EXPIRED", "REJECTED", "PROOF_SUBMITTED", "CHECKING", "REVIEW", "LATE_REVIEW", "DEPOSIT_PAID", "PAID"
    )) || state == "AWAITING_COLLECTION"
    val cashRemainder = remainderReady && selectedChannel?.methodName.equals("CASH", ignoreCase = true)
    val canUploadProof = needsChannelPick && selectedChannelId != null && !cashRemainder

    if (current.orderType == "DELIVERY"
        && current.shippingState != null
        && current.shippingState != "LEGACY"
        && current.shippingState != "ACCEPTED"
        && state == "NONE"
    ) {
        var renegotiateOpen by remember { mutableStateOf(false) }
        var renegotiateNote by remember { mutableStateOf("") }
        var renegotiateAt by remember { mutableStateOf(current.requestedDeliveryAt.orEmpty()) }
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardBg, border = BorderStroke(1.dp, BorderColor)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (current.shippingState == "QUOTED") "ဆိုင်အဆိုပြုချက် — နှိုင်းယှဉ်ကြည့်ပါ"
                    else "ဆိုင်မှ ပို့ချိန် အတည်ပြုရန် စောင့်ဆိုင်းပါ",
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                if (current.shippingState == "AWAITING_SHOP" || current.shippingState == "NEEDS_QUOTE") {
                    Text(
                        "ဆိုင်မှ စစ်ဆေးနေသည်။ အတည်ပြုပြီးလျှင် အသိပေးပါမည် — app ပိတ်ထားနိုင်ပါသည်။",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), SurfaceSoft) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        current.requestedDeliveryAt?.takeIf { it.isNotBlank() }?.let {
                            Text("မူလတောင်းဆိုချိန်", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(it.replace('T', ' ').take(16), fontWeight = FontWeight.SemiBold)
                        }
                        if (current.shippingState == "QUOTED") {
                            current.deliveryScheduledAt?.takeIf { it.isNotBlank() }?.let {
                                Text("ဆိုင်အဆိုပြုချိန်", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(it.replace('T', ' ').take(16), fontWeight = FontWeight.Bold, color = Primary)
                            }
                            Text(
                                when (current.deliveryHandler) {
                                    "HANDOFF" -> "ပို့နည်း · အပြင် Delivery အပ်"
                                    "OWN" -> "ပို့နည်း · ဆိုင်မှပို့"
                                    else -> "ပို့နည်း · မသတ်မှတ်ရသေး"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (current.deliveryHandler == "HANDOFF") {
                                Text("ဆိုင်ပို့ခ · ပို့ခ သီးခြားပေးရန်", fontWeight = FontWeight.SemiBold, color = Warning)
                                Text("စုစုပေါင်း ဆိုင်သို့ပေးရန် · ${moneyKs(current.total)}", fontWeight = FontWeight.Bold)
                            } else {
                                Text("ဆိုင်ပို့ခ · ${moneyKs(current.deliveryCharge)}", fontWeight = FontWeight.SemiBold)
                                Text("ငွေပေးချေမှု အကျဉ်းချုပ်", fontWeight = FontWeight.Bold, color = TextMain)
                    Text("ပစ္စည်းဖိုး · ${moneyKs(current.itemsTotal)}", style = MaterialTheme.typography.bodySmall)
                                Text("ဆိုင်သို့ပေးရန် · ${moneyKs(current.total)}", fontWeight = FontWeight.Bold, color = Primary)
                            }
                            current.shippingReason?.takeIf { it.isNotBlank() }?.let {
                                Text("အကြောင်းပြချက် · $it", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                    }
                }
                if (current.shippingState == "QUOTED" && current.status == "PENDING") {
                    Button(
                        enabled = !submitting,
                        onClick = {
                            scope.launch {
                                submitting = true; error = null
                                try {
                                    val prefs = PreferenceManager(context)
                                    val response = ApiClient.service.shippingDecision(
                                        ApiClient.bearer(prefs.authToken),
                                        current.id,
                                        ShippingDecision(current.shippingVersion, true)
                                    )
                                    val updated = response.body()?.data
                                    if (response.isSuccessful && updated != null) {
                                        current = updated
                                        onOrderUpdated(updated)
                                    } else error = response.body()?.message ?: "Quote ပြောင်းသွားသည် — ပြန်ဖတ်ပါ"
                                } catch (e: Exception) {
                                    error = e.message ?: "လက်ခံမရပါ"
                                } finally {
                                    submitting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnPrimary)
                        else Text("လက်ခံပြီး ငွေပေးချေမည်", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        enabled = !submitting,
                        onClick = { renegotiateOpen = !renegotiateOpen },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("အချိန်ပြန်ညှိမည်") }
                    if (renegotiateOpen) {
                        OutlinedTextField(
                            value = renegotiateAt,
                            onValueChange = { renegotiateAt = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ပို့ချိန်အသစ် (yyyy-MM-ddTHH:mm:ss)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = renegotiateNote,
                            onValueChange = { renegotiateNote = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("မှတ်ချက်") }
                        )
                        OutlinedButton(
                            enabled = !submitting && renegotiateAt.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    submitting = true; error = null
                                    try {
                                        val prefs = PreferenceManager(context)
                                        val whenIso = if (renegotiateAt.length == 16) "$renegotiateAt:00" else renegotiateAt
                                        val response = ApiClient.service.shippingDecision(
                                            ApiClient.bearer(prefs.authToken),
                                            current.id,
                                            ShippingDecision(
                                                version = current.shippingVersion,
                                                accept = false,
                                                scheduledAt = whenIso,
                                                reason = renegotiateNote.trim().ifBlank { null }
                                            )
                                        )
                                        val updated = response.body()?.data
                                        if (response.isSuccessful && updated != null) {
                                            current = updated
                                            onOrderUpdated(updated)
                                            renegotiateOpen = false
                                        } else error = response.body()?.message ?: "ပြန်ညှိမရပါ"
                                    } catch (e: Exception) {
                                        error = e.message ?: "ပြန်ညှိမရပါ"
                                    } finally {
                                        submitting = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("အဆိုပြုချက် ပို့မည်") }
                    }
                    TextButton(
                        enabled = !submitting,
                        onClick = {
                            scope.launch {
                                submitting = true; error = null
                                try {
                                    val prefs = PreferenceManager(context)
                                    val response = ApiClient.service.cancelOrder(ApiClient.bearer(prefs.authToken), current.id)
                                    val updated = response.body()?.data
                                    if (response.isSuccessful && updated != null) {
                                        current = updated
                                        onOrderUpdated(updated)
                                    } else error = response.body()?.message ?: "ပယ်ဖျက်မရပါ"
                                } catch (e: Exception) {
                                    error = e.message ?: "ပယ်ဖျက်မရပါ"
                                } finally {
                                    submitting = false
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                    ) { Text("ပယ်ဖျက်မည်") }
                }
                error?.let { Text(it, color = Danger) }
            }
        }
        return
    }

    if (current.orderType == "DELIVERY" && current.shippingState == "ACCEPTED") {
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), SuccessBg, border = BorderStroke(1.dp, Success.copy(alpha = 0.25f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ပို့ချိန် လက်ခံပြီး — ကျသင့် Delivery ခ", fontWeight = FontWeight.Bold, color = Success)
                Text(
                    if (current.deliveryHandler == "HANDOFF")
                        "ဆိုင်ပို့ခ မကောက် — ပို့ခ သီးခြားပေးရန် · ဆိုင်သို့ပေးရန် ${moneyKs(current.total)} — ငွေလွှဲပါ"
                    else
                        "ပို့ခ ${moneyKs(current.deliveryCharge)} · စုစုပေါင်း ${moneyKs(current.total)} — ငွေလွှဲပါ"
                )
            }
        }
    }

    if (current.orderType == "DELIVERY" && current.deliveryHandler == "HANDOFF") {
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), WarningBg, border = BorderStroke(1.dp, Warning.copy(alpha = 0.25f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("အပြင်ပို့ အပ်မည်", fontWeight = FontWeight.Bold, color = Warning)
                Text(
                    "ဆိုင်ဘောင်ချာတွင် ပို့ခ မထည့်ပါ။ အပြင်ပို့ခကို courier နှင့် သီးခြားရှင်းပါ။",
                    style = MaterialTheme.typography.bodySmall
                )
                current.shippingReason?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // Pending orders with no payment yet — short quiet hint only.
    val shopAlreadyAccepted = current.status.equals("CONFIRMED", ignoreCase = true)
        || current.shippingState.equals("ACCEPTED", ignoreCase = true)
        || state == "AWAITING_PAYMENT"
        || state == "AWAITING_COLLECTION"
    if (state == "NONE" && current.status.equals("PENDING", ignoreCase = true) && !shopAlreadyAccepted) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceSoft,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Warning)
                Text(
                    "ဆိုင်မှ order လက်ခံရန် စောင့်ဆိုင်းပါ။",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
        return
    }

    if (state == "NONE"
        && current.paymentChoice == "PENDING"
        && current.shippingState.equals("ACCEPTED", ignoreCase = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ပို့ချိန် လက်ခံပြီးပါပြီ", fontWeight = FontWeight.Bold, color = TextMain)
                Text(
                    "ငွေပေးချေနည်း ရွေးပါ။ အပြည့်လွှဲရင် ကျသင့်ငွေ (ပို့ခအပါ) ယခုလွှဲပါ။ လက်ခံချိန်ရှင်းရင် စရံကြိုလွှဲရပါမည်။",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
                Button(
                    onClick = {
                        choosingPay = true
                        error = null
                        scope.launch {
                            try {
                                val prefs = PreferenceManager(context)
                                val response = withContext(Dispatchers.IO) {
                                    ApiClient.service.choosePayment(
                                        ApiClient.bearer(prefs.authToken),
                                        current.id,
                                        com.sspd.servicemgmt.core.network.PaymentChoiceRequest("TRANSFER")
                                    )
                                }
                                val updated = response.body()?.data
                                if (response.isSuccessful && updated != null) {
                                    current = updated
                                    onOrderUpdated(updated)
                                } else {
                                    error = response.body()?.message ?: "ငွေပေးချေနည်း ရွေးမရပါ"
                                }
                            } catch (e: Exception) {
                                error = e.message ?: "ငွေပေးချေနည်း ရွေးမရပါ"
                            } finally {
                                choosingPay = false
                            }
                        }
                    },
                    enabled = !choosingPay,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("ငွေအပြည့် လွှဲမည်", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        choosingPay = true
                        error = null
                        scope.launch {
                            try {
                                val prefs = PreferenceManager(context)
                                val response = withContext(Dispatchers.IO) {
                                    ApiClient.service.choosePayment(
                                        ApiClient.bearer(prefs.authToken),
                                        current.id,
                                        com.sspd.servicemgmt.core.network.PaymentChoiceRequest("PAY_ON_COLLECTION")
                                    )
                                }
                                val updated = response.body()?.data
                                if (response.isSuccessful && updated != null) {
                                    current = updated
                                    onOrderUpdated(updated)
                                } else {
                                    error = response.body()?.message ?: "ငွေပေးချေနည်း ရွေးမရပါ"
                                }
                            } catch (e: Exception) {
                                error = e.message ?: "ငွေပေးချေနည်း ရွေးမရပါ"
                            } finally {
                                choosingPay = false
                            }
                        }
                    },
                    enabled = !choosingPay,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("လက်ခံချိန် ရှင်းမည် (စရံကြို)", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        return
    }

    if (state == "NONE" && current.paymentChoice == "PAY_ON_COLLECTION") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceSoft,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Text(
                "ပစ္စည်းလက်ခံချိန် ငွေရှင်းမည် — ဆိုင်အတည်ပြု စောင့်ဆိုင်းပါ။",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = statusUi.bg,
        border = BorderStroke(1.dp, statusUi.fg.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    statusUi.label,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = statusUi.fg,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (savedProofs.deposit?.image != null || savedProofs.remainder?.image != null) {
                Text("ငွေလွှဲအထောက်အထားများ", fontWeight = FontWeight.Bold, color = TextMain)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("ပထမပုံ · စရံ", savedProofs.deposit, current.depositAmount),
                        Triple("ဒုတိယပုံ · ကျန်ငွေ", savedProofs.remainder, current.remainingAmount)
                    ).forEach { (label, proof, due) ->
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = CardBg, border = BorderStroke(1.dp, BorderColor)) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryDark)
                                if (proof?.image != null) {
                                    AsyncImage(model = proof.image, contentDescription = label, modifier = Modifier.fillMaxWidth().height(120.dp))
                                    Text("တင်ထားငွေ · "+moneyKs(proof.amount ?: due), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    if (!proof.reference.isNullOrBlank()) Text("Ref · "+proof.reference, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                } else Text("မတင်ရသေးပါ", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                    }
                }
            }

            if (state == "DEPOSIT_PAID" && remainingAmount > 0.0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (current.orderType == "DELIVERY")
                            "စရံရရှိပြီး — ဆိုင်က ပို့ပါမည်။ ပစ္စည်းရောက်မှ ကျန် ${moneyKs(remainingAmount)} ပေးချေပါ။"
                        else
                            "စရံရရှိပြီး — ဆိုင်မှာ လက်ခံချိန် ကျန် ${moneyKs(remainingAmount)} ပေးချေပါ။",
                        fontWeight = FontWeight.SemiBold,
                        color = Success
                    )
                    Text(
                        "ဥပမာ ပစ္စည်း ၃၀,၀၀၀ ၏ စရံ ၃၀% ကြိုလွှဲပြီး ကျန်ငွေကို ပို့ရောက်ချိန် ရှင်းသည်။",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            if (state in setOf("PROOF_SUBMITTED", "CHECKING", "REVIEW", "LATE_REVIEW")) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state == "LATE_REVIEW") "နောက်ကျ အထောက်အထား ရပါပြီ — ဆိုင်က stock ပြန်စစ်မည်"
                        else "ပြေစာရရှိပြီး — ငွေဝင်မှုစစ်ဆေးနေသည်",
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Text(
                        if (state == "LATE_REVIEW") "ထပ်မံငွေလွှဲရန် မလိုပါ။ ဆိုင်က ဆက်ရောင်းမလား / ငွေပြန်အမ်းမလား ဆုံးဖြတ်ပါမည်။"
                        else "ထပ်မံငွေလွှဲရန် မလိုပါ။ ဆိုင်က ပြန်တင်ခိုင်းမှသာ ပြင်ခွင့်ရှိသည်။",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            if (state == "CHECKING" && checkingHold) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                    Text(
                        "စစ်ဆေးနေသည် — ခဏစောင့်ပါ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary
                    )
                }
            } else if (state == "CHECKING") {
                Text(
                    "ဆိုင်မှ ငွေလွှဲအချက်အလက်ကို စစ်ဆေးနေသည်။",
                    style = MaterialTheme.typography.bodySmall,
                    color = Primary
                )
            }

            if (showAmount && state !in setOf("PROOF_SUBMITTED", "CHECKING", "REVIEW", "LATE_REVIEW")) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ငွေပေးချေမှု အကျဉ်းချုပ်", fontWeight = FontWeight.Bold, color = TextMain)
                    Text("ပစ္စည်းဖိုး · ${moneyKs(current.itemsTotal)}", style = MaterialTheme.typography.bodySmall)
                    if (current.deliveryHandler == "HANDOFF") {
                        Text("ဆိုင်ပို့ခ · ပို့ခ သီးခြားပေးရန်", style = MaterialTheme.typography.bodySmall, color = Warning)
                    } else if ((current.deliveryCharge ?: 0.0) > 0 || current.orderType == "DELIVERY") {
                        Text("ဆိုင်ပို့ခ · ${moneyKs(current.deliveryCharge)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (depositAmount > 0.0) {
                        PaymentInfoRow(
                            label = "စရံ ${(current.depositPercent ?: 0.0).toInt()}%",
                            value = "${moneyKs(depositAmount)} · ${when {
                                depositConfirmed -> "အတည်ပြုပြီး ✓"
                                depositTransferred -> "လွှဲပြီး"
                                else -> "ပေးရန်"
                            }}"
                        )
                        if (remainingAmount > 0.0) {
                            PaymentInfoRow(
                                label = "ကျန်ငွေ",
                                value = "${moneyKs(remainingAmount)} · ${when {
                                    remainderConfirmed -> "အတည်ပြုပြီး ✓"
                                    remainderSubmitted -> "လွှဲပုံတင်ပြီး"
                                    depositTransferred -> "စရံနှုတ်ပြီး"
                                    else -> "ပေးရန်ကျန်"
                                }}"
                            )
                        }
                    }
                    PaymentInfoRow(label = "ပေးပြီးစုစုပေါင်း", value = moneyKs(paidSoFar))
                    PaymentInfoRow(label = "ပေးရန်ကျန်", value = moneyKs(balanceDue))
                    Text("ယခုပေးရန်", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        moneyKs(transferDue),
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if ((current.depositAmount ?: 0.0) > 0.0) {
                        Text(
                            "စရံလွှဲပြီးမှ အော်ဒါ ပယ်ဖျက်ပါက စရံငွေ ဆုံးရှုံးမည်။ ပြန်အမ်းမည် မဟုတ်ပါ။",
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (holdExpired && state in setOf("AWAITING_PAYMENT", "EXPIRED")) {
                Text(
                    "Hold အချိန်ကုန်ပါပြီ။ ပစ္စည်းဖယ်ထားမှု ပြန်လွှတ်ပြီးပါပြီ။ ငွေလွှဲပြီးသားဆို အထောက်အထားတင်ပါ — ဆိုင်က stock ပြန်စစ်မည်။ မလွှဲရသေးရင် ပြန်မှာယူပါ။",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Danger
                )
                Button(
                    onClick = { onReorder(current) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("ပြန်မှာယူမည်", fontWeight = FontWeight.Bold)
                }
            }

            if (waitingPayment && expiry != null && !expired && !holdExpired) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, tint = Danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ကျန်ချိန် · ${formatCountdown(remainingSec)}",
                        fontWeight = FontWeight.Bold,
                        color = Danger,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (needsChannelPick) {
                Text(
                    "ငွေလွှဲမည့် Channel ရွေးပါ",
                    fontWeight = FontWeight.SemiBold,
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (channels.isEmpty()) {
                    Text(
                        "Channel မရှိသေးပါ။ ဆိုင်ကို ဆက်သွယ်ပါ။",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectableChannels.forEach { ch ->
                            val selected = selectedChannelId == ch.id
                            FilterChip(
                                selected = selected,
                                enabled = !submitting && !choosingChannel,
                                onClick = {
                                    if (selectedChannelId != ch.id ||
                                        (if (remainderReady) current.collectionPaymentMethodId else current.paymentMethodId) != ch.id) {
                                    choosingChannel = true
                                    error = null
                                    scope.launch {
                                        try {
                                            val prefs = PreferenceManager(context)
                                            val response = ApiClient.service.choosePaymentChannel(
                                                ApiClient.bearer(prefs.authToken),
                                                current.id,
                                                PaymentChannelRequest(ch.id)
                                            )
                                            val body = response.body()
                                            if (response.isSuccessful && body?.success == true && body.data != null) {
                                                current = body.data
                                                selectedChannelId = ch.id
                                                onOrderUpdated(body.data)
                                            } else {
                                                val raw = response.errorBody()?.string()
                                                error = runCatching {
                                                    org.json.JSONObject(raw.orEmpty()).optString("message")
                                                }.getOrNull()?.takeIf { it.isNotBlank() }
                                                    ?: "Channel ရွေးမရပါ။"
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: "ချိတ်ဆက်မှု ပြန်စစ်ပါ။"
                                        } finally {
                                            choosingChannel = false
                                        }
                                    }
                                    }
                                },
                                label = { Text(ch.methodName ?: "Channel ${ch.id}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryLight,
                                    selectedLabelColor = PrimaryDark
                                )
                            )
                        }
                    }
                }

                val payee = selectedChannel?.payeeName?.takeIf { it.isNotBlank() }
                    ?: current.payeeName?.takeIf { it.isNotBlank() }
                val account = selectedChannel?.payeeAccountNo?.takeIf { it.isNotBlank() }
                    ?: current.payeeAccountNo?.takeIf { it.isNotBlank() }
                val channelName = selectedChannel?.methodName?.takeIf { it.isNotBlank() }
                    ?: current.paymentMethodName?.takeIf { it.isNotBlank() }
                if (selectedChannelId != null && (payee != null || account != null)) {
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.7f))
                    channelName?.let { PaymentInfoRow(label = "Channel", value = it) }
                    if (payee != null) PaymentInfoRow(label = "အကောင့်အမည်", value = payee, onCopy = {
                        clipboard.setText(AnnotatedString(payee))
                        copiedHint = "အကောင့်အမည် ကူးယူပြီး"
                    })
                    if (account != null) PaymentInfoRow(label = "အကောင့်နံပါတ်", value = account, onCopy = {
                        clipboard.setText(AnnotatedString(account))
                        copiedHint = "အကောင့်နံပါတ် ကူးယူပြီး"
                    })
                    (selectedChannel?.payeeHint ?: current.paymentInstructions)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { hint ->
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = TextMain)
                        }
                } else if (selectedChannelId == null && !current.paymentInstructions.isNullOrBlank()) {
                    Text(
                        current.paymentInstructions!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            } else if (isTransfer && state in setOf("REVIEW", "LATE_REVIEW", "PAID", "FULFILLED")) {
                current.paymentMethodName?.takeIf { it.isNotBlank() }?.let { channel ->
                    PaymentInfoRow(label = "Channel", value = channel)
                }
                current.payeeName?.takeIf { it.isNotBlank() }?.let { payee ->
                    PaymentInfoRow(label = "အကောင့်အမည်", value = payee)
                }
                current.payeeAccountNo?.takeIf { it.isNotBlank() }?.let { account ->
                    PaymentInfoRow(label = "အကောင့်နံပါတ်", value = account)
                }
            }

            if (cashRemainder) {
                Text(
                    "ပစ္စည်းရောက်ချိန် Rider ကို ကျန်ငွေ ${moneyKs(remainingAmount)} Cash ပေးပါ။ Screenshot မလိုပါ။",
                    style = MaterialTheme.typography.bodySmall,
                    color = Success
                )
            }

            if (state == "AWAITING_COLLECTION" && !expired) {
                Text(
                    "ပစ္စည်းလက်ခံချိန် ငွေရှင်းပါ။ ပစ္စည်းကို ဆိုင်မှ ဖယ်ထားပြီးပါပြီ။",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain
                )
            }

            current.paymentReviewNote?.takeIf { it.isNotBlank() }?.let { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }

            copiedHint?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Success)
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
            }

            if (canUploadProof) {
                TextButton(
                    onClick = { form = !form },
                    enabled = !submitting && !choosingChannel,
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryDark)
                ) {
                    Icon(Icons.Outlined.UploadFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            form -> "ပိတ်မည်"
                            expired || state != "AWAITING_PAYMENT" -> "ငွေလွှဲပြီးသား အထောက်အထားတင်ရန်"
                            else -> "ငွေလွှဲပြီး — အထောက်အထားတင်မည်"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (form) {
                    if (expired || state != "AWAITING_PAYMENT") {
                        Text(
                            "ငွေအသစ် မလွှဲပါနှင့်။ လွှဲပြီးသားဖြစ်မှ အထောက်အထားတင်ပါ။",
                            style = MaterialTheme.typography.bodySmall,
                            color = Danger
                        )
                    }
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg
                    )
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { if (it.length <= 120) reference = it },
                        label = { Text("Transaction no (မထည့်လည်းရ)") },
                        singleLine = true,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("လွှဲထားသည့်ငွေ (Ks)") },
                        singleLine = true,
                        enabled = !submitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                    OutlinedButton(
                        onClick = onPickImage,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Text(if (image == null) "ပြေစာပုံ ရွေးမည် (JPEG / PNG, 2 MB)" else "ပုံပြန်ရွေးမည်")
                    }
                    image?.let { uri ->
                        Text("ပြေစာ preview", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        AsyncImage(
                            model = uri,
                            contentDescription = "ငွေလွှဲအထောက်အထား",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(CardBg, RoundedCornerShape(12.dp))
                        )
                    }
                    Button(
                        enabled = !submitting &&
                            selectedChannelId != null &&
                            image != null &&
                            (amount.toBigDecimalOrNull()?.signum() ?: 0) > 0,
                            onClick = {
                            val methodId = selectedChannelId ?: return@Button
                            val proof = image ?: return@Button
                            submitting = true
                            error = null
                            scope.launch {
                                try {
                                    val bytes = withContext(Dispatchers.IO) {
                                        context.contentResolver.openInputStream(proof)?.use { input ->
                                            val output = ByteArrayOutputStream()
                                            val buffer = ByteArray(8192)
                                            while (true) {
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                output.write(buffer, 0, count)
                                                require(output.size() <= 2 * 1024 * 1024) {
                                                    "Screenshot ကို 2 MB အောက် ရွေးပါ။"
                                                }
                                            }
                                            output.toByteArray()
                                        }
                                    }
                                    val prefs = PreferenceManager(context)
                                    val part = MultipartBody.Part.createFormData(
                                        "image",
                                        "payment-proof.jpg",
                                        bytes?.toRequestBody("application/octet-stream".toMediaType())
                                            ?: throw IllegalStateException("Screenshot ကို 2 MB အောက် ရွေးပါ။")
                                    )
                                    val methodBody = methodId.toString()
                                        .toRequestBody("text/plain".toMediaType())
                                    val response = ApiClient.service.submitOrderPayment(
                                        ApiClient.bearer(prefs.authToken),
                                        current.id,
                                        reference.trim().ifBlank { "" }.toRequestBody("text/plain".toMediaType()),
                                        amount.toRequestBody("text/plain".toMediaType()),
                                        methodBody,
                                        part
                                    )
                                    val body = response.body()
                                    if (response.isSuccessful && body?.success == true && body.data != null) {
                                        current = body.data
                                        onOrderUpdated(body.data)
                                        form = false
                                        image = null
                                    } else {
                                        val raw = response.errorBody()?.string()
                                        error = runCatching {
                                            org.json.JSONObject(raw.orEmpty()).optString("message")
                                        }.getOrNull()?.takeIf { it.isNotBlank() }
                                            ?: "အထောက်အထားတင်မရပါ။ ပြန်ကြိုးစားပါ။"
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "ချိတ်ဆက်မှု ပြန်စစ်ပါ။"
                                } finally {
                                    submitting = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = OnPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (submitting) "တင်နေပါသည်…" else "အထောက်အထားတင်မည်",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentInfoRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(value, fontWeight = FontWeight.SemiBold, color = TextMain)
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "ကူးယူမည်", tint = Primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}



