package com.sspd.servicemgmt.feature.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.JournalDetailDTO
import com.sspd.servicemgmt.core.network.JournalEntryDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.feature.finance.JournalEntryViewModel.BalanceFilter
import com.sspd.servicemgmt.feature.finance.JournalEntryViewModel.DateShortcut
import com.sspd.servicemgmt.feature.finance.JournalEntryViewModel.JournalSource
import java.time.LocalDate
import kotlin.math.abs

private val JournalColor = Color(0xFF334155)
private val JournalBg = Color(0xFFF1F5F9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEntryScreen(onBack: () -> Unit) {
    val vm: JournalEntryViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var expandedId by remember { mutableStateOf<Int?>(null) }

    val visibleItems = remember(state.items, state.search, state.source, state.balanceFilter, state.dateFrom, state.dateTo) {
        state.items.filter { entry ->
            val keyword = state.search.trim().lowercase()
            val source = detectJournalSource(entry)
            val totals = entry.totals()
            val balanced = totals.isBalanced
            val entryDate = entry.entryDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val from = state.dateFrom.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val to = state.dateTo.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val dateOk = if (from == null && to == null) true else entryDate != null &&
                (from == null || !entryDate.isBefore(from)) && (to == null || !entryDate.isAfter(to))
            val text = listOf(entry.referenceNo, entry.description, entry.staffName, entry.id?.toString(), entry.details?.joinToString(" ") { it.accountName ?: "" })
                .joinToString(" ")
                .lowercase()

            dateOk &&
                (keyword.isBlank() || text.contains(keyword)) &&
                (state.source == JournalSource.ALL || source == state.source || (state.source == JournalSource.RETURN && (source == JournalSource.RETURN))) &&
                when (state.balanceFilter) {
                    BalanceFilter.ALL -> true
                    BalanceFilter.BALANCED -> balanced
                    BalanceFilter.CHECK -> !balanced
                }
        }
    }

    val totalDebit = visibleItems.sumOf { it.totals().debit }
    val totalCredit = visibleItems.sumOf { it.totals().credit }
    val checkCount = visibleItems.count { !it.totals().isBalanced }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဂျာနယ်မှတ်တမ်း", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                actions = { IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "Refresh", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JournalColor, titleContentColor = OnPrimary)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = vm::setSearch,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ref No / အကြောင်းအရာ / Account ရှာပါ...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextMuted) },
                    trailingIcon = {
                        if (state.search.isNotBlank()) IconButton(onClick = { vm.setSearch("") }) {
                            Icon(Icons.Outlined.Close, null, tint = TextMuted)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { JournalChip("Today", state.dateShortcut == DateShortcut.TODAY) { vm.applyDateShortcut(DateShortcut.TODAY) } }
                    item { JournalChip("This Week", state.dateShortcut == DateShortcut.WEEK) { vm.applyDateShortcut(DateShortcut.WEEK) } }
                    item { JournalChip("This Month", state.dateShortcut == DateShortcut.MONTH) { vm.applyDateShortcut(DateShortcut.MONTH) } }
                    item { JournalChip("All", state.dateShortcut == DateShortcut.ALL) { vm.applyDateShortcut(DateShortcut.ALL) } }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SourceChip("အားလုံး", state.source == JournalSource.ALL) { vm.setSource(JournalSource.ALL) } }
                    item { SourceChip("အရောင်း", state.source == JournalSource.SALE) { vm.setSource(JournalSource.SALE) } }
                    item { SourceChip("ဝယ်ယူ", state.source == JournalSource.PURCHASE) { vm.setSource(JournalSource.PURCHASE) } }
                    item { SourceChip("Return", state.source == JournalSource.RETURN) { vm.setSource(JournalSource.RETURN) } }
                    item { SourceChip("အသုံးစရိတ်", state.source == JournalSource.EXPENSE) { vm.setSource(JournalSource.EXPENSE) } }
                    item { SourceChip("ဝင်ငွေ", state.source == JournalSource.INCOME) { vm.setSource(JournalSource.INCOME) } }
                    item { SourceChip("Stock", state.source == JournalSource.STOCK) { vm.setSource(JournalSource.STOCK) } }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryBoxJournal("Entry", visibleItems.size.toString(), JournalColor, Modifier.weight(1f))
                    SummaryBoxJournal("Debit", moneyJournal(totalDebit), Success, Modifier.weight(1f))
                    SummaryBoxJournal("Credit", moneyJournal(totalCredit), Danger, Modifier.weight(1f))
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { StatusChip("အားလုံး", state.balanceFilter == BalanceFilter.ALL) { vm.setBalanceFilter(BalanceFilter.ALL) } }
                    item { StatusChip("Balanced", state.balanceFilter == BalanceFilter.BALANCED) { vm.setBalanceFilter(BalanceFilter.BALANCED) } }
                    item { StatusChip("စစ်ရန် $checkCount", state.balanceFilter == BalanceFilter.CHECK) { vm.setBalanceFilter(BalanceFilter.CHECK) } }
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
            } else if (visibleItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.MenuBook, null, tint = TextMuted, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("ကိုက်ညီသော ဂျာနယ်မှတ်တမ်း မရှိပါ", color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleItems) { entry ->
                        JournalEntryCard(
                            entry = entry,
                            expanded = expandedId == entry.id,
                            onClick = { expandedId = if (expandedId == entry.id) null else entry.id }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun JournalEntryCard(entry: JournalEntryDTO, expanded: Boolean, onClick: () -> Unit) {
    val totals = entry.totals()
    val source = detectJournalSource(entry)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (totals.isBalanced) BorderColor else Warning.copy(0.45f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.referenceNo ?: "#${entry.id}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = JournalColor, maxLines = 1)
                        SourceBadge(source)
                    }
                    Text(entry.description ?: "-", fontSize = 12.sp, color = TextMain, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Surface(color = if (totals.isBalanced) SuccessBg else WarningBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        if (totals.isBalanced) "OK" else "CHECK",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (totals.isBalanced) Success else Warning
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniAmount("DR", moneyJournal(totals.debit), Success, Modifier.weight(1f))
                MiniAmount("CR", moneyJournal(totals.credit), Danger, Modifier.weight(1f))
                MiniAmount("Diff", moneyJournal(abs(totals.debit - totals.credit)), if (totals.isBalanced) TextMuted else Warning, Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(entry.entryDate?.take(16)?.replace("T", " ") ?: "-", fontSize = 11.sp, color = TextMuted)
                Text(entry.staffName ?: "-", fontSize = 11.sp, color = TextMuted, maxLines = 1)
            }

            if (expanded) {
                HorizontalDivider(color = BorderColor)
                (entry.details ?: emptyList()).forEach { line -> JournalLine(line) }
            }
        }
    }
}

@Composable
private fun JournalLine(line: JournalDetailDTO) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(line.accountName ?: "Account #${line.accountId ?: "-"}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Account ID: ${line.accountId ?: "-"}", fontSize = 10.sp, color = TextMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("DR ${moneyJournal(line.debit ?: 0.0)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Success)
            Text("CR ${moneyJournal(line.credit ?: 0.0)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger)
        }
    }
}

@Composable
private fun JournalChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        colors = AssistChipDefaults.assistChipColors(containerColor = if (selected) JournalBg else CardBg, labelColor = if (selected) JournalColor else TextMuted),
        border = BorderStroke(1.dp, if (selected) JournalColor.copy(0.28f) else BorderColor)
    )
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) = JournalChip(label, selected, onClick)

@Composable
private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) = JournalChip(label, selected, onClick)

@Composable
private fun SourceBadge(source: JournalSource) {
    Surface(color = JournalBg, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, BorderColor)) {
        Text(source.label(), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JournalColor)
    }
}

@Composable
private fun SummaryBoxJournal(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = color.copy(0.08f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, color.copy(0.16f))) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = TextMuted, maxLines = 1)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun MiniAmount(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 10.sp, color = TextMuted)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
    }
}

