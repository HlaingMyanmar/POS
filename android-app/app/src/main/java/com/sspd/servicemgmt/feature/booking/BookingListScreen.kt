package com.sspd.servicemgmt.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.network.displayNo
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppSearchField
import com.sspd.servicemgmt.core.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

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

    LaunchedEffect(Unit) { while (true) { vm.load(); delay(30_000) } }
    LaunchedEffect(state.deleteSuccess) { state.deleteSuccess?.let { snackbar.showSnackbar(it); vm.clearDeleteSuccess() } }

    if (showFromPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = state.fromDate?.let { bookingDateToMillis(it) })
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = { TextButton(onClick = { dpState.selectedDateMillis?.let { vm.setFromDate(bookingMillisToDate(it)) }; showFromPicker = false }) { Text("အိုကေ") } },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("မလုပ်တော့ပါ") } }
        ) { DatePicker(state = dpState) }
    }
    if (showToPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = state.toDate?.let { bookingDateToMillis(it) })
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = { TextButton(onClick = { dpState.selectedDateMillis?.let { vm.setToDate(bookingMillisToDate(it)) }; showToPicker = false }) { Text("အိုကေ") } },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("မလုပ်တော့ပါ") } }
        ) { DatePicker(state = dpState) }
    }

    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            icon = { Icon(Icons.Outlined.Delete, null, tint = Danger) },
            title = { Text("Booking ဖျက်မည်", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("အောက်ပါ Booking ကို ဖျက်မည်မှာ သေချာပါသလား?")
                    Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(target.displayNo(), fontWeight = FontWeight.ExtraBold, color = Danger)
                            Text(target.customerName ?: "—", fontSize = 13.sp)
                        }
                    }
                    state.deleteError?.let { Text(it, color = Danger, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(onClick = { vm.delete() }, enabled = !state.deleting, colors = ButtonDefaults.buttonColors(containerColor = Danger)) {
                    if (state.deleting) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("ဖျက်မည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { vm.cancelDelete() }, enabled = !state.deleting) { Text("မဖျက်တော့ပါ") } }
        )
    }

    val filtered = state.items.filter { b ->
        val matchesSearch = state.search.isBlank() ||
            b.displayNo().contains(state.search, true) ||
            (b.customerName?.contains(state.search, true) == true) ||
            (b.customerPhone?.contains(state.search, true) == true) ||
            (b.complaintNote?.contains(state.search, true) == true)
        val matchesStatus = when (state.statusFilter) {
            "CONFIRMED" -> b.status?.uppercase() == "CONFIRMED"
            "ARRIVED"   -> b.status?.uppercase() == "ARRIVED"
            "CANCELED", "CANCELLED" -> b.status?.uppercase() in listOf("CANCELED", "CANCELLED")
            else        -> true
        }
        matchesSearch && matchesStatus
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("ပစ္စည်းလက်ခံ", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = Color.White) } },
                actions = { IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewBooking, containerColor = Primary, contentColor = Color.White, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Booking အသစ်", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {
            AppSearchField(
                value = state.search,
                onValueChange = vm::setSearch,
                placeholder = "Booking No, Customer, Phone, Complaint",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            val metrics = listOf(
                "စုစုပေါင်း" to state.items.size.toString(),
                "အတည်ပြု" to state.items.count { it.status?.uppercase() == "CONFIRMED" }.toString(),
                "လက်ခံပြီး" to state.items.count { it.status?.uppercase() == "ARRIVED" }.toString(),
                "ပယ်ဖျက်" to state.items.count { it.status?.uppercase() in listOf("CANCELED", "CANCELLED") }.toString()
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { (label, value) -> SummaryMetricCard(label, value) }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "အားလုံး", "CONFIRMED" to "အတည်ပြု", "ARRIVED" to "လက်ခံပြီး", "CANCELED" to "ပယ်ဖျက်").forEach { (k, v) ->
                    FilterChip(selected = state.statusFilter == k, onClick = { vm.setStatusFilter(k) }, label = { Text(v, fontSize = 12.sp) })
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DateRange, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                FilterChip(selected = state.fromDate != null, onClick = { showFromPicker = true }, label = { Text(state.fromDate ?: "မှ ရက်", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                Text("—", color = TextMuted)
                FilterChip(selected = state.toDate != null, onClick = { showToPicker = true }, label = { Text(state.toDate ?: "အထိ ရက်", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                if (state.fromDate != null || state.toDate != null) IconButton(onClick = { vm.clearDateFilter() }) { Icon(Icons.Outlined.Clear, null, tint = Danger) }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Booking မရှိသေးပါ", color = TextMuted)
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered) { b ->
                        BookingCard(b, onClick = { b.id?.let(onBookingClick) }, onEdit = { b.id?.let(onEditBooking) }, onDelete = { vm.confirmDelete(b) })
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BookingCard(booking: BookingDTO, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val status = booking.status?.uppercase().orEmpty()
    val canDelete = status == "CONFIRMED"
    val canEdit = status !in listOf("CANCELED", "CANCELLED") && booking.fullyConverted != true

    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(booking.displayNo(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                    Text(booking.customerName ?: "—", fontSize = 13.sp, color = TextMain)
                    booking.customerPhone?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = TextMuted) }
                    Text(formatDateTime(booking.appointmentDate ?: booking.bookingDate), fontSize = 11.sp, color = TextMuted)
                    booking.complaintNote?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 11.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                BookingStatusBadge(booking.status)
            }
            if (canEdit || canDelete) {
                HorizontalDivider(color = BorderColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (canEdit) TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("ပြင်ဆင်", fontSize = 12.sp) }
                    if (canDelete) TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("ဖျက်မည်", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(label: String, value: String) {
    Surface(color = CardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.defaultMinSize(minWidth = 100.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
        }
    }
}

private fun formatDateTime(value: String?) = value?.take(16)?.replace("T", "  ") ?: "—"
private fun bookingMillisToDate(millis: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
private fun bookingDateToMillis(dateStr: String) = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(dateStr)?.time ?: 0L }.getOrDefault(0L)
