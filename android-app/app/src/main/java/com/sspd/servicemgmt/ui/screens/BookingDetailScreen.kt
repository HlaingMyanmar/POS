package com.sspd.servicemgmt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.api.BookingDetailItemDTO
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.BookingDetailViewModel
import com.sspd.servicemgmt.utils.ImageCodec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(onBack: () -> Unit, onJobCreated: () -> Unit = {}, onEdit: () -> Unit = {}, onPrint: () -> Unit = {}) {
    val vm: BookingDetailViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.actionSuccess) {
        state.actionSuccess?.let { snackbar.showSnackbar(it); vm.clearActionSuccess() }
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let { snackbar.showSnackbar(it); vm.clearActionError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.booking?.invoiceNo ?: "လက်ခံမှု အသေးစိတ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) }
                },
                actions = {
                    val canEdit = state.booking?.status?.uppercase() !in listOf("CONVERTED", "COMPLETED", "CANCELLED")
                    if (canEdit) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "ပြင်ရန်", tint = Color.White) }
                    }
                    IconButton(onClick = onPrint) { Icon(Icons.Outlined.Print, "ပရင့်", tint = Color.White) }
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White) }
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

        val booking = state.booking ?: run {
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
            // ── Header ────────────────────────────────────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(booking.invoiceNo ?: "#${booking.id}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                            BookingDetailStatusBadge(booking.status)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(booking.bookingDate?.take(16)?.replace("T", "  ") ?: "—", fontSize = 12.sp, color = TextMuted)
                        if (!booking.shelfLocation.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(color = VioletBg, shape = RoundedCornerShape(6.dp)) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.LocationOn, null, tint = Violet, modifier = Modifier.size(12.dp))
                                    Text("ကန့်: ${booking.shelfLocation}", fontSize = 11.sp, color = Violet, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ── Info ──────────────────────────────────────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BookingInfoRow(Icons.Outlined.Person, "ဖောက်သည်",  booking.customerName ?: "—")
                        if (!booking.customerPhone.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            BookingInfoRow(Icons.Outlined.Phone, "ဖုန်း",    booking.customerPhone)
                        }
                        if (!booking.staffName.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            BookingInfoRow(Icons.Outlined.Badge, "ဝန်ထမ်း", booking.staffName)
                        }
                        if (!booking.appointmentDate.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            BookingInfoRow(Icons.Outlined.CalendarToday, "ချိန်းဆိုသည့်ရက်", booking.appointmentDate.take(16).replace("T", "  "))
                        }
                        if (booking.totalAmount != null) {
                            HorizontalDivider(color = BorderColor)
                            BookingInfoRow(Icons.Outlined.Payments, "ခန့်မှန်းကိုန်", "${String.format("%,.0f", booking.totalAmount)} Ks")
                        }
                        if ((booking.depositAmount ?: 0.0) > 0) {
                            HorizontalDivider(color = BorderColor)
                            BookingInfoRow(Icons.Outlined.AccountBalanceWallet, "လက်ခံငွေ", "${String.format("%,.0f", booking.depositAmount)} Ks")
                        }
                        if (!booking.paymentMethodName.isNullOrBlank()) {
                            HorizontalDivider(color = BorderColor)
                            BookingInfoRow(Icons.Outlined.Payments, "ငွေပေးချေနည်း", booking.paymentMethodName)
                        }
                    }
                }
            }

            // ── Devices ───────────────────────────────────────────────────────
            // Prefer devices[] list; fall back to legacy single-device fields
            val deviceList = booking.devices?.takeIf { it.isNotEmpty() }
            val hasLegacy  = !booking.brand.isNullOrBlank() || !booking.model.isNullOrBlank()

            if (deviceList != null) {
                item {
                    Text(
                        "ပစ္စည်းများ (${deviceList.size} ခု)",
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        color = TextMuted, letterSpacing = 0.5.sp
                    )
                }
                items(deviceList) { device ->
                    BookingDeviceCard(
                        deviceType       = device.deviceType,
                        brand            = device.brand,
                        model            = device.model,
                        serialNumber     = device.serialNumber,
                        color            = device.color,
                        accessories      = device.accessories,
                        problemDesc      = device.problemDesc,
                        deviceConditions = device.deviceConditions
                    )
                }
            } else if (hasLegacy) {
                item {
                    Text("ပစ္စည်း အချက်အလက်", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                }
                item {
                    BookingDeviceCard(
                        deviceType       = booking.deviceType,
                        brand            = booking.brand,
                        model            = booking.model,
                        serialNumber     = booking.serialNumber,
                        color            = booking.color,
                        accessories      = booking.accessories,
                        problemDesc      = booking.problemDesc,
                        deviceConditions = null
                    )
                }
            }

            // ── Services ──────────────────────────────────────────────────────
            if (!booking.details.isNullOrEmpty()) {
                item {
                    Text("ဝန်ဆောင်မှုများ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                }
                items(booking.details) { d -> BookingServiceRow(d) }
            }

            if (!booking.deviceInfos.isNullOrEmpty()) {
                item {
                    Text("ပစ္စည်းအခြေအနေ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                }
                item {
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            booking.deviceInfos.forEach { info ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(info.name ?: "-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        listOfNotNull(info.status, info.notice?.takeIf { it.isNotBlank() }).joinToString(" · "),
                                        fontSize = 12.sp, color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!booking.attachments.isNullOrEmpty() || !booking.signatureData.isNullOrBlank()) {
                item {
                    Text("လက်ခံဓာတ်ပုံ / လက်မှတ်", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                }
            }
            if (!booking.attachments.isNullOrEmpty()) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        booking.attachments.forEach { att ->
                            IntakePhotoThumb(
                                dataUrl = att.dataUrl,
                                label = att.attachmentType?.replace("INTAKE_PHOTO_DEVICE_", "ပစ္စည်း ") ?: "ဓာတ်ပုံ",
                                fileName = att.fileName
                            )
                        }
                    }
                }
            }
            if (!booking.signatureData.isNullOrBlank()) {
                item {
                    val sig = remember(booking.signatureData) { ImageCodec.decodeDataUri(booking.signatureData!!) }
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("ဖောက်သည်လက်မှတ်", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted)
                            Spacer(Modifier.height(8.dp))
                            if (sig != null) {
                                Image(
                                    bitmap = sig.asImageBitmap(),
                                    contentDescription = "လက်မှတ်",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(96.dp)
                                )
                            } else {
                                Text("လက်မှတ် ပြ၍မရပါ", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            // ── Status buttons ────────────────────────────────────────────────
            val canUpdateStatus = booking.status?.uppercase() !in listOf("CANCELLED", "COMPLETED", "Converted".uppercase())
            if (canUpdateStatus) {
                item {
                    Text("အဆင့် ပြောင်းရန်", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.5.sp)
                }
                item {
                    val statuses = listOf("Pending" to "စောင့်ဆိုင်း", "Confirmed" to "အတည်ပြု", "IN_STORAGE" to "သိမ်းထားပြီး", "Cancelled" to "ပယ်ဖျက်")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        statuses.forEach { (key, label) ->
                            val isCurrent = booking.status.equals(key, ignoreCase = true)
                            FilterChip(
                                selected = isCurrent,
                                onClick  = { if (!isCurrent && !state.actionLoading) vm.updateStatus(key) },
                                label    = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Convert to Job ────────────────────────────────────────────────
            val canConvert = booking.status?.uppercase() in listOf("PENDING", "CONFIRMED", "IN_STORAGE")
            if (canConvert) {
                item {
                    Button(
                        onClick  = { vm.convertToJob { onJobCreated() } },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Violet),
                        enabled  = !state.actionLoading
                    ) {
                        if (state.actionLoading)
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Outlined.Build, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ဝန်ဆောင်မှုအလုပ် ပြောင်းရန်", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            if (!booking.remark.isNullOrBlank()) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("မှတ်ချက်", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Warning, letterSpacing = 0.5.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(booking.remark, fontSize = 13.sp, color = TextMain)
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
private fun BookingDetailStatusBadge(status: String?) {
    val (bg, color, label) = when (status?.uppercase()) {
        "PENDING"    -> Triple(WarningBg, Warning, "စောင့်ဆိုင်း")
        "CONFIRMED"  -> Triple(VioletBg,  Violet,  "အတည်ပြု")
        "IN_STORAGE" -> Triple(VioletBg,  Violet,  "သိမ်းထားပြီး")
        "CONVERTED"  -> Triple(SuccessBg, Success, "အလုပ်ပြောင်းပြီး")
        "COMPLETED"  -> Triple(SuccessBg, Success, "ပြီးဆုံး")
        "CANCELLED"  -> Triple(DangerBg,  Danger,  "ပယ်ဖျက်")
        else         -> Triple(BorderColor, TextMuted, status ?: "—")
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun BookingInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(15.dp))
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BookingDeviceCard(
    deviceType: String?, brand: String?, model: String?,
    serialNumber: String?, color: String?, accessories: String?,
    problemDesc: String?, deviceConditions: String?
) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Smartphone, null, tint = Violet, modifier = Modifier.size(16.dp))
                Text(listOfNotNull(brand, model).joinToString(" ").ifBlank { "—" }, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                if (!deviceType.isNullOrBlank())
                    Surface(color = VioletBg, shape = RoundedCornerShape(4.dp)) {
                        Text(deviceType, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 9.sp, color = Violet, fontWeight = FontWeight.Bold)
                    }
            }
            if (!serialNumber.isNullOrBlank()) Text("S/N: $serialNumber", fontSize = 11.sp, color = TextMuted)
            if (!color.isNullOrBlank())        Text("အရောင်: $color", fontSize = 11.sp, color = TextMuted)
            if (!accessories.isNullOrBlank())  Text("ပါပစ္စည်း: $accessories", fontSize = 11.sp, color = TextMuted)
            if (!problemDesc.isNullOrBlank()) {
                HorizontalDivider(color = BorderColor)
                Text("ပြဿနာ: $problemDesc", fontSize = 12.sp, color = TextMain)
            }
            if (!deviceConditions.isNullOrBlank()) {
                Text("အခြေအနေ: $deviceConditions", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun IntakePhotoThumb(dataUrl: String?, label: String, fileName: String?) {
    val bmp = remember(dataUrl) { dataUrl?.let { ImageCodec.decodeDataUri(it) } } ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = fileName ?: "လက်ခံဓာတ်ပုံ",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(112.dp)
            )
        }
        Text(label, fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun BookingServiceRow(detail: BookingDetailItemDTO) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(detail.serviceName ?: "—", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Text("× ${detail.qty ?: 1}", fontSize = 11.sp, color = TextMuted)
            }
            Text("${String.format("%,.0f", detail.subtotal ?: 0.0)} Ks", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
        }
    }
}