private data class JournalTotals(val debit: Double, val credit: Double) {
    val isBalanced: Boolean get() = abs(debit - credit) < 0.01
}

private fun JournalEntryDTO.totals(): JournalTotals {
    val rows = details ?: emptyList()
    return JournalTotals(
        debit = rows.sumOf { it.debit ?: 0.0 },
        credit = rows.sumOf { it.credit ?: 0.0 }
    )
}

private fun detectJournalSource(entry: JournalEntryDTO): JournalSource {
    val text = listOf(entry.referenceNo, entry.description).joinToString(" ").uppercase()
    return when {
        text.contains("SALE RETURN") || text.startsWith("SR") || text.contains("SRET") -> JournalSource.RETURN
        text.contains("PURCHASE RETURN") || text.startsWith("PR") || text.contains("PRET") -> JournalSource.RETURN
        text.contains("SALE") || text.startsWith("SAL") -> JournalSource.SALE
        text.contains("PURCHASE") || text.startsWith("PUR") -> JournalSource.PURCHASE
        text.contains("EXPENSE") || text.startsWith("EXP") -> JournalSource.EXPENSE
        text.contains("INCOME") || text.startsWith("INC") -> JournalSource.INCOME
        text.contains("STOCK") || text.startsWith("ADJ") -> JournalSource.STOCK
        text.contains("OPENING") || text.startsWith("OB") -> JournalSource.OPENING
        else -> JournalSource.MANUAL
    }
}

private fun JournalSource.label(): String = when (this) {
    JournalSource.ALL -> "အားလုံး"
    JournalSource.SALE -> "Sale"
    JournalSource.PURCHASE -> "Purchase"
    JournalSource.RETURN -> "Return"
    JournalSource.EXPENSE -> "Expense"
    JournalSource.INCOME -> "Income"
    JournalSource.STOCK -> "Stock"
    JournalSource.OPENING -> "Opening"
    JournalSource.MANUAL -> "Manual"
}

private fun moneyJournal(v: Double): String = "%,.0f Ks".format(v)
