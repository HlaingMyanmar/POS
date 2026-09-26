package com.sspd.servicemgmt.feature.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.BookingAvailabilityDate
import com.sspd.servicemgmt.core.network.BookingAvailabilityPeriod
import com.sspd.servicemgmt.core.network.BookingAvailabilityWindow
import com.sspd.servicemgmt.core.network.BookingRequestPhotoBody
import com.sspd.servicemgmt.core.network.CatalogService
import com.sspd.servicemgmt.core.network.ServiceRequestBody
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private data class BookingPhotoSelection(
    val slot: Int,
    val uri: Uri,
    val dataUrl: String
)

private data class BookingChoice(val value: String, val label: String)

private fun formatMmFee(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("my", "MM"))
    nf.maximumFractionDigits = 0
    return "${nf.format(amount)} ကျပ်"
}

@Composable
fun CustomerServiceBookingForm(
    services: List<CatalogService> = emptyList(),
    defaultAddress: String,
    submitting: Boolean,
    onSubmit: (ServiceRequestBody) -> Unit,
    modifier: Modifier = Modifier,
    initialStep: Int = 1,
    /** Pre-selected catalog service from rates screen. */
    initialService: CatalogService? = null,
    /** Preview / tests: pre-select service mode (ONSITE / SHOP / UNDECIDED). */
    initialServiceMode: String = "UNDECIDED",
    initialProblem: String = "",
    initialServiceDate: String? = null,
    initialWindowId: Int? = null,
    initialPreferredAnytime: Boolean = true,
    initialPreferenceNote: String = "",
    /** Preview: open SHOP optional schedule picker immediately. */
    initialShopScheduleOpen: Boolean = false,
    initialShopDropOffDate: String? = null,
    showBottomNav: Boolean = true,
    onClearSelectedService: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val previewMode = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    var entryMode by remember { mutableStateOf("PROBLEM") }
    var requestType by remember { mutableStateOf("DIAGNOSIS") }
    var deviceCategory by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf(initialProblem) }
    var serviceMode by remember { mutableStateOf(initialServiceMode) }
    var serviceAddress by remember(defaultAddress) { mutableStateOf(defaultAddress) }
    var urgency by remember { mutableStateOf("NORMAL") }
    var contactPreference by remember { mutableStateOf("PHONE") }
    var appointment by remember {
        mutableStateOf(
            if (previewMode && initialServiceMode == "SHOP" && initialShopScheduleOpen) {
                val d = initialShopDropOffDate ?: previewTomorrow()
                runCatching {
                    LocalDateTime.of(java.time.LocalDate.parse(d), LocalTime.of(9, 0))
                }.getOrNull()
            } else null
        )
    }
    var serviceDate by remember { mutableStateOf(initialServiceDate) }
    var availabilityDates by remember {
        mutableStateOf(
            if (previewMode && (
                    initialServiceMode == "ONSITE" ||
                        (initialServiceMode == "SHOP" && initialShopScheduleOpen)
                    )
            ) sampleAvailabilityDates()
            else emptyList()
        )
    }
    var availabilityWindows by remember {
        mutableStateOf(
            if (previewMode && initialServiceMode == "ONSITE" && !initialServiceDate.isNullOrBlank())
                sampleAvailabilityWindows()
            else emptyList()
        )
    }
    var selectedWindowId by remember { mutableStateOf(initialWindowId) }
    var preferredAnytime by remember { mutableStateOf(initialPreferredAnytime) }
    var preferredTime by remember {
        mutableStateOf(
            if (previewMode && !initialPreferredAnytime) LocalTime.of(10, 0) else null
        )
    }
    var preferenceNote by remember { mutableStateOf(initialPreferenceNote) }
    var availabilityLoading by remember { mutableStateOf(false) }
    var availabilityDatesError by remember { mutableStateOf<String?>(null) }
    var availabilityWindowsError by remember { mutableStateOf<String?>(null) }
    var datesRetryKey by remember { mutableStateOf(0) }
    var windowsRetryKey by remember { mutableStateOf(0) }
    /** SHOP drop-off is optional; only load/constrain schedule after customer opts in. */
    var shopScheduleOpen by remember {
        mutableStateOf(previewMode && initialServiceMode == "SHOP" && initialShopScheduleOpen)
    }
    var shopDropOffDate by remember {
        mutableStateOf(
            if (previewMode && initialServiceMode == "SHOP" && initialShopScheduleOpen)
                initialShopDropOffDate ?: previewTomorrow()
            else null
        )
    }
    var photos by remember { mutableStateOf<List<BookingPhotoSelection>>(emptyList()) }
    var pendingSlot by remember { mutableStateOf(1) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var currentStep by remember(initialStep) { mutableStateOf(initialStep.coerceIn(1, 3)) }
    var showValidation by remember { mutableStateOf(false) }
    var outdoorNotice by remember {
        mutableStateOf(
            if (previewMode)
                "အိမ်အရောက်ဝန်ဆောင်မှုအတွက် Transportation charges ကျသင့်ပါသည်။"
            else null
        )
    }
    var outdoorFee by remember { mutableStateOf(if (previewMode) 5000.0 else null) }
    var outdoorBookingEnabled by remember { mutableStateOf(true) }
    var outdoorDisabledReason by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(previewMode) {
        if (previewMode) return@LaunchedEffect
        runCatching {
            ApiClient.service.branding().body()?.data
        }.getOrNull()?.let { branding ->
            outdoorNotice = branding.outdoorTransportationNotice?.trim()?.takeIf { it.isNotEmpty() }
            outdoorFee = branding.outdoorTransportationFee
            outdoorBookingEnabled = branding.outdoorBookingEnabled != false
            outdoorDisabledReason = branding.outdoorBookingDisabledReason?.trim()?.takeIf { it.isNotEmpty() }
            if (!outdoorBookingEnabled && serviceMode == "ONSITE") {
                serviceMode = "UNDECIDED"
            }
        }
    }

    BackHandler(enabled = currentStep > 1 && !submitting) {
        currentStep--
        showValidation = false
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { encodeBookingPhoto(context, uri) }
                    .onSuccess { encoded ->
                        photos = (photos.filterNot { it.slot == pendingSlot } +
                                BookingPhotoSelection(pendingSlot, uri, encoded)).sortedBy { it.slot }
                        photoError = null
                    }
                    .onFailure { photoError = "ပုံကို ပြင်ဆင်၍မရပါ။ JPG/PNG ပုံတစ်ပုံ ပြန်ရွေးပါ။" }
            }
        }
    }

    LaunchedEffect(serviceMode, urgency, previewMode, datesRetryKey, shopScheduleOpen) {
        if (previewMode) {
            availabilityDatesError = null
            if (serviceMode == "ONSITE" || (serviceMode == "SHOP" && shopScheduleOpen)) {
                availabilityDates = sampleAvailabilityDates()
            } else {
                availabilityDates = emptyList()
                if (serviceMode != "ONSITE") availabilityWindows = emptyList()
            }
            return@LaunchedEffect
        }
        val loadDates = serviceMode == "ONSITE" || (serviceMode == "SHOP" && shopScheduleOpen)
        if (!loadDates) {
            availabilityDates = emptyList()
            availabilityDatesError = null
            if (serviceMode != "ONSITE") {
                availabilityWindows = emptyList()
                availabilityWindowsError = null
                if (serviceMode != "SHOP") {
                    serviceDate = null
                    selectedWindowId = null
                }
            }
            return@LaunchedEffect
        }
        availabilityLoading = true
        availabilityDatesError = null
        val emergency = urgency == "EMERGENCY"
        runCatching {
            val res = ApiClient.service.bookingAvailabilityDates(serviceMode, emergency = emergency)
            if (!res.isSuccessful) {
                throw IllegalStateException(availabilityHttpMessage(res.code()))
            }
            res.body()?.data ?: throw IllegalStateException("ရက်စာရင်း မရရှိပါ")
        }.onSuccess { dates ->
            availabilityDates = dates
            if (serviceMode == "ONSITE") {
                if (serviceDate != null && dates.none { it.date == serviceDate && it.available == true }) {
                    serviceDate = null
                    selectedWindowId = null
                    availabilityWindows = emptyList()
                    availabilityWindowsError = null
                }
            } else if (serviceMode == "SHOP") {
                if (shopDropOffDate != null && dates.none { it.date == shopDropOffDate && it.available == true }) {
                    shopDropOffDate = null
                    appointment = null
                }
            }
        }.onFailure { err ->
            availabilityDates = emptyList()
            availabilityDatesError = availabilityErrorMessage(err)
            if (serviceMode == "ONSITE") {
                serviceDate = null
                selectedWindowId = null
                availabilityWindows = emptyList()
                availabilityWindowsError = null
            } else {
                shopDropOffDate = null
                appointment = null
            }
        }
        availabilityLoading = false
    }

    LaunchedEffect(serviceDate, urgency, serviceMode, previewMode, windowsRetryKey) {
        if (previewMode) {
            availabilityWindowsError = null
            availabilityWindows = if (serviceMode == "ONSITE" && !serviceDate.isNullOrBlank()) {
                sampleAvailabilityWindows()
            } else {
                emptyList()
            }
            return@LaunchedEffect
        }
        if (serviceMode != "ONSITE" || serviceDate.isNullOrBlank()) {
            if (serviceMode != "ONSITE") {
                availabilityWindows = emptyList()
                availabilityWindowsError = null
            }
            return@LaunchedEffect
        }
        availabilityLoading = true
        availabilityWindowsError = null
        runCatching {
            val res = ApiClient.service.bookingAvailabilityWindows(serviceDate!!, urgency == "EMERGENCY")
            if (!res.isSuccessful) {
                throw IllegalStateException(availabilityHttpMessage(res.code()))
            }
            res.body()?.data ?: throw IllegalStateException("Arrival window စာရင်း မရရှိပါ")
        }.onSuccess { windows ->
            availabilityWindows = windows
            if (selectedWindowId != null && windows.none {
                    it.windowId == selectedWindowId && (it.state == "AVAILABLE" || it.state == "FEW_LEFT")
                }) {
                selectedWindowId = null
            }
        }.onFailure { err ->
            availabilityWindows = emptyList()
            availabilityWindowsError = availabilityErrorMessage(err)
            selectedWindowId = null
        }
        availabilityLoading = false
    }

    val validSchedule = when (serviceMode) {
        "ONSITE" -> serviceDate != null
                && selectedWindowId != null
                && (preferredAnytime || preferredTime != null)
                && availabilityDatesError == null
                && availabilityWindowsError == null
        else -> true
    }
    val validProblem = problem.trim().isNotEmpty()
    val validAddress = serviceMode != "ONSITE" || serviceAddress.isNotBlank()
    val canSubmit = !submitting && validProblem && validAddress && validSchedule

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
            .imePadding()
    ) {
        // Step Progress Header
        BookingStepHeader(
            currentStep = currentStep,
            onBack = {
                if (currentStep > 1) {
                    currentStep--
                    showValidation = false
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (initialService != null) {
                item {
                    SelectedServiceBanner(
                        service = initialService,
                        onClear = onClearSelectedService
                    )
                }
            }
            if (currentStep == 1) {
                item {
                    FormSection("၁။ ဘာအတွက် အကူအညီလိုပါသလဲ?") {
                        ChoiceRow(
                            choices = listOf(
                                BookingChoice("PROBLEM", "ပြဿနာပြောမယ်"),
                                BookingChoice("CONSULT", "မသေချာပါ")
                            ),
                            selected = entryMode,
                            onSelect = {
                                entryMode = it
                                requestType = if (it == "CONSULT") "CONSULTATION"
                                else if (requestType == "CONSULTATION") "DIAGNOSIS" else requestType
                                showValidation = false
                            }
                        )
                        if (entryMode == "CONSULT") {
                            Text(
                                "Technician က ဖုန်း/Chat ဖြင့် မေးမြန်းပြီး သင့်တော်တဲ့ service ကို အကြံပြုပါမယ်။",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                item {
                    FormSection("၂။ လုပ်ပေးစေချင်တာ / ပြဿနာအမျိုးအစား") {
                        ChoiceRow(
                            choices = listOf(
                                BookingChoice("DIAGNOSIS", "စစ်ဆေးရန်"),
                                BookingChoice("REPAIR", "ပြုပြင်ရန်"),
                                BookingChoice("MAINTENANCE", "ထိန်းသိမ်းရန်"),
                                BookingChoice("INSTALLATION", "တပ်ဆင်ရန်"),
                                BookingChoice("CONSULTATION", "အကြံယူရန်")
                            ),
                            selected = requestType,
                            onSelect = { requestType = it }
                        )
                        OutlinedTextField(
                            value = problem,
                            onValueChange = {
                                if (it.length <= 2000) problem = it
                                showValidation = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            label = { Text("ဖြစ်နေတဲ့ပြဿနာ / လုပ်ပေးစေချင်တာ") },
                            placeholder = { Text("ဖြစ်နေတဲ့ပြဿနာကို အသေးစိတ် ရေးပေးပါ...") },
                            isError = showValidation && !validProblem,
                            supportingText = {
                                Text(
                                    if (showValidation && !validProblem)
                                        "ဖြစ်နေတဲ့ပြဿနာ / လုပ်ပေးစေချင်တာ ရေးပါ"
                                    else "ဘယ်အချိန်ကစဖြစ်တာ၊ ဘာတွေစမ်းပြီးပြီလဲ ထည့်ရေးပေးပါ"
                                )
                            }
                        )
                    }
                }

                if (showValidation && !validProblem) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = DangerBg,
                            border = BorderStroke(1.dp, Danger.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = Danger,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "ဖြစ်နေသည့် ပြဿနာ သို့မဟုတ် လုပ်ပေးစေချင်တာကို ရေးပါ",
                                    fontWeight = FontWeight.Bold,
                                    color = Danger,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            if (currentStep == 2) {
                item {
                    FormSection("၁။ ပစ္စည်းအချက်အလက်") {
                        ChoiceRow(
                            choices = listOf(
                                BookingChoice("PHONE", "ဖုန်း"),
                                BookingChoice("COMPUTER", "ကွန်ပျူတာ"),
                                BookingChoice("PRINTER", "ပရင်တာ"),
                                BookingChoice("NETWORK", "Network"),
                                BookingChoice("OTHER", "အခြား")
                            ),
                            selected = deviceCategory,
                            onSelect = { deviceCategory = if (deviceCategory == it) "" else it }
                        )
                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { if (it.length <= 200) deviceName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Brand / Model / ပစ္စည်းအမည်") },
                            placeholder = { Text("ဥပမာ — Dell Inspiron 15, Canon G3010") },
                            singleLine = true
                        )
                    }
                }

                item {
                    FormSection("၂။ ဘယ်လိုဝန်ဆောင်မှုပေးရမလဲ") {
                        ChoiceRow(
                            choices = buildList {
                                add(BookingChoice("UNDECIDED", "မသေချာသေး"))
                                add(BookingChoice("SHOP", "ဆိုင်ယူလာမယ်"))
                                if (outdoorBookingEnabled) add(BookingChoice("ONSITE", "အိမ်အရောက်"))
                            },
                            selected = serviceMode,
                            onSelect = { mode ->
                                serviceMode = mode
                                if (mode != "SHOP") {
                                    shopScheduleOpen = false
                                    shopDropOffDate = null
                                }
                                if (mode == "ONSITE" || mode == "SHOP") {
                                    appointment = null
                                }
                                if (mode != "ONSITE") {
                                    serviceDate = null
                                    selectedWindowId = null
                                    preferredTime = null
                                    preferredAnytime = true
                                    preferenceNote = ""
                                    availabilityWindows = emptyList()
                                    availabilityWindowsError = null
                                }
                            }
                        )
                        if (!outdoorBookingEnabled) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF5F3FF),
                                border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF6D28D9),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "အိမ်အရောက် ယာယီပိတ်ထားသည်",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF5B21B6)
                                        )
                                        Text(
                                            outdoorDisabledReason
                                                ?: "အိမ်အရောက်ဝန်ဆောင်မှုကို ယာယီပိတ်ထားပါသည်။ ဆိုင်သို့ ယူလာပေးပါ သို့မဟုတ် ဆိုင်နှင့် ညှိနှိုင်းပါ။",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF4C1D95)
                                        )
                                    }
                                }
                            }
                        }
                        if (serviceMode == "ONSITE") {
                            if (!outdoorNotice.isNullOrBlank() || outdoorFee != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFFF8E7),
                                    border = BorderStroke(1.dp, Color(0xFFFFE0A3))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFB45309),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                "Transportation charges အသိပေးချက်",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF92400E)
                                            )
                                            if (!outdoorNotice.isNullOrBlank()) {
                                                Text(
                                                    outdoorNotice!!,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF78350F)
                                                )
                                            }
                                            outdoorFee?.let { fee ->
                                                Text(
                                                    "Transportation charges — ${formatMmFee(fee)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF92400E)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = serviceAddress,
                                onValueChange = { if (it.length <= 2000) serviceAddress = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                isError = showValidation && serviceAddress.isBlank(),
                                label = { Text("လာရောက်ရမည့်လိပ်စာ *") },
                                supportingText = {
                                    if (showValidation && serviceAddress.isBlank()) Text("အိမ်အရောက် service အတွက် လိပ်စာလိုအပ်ပါတယ်", color = Danger)
                                }
                            )
                        }
                        if (serviceMode == "ONSITE") {
                            OnsiteSchedulePicker(
                                availabilityDates = availabilityDates,
                                selectedDate = serviceDate,
                                onSelectDate = {
                                    serviceDate = it
                                    selectedWindowId = null
                                    preferredAnytime = true
                                    preferredTime = null
                                    availabilityWindowsError = null
                                },
                                availabilityWindows = availabilityWindows,
                                selectedWindowId = selectedWindowId,
                                onSelectWindow = { selectedWindowId = it },
                                loadingDates = availabilityLoading,
                                loadingWindows = availabilityLoading,
                                datesError = availabilityDatesError,
                                windowsError = availabilityWindowsError,
                                onRetryDates = { datesRetryKey++ },
                                onRetryWindows = { windowsRetryKey++ },
                                showValidation = showValidation
                            )
                        } else if (serviceMode == "SHOP") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = CardBg,
                                border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f))
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
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(PrimaryLight),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Event,
                                                    contentDescription = null,
                                                    tint = Primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "ဆိုင်ယူလာမည့် ရက်စွဲနှင့် အချိန်",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = TextMain
                                                )
                                                Text(
                                                    text = "ဆိုင်ဖွင့်ချိန်အတွင်း မိမိအဆင်ပြေမည့်အချိန် (ရွေးချယ်နိုင်)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextMuted
                                                )
                                            }
                                        }
                                    }

                                    if (appointment != null) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = PrimaryLight.copy(alpha = 0.5f),
                                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Schedule,
                                                        contentDescription = null,
                                                        tint = Primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = appointment!!.format(DateTimeFormatter.ofPattern("dd MMM yyyy (EEE) · h:mm a")),
                                                        fontWeight = FontWeight.Bold,
                                                        color = PrimaryDark,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                                TextButton(
                                                    onClick = { appointment = null },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("ဖျက်မည်", color = Danger, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { showAppointmentPicker(context) { appointment = it } },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, BorderColor)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CalendarMonth,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (appointment != null) "ရက်စွဲ/အချိန် ပြောင်းမည်" else "ဆိုင်လာမည့်ရက်/အချိန် ရွေးမည်",
                                            fontWeight = FontWeight.Bold,
                                            color = Primary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showAppointmentPicker(context) { appointment = it } },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    appointment?.format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a"))
                                        ?: "လာရောက်မည့်ရက်/အချိန် ရွေးရန် (ရွေးချယ်နိုင်)"
                                )
                            }
                            if (appointment != null) {
                                AssistChip(onClick = { appointment = null }, label = { Text("အချိန်မသတ်မှတ်တော့ပါ") })
                            }
                        }
                    }
                }

                item {
                    FormSection("၃။ ဦးစားပေးမှုနှင့် ဆက်သွယ်ရန်") {
                        ChoiceRow(
                            choices = listOf(
                                BookingChoice("NORMAL", "ပုံမှန်"),
                                BookingChoice("SOON", "အမြန်"),
                                BookingChoice("EMERGENCY", "အရေးပေါ်")
                            ),
                            selected = urgency,
                            onSelect = { urgency = it }
                        )
                        if (urgency == "EMERGENCY") {
                            Text(
                                "အရေးပေါ်ရွေးထားပါသည်။ ဆိုင်ဘက်မှ အမြန်ဆုံးဆက်သွယ်ပေးပါမယ်။",
                                style = MaterialTheme.typography.bodySmall,
                                color = Danger
                            )
                        }
                        ChoiceRow(
                            choices = listOf(
                                BookingChoice("PHONE", "ဖုန်း"),
                                BookingChoice("VIBER", "Viber"),
                                BookingChoice("CHAT", "App Chat")
                            ),
                            selected = contactPreference,
                            onSelect = { contactPreference = it }
                        )
                    }
                }

                item {
                    FormSection("၄။ ပြဿနာပုံထည့်ရန် (အများဆုံး ၃ ပုံ)") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..3).forEach { slot ->
                                val photo = photos.firstOrNull { it.slot == slot }
                                BookingPhotoSlot(
                                    photo = photo,
                                    slot = slot,
                                    modifier = Modifier.weight(1f),
                                    onPick = {
                                        pendingSlot = slot
                                        if (!previewMode) photoPicker.launch("image/*")
                                    },
                                    onRemove = { photos = photos.filterNot { it.slot == slot } }
                                )
                            }
                        }
                        photoError?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            "ပုံများကို upload မလုပ်ခင် အရွယ်လျှော့ထားသဖြင့် data သုံးစွဲမှု သက်သာပါတယ်။",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                if (showValidation && (!validAddress || !validSchedule)) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = DangerBg,
                            border = BorderStroke(1.dp, Danger.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = Danger,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "ဖြည့်သွင်းရန် အချက်အလက်များ ကျန်ရှိနေပါသည်",
                                        fontWeight = FontWeight.Bold,
                                        color = Danger,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (!validAddress) {
                                        Text(
                                            text = "• အိမ်အရောက် ဝန်ဆောင်မှုအတွက် လာရောက်ရမည့် လိပ်စာ ဖြည့်သွင်းပါ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Danger
                                        )
                                    }
                                    if (serviceMode == "ONSITE" && serviceDate == null) {
                                        Text(
                                            text = "• လာရောက်မည့် ရက်စွဲ ရွေးချယ်ပါ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Danger
                                        )
                                    }
                                    if (serviceMode == "ONSITE" && serviceDate != null && selectedWindowId == null) {
                                        Text(
                                            text = "• လာရောက်မည့် Arrival Window အချိန် ရွေးချယ်ပါ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Danger
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (currentStep == 3) {
                item {
                    BookingReviewCard(
                        problem = problem,
                        deviceCategory = deviceCategory,
                        deviceName = deviceName,
                        serviceMode = serviceMode,
                        serviceAddress = serviceAddress,
                        appointment = appointment,
                        serviceDate = serviceDate,
                        windowLabel = availabilityWindows.firstOrNull { it.windowId == selectedWindowId }?.let {
                            "${it.name} ${(it.startTime ?: "").take(5)}–${(it.endTime ?: "").take(5)}"
                        },
                        preferredAnytime = preferredAnytime,
                        preferredTime = preferredTime,
                        preferenceNote = preferenceNote,
                        urgency = urgency,
                        contactPreference = contactPreference,
                        photoCount = photos.size,
                        outdoorNotice = outdoorNotice,
                        outdoorFee = outdoorFee,
                        onEditStep = { step ->
                            currentStep = step
                            showValidation = false
                        }
                    )
                }
            }
        }

        // Sticky Bottom Action Area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardBg,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = if (showBottomNav) 10.dp + 72.dp else 10.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = {
                            currentStep--
                            showValidation = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !submitting
                    ) {
                        Text("နောက်သို့", fontWeight = FontWeight.Bold)
                    }
                }

                if (currentStep < 3) {
                    Button(
                        onClick = {
                            val valid = when (currentStep) {
                                1 -> validProblem
                                2 -> validAddress && validSchedule
                                else -> true
                            }
                            if (valid) {
                                currentStep++
                                showValidation = false
                            } else {
                                showValidation = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("ဆက်လုပ်မည်", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            onSubmit(
                                ServiceRequestBody(
                                    serviceId = initialService?.id?.takeIf { it > 0 },
                                    serviceName = initialService?.name?.trim()?.ifBlank { null },
                                    displayedPrice = initialService?.let { catalogDisplayedPrice(it) },
                                    priceType = initialService?.priceType,
                                    requestType = requestType,
                                    deviceCategory = deviceCategory.ifBlank { null },
                                    deviceName = deviceName.trim().ifBlank { null },
                                    problem = problem.trim().ifBlank { null },
                                    serviceMode = serviceMode,
                                    serviceAddress = serviceAddress.trim().ifBlank { null },
                                    urgency = urgency,
                                    contactPreference = contactPreference,
                                    appointmentDate = if (serviceMode == "ONSITE") null
                                    else appointment?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                    serviceDate = if (serviceMode == "ONSITE") serviceDate else null,
                                    arrivalWindowId = if (serviceMode == "ONSITE") selectedWindowId else null,
                                    preferredTime = if (serviceMode == "ONSITE" && !preferredAnytime)
                                        preferredTime?.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                                    else null,
                                    preferredAnytime = if (serviceMode == "ONSITE") preferredAnytime else true,
                                    customerPreferenceNote = if (serviceMode == "ONSITE")
                                        preferenceNote.trim().ifBlank { null }
                                    else null,
                                    photos = photos.map {
                                        BookingRequestPhotoBody(
                                            slot = it.slot,
                                            fileName = "customer-booking-${it.slot}.jpg",
                                            dataUrl = it.dataUrl
                                        )
                                    }
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        enabled = canSubmit
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = OnPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("ပို့နေသည်…", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Service Booking တင်မည်", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedServiceBanner(
    service: CatalogService,
    onClear: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = PrimaryLight,
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "ရွေးထားသော ဝန်ဆောင်မှု",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryDark
                )
                Text(
                    service.name.orEmpty().ifBlank { "ဝန်ဆောင်မှု" },
                    fontWeight = FontWeight.Bold,
                    color = TextMain,
                    maxLines = 2
                )
                service.serviceTypeName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            if (onClear != null) {
                TextButton(onClick = onClear) {
                    Text("ဖယ်ရှားမည်", color = Primary)
                }
            }
        }
    }
}

@Composable
private fun BookingStepHeader(
    currentStep: Int,
    onBack: () -> Unit
) {
    val stepTitle = when (currentStep) {
        1 -> "လိုအပ်ချက်"
        2 -> "ပစ္စည်းနှင့် နေရာ"
        else -> "စစ်ဆေးအတည်ပြု"
    }
    val progress = when (currentStep) {
        1 -> 0.33f
        2 -> 0.66f
        else -> 1.0f
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "နောက်သို့",
                            tint = TextMain
                        )
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Service Booking",
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "အဆင့် $currentStep / 3 · $stepTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
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
private fun BookingReviewCard(
    problem: String,
    deviceCategory: String,
    deviceName: String,
    serviceMode: String,
    serviceAddress: String,
    appointment: LocalDateTime?,
    serviceDate: String?,
    windowLabel: String?,
    preferredAnytime: Boolean,
    preferredTime: LocalTime?,
    preferenceNote: String,
    urgency: String,
    contactPreference: String,
    photoCount: Int,
    outdoorNotice: String?,
    outdoorFee: Double?,
    onEditStep: (Int) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Booking အကျဉ်းချုပ်",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain
                )
            }

            // Section 1: Service & Requirement
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "၁။ ဝန်ဆောင်မှု / လိုအပ်ချက်",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDark
                    )
                    TextButton(
                        onClick = { onEditStep(1) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("ပြင်မည် >", color = Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                if (problem.isNotBlank()) {
                    ReviewLine("ပြဿနာ", problem)
                }
            }

            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

            // Section 2: Device & Location
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "၂။ ပစ္စည်းနှင့် နေရာ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDark
                    )
                    TextButton(
                        onClick = { onEditStep(2) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("ပြင်မည် >", color = Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                val deviceLine = listOfNotNull(
                    deviceCategory.ifBlank { null },
                    deviceName.trim().ifBlank { null }
                ).joinToString(" • ").ifBlank { "မသတ်မှတ်ထားပါ" }
                ReviewLine("ပစ္စည်း", deviceLine)

                val modeLabel = when (serviceMode) {
                    "SHOP" -> "ဆိုင်ကို ယူလာမည်"
                    "ONSITE" -> "အိမ်အရောက် ($serviceAddress)"
                    else -> "ဆိုင်နှင့် ညှိနှိုင်းမည်"
                }
                ReviewLine("ဝန်ဆောင်မှုပုံစံ", modeLabel)
                if (serviceMode == "ONSITE") {
                    val transportLine = buildList {
                        outdoorNotice?.takeIf { it.isNotBlank() }?.let { add(it) }
                        outdoorFee?.let { add("Transportation charges — ${formatMmFee(it)}") }
                    }.joinToString("\n")
                    if (transportLine.isNotBlank()) {
                        ReviewLine("Transportation charges", transportLine)
                    }
                }
                ReviewLine(
                    "ရက်ချိန်း",
                    when {
                        serviceMode == "ONSITE" && !serviceDate.isNullOrBlank() -> buildString {
                            append(serviceDate)
                            if (!windowLabel.isNullOrBlank()) append(" · ").append(windowLabel)
                            append(" · ")
                            append(
                                if (preferredAnytime) "Anytime"
                                else preferredTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "—"
                            )
                            if (preferenceNote.isNotBlank()) append("\n").append(preferenceNote)
                        }
                        else -> appointment?.format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a"))
                            ?: "မသတ်မှတ်ထားပါ"
                    }
                )
                val urgencyLabel = when (urgency) {
                    "SOON" -> "အမြန်"
                    "EMERGENCY" -> "အရေးပေါ်"
                    else -> "ပုံမှန်"
                }
                ReviewLine("ဦးစားပေးမှု", urgencyLabel)
                ReviewLine("ဆက်သွယ်ရန်", contactPreference)
                ReviewLine("ဓာတ်ပုံ", if (photoCount > 0) "$photoCount ပုံ ထည့်ထားသည်" else "မထည့်ထားပါ")
            }
        }
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.width(110.dp))
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextMain, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextMain)
            content()
        }
    }
}

