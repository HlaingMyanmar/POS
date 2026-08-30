package com.sspd.servicemgmt.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppSearchField

import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingListScreen(
    onBack:          () -> Unit,
    onBookingClick:  (Int) -> Unit = {},
    onNewBooking:    () -> Unit    = {},
    onEditBooking:   (Int) -> Unit = {}
) {
    val vm: BookingListViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) { vm.load(); delay(30_000) }
    }

    // ── Date pickers ─────────────────────────────────────────────────────────
    if (showFromPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = state.fromDate?.let { bookingDateToMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setFromDate(bookingMillisToDate(it)) }
                    showFromPicker = false
                }) { Text("အိုကေ") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("မလုပ်တော့ပါ") } }
        ) { DatePicker(state = dpState) }
    }

    if (showToPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = state.toDate?.let { bookingDateToMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setToDate(bookingMillisToDate(it)) }
                    showToPicker = false
                }) { Text("အိုကေ") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("မလုပ်တော့ပါ") } }
        ) { DatePicker(state = dpState) }
    }

    LaunchedEffect(state.deleteSuccess) {
        state.deleteSuccess?.let { snackbar.showSnackbar(it); vm.clearDeleteSuccess() }
    }

    // Delete confirmation dialog
    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            icon  = { Icon(Icons.Outlined.Delete, "ဖျက်ရန်", tint = Danger) },
            title = { Text("လက်ခံမှု ဖျက်မည်", fontWeight = FontWeight.ExtraBold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("အောက်ပါ လက်ခံမှုကို ဖျက်မည်မှာ သေချာပါသလား?")
                    Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(target.invoiceNo ?: "#${target.id}", fontWeight = FontWeight.ExtraBold, color = Danger)
                            Text(target.customerName ?: "—", fontSize = 13.sp, color = TextMain)
                        }
                    }
                    val deleteError = state.deleteError
                    if (!deleteError.isNullOrBlank())
                        Text(deleteError, color = Danger, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick  = { vm.delete() },
                    enabled  = !state.deleting,
                    colors   = ButtonDefaults.buttonColors(containerColor = Danger)
                ) {
                    if (state.deleting)
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("ဖျက်မည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDelete() }, enabled = !state.deleting) { Text("မဖျက်တော့ပါ") }
            }
        )
    }

    val filtered = state.items.filter {
        state.search.isBlank() ||
        (it.invoiceNo?.contains(state.search, true) == true) ||
        (it.customerName?.contains(state.search, true) == true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("လက်ခံမှုများ", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = onNewBooking,
                containerColor = Primary,
                contentColor   = Color.White,
                icon           = { Icon(Icons.Outlined.Add, null) },
                text           = { Text("လက်ခံမှု အသစ်", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {
            AppSearchField(
                value = state.search,
                onValueChange = { vm.setSearch(it) },
                placeholder = "လက်ခံမှု ရှာဖွေရန်...",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // ── Date filter row ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.DateRange, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                FilterChip(
                    selected  = state.fromDate != null,
                    onClick   = { showFromPicker = true },
                    label     = { Text(state.fromDate ?: "မှ ရက်", fontSize = 11.sp) },
                    modifier  = Modifier.weight(1f)
                )
                Text("—", color = TextMuted, fontSize = 12.sp)
                FilterChip(
                    selected  = state.toDate != null,
                    onClick   = { showToPicker = true },
                    label     = { Text(state.toDate ?: "အထိ ရက်", fontSize = 11.sp) },
                    modifier  = Modifier.weight(1f)
                )
                if (state.fromDate != null || state.toDate != null) {
                    IconButton(
                        onClick  = { vm.clearDateFilter() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Outlined.Clear, "ရှင်းရန်", tint = Danger, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppLoading()
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("လက်ခံမှု မရှိပါ", color = TextMuted)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onNewBooking) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("လက်ခံမှု အသစ် ဖန်တီးရန်")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { b ->
                        BookingCard(
                            booking     = b,
                            onClick     = { b.id?.let { onBookingClick(it) } },
                            onEdit      = { b.id?.let { onEditBooking(it) } },
                            onDelete    = { vm.confirmDelete(b) }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BookingCard(
    booking:  BookingDTO,
    onClick:  () -> Unit,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        border   = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(booking.invoiceNo ?: "-", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                    Text(booking.customerName ?: "-", fontSize = 13.sp, color = TextMain)
                    Spacer(Modifier.height(4.dp))
                    val displayBrand = booking.brand?.takeIf { it.isNotBlank() }
                        ?: booking.devices?.firstOrNull()?.brand
                    val displayModel = booking.model?.takeIf { it.isNotBlank() }
                        ?: booking.devices?.firstOrNull()?.model
                    if (!displayBrand.isNullOrBlank() || !displayModel.isNullOrBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Smartphone, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                            Text("${displayBrand ?: ""} ${displayModel ?: ""}".trim(), fontSize = 11.sp, color = TextMuted)
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarToday, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                            Text(booking.bookingDate?.take(10) ?: "-", fontSize = 11.sp, color = TextMuted)
                        }
                        if (!booking.shelfLocation.isNullOrBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.LocationOn, null, tint = Violet, modifier = Modifier.size(11.dp))
                                Text(booking.shelfLocation, fontSize = 11.sp, color = Violet, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BookingStatusBadge(booking.status)
                    if ((booking.totalAmount ?: 0.0) > 0)
                        Text("${String.format("%,.0f", booking.totalAmount)} Ks", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                }
            }

            // Action row — only for editable statuses
            val canEdit = booking.status?.uppercase() !in listOf("CONVERTED", "COMPLETED", "CANCELLED")
            if (canEdit) {
                HorizontalDivider(color = BorderColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onEdit,
                        colors  = ButtonDefaults.textButtonColors(contentColor = Primary)
                    ) {
                        Icon(Icons.Outlined.Edit, "ပြင်ရန်", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ပြင်ဆင်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onDelete,
                        colors  = ButtonDefaults.textButtonColors(contentColor = Danger)
                    ) {
                        Icon(Icons.Outlined.Delete, "ဖျက်ရန်", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ဖျက်မည်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingStatusBadge(status: String?) {
    val (bg, color, label) = when (status?.uppercase()) {
        "PENDING"     -> Triple(WarningBg,    Warning, "စောင့်ဆိုင်း")
        "CONFIRMED"   -> Triple(VioletBg,     Violet,  "အတည်ပြု")
        "IN_STORAGE"  -> Triple(VioletBg,     Violet,  "သိမ်းထားပြီး")
        "IN_PROGRESS" -> Triple(VioletBg,     Violet,  "လုပ်ဆဲ")
        "CONVERTED"   -> Triple(SuccessBg,    Success, "အလုပ်ပြောင်းပြီး")
        "COMPLETED"   -> Triple(SuccessBg,    Success, "ပြီးဆုံး")
        "DELIVERED"   -> Triple(PrimaryLight, Primary, "ပြန်ပေး")
        "CANCELLED"   -> Triple(DangerBg,     Danger,  "ပယ်ဖျက်")
        else          -> Triple(BorderColor,  TextMuted, status ?: "-")
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun bookingMillisToDate(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(millis))
}

private fun bookingDateToMillis(dateStr: String): Long {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(dateStr)?.time ?: 0L
    } catch (_: Exception) { 0L }
}
