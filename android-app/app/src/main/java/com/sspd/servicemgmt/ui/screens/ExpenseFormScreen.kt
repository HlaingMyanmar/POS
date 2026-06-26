package com.sspd.servicemgmt.ui.screens

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
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.utils.rememberIsTablet
import com.sspd.servicemgmt.ui.viewmodel.ExpenseFormViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormScreen(onBack: () -> Unit, onSuccess: () -> Unit) {
    val vm: ExpenseFormViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    val accent = if (vm.isExpense) Danger else Success
    val accentBg = if (vm.isExpense) DangerBg else SuccessBg
    val title = if (vm.isExpense) "ကုန်ကျစရိတ် ထည့်ရန်" else "ဝင်ငွေ ထည့်ရန်"

    var showPmSheet    by rememberSaveable { mutableStateOf(false) }
    var showStaffSheet by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val dpState        = rememberDatePickerState(
        initialSelectedDateMillis = run {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .parse(state.entryDate)?.time
            } catch (_: Exception) { null }
        }
    )

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                            .format(java.util.Date(millis))
                        vm.setEntryDate(date)
                    }
                    showDatePicker = false
                }) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = dpState) }
    }

    // Payment method sheet
    if (showPmSheet) {
        ModalBottomSheet(onDismissRequest = { showPmSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("ငွေပေးချေနည်း ရွေးပါ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                state.paymentMethods.forEach { pm ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { vm.selectPm(pm); showPmSheet = false }.padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pm.methodName, fontSize = 14.sp, color = TextMain)
                        if (state.selectedPm?.id == pm.id) Icon(Icons.Outlined.Check, null, tint = accent, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BorderColor)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Staff sheet
    if (showStaffSheet) {
        ModalBottomSheet(onDismissRequest = { showStaffSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("ဝန်ထမ်း ရွေးပါ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                state.staffList.forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { vm.selectStaff(s); showStaffSheet = false }.padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Text(s.role, fontSize = 11.sp, color = TextMuted)
                        }
                        if (state.selectedStaff?.id == s.id) Icon(Icons.Outlined.Check, null, tint = accent, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BorderColor)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = accent, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                AppLoading()
            }
            return@Scaffold
        }

        val isTablet = rememberIsTablet()
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTablet) 64.dp else 16.dp, vertical = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Amount ───────────────────────────────────────────────────────
            Surface(color = accentBg, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (vm.isExpense) "ကုန်ကျသည့် ပမာဏ" else "ဝင်ငွေ ပမာဏ", fontSize = 12.sp, color = accent)
                    OutlinedTextField(
                        value = state.amountStr, onValueChange = { vm.setAmount(it) },
                        placeholder = { Text("0", fontSize = 24.sp, color = accent.copy(0.4f)) },
                        textStyle   = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = accent,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier    = Modifier.fillMaxWidth(),
                        singleLine  = true,
                        shape       = RoundedCornerShape(12.dp),
                        suffix = { Text("Ks", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = accent,
                            unfocusedBorderColor = accent.copy(0.4f)
                        )
                    )
                }
            }

            // ── Date ─────────────────────────────────────────────────────────
            EFSection(Icons.Outlined.CalendarMonth, "ရက်စွဲ", accent)
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarToday, null, tint = accent, modifier = Modifier.size(18.dp))
                        Text(state.entryDate, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    }
                    Surface(color = accentBg, shape = RoundedCornerShape(6.dp)) {
                        Text("ပြောင်းရန်", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Account ──────────────────────────────────────────────────────
            EFSection(Icons.Outlined.AccountBalance, if (vm.isExpense) "ကုန်ကျစရိတ် အကောင့် *" else "ဝင်ငွေ အကောင့် *", accent)
            Column {
                OutlinedTextField(
                    value = state.accountQuery, onValueChange = { vm.setAccountQuery(it) },
                    label = { Text("အကောင့် ရှာပါ *") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = if (state.selectedAccount != null) ({
                        Icon(Icons.Outlined.CheckCircle, null, tint = accent, modifier = Modifier.size(20.dp))
                    }) else null,
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                val suggestions = if (state.accountQuery.length >= 1)
                    state.accounts.filter { it.accountName?.contains(state.accountQuery, true) == true }.take(6)
                else emptyList()
                if (suggestions.isNotEmpty() && state.selectedAccount == null) {
                    Card(shape = RoundedCornerShape(0.dp, 0.dp, 12.dp, 12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                        suggestions.forEach { acc ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { vm.selectAccount(acc) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(acc.accountName ?: "—", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                    if (!acc.code.isNullOrBlank()) Text(acc.code, fontSize = 10.sp, color = TextMuted)
                                }
                                acc.accountType?.let { t ->
                                    Surface(color = accentBg, shape = RoundedCornerShape(4.dp)) {
                                        Text(t, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = accent, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            HorizontalDivider(color = BorderColor)
                        }
                    }
                }
            }

            // ── Payment method ───────────────────────────────────────────────
            EFSection(Icons.Outlined.Payments, "ငွေပေးချေနည်း *", accent)
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showPmSheet = true },
                shape    = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccountBalance, null, tint = if (state.selectedPm != null) accent else TextMuted, modifier = Modifier.size(18.dp))
                        Text(state.selectedPm?.methodName ?: "ငွေပေးချေနည်း ရွေးပါ *", fontSize = 13.sp, color = if (state.selectedPm != null) TextMain else TextMuted)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            // ── Staff ────────────────────────────────────────────────────────
            EFSection(Icons.Outlined.Badge, "ဝန်ထမ်း *", accent)
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showStaffSheet = true },
                shape    = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, null, tint = if (state.selectedStaff != null) accent else TextMuted, modifier = Modifier.size(18.dp))
                        Column {
                            Text(state.selectedStaff?.name ?: "ဝန်ထမ်း ရွေးပါ *", fontSize = 13.sp, color = if (state.selectedStaff != null) TextMain else TextMuted)
                            if (state.selectedStaff != null) Text(state.selectedStaff!!.role, fontSize = 10.sp, color = TextMuted)
                        }
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            // ── Description ──────────────────────────────────────────────────
            EFSection(Icons.Outlined.Notes, "မှတ်ချက်", accent)
            OutlinedTextField(
                value = state.description, onValueChange = { vm.setDescription(it) },
                label = { Text("မှတ်ချက် (optional)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
                maxLines = 3, shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            // Error
            state.saveError?.let { err ->
                Surface(color = DangerBg, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(18.dp))
                        Text(err, fontSize = 13.sp, color = Danger, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Save
            Button(
                onClick  = { vm.save { onSuccess() } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = accent),
                enabled  = !state.saving
            ) {
                if (state.saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EFSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = accent)
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
    }
}
