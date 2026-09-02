package com.sspd.servicemgmt.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Booking", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("ချိန်းဆိုမှတ်တမ်း · ပစ္စည်းလက်ခံ", fontSize = 11.sp, color = OnPrimary.copy(alpha = 0.85f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = OnPrimary) } },
                actions = { IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, null, tint = OnPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = OnPrimary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewBooking,
                containerColor = Primary,
                contentColor = OnPrimary,
            ) {
                Icon(Icons.Outlined.Add, "Booking အသစ်")
            }
        }
    ) { padding ->
        BookingListContent(
            modifier = Modifier.padding(padding),
            search = state.search,
            onSearchChange = vm::setSearch,
            items = state.items,
            loading = state.loading,
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
        AppSearchField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = "နံပါတ်၊ ဖောက်သည်၊ ဖုန်း၊ တိုင်ပင်ချက်",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        BookingStatusSummaryGrid(
            items = items,
            selected = statusFilter,
            onSelect = onStatusFilterChange,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DateRange, null, tint = TextMuted, modifier = Modifier.size(18.dp))
            OutlinedCard(
                modifier = Modifier.weight(1f).clickable(onClick = onFromDateClick),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (fromDate != null) Primary else BorderColor),
                colors = CardDefaults.outlinedCardColors(containerColor = CardBg),
            ) {
                Text(
                    fromDate ?: "မှ",
                    fontSize = 12.sp,
                    fontWeight = if (fromDate != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (fromDate != null) TextMain else TextMuted,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Text("→", color = TextMuted, fontSize = 12.sp)
            OutlinedCard(
                modifier = Modifier.weight(1f).clickable(onClick = onToDateClick),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (toDate != null) Primary else BorderColor),
                colors = CardDefaults.outlinedCardColors(containerColor = CardBg),
            ) {
                Text(
                    toDate ?: "အထိ",
                    fontSize = 12.sp,
                    fontWeight = if (toDate != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (toDate != null) TextMain else TextMuted,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            if (fromDate != null || toDate != null) {
                IconButton(onClick = onClearDateFilter, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Clear, "ရက်စ filter ဖယ်ရန်", tint = Danger, modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (filtered.size == items.size) "စာရင်း ${filtered.size} ခု"
                else "စာရင်း ${filtered.size} / ${items.size} ခု",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMain,
            )
            if (statusFilter != "ALL" || search.isNotBlank()) {
                TextButton(onClick = {
                    onStatusFilterChange("ALL")
                    if (search.isNotBlank()) onSearchChange("")
                }) {
                    Icon(Icons.Outlined.FilterAltOff, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Filter ရှင်းမည်", fontSize = 12.sp)
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
            filtered.isEmpty() -> BookingListEmptyState(
                hasFilters = statusFilter != "ALL" || search.isNotBlank() || fromDate != null || toDate != null,
                onClearFilters = {
                    onStatusFilterChange("ALL")
                    onSearchChange("")
                    onClearDateFilter()
                },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id ?: it.bookingNo ?: it.hashCode() }) { b ->
                    BookingCard(
                        booking = b,
                        onClick = { b.id?.let(onBookingClick) },
                        onEdit = { b.id?.let(onEditBooking) },
                        onDelete = { onDeleteBooking(b) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingStatusSummaryGrid(
    items: List<BookingDTO>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val confirmed = items.count { it.status?.uppercase() == "CONFIRMED" }
    val arrived = items.count { it.status?.uppercase() == "ARRIVED" }
    val canceled = items.count { it.status?.uppercase() in listOf("CANCELED", "CANCELLED") }
    val cells = listOf(
        SummaryCell("ALL", "စုစုပေါင်း", items.size.toString(), Primary, PrimaryLight),
        SummaryCell("CONFIRMED", "အတည်ပြု", confirmed.toString(), Violet, VioletBg),
        SummaryCell("ARRIVED", "လက်ခံပြီး", arrived.toString(), Color(0xFFB45309), WarningBg),
        SummaryCell("CANCELED", "ပယ်ဖျက်", canceled.toString(), Danger, DangerBg),
    )

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    val isSelected = selected == cell.key
                    Surface(
                        onClick = { onSelect(cell.key) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) cell.tintBg else CardBg,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) cell.tint else BorderColor,
                        ),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(cell.label, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                            Text(
                                cell.value,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) cell.tint else TextMain,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class SummaryCell(
    val key: String,
    val label: String,
    val value: String,
    val tint: Color,
    val tintBg: Color,
)

@Composable
private fun BookingListEmptyState(hasFilters: Boolean, onClearFilters: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = PrimaryLight, shape = CircleShape) {
                Icon(
                    Icons.Outlined.EventAvailable,
                    null,
                    tint = Primary,
                    modifier = Modifier.padding(20.dp).size(36.dp),
                )
            }
            Text(
                if (hasFilters) "Filter နဲ့ မကိုက်ညီပါ" else "Booking မရှိသေးပါ",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextMain,
                textAlign = TextAlign.Center,
            )
            Text(
                if (hasFilters) "Filter ကို ပြောင်းကြည့်ပါ သို့မဟုတ် ရှင်းပြီး ပြန်ကြည့်ပါ။"
                else "ဖောက်သည် ချိန်းဆိုမှတ်တမ်းအသစ် ထည့်ရန် + ခလုတ်ကို နှိပ်ပါ။",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
            )
            if (hasFilters) {
                OutlinedButton(onClick = onClearFilters, shape = RoundedCornerShape(12.dp)) {
                    Text("Filter ရှင်းမည်", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingListPreviewShell(content: @Composable (Modifier) -> Unit) {
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Booking", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("ချိန်းဆိုမှတ်တမ်း · ပစ္စည်းလက်ခံ", fontSize = 11.sp, color = OnPrimary.copy(alpha = 0.85f))
                        }
                    },
                    navigationIcon = { Icon(Icons.Outlined.ArrowBack, null, tint = OnPrimary) },
                    actions = { Icon(Icons.Outlined.Refresh, null, tint = OnPrimary) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = OnPrimary),
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {}, containerColor = Primary, contentColor = OnPrimary) {
                    Icon(Icons.Outlined.Add, null)
                }
            },
        ) { padding -> content(Modifier.padding(padding)) }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingCard(
    booking: BookingDTO,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = booking.status?.uppercase().orEmpty()
    val canDelete = status == "CONFIRMED"
    val canEdit = status !in listOf("CANCELED", "CANCELLED") && booking.fullyConverted != true
    val accent = when (status) {
        "CONFIRMED" -> Primary
        "ARRIVED"   -> Color(0xFFB45309)
        "CANCELED", "CANCELLED" -> Danger
        else        -> TextMuted
    }
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(5.dp)
                    .heightIn(min = 120.dp)
                    .background(accent),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            booking.customerName ?: "—",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            booking.displayNo(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary,
                        )
                    }
                    BookingStatusBadge(booking.status)
                    if (canEdit || canDelete) {
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Outlined.MoreVert, "Actions", tint = TextMuted, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (canEdit) {
                                    DropdownMenuItem(
                                        text = { Text("ပြင်ဆင်") },
                                        onClick = { menuOpen = false; onEdit() },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    )
                                }
                                if (canDelete) {
                                    DropdownMenuItem(
                                        text = { Text("ဖျက်မည်", color = Danger) },
                                        onClick = { menuOpen = false; onDelete() },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Danger) },
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Schedule, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Text(
                            formatDateTime(booking.appointmentDate ?: booking.bookingDate),
                            fontSize = 12.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    booking.customerPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Phone, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            Text(phone, fontSize = 12.sp, color = TextMuted, maxLines = 1)
                        }
                    }
                }

                Surface(color = PrimaryLight, shape = RoundedCornerShape(10.dp)) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null, tint = Primary, modifier = Modifier.size(14.dp))
                        Text(
                            bookingNextAction(booking),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary,
                        )
                    }
                }

                booking.complaintNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Outlined.ReportProblem, null, tint = Warning, modifier = Modifier.size(14.dp))
                        Text(
                            note,
                            fontSize = 12.sp,
                            color = TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 17.sp,
                        )
                    }
                }

                val itemCount = booking.items?.size ?: 0
                if (itemCount > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Inventory2, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Text("လက်ခံပစ္စည်း $itemCount ခု", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

private fun formatDateTime(value: String?) = value?.take(16)?.replace("T", "  ") ?: "—"
private fun bookingMillisToDate(millis: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
private fun bookingDateToMillis(dateStr: String) = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(dateStr)?.time ?: 0L }.getOrDefault(0L)
