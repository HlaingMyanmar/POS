package com.sspd.servicemgmt.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppPickerSheet
import com.sspd.servicemgmt.core.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingFormScreen(onBack: () -> Unit, onSuccess: () -> Unit) {
    val vm: BookingFormViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    var showCustomerSheet by rememberSaveable { mutableStateOf(false) }
    var showBookingDatePicker by rememberSaveable { mutableStateOf(false) }
    var showAppointmentDatePicker by rememberSaveable { mutableStateOf(false) }
    var showAppointmentTimePicker by rememberSaveable { mutableStateOf(false) }
    var tempAppointmentDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }

    if (state.showNewCustomerDialog) {
        AlertDialog(
            onDismissRequest = { vm.dismissNewCustomerDialog() },
            title = { Text("Customer အသစ်", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.newCustomerName,
                        onValueChange = vm::setNewCustomerName,
                        label = { Text("အမည် *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = state.newCustomerPhone,
                        onValueChange = vm::setNewCustomerPhone,
                        label = { Text("ဖုန်း") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    OutlinedTextField(
                        value = state.newCustomerAddress,
                        onValueChange = vm::setNewCustomerAddress,
                        label = { Text("လိပ်စာ") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = RoundedCornerShape(10.dp)
                    )
                    state.newCustomerError?.let { Text(it, color = Danger, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(onClick = { vm.createCustomer() }, enabled = !state.creatingCustomer, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    if (state.creatingCustomer) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("သိမ်းမည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissNewCustomerDialog() }, enabled = !state.creatingCustomer) { Text("မလုပ်တော့ပါ") }
            }
        )
    }

    if (showCustomerSheet) {
        val filtered = state.customers.filter {
            state.customerQuery.isBlank() ||
                it.name.contains(state.customerQuery, true) ||
                (it.phone?.contains(state.customerQuery, true) == true)
        }
        AppPickerSheet(
            title = "ဖောက်သည် ရွေးပါ",
            items = filtered,
            label = { it.name },
            subtitle = { it.phone },
            onSelect = { vm.selectCustomer(it); showCustomerSheet = false },
            onDismiss = { showCustomerSheet = false }
        )
    }

    if (showBookingDatePicker) {
        val dp = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showBookingDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dp.selectedDateMillis?.let { vm.setBookingDate(formatDateMillis(it)) }
                    showBookingDatePicker = false
                }) { Text("အိုကေ") }
            },
            dismissButton = { TextButton(onClick = { showBookingDatePicker = false }) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = dp) }
    }

    if (showAppointmentDatePicker) {
        val parsed = parseAppointmentDateTime(state.appointmentDate)
        val dp = rememberDatePickerState(initialSelectedDateMillis = parsed?.dateMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showAppointmentDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    tempAppointmentDateMillis = dp.selectedDateMillis
                    showAppointmentDatePicker = false
                    showAppointmentTimePicker = true
                }) { Text("နောက်တစ်ဆင့် →") }
            },
            dismissButton = { TextButton(onClick = { showAppointmentDatePicker = false }) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = dp) }
    }

    if (showAppointmentTimePicker) {
        val parsed = parseAppointmentDateTime(state.appointmentDate)
        val tp = rememberTimePickerState(
            initialHour = parsed?.hour ?: 9,
            initialMinute = parsed?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showAppointmentTimePicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Schedule, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text("Appointment Time", fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = tp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dateStr = tempAppointmentDateMillis?.let { formatDateMillis(it) }
                        ?: parsed?.dateMillis?.let { formatDateMillis(it) }
                        ?: ""
                    if (dateStr.isNotBlank()) {
                        val timeStr = String.format("%02d:%02d", tp.hour, tp.minute)
                        vm.setAppointmentDate("$dateStr $timeStr")
                    }
                    showAppointmentTimePicker = false
                }) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAppointmentTimePicker = false
                    showAppointmentDatePicker = true
                }) { Text("← ပြန်") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Booking ပြင်ဆင်ရန်" else "Booking အသစ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = OnPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = OnPrimary)
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("ဖောက်သည် *", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextButton(onClick = { vm.showNewCustomerDialog() }) {
                            Icon(Icons.Outlined.PersonAdd, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("အသစ်", fontSize = 12.sp)
                        }
                    }
                    OutlinedCard(
                        onClick = { showCustomerSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("ဖောက်သည် *", fontSize = 11.sp, color = TextMuted)
                                Text(state.selectedCustomer?.name ?: state.customerQuery.ifBlank { "ရွေးပါ" }, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Outlined.Search, null, tint = TextMuted)
                        }
                    }

                    OutlinedTextField(
                        value = state.bookingDate,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { showBookingDatePicker = true },
                        enabled = false,
                        label = { Text("Booking Date *") },
                        trailingIcon = { Icon(Icons.Outlined.CalendarToday, null) },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = TextMain, disabledBorderColor = BorderColor, disabledLabelColor = TextMuted)
                    )

                    BookingAppointmentTimeField(
                        appointmentDate = state.appointmentDate,
                        onOpenPicker = {
                            tempAppointmentDateMillis = parseAppointmentDateTime(state.appointmentDate)?.dateMillis
                            showAppointmentDatePicker = true
                        },
                        onClear = { vm.setAppointmentDate("") }
                    )

                    OutlinedTextField(
                        value = state.complaintNote,
                        onValueChange = vm::setComplaintNote,
                        label = { Text("Customer Complaint") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = state.remark,
                        onValueChange = vm::setRemark,
                        label = { Text("မှတ်ချက်") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            state.saveError?.let { Text(it, color = Danger, fontSize = 13.sp) }

            Button(
                onClick = { vm.save { onSuccess() } },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !state.saving,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (vm.isEdit) "ပြင်ဆင်မှု သိမ်းမည်" else "Booking သိမ်းမည်", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun formatDateMillis(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(millis))
}

private data class ParsedAppointmentDateTime(
    val dateMillis: Long,
    val hour: Int,
    val minute: Int
)

private fun parseAppointmentDateTime(value: String): ParsedAppointmentDateTime? {
    if (value.isBlank()) return null
    return try {
        val normalized = value.trim().replace("T", " ")
        val parts = normalized.split(" ", limit = 2)
        if (parts.size < 2) return null
        val dateMillis = dateStringToMillis(parts[0]) ?: return null
        val timeParts = parts[1].split(":")
        ParsedAppointmentDateTime(
            dateMillis = dateMillis,
            hour = timeParts[0].toInt(),
            minute = timeParts.getOrElse(1) { "0" }.toInt()
        )
    } catch (_: Exception) {
        null
    }
}

private fun dateStringToMillis(date: String): Long? = try {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    sdf.parse(date)?.time
} catch (_: Exception) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingAppointmentTimeField(
    appointmentDate: String,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAppointmentTime = appointmentDate.isNotBlank()
    OutlinedCard(
        onClick = onOpenPicker,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (hasAppointmentTime) Primary else BorderColor)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    null,
                    tint = if (hasAppointmentTime) Primary else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        "Appointment Time",
                        fontSize = 11.sp,
                        color = if (hasAppointmentTime) Primary else TextMuted
                    )
                    Text(
                        if (hasAppointmentTime) appointmentDate else "ရက်စွဲ/အချိန် ရွေးပါ",
                        fontSize = if (hasAppointmentTime) 14.sp else 13.sp,
                        fontWeight = if (hasAppointmentTime) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (hasAppointmentTime) TextMain else TextMuted
                    )
                }
            }
            if (hasAppointmentTime) {
                IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Close, "ရှင်းရန်", tint = Danger, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingFormPreviewBody(
    appointmentDate: String,
    isEdit: Boolean = false,
) {
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (isEdit) "Booking ပြင်ဆင်ရန်" else "Booking အသစ်",
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    navigationIcon = { Icon(Icons.Outlined.ArrowBack, null, tint = OnPrimary) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = OnPrimary)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(ScreenBg)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ဖောက်သည် *", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            TextButton(onClick = {}) {
                                Icon(Icons.Outlined.PersonAdd, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("အသစ်", fontSize = 12.sp)
                            }
                        }
                        OutlinedCard(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("ဖောက်သည် *", fontSize = 11.sp, color = TextMuted)
                                    Text("U Aung Kyaw", fontWeight = FontWeight.SemiBold)
                                }
                                Icon(Icons.Outlined.Search, null, tint = TextMuted)
                            }
                        }
                        OutlinedTextField(
                            value = "2026-09-02",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Booking Date *") },
                            trailingIcon = { Icon(Icons.Outlined.CalendarToday, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = TextMain,
                                disabledBorderColor = BorderColor,
                                disabledLabelColor = TextMuted
                            )
                        )
                        BookingAppointmentTimeField(
                            appointmentDate = appointmentDate,
                            onOpenPicker = {},
                            onClear = {}
                        )
                        OutlinedTextField(
                            value = "Screen flickering after update",
                            onValueChange = {},
                            label = { Text("Customer Complaint") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("မှတ်ချက်") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Booking သိမ်းမည်", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Booking — Appointment empty", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun BookingFormAppointmentEmptyPreview() {
    BookingFormPreviewBody(appointmentDate = "")
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Booking — Appointment selected", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun BookingFormAppointmentSelectedPreview() {
    BookingFormPreviewBody(appointmentDate = "2026-09-02 14:30")
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Appointment — Date picker", showBackground = true, widthDp = 390, heightDp = 520)
@Composable
private fun BookingAppointmentDatePickerPreview() {
    AppTheme {
        val dp = rememberDatePickerState(initialSelectedDateMillis = dateStringToMillis("2026-09-02"))
        DatePickerDialog(
            onDismissRequest = {},
            confirmButton = { TextButton(onClick = {}) { Text("နောက်တစ်ဆင့် →") } },
            dismissButton = { TextButton(onClick = {}) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = dp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Appointment — Time picker", showBackground = true, widthDp = 390, heightDp = 420)
@Composable
private fun BookingAppointmentTimePickerPreview() {
    AppTheme {
        val tp = rememberTimePickerState(initialHour = 14, initialMinute = 30, is24Hour = true)
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Schedule, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text("Appointment Time", fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = tp)
                }
            },
            confirmButton = { TextButton(onClick = {}) { Text("OK", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = {}) { Text("← ပြန်") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Appointment Time field", showBackground = true, widthDp = 390)
@Composable
private fun BookingAppointmentTimeFieldPreview() {
    AppTheme {
        Column(
            Modifier.background(ScreenBg).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BookingAppointmentTimeField(appointmentDate = "", onOpenPicker = {}, onClear = {})
            BookingAppointmentTimeField(appointmentDate = "2026-09-02 14:30", onOpenPicker = {}, onClear = {})
        }
    }
}
