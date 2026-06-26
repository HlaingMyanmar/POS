package com.sspd.servicemgmt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.ExpenseViewModel
import com.sspd.servicemgmt.ui.viewmodel.ExpenseViewModel.DateShortcut
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    onBack:     () -> Unit,
    onNewEntry: (type: String) -> Unit = {}
) {
    val vm: ExpenseViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) { vm.load(); delay(30_000) }
    }

    // ── Date pickers ─────────────────────────────────────────────────────────
    if (showFromPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = state.fromDate?.let { dateToMs(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setFromDate(msToDate(it)) }
                    showFromPicker = false
                }) { Text("အိုကေ") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("မလုပ်တော့ပါ") } }
        ) { DatePicker(state = dpState) }
    }

    if (showToPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = state.toDate?.let { dateToMs(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setToDate(msToDate(it)) }
                    showToPicker = false
                }) { Text("အိုကေ") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("မလုပ်တော့ပါ") } }
        ) { DatePicker(state = dpState) }
    }

    // ── Date-filtered lists ───────────────────────────────────────────────────
    val from = state.fromDate
    val to   = state.toDate
    val expenses = state.expenses.filter { e ->
        val d = e.expenseDate?.take(10) ?: ""
        (from == null || d >= from) && (to == null || d <= to)
    }
    val incomes = state.incomes.filter { inc ->
        val d = inc.incomeDate?.take(10) ?: ""
        (from == null || d >= from) && (to == null || d <= to)
    }

    val totalExpense = expenses.sumOf { it.amount }
    val totalIncome  = incomes.sumOf  { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဝင်ငွေ / ကုန်ကျစရိတ်", fontWeight = FontWeight.ExtraBold) },
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
            FloatingActionButton(
                onClick        = { onNewEntry(if (selectedTab == 0) "EXPENSE" else "INCOME") },
                containerColor = if (selectedTab == 0) Danger else Success
            ) { Icon(Icons.Outlined.Add, null, tint = Color.White) }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {

            // ── Summary bar ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(
                    label    = "ကုန်ကျစရိတ်",
                    amount   = totalExpense,
                    color    = Danger,
                    bg       = DangerBg,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    label    = "ဝင်ငွေ",
                    amount   = totalIncome,
                    color    = Success,
                    bg       = SuccessBg,
                    modifier = Modifier.weight(1f)
                )
                val net = totalIncome - totalExpense
                SummaryCard(
                    label    = "အသားတင်",
                    amount   = net,
                    color    = if (net >= 0) Success else Danger,
                    bg       = if (net >= 0) SuccessBg else DangerBg,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Date filter row ──────────────────────────────────────────────
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { DateShortcutChip("Today", state.dateShortcut == DateShortcut.TODAY) { vm.applyDateShortcut(DateShortcut.TODAY) } }
                item { DateShortcutChip("This Week", state.dateShortcut == DateShortcut.WEEK) { vm.applyDateShortcut(DateShortcut.WEEK) } }
                item { DateShortcutChip("This Month", state.dateShortcut == DateShortcut.MONTH) { vm.applyDateShortcut(DateShortcut.MONTH) } }
                item { DateShortcutChip("All", state.dateShortcut == DateShortcut.ALL) { vm.applyDateShortcut(DateShortcut.ALL) } }
            }
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
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Clear, "ရှင်းရန်", tint = Danger, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── Tabs ─────────────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab, containerColor = CardBg, contentColor = Primary) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text = {
                        Text(
                            "ကုန်ကျစရိတ် (${expenses.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.ExtraBold else FontWeight.Normal,
                            fontSize = 13.sp, color = if (selectedTab == 0) Danger else TextMuted
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text = {
                        Text(
                            "ဝင်ငွေ (${incomes.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.ExtraBold else FontWeight.Normal,
                            fontSize = 13.sp, color = if (selectedTab == 1) Success else TextMuted
                        )
                    }
                )
            }

            // ── List ─────────────────────────────────────────────────────────
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppLoading()
                }
            } else if (selectedTab == 0) {
                if (expenses.isEmpty()) {
                    EmptyState("ကုန်ကျစရိတ် မရှိသေးပါ")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(expenses) { e ->
                            EntryCard(
                                code    = e.expenseCode,
                                account = e.accountName ?: "—",
                                date    = e.expenseDate?.take(10) ?: "—",
                                staff   = e.staffName,
                                pm      = e.paymentMethodName,
                                desc    = e.description,
                                amount  = e.amount,
                                color   = Danger,
                                bg      = DangerBg
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            } else {
                if (incomes.isEmpty()) {
                    EmptyState("ဝင်ငွေ မရှိသေးပါ")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(incomes) { inc ->
                            EntryCard(
                                code    = inc.incomeCode,
                                account = inc.accountName ?: "—",
                                date    = inc.incomeDate?.take(10) ?: "—",
                                staff   = inc.staffName,
                                pm      = inc.paymentMethodName,
                                desc    = inc.description,
                                amount  = inc.amount,
                                color   = Success,
                                bg      = SuccessBg
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateShortcutChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) Primary.copy(0.12f) else CardBg,
            labelColor = if (selected) Primary else TextMuted
        ),
        border = BorderStroke(1.dp, if (selected) Primary.copy(0.35f) else BorderColor)
    )
}

@Composable
private fun SummaryCard(label: String, amount: Long, color: Color, bg: Color, modifier: Modifier) {
    Surface(color = bg, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
            Text("${amount.fmt()} Ks", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun EntryCard(
    code: String?, account: String, date: String,
    staff: String?, pm: String?, desc: String?,
    amount: Long, color: Color, bg: Color
) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(account, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                    if (code != null) {
                        Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
                            Text(code, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!desc.isNullOrBlank()) Text(desc, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarToday, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                        Text(date, fontSize = 10.sp, color = TextMuted)
                    }
                    if (!staff.isNullOrBlank()) Text("• $staff", fontSize = 10.sp, color = TextMuted)
                    if (!pm.isNullOrBlank()) Text("• $pm", fontSize = 10.sp, color = TextMuted)
                }
            }
            Text("${amount.fmt()} Ks", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun EmptyState(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, color = TextMuted)
    }
}

private fun Long.fmt() = String.format("%,d", this)

private fun msToDate(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(millis))
}

private fun dateToMs(dateStr: String): Long {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        sdf.parse(dateStr)?.time ?: 0L
    } catch (_: Exception) { 0L }
}

