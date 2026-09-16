package com.sspd.servicemgmt.feature.booking

import android.R
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.network.BookingItemDTO
import com.sspd.servicemgmt.core.network.displayNo
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppPullRefresh
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
                Button(onClick = { vm.delete() }, enabled = !state.deleting, colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White, disabledContainerColor = BorderColor, disabledContentColor = TextMuted)) {
                    if (state.deleting) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("ဖျက်မည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { vm.cancelDelete() }, enabled = !state.deleting) { Text("မဖျက်တော့ပါ") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("ပစ္စည်းလက်ခံ", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = Color.White) } },
                actions = { IconButton(onClick = { vm.refresh() }) { Icon(Icons.Outlined.Refresh, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewBooking, containerColor = Primary, contentColor = Color.White, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Booking အသစ်", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        BookingListContent(
            modifier = Modifier.padding(padding),
            search = state.search,
            onSearchChange = vm::setSearch,
            items = state.items,
            loading = state.loading,
            refreshing = state.refreshing,
            onRefresh = vm::refresh,
            statusFilter = state.statusFilter,
            onStatusFilterChange = vm::setStatusFilter,
            fromDate = state.fromDate,
            toDate = state.toDate,
            onFromDateClick = { showFromPicker = true },
            onToDateClick = { showToPicker = true },
            onClearDateFilter = { vm.clearDateFilter() },
            onBookingClick = onBookingClick,
            onEditBooking = onEditBooking,
            onDeleteBooking = vm::confirmDelete,
        )
    }
}

@Composable
private fun BookingListContent(
    search: String,
    onSearchChange: (String) -> Unit,
    items: List<BookingDTO>,
    loading: Boolean,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    fromDate: String?,
    toDate: String?,
    onFromDateClick: () -> Unit,
    onToDateClick: () -> Unit,
    onClearDateFilter: () -> Unit,
    onBookingClick: (Int) -> Unit,
    onEditBooking: (Int) -> Unit,
    onDeleteBooking: (BookingDTO) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = items.filter { b ->
        val matchesSearch = search.isBlank() ||
            b.displayNo().contains(search, true) ||
            (b.customerName?.contains(search, true) == true) ||
            (b.customerPhone?.contains(search, true) == true) ||
            (b.complaintNote?.contains(search, true) == true)
        val matchesStatus = when (statusFilter) {
            "CONFIRMED" -> b.status?.uppercase() == "CONFIRMED"
            "ARRIVED"   -> b.status?.uppercase() == "ARRIVED"
            "CANCELED", "CANCELLED" -> b.status?.uppercase() in listOf("CANCELED", "CANCELLED")
            else        -> true
        }
        matchesSearch && matchesStatus
    }

    Column(modifier.fillMaxSize().background(ScreenBg)) {
        AppPullRefresh(
            refreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f)
        ) {
        Column(Modifier.fillMaxSize()) {
        AppSearchField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = "Booking No, Customer, Phone, Complaint",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        val metrics = listOf(
            "စုစုပေါင်း" to items.size.toString(),
            "အတည်ပြု" to items.count { it.status?.uppercase() == "CONFIRMED" }.toString(),
            "လက်ခံပြီး" to items.count { it.status?.uppercase() == "ARRIVED" }.toString(),
            "ပယ်ဖျက်" to items.count { it.status?.uppercase() in listOf("CANCELED", "CANCELLED") }.toString()
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.forEach { (label, value) -> SummaryMetricCard(label, value) }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "အားလုံး", "CONFIRMED" to "အတည်ပြု", "ARRIVED" to "လက်ခံပြီး", "CANCELED" to "ပယ်ဖျက်").forEach { (k, v) ->
                FilterChip(selected = statusFilter == k, onClick = { onStatusFilterChange(k) }, label = { Text(v, fontSize = 12.sp) })
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DateRange, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            FilterChip(selected = fromDate != null, onClick = onFromDateClick, label = { Text(fromDate ?: "ရက် မှ", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
            Text("—", color = TextMuted)
            FilterChip(selected = toDate != null, onClick = onToDateClick, label = { Text(toDate ?: "ရက် အထိ", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
            if (fromDate != null || toDate != null) IconButton(onClick = onClearDateFilter) { Icon(Icons.Outlined.Clear, null, tint = Danger) }
        }

        when {
            loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Booking မရှိသေးပါ", color = TextMuted)
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { b ->
                    BookingCard(b, onClick = { b.id?.let(onBookingClick) }, onEdit = { b.id?.let(onEditBooking) }, onDelete = { onDeleteBooking(b) })
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
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

private fun sampleBookings(): List<BookingDTO> = listOf(
    BookingDTO(
        id = 1,
        bookingNo = "BK-20260902-001",
        customerName = "U Aung Kyaw",
        customerPhone = "09-123456789",
        appointmentDate = "2026-09-02T14:30:00",
        complaintNote = "Screen flickering after update",
        status = "CONFIRMED",
    ),
    BookingDTO(
        id = 2,
        bookingNo = "BK-20260902-002",
        customerName = "Daw May Thu",
        customerPhone = "09-987654321",
        appointmentDate = "2026-09-02T10:00:00",
        complaintNote = "Battery drains fast",
        status = "ARRIVED",
        items = listOf(
            BookingItemDTO(itemName = "iPhone 14"),
            BookingItemDTO(itemName = "AirPods"),
        ),
    ),
    BookingDTO(
        id = 3,
        bookingNo = "BK-20260901-003",
        customerName = "Ko Min Htet",
        customerPhone = "09-555123456",
        appointmentDate = "2026-09-01T09:00:00",
        complaintNote = "Keyboard not working",
        status = "CONFIRMED",
    ),
    BookingDTO(
        id = 4,
        bookingNo = "BK-20260830-004",
        customerName = "Ma Hnin",
        appointmentDate = "2026-08-30T16:00:00",
        status = "CANCELED",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingListPreviewShell(content: @Composable (Modifier: Modifier) -> Unit) {
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ပစ္စည်းလက်ခံ", fontWeight = FontWeight.ExtraBold,color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White),
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {},
                    containerColor = Primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("Booking အသစ်", fontWeight = FontWeight.Bold,color = Color.White) },
                )
            },
        ) { padding -> content(Modifier.padding(padding)) }
    }
}

@Preview(name = "List — with bookings", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BookingListWithItemsPreview() {
    BookingListPreviewShell { padding ->
        BookingListContent(
            modifier = padding,
            search = "",
            onSearchChange = {},
            items = sampleBookings(),
            loading = false,
            statusFilter = "ALL",
            onStatusFilterChange = {},
            fromDate = "2026-09-01",
            toDate = "2026-09-02",
            onFromDateClick = {},
            onToDateClick = {},
            onClearDateFilter = {},
            onBookingClick = {},
            onEditBooking = {},
            onDeleteBooking = {},
        )
    }
}

@Preview(name = "List — empty", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BookingListEmptyPreview() {
    BookingListPreviewShell { padding ->
        BookingListContent(
            modifier = padding,
            search = "",
            onSearchChange = {},
            items = emptyList(),
            loading = false,
            statusFilter = "ALL",
            onStatusFilterChange = {},
            fromDate = null,
            toDate = null,
            onFromDateClick = {},
            onToDateClick = {},
            onClearDateFilter = {},
            onBookingClick = {},
            onEditBooking = {},
            onDeleteBooking = {},
        )
    }
}

@Preview(name = "List — loading", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BookingListLoadingPreview() {
    BookingListPreviewShell { padding ->
        BookingListContent(
            modifier = padding,
            search = "",
            onSearchChange = {},
            items = emptyList(),
            loading = true,
            statusFilter = "ALL",
            onStatusFilterChange = {},
            fromDate = null,
            toDate = null,
            onFromDateClick = {},
            onToDateClick = {},
            onClearDateFilter = {},
            onBookingClick = {},
            onEditBooking = {},
            onDeleteBooking = {},
        )
    }
}

@Preview(name = "List — confirmed filter", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BookingListConfirmedFilterPreview() {
    BookingListPreviewShell { padding ->
        BookingListContent(
            modifier = padding,
            search = "",
            onSearchChange = {},
            items = sampleBookings(),
            loading = false,
            statusFilter = "CONFIRMED",
            onStatusFilterChange = {},
            fromDate = null,
            toDate = null,
            onFromDateClick = {},
            onToDateClick = {},
            onClearDateFilter = {},
            onBookingClick = {},
            onEditBooking = {},
            onDeleteBooking = {},
        )
    }
}

@Preview(name = "Booking card", showBackground = true, widthDp = 390)
@Composable
private fun BookingCardPreview() {
    AppTheme {
        Box(Modifier.background(ScreenBg).padding(12.dp)) {
            BookingCard(sampleBookings().first(), onClick = {}, onEdit = {}, onDelete = {})
        }
    }
}

