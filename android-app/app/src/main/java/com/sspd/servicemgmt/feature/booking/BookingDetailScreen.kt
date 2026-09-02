package com.sspd.servicemgmt.feature.booking

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.BookingItemDTO
import com.sspd.servicemgmt.core.network.BookingItemPhotoDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.network.displayNo
import com.sspd.servicemgmt.core.ui.component.BookingItemPhotoThumb
import com.sspd.servicemgmt.core.ui.component.BookingPhotoViewerDialog
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.util.ImageCodec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    onBack: () -> Unit,
    onJobCreated: () -> Unit = {},
    onEdit: () -> Unit = {},
    onPrint: () -> Unit = {},
    onJobClick: (Int) -> Unit = {}
) {
    val vm: BookingDetailViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showReceiveSheet by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmTitle by remember { mutableStateOf("") }
    var confirmText by remember { mutableStateOf("") }

    LaunchedEffect(state.actionSuccess) {
        state.actionSuccess?.let { snackbar.showSnackbar(it); vm.clearActionSuccess() }
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let { snackbar.showSnackbar(it); vm.clearActionError() }
    }

    confirmAction?.let {
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(confirmTitle, fontWeight = FontWeight.ExtraBold) },
            text = { Text(confirmText) },
            confirmButton = {
                Button(onClick = { it(); confirmAction = null }, enabled = !state.actionLoading) {
                    Text("ဆက်လုပ်မည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("မလုပ်တော့ပါ") } }
        )
    }

    if (showReceiveSheet) {
        val booking = state.booking
        ReceiveItemsSheet(
            complaintNote = booking?.complaintNote ?: booking?.problemDesc,
            loading = state.actionLoading,
            onDismiss = { showReceiveSheet = false },
            onSubmit = { items ->
                vm.receiveItems(items) {
                    showReceiveSheet = false
                    onPrint()
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.booking?.displayNo() ?: "Booking အသေးစိတ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = OnPrimary) } },
                actions = {
                    val b = state.booking
                    val canEdit = b != null && b.status?.uppercase() !in listOf("CANCELED", "CANCELLED") && b.fullyConverted != true
                    if (canEdit) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, null, tint = Color.White) }
                    if (!b?.items.isNullOrEmpty()) IconButton(onClick = onPrint) { Icon(Icons.Outlined.Print, null, tint = Color.White) }
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = OnPrimary)
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }
        val booking = state.booking ?: run {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("ဒေတာ မတွေ့ပါ", color = TextMuted) }
            return@Scaffold
        }

        val status = booking.status?.uppercase().orEmpty()
        val linkedJobs = booking.linkedJobs.orEmpty()
        val items = booking.items.orEmpty()
        val canEdit = status !in listOf("CANCELED", "CANCELLED") && booking.fullyConverted != true

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(booking.displayNo(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                            BookingStatusBadge(booking.status)
                        }
                        Text("ချိန်းဆိုချိန်: ${formatDateTime(booking.appointmentDate ?: booking.bookingDate)}", fontSize = 12.sp, color = TextMuted)
                        Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("နောက်လုပ်ရန်", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted)
                                Text(bookingNextAction(booking), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
                            }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow(Icons.Outlined.Person, "ဖောက်သည်", booking.customerName ?: "—")
                        booking.customerPhone?.takeIf { it.isNotBlank() }?.let { InfoRow(Icons.Outlined.Phone, "ဖုန်း", it) }
                        InfoRow(Icons.Outlined.ReportProblem, "တိုင်ပင်ချက်", booking.complaintNote ?: booking.problemDesc ?: "—")
                        booking.remark?.takeIf { it.isNotBlank() }?.let { InfoRow(Icons.Outlined.Notes, "မှတ်ချက်", it) }
                    }
                }
            }

            item {
                Text("လုပ်ဆောင်ချက်များ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (status == "CONFIRMED" && linkedJobs.isEmpty()) {
                        ActionButton("Outdoor Job ပြောင်း", Icons.Outlined.Map, Violet, state.actionLoading) {
                            confirmTitle = "Outdoor Job ပြောင်းမည်လား?"
                            confirmText = "OUTDOOR ServiceJob တစ်ခု ဖန်တီးပါမည်။"
                            confirmAction = { vm.convertOutdoor(); onJobCreated() }
                        }
                        ActionButton("ပစ္စည်းလက်ခံ", Icons.Outlined.Inventory2, Color(0xFFF59E0B), state.actionLoading) {
                            showReceiveSheet = true
                        }
                    }
                    if (status == "ARRIVED" && (booking.unconvertedItemCount ?: 0L) > 0) {
                        ActionButton("Indoor Job ပြောင်း", Icons.Outlined.Home, Success, state.actionLoading) {
                            confirmTitle = "Indoor Jobs ပြောင်းမည်လား?"
                            confirmText = "မပြောင်းရသေးသော ပစ္စည်းတစ်ခုစီအတွက် Job တစ်ခုစီ ဖန်တီးပါမည်။"
                            confirmAction = { vm.convertIndoor(); onJobCreated() }
                        }
                    }
                    if (items.isNotEmpty()) {
                        OutlinedButton(onClick = onPrint, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Print, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("လက်ခံ Voucher", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (canEdit && linkedJobs.isEmpty()) {
                        OutlinedButton(
                            onClick = {
                                confirmTitle = "Booking ပယ်ဖျက်မည်လား?"
                                confirmText = "Job ပြောင်းပြီးပါက Cancel မရပါ။"
                                confirmAction = { vm.cancelBooking() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                            enabled = !state.actionLoading
                        ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            item { Text("လက်ခံပစ္စည်း (${items.size})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted) }
            if (items.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        Text("ဆိုင်အပ်ပစ္စည်း မရှိသေးပါ", Modifier.padding(20.dp), color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(items) { item ->
                    BookingItemCard(
                        item = item,
                        complaintFallback = booking.complaintNote,
                        canRemove = status == "ARRIVED" && item.convertedJobId == null,
                        onRemove = { item.id?.let { id ->
                            confirmTitle = "ပစ္စည်းဖယ်မည်လား?"
                            confirmText = item.itemName ?: ""
                            confirmAction = { vm.removeItem(id) }
                        } }
                    )
                }
            }

            item { Text("Linked Jobs (${linkedJobs.size})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted) }
            if (linkedJobs.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        Text("Service Job မပြောင်းရသေးပါ", Modifier.padding(20.dp), color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(linkedJobs) { job -> LinkedJobRow(job, onJobClick) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, loading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = color), shape = RoundedCornerShape(12.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(15.dp))
        Column {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
        }
    }
}

@Composable
private fun BookingItemCard(item: BookingItemDTO, complaintFallback: String?, canRemove: Boolean, onRemove: () -> Unit) {
    var viewerPhoto by remember { mutableStateOf<BookingItemPhotoDTO?>(null) }

    if (viewerPhoto != null) {
        BookingPhotoViewerDialog(photo = viewerPhoto!!, onDismiss = { viewerPhoto = null })
    }

    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.itemName ?: "—", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text(listOfNotNull(item.deviceType, item.serialNo?.let { "S/N $it" }).joinToString(" · "), fontSize = 11.sp, color = TextMuted)
                }
                if (item.convertedJobId != null) {
                    Surface(color = SuccessBg, shape = RoundedCornerShape(12.dp)) {
                        Text("Job #${item.convertedJobId}", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = Success, fontWeight = FontWeight.Bold)
                    }
                } else if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Delete, null, tint = Danger, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item.noticed?.takeIf { it.isNotBlank() }?.let { Text("Noticed: $it", fontSize = 12.sp, color = Warning) }
            Text(item.problemDesc ?: complaintFallback ?: "ပြဿနာဖော်ပြချက်မရှိ", fontSize = 12.sp, color = TextMain)
            item.photos?.takeIf { it.isNotEmpty() }?.let { photos ->
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEach { photo ->
                        BookingItemPhotoThumb(photo = photo, onClick = { viewerPhoto = photo })
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedJobRow(job: ServiceJobDTO, onJobClick: (Int) -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth().clickable { job.id?.let(onJobClick) }
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(job.jobNo ?: "—", fontWeight = FontWeight.ExtraBold, color = Violet, fontSize = 13.sp)
                Text("${job.serviceMode ?: ""} · ${job.itemName ?: "—"}", fontSize = 11.sp, color = TextMuted)
            }
            Text(job.status ?: "—", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (job.status == "CANCELLED") Danger else Primary)
        }
    }
}

private data class ReceiveItemDraft(
    var itemName: String = "",
    var deviceType: String = "",
    var serialNo: String = "",
    var color: String = "",
    var accessories: String = "",
    var itemCondition: String = "",
    var noticed: String = "",
    var problemDesc: String = "",
    var photos: List<BookingItemPhotoDTO> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveItemsSheet(complaintNote: String?, loading: Boolean, onDismiss: () -> Unit, onSubmit: (List<BookingItemDTO>) -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(listOf(ReceiveItemDraft())) }
    var photoTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = photoTarget ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
            else @Suppress("DEPRECATION") android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val dataUrl = ImageCodec.bitmapToDataUri(bmp)
            items = items.toMutableList().also { list ->
                val draft = list[target.first]
                val photos = draft.photos.toMutableList()
                photos.removeAll { it.slot == target.second }
                photos.add(BookingItemPhotoDTO(slot = target.second, dataUrl = dataUrl, contentType = "image/jpeg"))
                list[target.first] = draft.copy(photos = photos.sortedBy { it.slot })
            }
        }
        photoTarget = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ReceiveItemsFormContent(
            items = items,
            onItemsChange = { items = it },
            complaintNote = complaintNote,
            loading = loading,
            onPhotoClick = { index, slot -> photoTarget = index to slot; galleryLauncher.launch("image/*") },
            onSubmit = {
                onSubmit(items.filter { it.itemName.isNotBlank() }.map { d ->
                    BookingItemDTO(
                        itemName = d.itemName.trim(),
                        deviceType = d.deviceType.trim().ifBlank { null },
                        serialNo = d.serialNo.trim().ifBlank { null },
                        color = d.color.trim().ifBlank { null },
                        accessories = d.accessories.trim().ifBlank { null },
                        itemCondition = d.itemCondition.trim().ifBlank { null },
                        noticed = d.noticed.trim().ifBlank { null },
                        problemDesc = d.problemDesc.trim().ifBlank { complaintNote },
                        photos = d.photos
                    )
                })
            },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun ReceiveItemsFormContent(
    items: List<ReceiveItemDraft>,
    onItemsChange: (List<ReceiveItemDraft>) -> Unit,
    complaintNote: String?,
    loading: Boolean,
    onPhotoClick: (itemIndex: Int, slot: Int) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ဆိုင်အပ်ပစ္စည်း လက်ခံရန်", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Surface(color = PrimaryLight, shape = RoundedCornerShape(10.dp)) {
            Text(
                "ပစ္စည်းတစ်ခုစီသည် Indoor convert လုပ်ချိန်တွင် Service Job တစ်ခုစီဖြစ်လာပါမည်။",
                Modifier.padding(12.dp),
                fontSize = 12.sp,
                color = Primary,
            )
        }
        complaintNote?.takeIf { it.isNotBlank() }?.let { note ->
            Surface(color = Color(0xFFFFF8E7), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("တိုင်ပင်ချက် (Booking)", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted)
                    Text(note, fontSize = 12.sp, color = TextMain)
                }
            }
        }
        items.forEachIndexed { index, draft ->
            Card(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ပစ္စည်း ${index + 1}", fontWeight = FontWeight.Bold)
                        if (items.size > 1) {
                            IconButton(onClick = {
                                onItemsChange(items.toMutableList().also { it.removeAt(index) })
                            }) {
                                Icon(Icons.Outlined.Delete, null, tint = Danger)
                            }
                        }
                    }
                    OutlinedTextField(
                        draft.itemName,
                        { v -> onItemsChange(items.toMutableList().also { it[index] = it[index].copy(itemName = v) }) },
                        label = { Text("ပစ္စည်းအမည် *") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        draft.deviceType,
                        { v -> onItemsChange(items.toMutableList().also { it[index] = it[index].copy(deviceType = v) }) },
                        label = { Text("Device Type") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        draft.serialNo,
                        { v -> onItemsChange(items.toMutableList().also { it[index] = it[index].copy(serialNo = v) }) },
                        label = { Text("Serial No") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        draft.problemDesc,
                        { v -> onItemsChange(items.toMutableList().also { it[index] = it[index].copy(problemDesc = v) }) },
                        label = { Text("Problem") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { slot ->
                            val slotPhoto = draft.photos.firstOrNull { it.slot == slot }
                            if (slotPhoto != null) {
                                BookingItemPhotoThumb(photo = slotPhoto, size = 64.dp)
                            }
                            OutlinedButton(onClick = { onPhotoClick(index, slot) }) {
                                Icon(
                                    if (slotPhoto != null) Icons.Outlined.CheckCircle else Icons.Outlined.PhotoCamera,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("ပုံ $slot", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { onItemsChange(items + ReceiveItemDraft()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("နောက်ထပ်ပစ္စည်း")
        }
        Button(
            onClick = onSubmit,
            enabled = !loading && items.any { it.itemName.isNotBlank() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("ပစ္စည်းလက်ခံမည်", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(name = "Receive — empty item", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun ReceiveItemsEmptyPreview() {
    AppTheme {
        Surface(color = ScreenBg) {
            ReceiveItemsFormContent(
                items = listOf(ReceiveItemDraft()),
                onItemsChange = {},
                complaintNote = "Screen flickering after update",
                loading = false,
                onPhotoClick = { _, _ -> },
                onSubmit = {},
            )
        }
    }
}

@Preview(name = "Receive — two items filled", showBackground = true, widthDp = 390, heightDp = 920)
@Composable
private fun ReceiveItemsFilledPreview() {
    AppTheme {
        Surface(color = ScreenBg) {
            ReceiveItemsFormContent(
                items = listOf(
                    ReceiveItemDraft(
                        itemName = "iPhone 14 Pro",
                        deviceType = "Smartphone",
                        serialNo = "SN-88421",
                        problemDesc = "Battery drains fast, screen has lines",
                    ),
                    ReceiveItemDraft(
                        itemName = "MacBook Air M2",
                        deviceType = "Laptop",
                        serialNo = "C02XYZ123",
                        problemDesc = "Keyboard key stuck",
                        photos = listOf(BookingItemPhotoDTO(slot = 1, contentType = "image/jpeg")),
                    ),
                ),
                onItemsChange = {},
                complaintNote = null,
                loading = false,
                onPhotoClick = { _, _ -> },
                onSubmit = {},
            )
        }
    }
}

@Preview(name = "Receive — submitting", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
private fun ReceiveItemsLoadingPreview() {
    AppTheme {
        Surface(color = ScreenBg) {
            ReceiveItemsFormContent(
                items = listOf(
                    ReceiveItemDraft(itemName = "Samsung A54", deviceType = "Smartphone", serialNo = "R58N001"),
                ),
                onItemsChange = {},
                complaintNote = "Cannot charge",
                loading = true,
                onPhotoClick = { _, _ -> },
                onSubmit = {},
            )
        }
    }
}

private fun formatDateTime(value: String?): String =
    value?.take(16)?.replace("T", "  ") ?: "—"