@Composable
private fun ChoiceRow(
    choices: List<BookingChoice>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        choices.forEach { choice ->
            FilterChip(
                selected = selected == choice.value,
                onClick = { onSelect(choice.value) },
                label = { Text(choice.label) }
            )
        }
    }
}

@Composable
private fun BookingPhotoSlot(
    photo: BookingPhotoSelection?,
    slot: Int,
    modifier: Modifier,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSoft)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onPick),
        color = SurfaceSoft
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (photo == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddAPhoto,
                        contentDescription = "ဓာတ်ပုံထည့်ရန်",
                        tint = TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "ပုံ $slot",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            } else {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = "Booking photo $slot",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "ပုံ ဖယ်မည်",
                        tint = OnPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnsiteSchedulePicker(
    availabilityDates: List<BookingAvailabilityDate>,
    selectedDate: String?,
    onSelectDate: (String) -> Unit,
    availabilityWindows: List<BookingAvailabilityWindow>,
    selectedWindowId: Int?,
    onSelectWindow: (Int) -> Unit,
    loadingDates: Boolean,
    loadingWindows: Boolean,
    datesError: String?,
    windowsError: String?,
    onRetryDates: () -> Unit,
    onRetryWindows: () -> Unit,
    showValidation: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardBg,
        border = BorderStroke(1.dp, if (showValidation && (selectedDate == null || selectedWindowId == null)) Danger.copy(alpha = 0.6f) else BorderColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(
                        imageVector = Icons.Outlined.Event,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "လာရောက်မည့် ရက်စွဲနှင့် Arrival Window",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                    Text(
                        text = "Technician လာရောက်မည့် ရက်စွဲနှင့် အချိန်အပိုင်းအခြား ရွေးပါ",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            HorizontalDivider(color = BorderColor.copy(alpha = 0.4f))

            // 1. Date Selection
            Text(
                text = "၁။ လာရောက်မည့် ရက်စွဲ ရွေးပါ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryDark
            )

            if (loadingDates && datesError == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                    Text("ရနိုင်သော ရက်စွဲများ ဖတ်နေသည်...", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }

            datesError?.let { err ->
                AvailabilityLoadErrorBanner(message = err, onRetry = onRetryDates)
            }

            if (datesError == null) {
                val openDates = availabilityDates.filter { it.available == true }
                if (!loadingDates && openDates.isEmpty()) {
                    Text(
                        text = "လက်ရှိ ရနိုင်သော ရက် မရှိသေးပါ။ ဆိုင်သို့ တိုက်ရိုက် ဆက်သွယ်ပေးပါ။",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        openDates.forEach { d ->
                            val isSelected = selectedDate == d.date
                            Surface(
                                onClick = { d.date?.let(onSelectDate) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Primary else SurfaceSoft,
                                border = BorderStroke(1.dp, if (isSelected) Primary else BorderColor)
                            ) {
                                Text(
                                    text = formatDateLabel(d.date),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) OnPrimary else TextMain
                                )
                            }
                        }
                    }
                }
            }

            if (showValidation && selectedDate == null && datesError == null) {
                Text("• လာရောက်မည့် ရက်စွဲ ရွေးချယ်ပါ", color = Danger, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }

            // 2. Window Selection
            if (!selectedDate.isNullOrBlank() && datesError == null) {
                HorizontalDivider(color = BorderColor.copy(alpha = 0.4f))

                Text(
                    text = "၂။ Arrival Window အချိန် ရွေးပါ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark
                )

                if (loadingWindows && windowsError == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                        Text("Arrival Window အချိန်များ ဖတ်နေသည်...", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }

                windowsError?.let { err ->
                    AvailabilityLoadErrorBanner(message = err, onRetry = onRetryWindows)
                }

                if (windowsError == null) {
                    if (!loadingWindows && availabilityWindows.isEmpty()) {
                        Text(
                            text = "ဤနေ့အတွက် Arrival Window မရှိသေးပါ။ အခြားရက်စွဲ ရွေးချယ်ပါ",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            availabilityWindows.forEach { w ->
                                val isBookable = w.state == "AVAILABLE" || w.state == "FEW_LEFT"
                                val isSelected = selectedWindowId == w.windowId
                                val windowTitle = when (w.name?.uppercase()) {
                                    "MORNING" -> "မနက်ပိုင်း"
                                    "AFTERNOON" -> "မွန်းလွဲပိုင်း"
                                    "EVENING" -> "ညနေပိုင်း"
                                    else -> w.name.orEmpty().ifBlank { "Window" }
                                }
                                val timeRange = formatWindowTimeRange(w.startTime, w.endTime)

                                Surface(
                                    onClick = { if (isBookable && w.windowId != null) onSelectWindow(w.windowId) },
                                    enabled = isBookable,
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        isSelected -> PrimaryLight
                                        !isBookable -> SurfaceSoft.copy(alpha = 0.6f)
                                        else -> SurfaceSoft
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            isSelected -> Primary
                                            !isBookable -> BorderColor.copy(alpha = 0.3f)
                                            else -> BorderColor
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 11.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "$windowTitle ($timeRange)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) PrimaryDark else if (!isBookable) TextMuted else TextMain
                                            )
                                        }

                                        val (statusText, statusFg, statusBg) = when (w.state) {
                                            "FEW_LEFT" -> Triple("ကျန် ${w.remaining} နေရာ", Warning, WarningBg)
                                            "FULL" -> Triple("ပြည့်ပါပြီ", Danger, DangerBg)
                                            "UNAVAILABLE" -> Triple("မရနိုင်ပါ", TextMuted, SurfaceSoft)
                                            else -> Triple("✓ ရနိုင်သည်", Success, SuccessBg)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = statusBg
                                        ) {
                                            Text(
                                                text = statusText,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = statusFg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showValidation && selectedWindowId == null && windowsError == null) {
                    Text("• လာရောက်မည့် Arrival Window အချိန် ရွေးချယ်ပါ", color = Danger, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatDateLabel(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "—"
    return runCatching {
        val date = LocalDate.parse(dateStr)
        val formatter = DateTimeFormatter.ofPattern("dd MMM (EEE)", Locale.ENGLISH)
        date.format(formatter)
    }.getOrDefault(dateStr)
}

private fun formatWindowTimeRange(startTime: String?, endTime: String?): String {
    val start = parseWindowTime(startTime)
    val end = parseWindowTime(endTime)
    if (start != null && end != null) {
        val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        return "${start.format(fmt)} – ${end.format(fmt)}"
    }
    return listOfNotNull(startTime?.take(5), endTime?.take(5)).joinToString(" – ")
}

private fun parseWindowTime(raw: String?): LocalTime? {
    if (raw.isNullOrBlank() || raw.length < 5) return null
    return runCatching {
        val parts = raw.split(':')
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }.getOrNull()
}

private fun showAppointmentPicker(context: Context, onSelected: (LocalDateTime) -> Unit) {
    val now = Calendar.getInstance()
    val dateDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute -> onSelected(LocalDateTime.of(year, month + 1, day, hour, minute)) },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                false
            ).show()
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH)
    )
    dateDialog.datePicker.minDate = System.currentTimeMillis() - 60_000
    dateDialog.show()
}

private fun showPreferredTimePicker(context: Context, onSelected: (LocalTime) -> Unit) {
    val now = Calendar.getInstance()
    TimePickerDialog(
        context,
        { _, hour, minute -> onSelected(LocalTime.of(hour, minute)) },
        now.get(Calendar.HOUR_OF_DAY),
        now.get(Calendar.MINUTE),
        false
    ).show()
}

private suspend fun encodeBookingPhoto(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Image cannot be opened")
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalArgumentException("Unsupported image")
    }
    var sample = 1
    while (bounds.outWidth / sample > 3200 || bounds.outHeight / sample > 3200) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val source = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: throw IllegalArgumentException("Image cannot be decoded")
    val maxSide = 1600
    val scale = minOf(1.0, maxSide.toDouble() / maxOf(source.width, source.height))
    val target = if (scale < 1.0) {
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    } else source
    try {
        val output = ByteArrayOutputStream()
        check(target.compress(Bitmap.CompressFormat.JPEG, 82, output)) { "Image compression failed" }
        "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    } finally {
        if (target !== source) target.recycle()
        source.recycle()
    }
}

@Composable
private fun AvailabilityLoadErrorBanner(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF1F2),
        border = BorderStroke(1.dp, Color(0xFFFECDD3))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9F1239)
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("ထပ်ကြိုးစားမည်", fontWeight = FontWeight.Bold, color = Color(0xFF9F1239))
            }
        }
    }
}

private fun availabilityHttpMessage(code: Int): String = when (code) {
    in 500..599 -> "ဆာဗာချို့ယွင်းနေပါသည် ($code)။ ခဏနေမှ ထပ်ကြိုးစားပါ။"
    in 400..499 -> "ရက်/window စာရင်း တောင်းဆိုမှု မအောင်မြင်ပါ ($code)။"
    else -> "ရက်/window စာရင်း မရရှိပါ ($code)။"
}

private fun availabilityErrorMessage(error: Throwable): String {
    val network = error is java.io.IOException
            || error.cause is java.io.IOException
            || error.message?.contains("Unable to resolve host", ignoreCase = true) == true
            || error.message?.contains("failed to connect", ignoreCase = true) == true
            || error.message?.contains("timeout", ignoreCase = true) == true
    return when {
        network -> "အင်တာနက်ချိတ်ဆက်မှု ပြတ်နေပါသည်။ ချိတ်ဆက်ပြီး ထပ်ကြိုးစားပါ။"
        !error.message.isNullOrBlank() -> error.message!!
        else -> "ရက်/window စာရင်း ဖတ်မရပါ။ ထပ်ကြိုးစားပါ။"
    }
}

private fun sampleAvailabilityDates(): List<BookingAvailabilityDate> {
    val today = java.time.LocalDate.now()
    return (0..6).map { offset ->
        val d = today.plusDays(offset.toLong())
        val closedSunday = d.dayOfWeek.value == 7
        BookingAvailabilityDate(
            date = d.toString(),
            available = !closedSunday,
            reason = if (closedSunday) "ဤနေ့တွင် ဆိုင်ပိတ်ထားပါသည်" else null,
            openPeriods = if (closedSunday) emptyList() else listOf(
                BookingAvailabilityPeriod(opensAt = "09:00:00", closesAt = "12:00:00"),
                BookingAvailabilityPeriod(opensAt = "13:00:00", closesAt = "18:00:00")
            )
        )
    }
}

private fun parseAvailabilityTime(raw: String?): LocalTime? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    return runCatching {
        when {
            trimmed.length >= 8 && trimmed[2] == ':' -> LocalTime.parse(trimmed.take(8))
            else -> LocalTime.parse(trimmed)
        }
    }.getOrNull()
}

private fun formatOpenPeriodLabel(opensAt: String?, closesAt: String?): String {
    val a = (opensAt ?: "").take(5)
    val b = (closesAt ?: "").take(5)
    return when {
        a.isNotBlank() && b.isNotBlank() -> "$a–$b"
        a.isNotBlank() -> a
        else -> "—"
    }
}

private fun sampleAvailabilityWindows(): List<BookingAvailabilityWindow> = listOf(
    BookingAvailabilityWindow(
        windowId = 1,
        name = "Morning",
        startTime = "09:00:00",
        endTime = "12:00:00",
        capacity = 2,
        booked = 0,
        remaining = 2,
        state = "AVAILABLE"
    ),
    BookingAvailabilityWindow(
        windowId = 2,
        name = "Afternoon",
        startTime = "13:00:00",
        endTime = "16:00:00",
        capacity = 2,
        booked = 1,
        remaining = 1,
        state = "FEW_LEFT"
    ),
    BookingAvailabilityWindow(
        windowId = 3,
        name = "Evening",
        startTime = "16:00:00",
        endTime = "18:00:00",
        capacity = 1,
        booked = 1,
        remaining = 0,
        state = "FULL"
    )
)

private fun previewTomorrow(): String {
    var d = java.time.LocalDate.now().plusDays(1)
    while (d.dayOfWeek.value == 7) {
        d = d.plusDays(1)
    }
    return d.toString()
}

@Preview(name = "1 Step 1 Requirements", showBackground = true, showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerServiceBookingStep1Preview() {
    AppTheme {
        Surface {
            CustomerServiceBookingForm(
                defaultAddress = "ရန်ကုန်မြို့၊ လှိုင်မြို့နယ်၊ အမှတ် ၁၂",
                submitting = false,
                onSubmit = {},
                initialStep = 1
            )
        }
    }
}

@Preview(name = "2 Step 2 Device & Location", showBackground = true, showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerServiceBookingStep2Preview() {
    AppTheme {
        Surface {
            CustomerServiceBookingForm(
                defaultAddress = "ရန်ကုန်မြို့၊ လှိုင်မြို့နယ်၊ အမှတ် ၁၂",
                submitting = false,
                onSubmit = {},
                initialStep = 2
            )
        }
    }
}

@Preview(name = "2b ONSITE date & windows", showBackground = true, showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerServiceBookingOnsiteWindowsPreview() {
    AppTheme {
        Surface {
            CustomerServiceBookingForm(
                defaultAddress = "ရန်ကုန်မြို့၊ လှိုင်မြို့နယ်၊ အမှတ် ၁၂",
                submitting = false,
                onSubmit = {},
                initialStep = 2,
                initialServiceMode = "ONSITE",
                initialProblem = "Laptop ပူပြီး ပိတ်သွားသည်",
                initialServiceDate = previewTomorrow(),
                initialWindowId = 1,
                initialPreferredAnytime = true,
                initialPreferenceNote = "မနက်ပိုင်း နှောင်းပိုင်း ဆက်သွယ်ပါ"
            )
        }
    }
}

@Preview(name = "2c SHOP drop-off", showBackground = true, showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerServiceBookingShopPreview() {
    AppTheme {
        Surface {
            CustomerServiceBookingForm(
                defaultAddress = "ရန်ကုန်မြို့၊ လှိုင်မြို့နယ်၊ အမှတ် ၁၂",
                submitting = false,
                onSubmit = {},
                initialStep = 2,
                initialServiceMode = "SHOP",
                initialProblem = "Printer စက္ကူမစား"
            )
        }
    }
}

@Preview(name = "2d SHOP schedule slots", showBackground = true, showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerServiceBookingShopSchedulePreview() {
    AppTheme {
        Surface {
            CustomerServiceBookingForm(
                defaultAddress = "ရန်ကုန်မြို့၊ လှိုင်မြို့နယ်၊ အမှတ် ၁၂",
                submitting = false,
                onSubmit = {},
                initialStep = 2,
                initialServiceMode = "SHOP",
                initialProblem = "Printer စက္ကူမစား",
                initialShopScheduleOpen = true,
                initialShopDropOffDate = previewTomorrow()
            )
        }
    }
}

@Preview(name = "3 Step 3 Review & Confirm", showBackground = true, showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerServiceBookingStep3Preview() {
    AppTheme {
        Surface {
            CustomerServiceBookingForm(
                defaultAddress = "ရန်ကုန်မြို့၊ လှိုင်မြို့နယ်၊ အမှတ် ၁၂",
                submitting = false,
                onSubmit = {},
                initialStep = 3
            )
        }
    }
}

@Preview(name = "3b ONSITE review", showBackground = true, showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerServiceBookingOnsiteReviewPreview() {
    AppTheme {
        Surface {
            CustomerServiceBookingForm(
                defaultAddress = "ရန်ကုန်မြို့၊ လှိုင်မြို့နယ်၊ အမှတ် ၁၂",
                submitting = false,
                onSubmit = {},
                initialStep = 3,
                initialServiceMode = "ONSITE",
                initialProblem = "Laptop ပူပြီး ပိတ်သွားသည်",
                initialServiceDate = previewTomorrow(),
                initialWindowId = 1,
                initialPreferredAnytime = false,
                initialPreferenceNote = "မနက် ၁၀ နာရီခန့်"
            )
        }
    }
}
