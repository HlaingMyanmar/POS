package com.sspd.servicemgmt.feature.home

import android.widget.Toast

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.util.PreferenceManager
import com.sspd.servicemgmt.core.network.OrderLineRequest
import com.sspd.servicemgmt.core.network.PromoCodeRequest
import coil.compose.AsyncImage
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.CatalogProduct
import com.sspd.servicemgmt.core.feature.CustomerAppFeatures
import com.sspd.servicemgmt.core.network.DeliveryRegion
import com.sspd.servicemgmt.core.network.DeliveryTownship
import com.sspd.servicemgmt.core.network.DeliveryWard
import com.sspd.servicemgmt.core.ui.component.ErrorRetryBanner
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.DangerBg
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.Success
import com.sspd.servicemgmt.core.ui.theme.SuccessBg
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.ui.theme.Warning
import com.sspd.servicemgmt.feature.cart.CartItem
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

private val PanelShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)

private enum class CartStep { CART, ORDER, FULFILLMENT, RECIPIENT, SCHEDULE, PAYMENT, CONFIRM }

private fun cartStepFromPhase(phase: String): CartStep = when (phase.uppercase()) {
    "ORDER", "CHECKOUT" -> CartStep.ORDER
    "FULFILLMENT" -> CartStep.FULFILLMENT
    "RECIPIENT" -> CartStep.RECIPIENT
    "SCHEDULE" -> CartStep.SCHEDULE
    "PAYMENT" -> CartStep.PAYMENT
    "CONFIRM" -> CartStep.CONFIRM
    else -> CartStep.CART
}

private fun checkoutPath(orderType: String): List<CartStep> =
    if (orderType == "DELIVERY") {
        listOf(CartStep.ORDER, CartStep.FULFILLMENT, CartStep.RECIPIENT, CartStep.SCHEDULE, CartStep.CONFIRM)
    } else {
        listOf(CartStep.ORDER, CartStep.FULFILLMENT, CartStep.PAYMENT, CartStep.CONFIRM)
    }

private fun formatRequestedAt(cal: Calendar): String =
    String.format(
        Locale.US,
        "%04d-%02d-%02dT%02d:%02d:00",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE)
    )

private fun parseRequestedAt(value: String?): Calendar {
    val cal = Calendar.getInstance()
    cal.add(Calendar.HOUR_OF_DAY, 2)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    if (value.isNullOrBlank() || value.length < 16) return cal
    return runCatching {
        val y = value.substring(0, 4).toInt()
        val m = value.substring(5, 7).toInt() - 1
        val d = value.substring(8, 10).toInt()
        val h = value.substring(11, 13).toInt()
        val min = value.substring(14, 16).toInt()
        Calendar.getInstance().apply {
            set(y, m, d, h, min, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }.getOrDefault(cal)
}

private fun displayRequestedAt(value: String?): String {
    if (value.isNullOrBlank()) return "ရက်နှင့် အချိန် ရွေးပါ"
    val cal = parseRequestedAt(value)
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val amPm = if (hour24 >= 12) "PM" else "AM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return String.format(
        Locale.US,
        "%02d/%02d/%04d · %d:%02d %s",
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR),
        hour12,
        minute,
        amPm
    )
}

