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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Booking ပြင်ဆင်ရန်" else "Booking အသစ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
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

                    OutlinedTextField(
                        value = state.appointmentDate,
                        onValueChange = vm::setAppointmentDate,
                        label = { Text("Appointment Time") },
                        placeholder = { Text("YYYY-MM-DD HH:mm") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
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