@Composable
fun CustomerCartScreen(
    items: List<CartItem>,
    note: String,
    onNoteChange: (String) -> Unit,
    onChangeQty: (CatalogProduct, Int) -> Unit,
    onRemove: (CatalogProduct) -> Unit,
    onCheckout: () -> Unit,
    onBrowseProducts: () -> Unit,
    paymentChoice: String = "TRANSFER",
    onPaymentChoiceChange: (String) -> Unit = {},
    checkoutEnabled: Boolean = items.isNotEmpty(),
    startPhase: String = "CART",
    orderType: String = "DELIVERY",
    onOrderTypeChange: (String) -> Unit = {},
    deliveryMode: String = "PROFILE",
    onDeliveryModeChange: (String) -> Unit = {},
    deliveryPhone: String = "",
    onDeliveryPhoneChange: (String) -> Unit = {},
    deliveryAddress: String = "",
    onDeliveryAddressChange: (String) -> Unit = {},
    profileAddress: String? = null,
    profilePhone: String? = null,
    locations: List<DeliveryRegion> = emptyList(),
    selectedRegionId: Int? = null,
    onRegionChange: (Int?) -> Unit = {},
    selectedTownshipId: Int? = null,
    onTownshipChange: (Int?) -> Unit = {},
    selectedWardId: Int? = null,
    onWardChange: (Int?) -> Unit = {},
    pickupDepositPercent: Double = 30.0,
    checkoutPlacing: Boolean = false,
    quotedDeliveryCharge: Double? = null,
    quoteState: String? = null,
    quoteLoading: Boolean = false,
    quoteReason: String? = null,
    quoteError: String? = null,
    onRetryQuote: () -> Unit = {},
    deliveryEnabled: Boolean = true,
    deliveryHours: DeliveryHoursConfig = DeliveryHoursConfig(),
    requestedDeliveryAt: String? = null,
    onRequestedDeliveryAtChange: (String) -> Unit = {},
    onAppliedPromoChange: (String?) -> Unit = {},
    bottomBarPadding: Dp = 80.dp
) {
    var step by rememberSaveable { mutableStateOf(cartStepFromPhase(startPhase)) }
    if (checkoutPlacing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("အော်ဒါတင်နေသည်") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text("ဆိုင်ထံ ပို့နေသည် — ခဏစောင့်ပါ။")
                }
            },
            confirmButton = {}
        )
    }
    BackHandler(enabled = checkoutPlacing || step != CartStep.CART) {
        if (!checkoutPlacing) {
            val steps = checkoutPath(orderType)
            val index = steps.indexOf(step)
            step = if (index > 0) steps[index - 1] else CartStep.CART
        }
    }
    LaunchedEffect(deliveryEnabled, orderType) {
        if (!deliveryEnabled && orderType != "PICKUP") {
            onOrderTypeChange("PICKUP")
        }
        if (orderType == "PICKUP" && paymentChoice != "TRANSFER") {
            onPaymentChoiceChange("TRANSFER")
        }
    }
    LaunchedEffect(items.isEmpty()) {
        if (items.isEmpty()) step = CartStep.CART
    }
    LaunchedEffect(orderType, step) {
        if (orderType != "DELIVERY" && (step == CartStep.RECIPIENT || step == CartStep.SCHEDULE)) {
            step = CartStep.PAYMENT
        }
        val allowed = checkoutPath(orderType)
        if (step != CartStep.CART && step !in allowed) {
            step = allowed.first()
        }
    }

    val itemCount = items.sumOf { it.qty }
    val itemsTotal = items.sumOf { (it.product.sellingPrice ?: 0.0) * it.qty }
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val prefs = remember(context, isPreview) {
        if (isPreview) null
        else runCatching { PreferenceManager(context.applicationContext) }.getOrNull()
    }
    val promoScope = rememberCoroutineScope()
    var promoCode by remember { mutableStateOf("") }
    var promoDiscount by remember { mutableDoubleStateOf(0.0) }
    var promoValidating by remember { mutableStateOf(false) }
    var promoError by remember { mutableStateOf<String?>(null) }
    var appliedPromo by remember { mutableStateOf<String?>(null) }
    val cartStamp = items.joinToString("|") { "${it.product.id}:${it.qty}" }
    LaunchedEffect(cartStamp) {
        promoDiscount = 0.0
        appliedPromo = null
        promoError = null
        onAppliedPromoChange(null)
    }

    val selectedRegion = locations.firstOrNull { it.id == selectedRegionId }
    val selectedTownship = selectedRegion?.townshipsOrEmpty()?.firstOrNull { it.id == selectedTownshipId }
    val selectedWard = selectedTownship?.wardsOrEmpty()?.firstOrNull { it.id == selectedWardId }
    val deliveryFee = when {
        orderType != "DELIVERY" -> 0.0
        quotedDeliveryCharge != null -> quotedDeliveryCharge
        else -> 0.0
    }
    val subtotal = (itemsTotal + deliveryFee) - promoDiscount
    val depositPercent = pickupDepositPercent.takeIf { it.isFinite() }?.coerceIn(1.0, 100.0) ?: 30.0
    val needsDeposit = orderType == "PICKUP" || (orderType == "DELIVERY" && paymentChoice == "PAY_ON_COLLECTION")
    val orderDeposit = if (needsDeposit) {
        val totalSafe = (itemsTotal - promoDiscount).coerceAtLeast(0.0)
        BigDecimal.valueOf(totalSafe)
            .multiply(BigDecimal.valueOf(depositPercent))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            .toDouble()
    } else {
        0.0
    }
    val remainingAfterDeposit = if (needsDeposit) {
        (itemsTotal - promoDiscount + deliveryFee - orderDeposit).coerceAtLeast(0.0)
    } else {
        0.0
    }
    val path = checkoutPath(orderType)
    val stepIndex = path.indexOf(step).coerceAtLeast(0)
    val fulfillmentReady = orderType != "DELIVERY" || selectedTownship != null
    val quoteReady = orderType != "DELIVERY"
        || (!quoteLoading && quoteError.isNullOrBlank() && quotedDeliveryCharge != null)
    val recipientReady = if (deliveryMode == "PROFILE") {
        !profileAddress.isNullOrBlank()
    } else {
        deliveryPhone.isNotBlank() && deliveryAddress.isNotBlank()
    }
    val scheduleReady = orderType != "DELIVERY" || deliveryScheduleError(requestedDeliveryAt, deliveryHours) == null

    fun goNext() {
        val idx = path.indexOf(step)
        if (idx >= 0 && idx < path.lastIndex) step = path[idx + 1]
    }

    fun goBack() {
        if (checkoutPlacing) return
        val idx = path.indexOf(step)
        step = if (idx > 0) path[idx - 1] else CartStep.CART
    }

    fun stepTitle(): String = when (step) {
        CartStep.CART -> "ခြင်းတောင်း"
        CartStep.ORDER -> "ပစ္စည်းစာရင်း စစ်ဆေးရန်"
        CartStep.FULFILLMENT -> "ပို့ဆောင်ပုံ"
        CartStep.RECIPIENT -> "လက်ခံမည့်သူ / ပို့မည့်နေရာ"
        CartStep.SCHEDULE -> "ပို့ချိန်"
        CartStep.PAYMENT -> "ငွေပေးချေနည်း"
        CartStep.CONFIRM -> "အော်ဒါတင်မည်"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .imePadding()
    ) {
        AnimatedContent(
            targetState = if (items.isEmpty()) null else step,
            transitionSpec = {
                fadeIn().togetherWith(fadeOut())
            },
            label = "CartStepTransition",
            modifier = Modifier.fillMaxSize()
        ) { targetStep ->
            Column(Modifier.fillMaxSize()) {
            if (targetStep == null) {
                CartEmptyState(onBrowseProducts = onBrowseProducts)
            } else when (targetStep) {
            CartStep.CART -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        CartHeader(itemCount = itemCount, varietyCount = items.size, subtotal = itemsTotal)
                    }
                    if (CustomerAppFeatures.PROMO_CODES) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = promoCode,
                                    onValueChange = { promoCode = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Promo Code") },
                                    singleLine = true,
                                    shape = FieldShape,
                                    colors = cartFieldColors()
                                )
                                Button(
                                    onClick = {
                                        val code = promoCode.trim()
                                        if (code.isBlank()) return@Button
                                        promoValidating = true
                                        promoError = null
                                        promoScope.launch {
                                            try {
                                                val lines = items.map { OrderLineRequest(it.product.id, it.qty) }
                                                val res = ApiClient.service.validatePromoCode(
                                                    ApiClient.bearer(prefs?.authToken.orEmpty()),
                                                    PromoCodeRequest(code, lines)
                                                )
                                                val quote = res.body()?.data
                                                if (res.isSuccessful && res.body()?.success == true && quote != null) {
                                                    promoDiscount = quote.discountAmount
                                                    appliedPromo = quote.promoCode ?: code
                                                    onAppliedPromoChange(appliedPromo)
                                                    promoError = null
                                                } else {
                                                    promoError = res.body()?.message ?: "Promo code မမှန်ပါ"
                                                    promoDiscount = 0.0
                                                    appliedPromo = null
                                                    onAppliedPromoChange(null)
                                                }
                                            } catch (e: Exception) {
                                                promoError = e.message
                                                promoDiscount = 0.0
                                                appliedPromo = null
                                                onAppliedPromoChange(null)
                                            } finally {
                                                promoValidating = false
                                            }
                                        }
                                    },
                                    enabled = !promoValidating,
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    if (promoValidating) CircularProgressIndicator(Modifier.size(18.dp), color = OnPrimary)
                                    else Text("Apply")
                                }
                            }
                            promoError?.let { err ->
                                Text(err, color = Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                            }
                            if (promoDiscount > 0) {
                                Text("Discount: ${money(promoDiscount)}", color = Success, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    itemsIndexed(items, key = { index, item -> "${item.product.id}-$index" }) { _, item ->
                        CartLineCard(
                            item = item,
                            onMinus = { onChangeQty(item.product, item.qty - 1) },
                            onPlus = { onChangeQty(item.product, item.qty + 1) },
                            onRemove = { onRemove(item.product) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                CartContinueBar(
                    itemCount = itemCount,
                    itemsTotal = itemsTotal,
                    onContinueShopping = onBrowseProducts,
                    onContinueCheckout = { step = CartStep.ORDER }
                )
            }

            CartStep.ORDER -> {
                CheckoutStepHeader(
                    title = stepTitle(),
                    subtitle = "အဆင့် ${stepIndex + 1} / ${path.size} · ပစ္စည်း $itemCount ခု",
                    stepIndex = stepIndex,
                    totalSteps = path.size,
                    onBack = { step = CartStep.CART }
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CheckoutOrderSummary(
                            itemCount = itemCount,
                            varietyCount = items.size,
                            itemsTotal = itemsTotal
                        )
                    }
                    item { CheckoutItemsCard(items = items) }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                CheckoutNextBar(
                    caption = money(itemsTotal),
                    detail = "$itemCount ခု",
                    buttonLabel = "ပို့ဆောင်ပုံ ရွေးမည်",
                    enabled = true,
                    onClick = ::goNext
                )
            }

            CartStep.FULFILLMENT -> {
                var locationSearchFocused by remember { mutableStateOf(false) }
                CheckoutStepHeader(
                    title = stepTitle(),
                    subtitle = "အဆင့် ${stepIndex + 1} / ${path.size} · ပို့ပုံ ရွေးပါ",
                    stepIndex = stepIndex,
                    totalSteps = path.size,
                    onBack = ::goBack
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = if (locationSearchFocused) 24.dp else 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CartFulfillmentCard(
                            orderType = orderType,
                            onOrderTypeChange = onOrderTypeChange,
                            deliveryEnabled = deliveryEnabled,
                            locations = locations,
                            selectedRegionId = selectedRegionId,
                            onRegionChange = onRegionChange,
                            selectedTownshipId = selectedTownshipId,
                            onTownshipChange = onTownshipChange,
                            selectedWardId = selectedWardId,
                            onWardChange = onWardChange,
                            onSearchFocusChange = { locationSearchFocused = it }
                        )
                    }
                    if (orderType == "DELIVERY" && selectedWard != null) {
                        item {
                            DeliveryQuotePreview(
                                loading = quoteLoading,
                                error = quoteError,
                                charge = quotedDeliveryCharge,
                                reason = quoteReason,
                                onRetry = onRetryQuote
                            )
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                AnimatedVisibility(visible = !locationSearchFocused) {
                    CheckoutNextBar(
                        caption = if (orderType == "DELIVERY") {
                            when {
                                selectedTownship == null -> "မြို့နယ် ရွေးပါ"
                                quoteLoading -> "ပို့ခတွက်နေသည်…"
                                !quoteError.isNullOrBlank() -> "ပို့ခ ပြန်တွက်ပါ"
                                quotedDeliveryCharge != null -> money(quotedDeliveryCharge)
                                else -> "ပို့ခ စောင့်နေသည်"
                            }
                        } else "ပို့ဆောင်ခ မလိုပါ",
                        detail = if (orderType == "DELIVERY") "ပို့ဆောင်ခ" else "ဆိုင်မှာ လာယူမည်",
                        buttonLabel = if (orderType == "DELIVERY") "လက်ခံမည့်သူ ဖြည့်မည်" else "ငွေပေးချေနည်း ရွေးမည်",
                        enabled = fulfillmentReady && quoteReady,
                        onClick = ::goNext
                    )
                }
            }

            CartStep.RECIPIENT -> {
                CheckoutStepHeader(
                    title = stepTitle(),
                    subtitle = "အဆင့် ${stepIndex + 1} / ${path.size} · ပို့မည့်နေရာ",
                    stepIndex = stepIndex,
                    totalSteps = path.size,
                    onBack = ::goBack
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CartRecipientCard(
                            deliveryMode = deliveryMode,
                            onDeliveryModeChange = onDeliveryModeChange,
                            deliveryPhone = deliveryPhone,
                            onDeliveryPhoneChange = onDeliveryPhoneChange,
                            deliveryAddress = deliveryAddress,
                            onDeliveryAddressChange = onDeliveryAddressChange,
                            profileAddress = profileAddress,
                            profilePhone = profilePhone,
                            regionName = selectedRegion?.name,
                            townshipName = selectedTownship?.name,
                            wardName = selectedWard?.name,
                            quotedCharge = quotedDeliveryCharge,
                            onEditArea = { step = CartStep.FULFILLMENT }
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                CheckoutNextBar(
                    caption = if (deliveryMode == "PROFILE") "Profile လိပ်စာ" else "အသေးစိတ်လိပ်စာ",
                    detail = "လက်ခံမည့်သူ / ပို့မည့်နေရာ",
                    buttonLabel = "ပို့ချိန် ရွေးမည်",
                    enabled = recipientReady,
                    onClick = ::goNext
                )
            }

            CartStep.SCHEDULE -> {
                CheckoutStepHeader(
                    title = stepTitle(),
                    subtitle = "အဆင့် ${stepIndex + 1} / ${path.size} · ဆိုင်အတည်ပြုရန်",
                    stepIndex = stepIndex,
                    totalSteps = path.size,
                    onBack = ::goBack
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CartScheduleCard(
                            requestedDeliveryAt = requestedDeliveryAt,
                            onRequestedDeliveryAtChange = onRequestedDeliveryAtChange,
                            hours = deliveryHours
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                CheckoutNextBar(
                    caption = displayRequestedAt(requestedDeliveryAt),
                    detail = "ပို့ချိန်",
                    buttonLabel = "အော်ဒါ အတည်ပြုမည်",
                    enabled = scheduleReady,
                    onClick = ::goNext
                )
            }

            CartStep.PAYMENT -> {
                val paymentIsLast = path.last() == CartStep.PAYMENT
                CheckoutStepHeader(
                    title = stepTitle(),
                    subtitle = "အဆင့် ${stepIndex + 1} / ${path.size} · ပေးချေနည်း",
                    stepIndex = stepIndex,
                    totalSteps = path.size,
                    onBack = ::goBack
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (orderType == "DELIVERY") {
                        item {
                            CheckoutOrderSummary(
                                itemCount = itemCount,
                                varietyCount = items.size,
                                itemsTotal = itemsTotal
                            )
                        }
                    }
                    item {
                        CartPaymentChoiceCard(
                            paymentChoice = paymentChoice,
                            onPaymentChoiceChange = onPaymentChoiceChange,
                            pickupRequired = orderType == "PICKUP",
                            collectionDeposit = orderType == "DELIVERY" && paymentChoice == "PAY_ON_COLLECTION",
                            depositPercent = depositPercent,
                            depositAmount = orderDeposit,
                            remainingAmount = remainingAfterDeposit,
                            deliveryFee = if (orderType == "DELIVERY") deliveryFee else null
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                if (paymentIsLast) {
                    CartCheckoutBar(
                        subtotal = subtotal,
                        itemCount = itemCount,
                        enabled = checkoutEnabled && !checkoutPlacing && fulfillmentReady && quoteReady && (orderType != "DELIVERY" || recipientReady),
                        placing = checkoutPlacing,
                        onCheckout = onCheckout,
                        showBottomNav = false
                    )
                } else {
                    CheckoutNextBar(
                        caption = when {
                            needsDeposit -> "စရံ ${depositPercent.toInt()}%"
                            paymentChoice == "TRANSFER" -> "ဘဏ် / Wallet"
                            else -> "လက်ခံချိန်"
                        },
                        detail = "ငွေပေးချေ",
                        buttonLabel = "အော်ဒါ အတည်ပြုမည်",
                        enabled = true,
                        onClick = ::goNext
                    )
                }
            }

            CartStep.CONFIRM -> {
                val confirmIsLast = path.last() == CartStep.CONFIRM
                CheckoutStepHeader(
                    title = stepTitle(),
                    subtitle = "အဆင့် ${stepIndex + 1} / ${path.size} · ပို့ခ / စုစုပေါင်း",
                    stepIndex = stepIndex,
                    totalSteps = path.size,
                    onBack = ::goBack
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CheckoutOrderSummary(
                            itemCount = itemCount,
                            varietyCount = items.size,
                            itemsTotal = itemsTotal
                        )
                    }
                    item {
                        CheckoutConfirmRecap(
                            orderType = orderType,
                            paymentChoice = paymentChoice,
                            townshipName = selectedTownship?.name,
                            wardName = selectedWard?.name,
                            deliveryMode = deliveryMode,
                            address = if (deliveryMode == "PROFILE") profileAddress else deliveryAddress,
                            phone = if (deliveryMode == "PROFILE") profilePhone else deliveryPhone,
                            requestedDeliveryAt = requestedDeliveryAt
                        )
                    }
                    item {
                        CheckoutNoteCard(
                            note = note,
                            onNoteChange = onNoteChange,
                            enabled = !checkoutPlacing
                        )
                    }
                    item {
                        CheckoutPriceBreakdownCard(
                            itemsTotal = itemsTotal,
                            deliveryFee = deliveryFee,
                            showDeliveryLine = orderType == "DELIVERY",
                            subtotal = subtotal,
                            discountAmount = promoDiscount.takeIf { it > 0 },
                            pickupDepositPercent = if (needsDeposit) depositPercent else null,
                            pickupDeposit = if (needsDeposit) orderDeposit else null,
                            pickupRemaining = if (needsDeposit) remainingAfterDeposit else null
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                if (confirmIsLast) {
                    CartCheckoutBar(
                        subtotal = subtotal,
                        itemCount = itemCount,
                        enabled = checkoutEnabled && !checkoutPlacing && fulfillmentReady && quoteReady &&
                            (orderType != "DELIVERY" || (recipientReady && scheduleReady)),
                        placing = checkoutPlacing,
                        onCheckout = onCheckout,
                        showBottomNav = false
                    )
                } else {
                    CheckoutNextBar(
                        caption = if (orderType == "DELIVERY") money(deliveryFee) else money(itemsTotal),
                        detail = if (orderType == "DELIVERY") "ခန့်မှန်းပို့ဆောင်ခ" else "စုစုပေါင်း",
                        buttonLabel = "ငွေပေးချေနည်း ရွေးမည်",
                        enabled = fulfillmentReady && quoteReady,
                        onClick = ::goNext,
                        showBottomNav = false
                    )
                }
            }
        }
    }
}
}
}

@Composable
private fun CheckoutStepHeader(
    title: String,
    subtitle: String,
    stepIndex: Int = 0,
    totalSteps: Int = 1,
    onBack: () -> Unit
) {
    Surface(
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "နောက်သို့",
                        tint = TextMain
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            val progress = ((stepIndex + 1).toFloat() / totalSteps.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Primary,
                trackColor = PrimaryLight
            )
        }
    }
}

@Composable
private fun CheckoutItemsCard(items: List<CartItem>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "ပစ္စည်းစာရင်း",
                fontWeight = FontWeight.Bold,
                color = TextMain,
                style = MaterialTheme.typography.titleSmall
            )
            items.forEach { item ->
                val unit = item.product.sellingPrice ?: 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.product.name ?: "-",
                            fontWeight = FontWeight.SemiBold,
                            color = TextMain,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "x${item.qty} · ${money(unit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Text(
                        money(unit * item.qty),
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckoutNextBar(
    caption: String,
    detail: String,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    showBottomNav: Boolean = false
) {
    Surface(
        color = CardBg,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = if (showBottomNav) 10.dp + 72.dp else 10.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "စုစုပေါင်း",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    caption,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Primary
                )
            }
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )

            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    disabledContainerColor = Primary.copy(alpha = 0.35f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(buttonLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun CheckoutConfirmRecap(
    orderType: String,
    paymentChoice: String,
    townshipName: String?,
    wardName: String?,
    deliveryMode: String,
    address: String?,
    phone: String?,
    requestedDeliveryAt: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("အတည်ပြု အနှစ်ချုပ်", fontWeight = FontWeight.Bold, color = TextMain)
            Text(
                if (orderType == "PICKUP") "ဆိုင်မှာ လာယူမည်" else "သွားပို့မည်",
                style = MaterialTheme.typography.bodySmall,
                color = TextMain
            )
            if (orderType == "DELIVERY") {
                Text(
                    listOfNotNull(townshipName, wardName).joinToString(" · ").ifBlank { "ရပ်ကွက် ရွေးထားသည်" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    (if (deliveryMode == "PROFILE") "Profile · " else "") + address.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                if (!phone.isNullOrBlank()) {
                    Text(phone, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Text(
                    "ခန့်မှန်းပို့ခ (ဆိုင်ပို့)။ အပြင်ပို့ အပ်ရင် ဆိုင်က ပို့ခ မကောက်ပါ။",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    "တောင်းဆို ပို့ချိန် · ${displayRequestedAt(requestedDeliveryAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "ဆိုင်က ပို့ချိန်နှင့် ကိုယ်တိုင်ပို့ / အပြင်ပို့ အပ်မည်ကို အတည်ပြုပြီးမှ ငွေပေးချေနည်း ရွေးပါမည်။ စရံသည် ကြိုလွှဲဖြစ်သည်။",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            if (orderType == "PICKUP") Text(
                when {
                    paymentChoice == "TRANSFER" || orderType == "PICKUP" -> "ငွေလွှဲ · စရံကြို"
                    else -> "ပစ္စည်းလက်ခံချိန် ငွေရှင်း"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMain,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CheckoutOrderSummary(
    itemCount: Int,
    varietyCount: Int,
    itemsTotal: Double
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ShoppingCart, null, tint = Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ခြင်းတောင်း အနှစ်ချုပ်",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    "$itemCount ခု · $varietyCount မျိုး",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Text(
                money(itemsTotal),
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun CartContinueBar(
    itemCount: Int,
    itemsTotal: Double,
    onContinueShopping: () -> Unit,
    onContinueCheckout: () -> Unit,
    showBottomNav: Boolean = true
) {
    Surface(
        color = CardBg,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = if (showBottomNav) 10.dp + 72.dp else 10.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "စုစုပေါင်း",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    money(itemsTotal),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Primary
                )
            }
            Text(
                "$itemCount ခု",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )

            Button(
                onClick = onContinueCheckout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text("အော်ဒါတင်ရန် ဆက်သွားမည်", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun CartHeader(itemCount: Int, varietyCount: Int, subtotal: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ShoppingCart, null, tint = Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ခြင်းတောင်း",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    "$itemCount ခု · $varietyCount မျိုး",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("စုစုပေါင်း", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(
                    money(subtotal),
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun CartEmptyState(onBrowseProducts: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(PrimaryLight)
                .border(1.dp, Primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.ShoppingCart,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "ခြင်းတောင်း ဗလာဖြစ်နေသည်",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = TextMain
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "ပစ္စည်းများမှ ရွေးချယ်ပြီး ခြင်းတောင်းထဲ ထည့်ပါ။\nပြီးမှ ဤနေရာမှ အော်ဒါတင်နိုင်ပါသည်။",
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onBrowseProducts,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("ပစ္စည်းများ ကြည့်မည်", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CartLineCard(
    item: CartItem,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onRemove: () -> Unit
) {
    val product = item.product
    val stock = (product.stockQty ?: 0).coerceAtLeast(0)
    val unit = product.sellingPrice ?: 0.0
    val lineTotal = unit * item.qty
    val atMax = item.qty >= stock
    val imageUrl = (product.thumbnailUrl ?: product.photoUrls.orEmpty().firstOrNull())?.cartAssetUrl()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceSoft)
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = product.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Inventory2,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.name ?: "-",
                        fontWeight = FontWeight.SemiBold,
                        color = TextMain,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            product.brandName?.takeIf { it.isNotBlank() },
                            product.categoryName?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ").ifBlank { "ပစ္စည်း" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(money(unit), color = Primary, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (stock <= 0) DangerBg else SurfaceSoft
                        ) {
                            Text(
                                if (stock <= 0) "ကုန်နေ" else "ကျန် $stock",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (stock <= 0) Danger else TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DangerBg)
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "ဖယ်ရှားမည်",
                        tint = Danger,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.8f))
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CartQtyStepper(
                    qty = item.qty,
                    canMinus = item.qty > 0,
                    canPlus = !atMax,
                    onMinus = onMinus,
                    onPlus = onPlus
                )
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("လိုင်းစုစုပေါင်း", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        money(lineTotal),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextMain
                    )
                }
            }

            if (atMax && stock > 0) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DangerBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Danger.copy(alpha = 0.15f))
                ) {
                    Text(
                        "Stock အများဆုံး ရောက်ပါပြီ",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        color = Danger,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CartQtyStepper(
    qty: Int,
    canMinus: Boolean,
    canPlus: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
            .background(SurfaceSoft)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMinus,
            enabled = canMinus,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (canMinus) CardBg else Color.Transparent)
        ) {
            Icon(
                Icons.Outlined.Remove,
                contentDescription = "လျှော့မည်",
                tint = if (canMinus) Primary else TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            qty.toString(),
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = TextMain
        )
        IconButton(
            onClick = onPlus,
            enabled = canPlus,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (canPlus) Primary else Color.Transparent)
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "တိုးမည်",
                tint = if (canPlus) OnPrimary else TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CartPaymentChoiceCard(
    paymentChoice: String,
    onPaymentChoiceChange: (String) -> Unit,
    pickupRequired: Boolean = false,
    collectionDeposit: Boolean = false,
    depositPercent: Double = 30.0,
    depositAmount: Double = 0.0,
    remainingAmount: Double = 0.0,
    deliveryFee: Double? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Payments, null, tint = Primary, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(
                        "ငွေပေးချေနည်း",
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        when {
                            pickupRequired ->
                                "ဆိုင်မှာလာယူရင် စရံငွေ ကြိုလွှဲရပါမည်။ ကျန်ငွေ ဆိုင်မှာ ပေးချေပါမည်။"
                            collectionDeposit ->
                                "လက်ခံချိန် ရှင်းမည် ဆိုရင်လည်း စရံကြိုလွှဲရပါမည်။ ကျန်ငွေ ပို့ချိန် ပေးချေပါမည်။"
                            else ->
                                "ပို့ဆောင်ခ အတည်ပြုပြီးပါပြီ။ ဆိုင်မှ order လက်ခံပြီးမှ ငွေလွှဲအချက်အလက် ရပါမည်။"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            if (deliveryFee != null) {
                Text(
                    "ပို့ဆောင်ခ ${money(deliveryFee)}",
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!pickupRequired) {
                ChoiceChip(
                    modifier = Modifier.fillMaxWidth(),
                    selected = paymentChoice == "TRANSFER",
                    label = "ဆိုင်အတည်ပြုပြီးမှ ဘဏ် / Wallet ငွေလွှဲမည်",
                    onClick = { onPaymentChoiceChange("TRANSFER") }
                )
                ChoiceChip(
                    modifier = Modifier.fillMaxWidth(),
                    selected = paymentChoice == "PAY_ON_COLLECTION",
                    label = "ပစ္စည်းလက်ခံချိန် ငွေရှင်းမည် (စရံကြိုလွှဲရမည်)",
                    onClick = { onPaymentChoiceChange("PAY_ON_COLLECTION") }
                )
            }
            if (pickupRequired || collectionDeposit) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SuccessBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Success.copy(alpha = 0.22f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "စရံ ${depositPercent.toInt()}% · ${money(depositAmount)} ကြိုလွှဲရမည်",
                            fontWeight = FontWeight.Bold,
                            color = Success,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (remainingAmount > 0) {
                            Text(
                                if (pickupRequired)
                                    "ကျန် ${money(remainingAmount)} ဆိုင်မှာ လက်ခံချိန် ပေးချေပါမည်"
                                else
                                    "ကျန် ${money(remainingAmount)} ပို့ချိန် ပေးချေပါမည်",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            "စရံလွှဲပြီးမှ အော်ဒါ ပယ်ဖျက်ပါက စရံငွေ ဆုံးရှုံးမည်။ ပြန်အမ်းမည် မဟုတ်ပါ။",
                            color = Warning,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryQuotePreview(
    loading: Boolean,
    error: String?,
    charge: Double?,
    reason: String?,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("ပို့ဆောင်ခ", fontWeight = FontWeight.Bold, color = TextMain)
            when {
                loading -> Text("တွက်နေသည်…", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                !error.isNullOrBlank() -> {
                    ErrorRetryBanner(message = error, onRetry = onRetry)
                }
                charge != null -> Text(
                    money(charge),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Primary
                )
                else -> Text("ရပ်ကွက် ရွေးပါ", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            val note = reason?.takeIf {
                it.isNotBlank() && !it.contains("Product ", ignoreCase = true) && !it.contains("exceeds", ignoreCase = true)
            }
            if (note != null && error.isNullOrBlank() && !loading) {
                Text(note, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CartScheduleCard(
    requestedDeliveryAt: String?,
    onRequestedDeliveryAtChange: (String) -> Unit,
    hours: DeliveryHoursConfig
) {
    val context = LocalContext.current
    val cal = parseRequestedAt(requestedDeliveryAt)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ပို့မည့် ရက်နှင့် အချိန်", fontWeight = FontWeight.Bold, color = TextMain)
            Text(
                "ဤအချိန်ကို ဆိုင်က အတည်ပြုပြီးမှ ငွေပေးချေနည်း ရွေးပါမည်။ ဆိုင်က ကိုယ်တိုင်ပို့ သို့မဟုတ် အပြင်ပို့ အပ်မည်ကိုလည်း အတည်ပြုပါမည်။",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Warning.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Warning.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Warning, null, tint = Warning, modifier = Modifier.size(18.dp))
                    Text(
                        "အသိပေးချက် · ${scheduleHint(hours)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMain,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    val minCal = minSelectableDate(hours)
                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            cal.set(Calendar.YEAR, y)
                            cal.set(Calendar.MONTH, m)
                            cal.set(Calendar.DAY_OF_MONTH, d)
                            val dayError = deliveryScheduleError(
                                formatRequestedAt(cal.apply {
                                    val openHour = windowFor(this, hours).opensAt?.take(5)?.split(':')
                                    set(Calendar.HOUR_OF_DAY, openHour?.getOrNull(0)?.toIntOrNull() ?: 9)
                                    set(Calendar.MINUTE, openHour?.getOrNull(1)?.toIntOrNull() ?: 0)
                                }),
                                hours
                            )
                            if (dayError != null && !dayError.contains("အချိန်")) {
                                Toast.makeText(context, dayError, Toast.LENGTH_LONG).show()
                                return@DatePickerDialog
                            }
                            val window = windowFor(cal, hours)
                            val openHour = window.opensAt?.take(5)?.split(':')
                            val startHour = openHour?.getOrNull(0)?.toIntOrNull() ?: 9
                            android.app.TimePickerDialog(
                                context,
                                { _, h, min ->
                                    cal.set(Calendar.HOUR_OF_DAY, h)
                                    cal.set(Calendar.MINUTE, min)
                                    val next = formatRequestedAt(cal)
                                    val error = deliveryScheduleError(next, hours)
                                    if (error == null) onRequestedDeliveryAtChange(next)
                                    else Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                },
                                cal.get(Calendar.HOUR_OF_DAY).takeIf { hour ->
                                    val closeHour = window.closesAt?.take(5)?.split(':')?.getOrNull(0)?.toIntOrNull() ?: 18
                                    hour in startHour until closeHour
                                } ?: startHour,
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    )
                    datePickerDialog.datePicker.minDate = minCal.timeInMillis
                    datePickerDialog.show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(displayRequestedAt(requestedDeliveryAt))
            }
        }
    }
}

@Composable
private fun CartFulfillmentCard(
    orderType: String,
    onOrderTypeChange: (String) -> Unit,
    deliveryEnabled: Boolean = true,
    deliveryOpensAt: String = "09:00",
    deliveryClosesAt: String = "18:00",
    deliveryDays: String = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
    locations: List<DeliveryRegion>,
    selectedRegionId: Int?,
    onRegionChange: (Int?) -> Unit,
    selectedTownshipId: Int?,
    onTownshipChange: (Int?) -> Unit,
    selectedWardId: Int?,
    onWardChange: (Int?) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.LocalShipping, null, tint = Primary, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(
                        "ပို့ဆောင်ပုံ",
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "သွားပို့ သို့မဟုတ် ဆိုင်မှာ လာယူမည်",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (deliveryEnabled) {
                    ChoiceChip(
                        modifier = Modifier.weight(1f),
                        selected = orderType == "DELIVERY",
                        label = "သွားပို့",
                        onClick = { onOrderTypeChange("DELIVERY") }
                    )
                }
                ChoiceChip(
                    modifier = Modifier.weight(1f),
                    selected = orderType == "PICKUP",
                    label = "ဆိုင်မှာ လာယူမည်",
                    onClick = { onOrderTypeChange("PICKUP") }
                )
            }
            if (!deliveryEnabled) {
                Text(
                    "သွားပို့ ယာယီပိတ်ထားသည်။ ဆိုင်မှာလာယူ + စရံကြိုလွှဲ သာ ရနိုင်သည်။",
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning
                )
            }

            if (orderType == "DELIVERY") {
                Text(
                    "ရပ်ကွက် ရွေးပြီး ပို့ဆောင်ခ ကြည့်ပြီးမှသာ အော်ဒါ တင်ပါမည်။",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                val region = locations.firstOrNull { it.id == selectedRegionId }
                val township = region?.townshipsOrEmpty()?.firstOrNull { it.id == selectedTownshipId }
                val ward = township?.wardsOrEmpty()?.firstOrNull { it.id == selectedWardId }

                var activeSheet by remember { mutableStateOf<String?>(null) }

                LocationSelectionRow(
                    title = "တိုင်း / ပြည်နယ်",
                    selectedValue = region?.name.orEmpty(),
                    emptyText = "တိုင်း / ပြည်နယ် ရွေးပါ",
                    enabled = true,
                    onClick = { activeSheet = "REGION" }
                )
                LocationSelectionRow(
                    title = "မြို့နယ်",
                    selectedValue = township?.name.orEmpty(),
                    emptyText = if (selectedRegionId == null) "အရင် တိုင်း ရွေးပါ" else "မြို့နယ် ရွေးပါ",
                    enabled = selectedRegionId != null,
                    onClick = { activeSheet = "TOWNSHIP" }
                )
                LocationSelectionRow(
                    title = "ရပ်ကွက်",
                    selectedValue = listOfNotNull(ward?.name, ward?.deliveryCharge?.let { money(it) }).joinToString(" · "),
                    emptyText = if (selectedTownshipId == null) "အရင် မြို့နယ် ရွေးပါ" else "ရပ်ကွက် ရွေးပါ",
                    enabled = selectedTownshipId != null,
                    onClick = { activeSheet = "WARD" }
                )

                when (activeSheet) {
                    "REGION" -> LocationSelectionSheet(
                        title = "တိုင်း / ပြည်နယ် ရွေးပါ",
                        searchPlaceholder = "တိုင်း ရှာပါ...",
                        options = locations.map { LocationOption(it.id, it.name.orEmpty()) },
                        selectedId = selectedRegionId,
                        onSelect = {
                            onRegionChange(it)
                            onTownshipChange(null)
                            onWardChange(null)
                        },
                        onDismiss = { activeSheet = null }
                    )
                    "TOWNSHIP" -> LocationSelectionSheet(
                        title = "မြို့နယ် ရွေးပါ",
                        searchPlaceholder = "မြို့နယ် ရှာပါ...",
                        options = region?.townshipsOrEmpty().orEmpty().map { LocationOption(it.id, it.name.orEmpty()) },
                        selectedId = selectedTownshipId,
                        onSelect = {
                            onTownshipChange(it)
                            onWardChange(null)
                        },
                        onDismiss = { activeSheet = null }
                    )
                    "WARD" -> LocationSelectionSheet(
                        title = "ရပ်ကွက် ရွေးပါ",
                        searchPlaceholder = "ရပ်ကွက် ရှာပါ...",
                        options = township?.wardsOrEmpty().orEmpty().map {
                            LocationOption(
                                it.id,
                                it.name.orEmpty(),
                                it.deliveryCharge?.let { charge -> money(charge) }
                            )
                        },
                        selectedId = selectedWardId,
                        onSelect = { onWardChange(it) },
                        onDismiss = { activeSheet = null }
                    )
                    null -> {}
                }
            } else {
                Text(
                    "ဆိုင်သို့ လာယူပါမည် — ပို့ဆောင်ခ မလိုပါ",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private data class LocationOption(
    val id: Int,
    val title: String,
    val subtitle: String? = null
)

@Composable
private fun LocationSelectionRow(
    title: String,
    selectedValue: String,
    emptyText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            color = TextMain,
            style = MaterialTheme.typography.bodyMedium
        )
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            color = SurfaceSoft,
            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedValue.isNotBlank()) selectedValue else emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedValue.isNotBlank() && enabled) TextMain else TextMuted
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (enabled) TextMuted else TextMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactLocationSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "ရှာပါ"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholderText, color = TextMuted, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Primary, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (value.isNotBlank()) {
            {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "ရှင်းရန်", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        } else null,
        singleLine = true,
        shape = FieldShape,
        colors = cartFieldColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSelectionSheet(
    title: String,
    searchPlaceholder: String,
    options: List<LocationOption>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, options) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) options
        else options.filter { it.title.lowercase().contains(q) || (it.subtitle?.lowercase()?.contains(q) == true) }
    }

    BackHandler(enabled = true) {
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = CardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceSoft)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "ပိတ်မည်",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            CompactLocationSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                placeholderText = searchPlaceholder
            )

            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ရလဒ် မတွေ့ပါ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(filtered, key = { index, item -> "${item.id}-$index" }) { _, option ->
                        val isSelected = selectedId == option.id
                        val displayLabel = if (option.subtitle.isNullOrBlank()) option.title else "${option.title} · ${option.subtitle}"
                        Surface(
                            onClick = {
                                onSelect(option.id)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) PrimaryLight else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = displayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Primary else TextMain,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelect(option.id)
                                        onDismiss()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = Primary)
                                )
                            }
                        }
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CartRecipientCard(
    deliveryMode: String,
    onDeliveryModeChange: (String) -> Unit,
    deliveryPhone: String,
    onDeliveryPhoneChange: (String) -> Unit,
    deliveryAddress: String,
    onDeliveryAddressChange: (String) -> Unit,
    profileAddress: String?,
    profilePhone: String?,
    regionName: String? = null,
    townshipName: String? = null,
    wardName: String? = null,
    quotedCharge: Double? = null,
    onEditArea: () -> Unit = {}
) {
    val areaLine = listOfNotNull(
        regionName?.takeIf { it.isNotBlank() },
        townshipName?.takeIf { it.isNotBlank() },
        wardName?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Person, null, tint = Primary, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(
                        "လက်ခံမည့်သူ / ပို့မည့်နေရာ",
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "ဖုန်းနှင့် အသေးစိတ်လမ်းကြောင်း",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            if (areaLine.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryLight.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Place, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ပို့မည့်ဒေသ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(areaLine, fontWeight = FontWeight.SemiBold, color = TextMain, style = MaterialTheme.typography.bodySmall)
                            quotedCharge?.let {
                                Text("ပို့ခ ${money(it)}", style = MaterialTheme.typography.labelSmall, color = Primary)
                            }
                        }
                        TextButton(onClick = onEditArea) { Text("ပြင်မည်") }
                    }
                }
            }
            ChoiceChip(
                modifier = Modifier.fillMaxWidth(),
                selected = deliveryMode == "PROFILE",
                label = "သိမ်းထားသော လိပ်စာသို့ ပို့မည်",
                onClick = { onDeliveryModeChange("PROFILE") }
            )
            ChoiceChip(
                modifier = Modifier.fillMaxWidth(),
                selected = deliveryMode == "OTHER",
                label = "အခြားနေရာသို့ ပို့မည်",
                onClick = { onDeliveryModeChange("OTHER") }
            )

            if (deliveryMode == "PROFILE") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceSoft,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Profile လိပ်စာ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            profileAddress?.takeIf { it.isNotBlank() } ?: "လိပ်စာ မရှိသေး — Profile တွင် ဖြည့်ပါ",
                            color = if (profileAddress.isNullOrBlank()) Danger else TextMain,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!profilePhone.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("ဖုန်း · $profilePhone", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = deliveryPhone,
                    onValueChange = onDeliveryPhoneChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("လက်ခံမည့်သူ ဖုန်းနံပါတ်") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = cartFieldColors()
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryLight.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.2f))
                ) {
                    Text(
                        "မြို့နယ်/ရပ်ကွက် ရွေးပြီးပါပြီ။ ဒီမှာ အိမ်နံပါတ်နဲ့ လမ်းအမည်သာ ဖြည့်ပါ။",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                OutlinedTextField(
                    value = deliveryAddress,
                    onValueChange = onDeliveryAddressChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("အသေးစိတ်လိပ်စာ (အိမ်နံပါတ်၊ လမ်းအမည်)") },
                    placeholder = { Text("ဥပမာ — အမှတ် ၁၂၊ ဗိုလ်ချုပ်လမ်း၊ ၃ လွှာ") },
                    minLines = 2,
                    maxLines = 4,
                    shape = FieldShape,
                    colors = cartFieldColors()
                )
                Text(
                    "အော်ဒါတင်စဉ် GPS တည်နေရာကို ယူပါမည်",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PrimaryLight else SurfaceSoft)
            .border(
                1.dp,
                if (selected) Primary.copy(alpha = 0.45f) else BorderColor,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Primary else TextMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun cartFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = BorderColor,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextMuted,
    cursorColor = Primary,
    focusedContainerColor = CardBg,
    unfocusedContainerColor = SurfaceSoft
)

@Composable
private fun CheckoutNoteCard(
    note: String,
    onNoteChange: (String) -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "မှတ်ချက် (optional)",
                fontWeight = FontWeight.Bold,
                color = TextMain,
                style = MaterialTheme.typography.titleSmall
            )
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ဥပမာ — မနက် ၁၀ နာရီ ခေါ်ပါ") },
                singleLine = true,
                shape = FieldShape,
                colors = cartFieldColors()
            )
        }
    }
}

@Composable
private fun CheckoutPriceBreakdownCard(
    itemsTotal: Double,
    deliveryFee: Double,
    showDeliveryLine: Boolean,
    subtotal: Double,
    discountAmount: Double? = null,
    pickupDepositPercent: Double? = null,
    pickupDeposit: Double? = null,
    pickupRemaining: Double? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "ကျသင့်ငွေ အသေးစိတ်",
                fontWeight = FontWeight.Bold,
                color = TextMain,
                style = MaterialTheme.typography.titleSmall
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ပစ္စည်း စုစုပေါင်း", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Text(money(itemsTotal), color = TextMain, style = MaterialTheme.typography.bodySmall)
            }
            val discount = discountAmount ?: 0.0
            if (discount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Promo လျှော့", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text("-${money(discount)}", color = Success, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (showDeliveryLine) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ခန့်မှန်းပို့ခ (ဆိုင်ပို့)", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text(money(deliveryFee), color = TextMain, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (pickupDeposit != null && pickupDepositPercent != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("စရံ ${pickupDepositPercent.toInt()}% ကြိုလွှဲ", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text(money(pickupDeposit), color = Success, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
                if ((pickupRemaining ?: 0.0) > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ကျန် (လက်ခံချိန်)", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        Text(money(pickupRemaining ?: 0.0), color = TextMain, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "စရံလွှဲပြီးမှ ပယ်ဖျက်ပါက စရံငွေ ဆုံးရှုံးမည်။",
                    color = Warning,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ခန့်မှန်းစုစုပေါင်း", fontWeight = FontWeight.Bold, color = TextMain)
                Text(money(subtotal), fontWeight = FontWeight.ExtraBold, color = Primary, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun CartCheckoutBar(
    subtotal: Double,
    itemCount: Int,
    enabled: Boolean,
    placing: Boolean = false,
    onCheckout: () -> Unit,
    showBottomNav: Boolean = false
) {
    Surface(
        color = CardBg,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = if (showBottomNav) 10.dp + 72.dp else 10.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "စုစုပေါင်း",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    money(subtotal),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Primary
                )
            }
            Text(
                "$itemCount ခု",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )

            Button(
                onClick = onCheckout,
                enabled = enabled && !placing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    disabledContainerColor = Primary.copy(alpha = 0.35f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                if (placing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = OnPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("အော်ဒါတင်နေသည်…", fontWeight = FontWeight.Bold)
                } else {
                    Text("အော်ဒါ တင်မည်", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

private fun money(amount: Double?): String {
    val safe = amount?.takeIf { it.isFinite() } ?: 0.0
    val absValue = abs(safe).toLong()
    val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
}

private fun String.cartAssetUrl(): String =
    if (startsWith("http://") || startsWith("https://")) this
    else BuildConfig.DEFAULT_BASE_URL.trimEnd('/') + "/" + trimStart('/')

private fun sampleCartItems(): List<CartItem> = listOf(
    CartItem(
        product = CatalogProduct(
            id = 1,
            name = "Dell Inspiron 15 3530",
            productCode = "DELL-3530",
            categoryName = "Laptop",
            brandName = "Dell",
            productType = "New",
            sellingPrice = 1_350_000.0,
            stockQty = 4,
            photoUrls = emptyList()
        ),
        qty = 1
    ),
    CartItem(
        product = CatalogProduct(
            id = 2,
            name = "HP 15.6 Backpack",
            productCode = "HP-BAG",
            categoryName = "Accessories",
            brandName = "HP",
            productType = "New",
            sellingPrice = 48_000.0,
            stockQty = 12,
            photoUrls = emptyList()
        ),
        qty = 2
    )
)

private fun sampleLocations(): List<DeliveryRegion> = listOf(
    DeliveryRegion(
        id = 1,
        name = "ရန်ကုန်တိုင်း",
        kind = "YANGON",
        townships = listOf(
            DeliveryTownship(
                id = 11,
                name = "ရန်ကင်း",
                wards = listOf(
                    DeliveryWard(id = 101, name = "စမ်းချောင်း (၁)", deliveryCharge = 3_000.0),
                    DeliveryWard(id = 102, name = "စမ်းချောင်း (၂)", deliveryCharge = 3_500.0)
                )
            ),
            DeliveryTownship(
                id = 12,
                name = "ကမာရွတ်",
                wards = listOf(
                    DeliveryWard(id = 103, name = "အမှတ် (၁) ရပ်ကွက်", deliveryCharge = 2_500.0)
                )
            )
        )
    )
)

@Composable
private fun PreviewCheckoutScreen(
    startPhase: String,
    orderType: String = "DELIVERY",
    paymentChoice: String = "TRANSFER",
    deliveryMode: String = "PROFILE"
) {
    var note by remember { mutableStateOf("မနက် ၁၀ နာရီ ခေါ်ပါ") }
    var pay by remember { mutableStateOf(paymentChoice) }
    var type by remember { mutableStateOf(orderType) }
    var mode by remember { mutableStateOf(deliveryMode) }
    var regionId by remember { mutableStateOf<Int?>(1) }
    var townshipId by remember { mutableStateOf<Int?>(11) }
    var wardId by remember { mutableStateOf<Int?>(101) }
    AppTheme {
        CustomerCartScreen(
            items = sampleCartItems(),
            note = note,
            onNoteChange = { note = it },
            onChangeQty = { _, _ -> },
            onRemove = {},
            onCheckout = {},
            onBrowseProducts = {},
            paymentChoice = pay,
            onPaymentChoiceChange = { pay = it },
            startPhase = startPhase,
            orderType = type,
            onOrderTypeChange = { type = it },
            deliveryMode = mode,
            onDeliveryModeChange = { mode = it },
            profileAddress = "အမှတ် ၁၂၊ ဗိုလ်ချုပ်လမ်း၊ ရန်ကင်း",
            profilePhone = "09 123 456 789",
            locations = sampleLocations(),
            selectedRegionId = regionId,
            onRegionChange = {
                regionId = it
                townshipId = null
                wardId = null
            },
            selectedTownshipId = townshipId,
            onTownshipChange = {
                townshipId = it
                wardId = null
            },
            selectedWardId = wardId,
            onWardChange = { wardId = it },
            pickupDepositPercent = 30.0
        )
    }
}

@Preview(name = "1 Order confirm", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerOrderConfirmDeliveryPreview() {
    PreviewCheckoutScreen(startPhase = "ORDER")
}

@Preview(name = "2 Fulfillment", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerFulfillmentPreview() {
    PreviewCheckoutScreen(startPhase = "FULFILLMENT")
}

@Preview(name = "3 Recipient", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerRecipientPreview() {
    PreviewCheckoutScreen(startPhase = "RECIPIENT")
}

@Preview(name = "4 Payment", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerPaymentPreview() {
    PreviewCheckoutScreen(startPhase = "PAYMENT")
}

@Preview(name = "5 Confirm bar", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerConfirmPreview() {
    PreviewCheckoutScreen(startPhase = "CONFIRM")
}

@Preview(name = "Pickup path", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerOrderConfirmPickupPreview() {
    PreviewCheckoutScreen(startPhase = "ORDER", orderType = "PICKUP", paymentChoice = "TRANSFER")
}

@Preview(name = "Cart filled", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerCartFilledPreview() {
    var note by remember { mutableStateOf("ဆိုင်မှာ လာယူမည်") }
    AppTheme {
        CustomerCartScreen(
            items = sampleCartItems(),
            note = note,
            onNoteChange = { note = it },
            onChangeQty = { _, _ -> },
            onRemove = {},
            onCheckout = {},
            onBrowseProducts = {}
        )
    }
}

@Preview(name = "Cart empty", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerCartEmptyPreview() {
    AppTheme {
        CustomerCartScreen(
            items = emptyList(),
            note = "",
            onNoteChange = {},
            onChangeQty = { _, _ -> },
            onRemove = {},
            onCheckout = {},
            onBrowseProducts = {}
        )
    }
}

@Preview(name = "Cart stock max", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerCartStockMaxPreview() {
    AppTheme {
        CustomerCartScreen(
            items = listOf(
                CartItem(
                    product = CatalogProduct(
                        id = 3,
                        name = "Logitech M185 Mouse",
                        categoryName = "Accessories",
                        brandName = "Logitech",
                        sellingPrice = 18_500.0,
                        stockQty = 2,
                        photoUrls = emptyList()
                    ),
                    qty = 2
                )
            ),
            note = "",
            onNoteChange = {},
            onChangeQty = { _, _ -> },
            onRemove = {},
            onCheckout = {},
            onBrowseProducts = {}
        )
    }
}
