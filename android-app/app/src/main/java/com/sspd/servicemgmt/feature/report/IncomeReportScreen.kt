package com.sspd.servicemgmt.feature.report

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.MonthlyDataDTO
import com.sspd.servicemgmt.core.network.PeriodSummaryDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.*

import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeReportScreen(onBack: () -> Unit) {
    val vm: IncomeReportViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    val monthNames = remember {
        arrayOf("ဇန်","ဖေ","မတ်","ဧ","မေ","ဇွန်","ဇူ","သြ","စက်","အောက်","နို","ဒီ")
    }
    val monthNamesFull = remember {
        arrayOf("ဇန်နဝါရီ","ဖေဖော်ဝါရီ","မတ်","ဧပြီ","မေ","ဇွန်","ဇူလိုင်","သြဂုတ်","စက်တင်ဘာ","အောက်တိုဘာ","နိုဝင်ဘာ","ဒီဇင်ဘာ")
    }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    if (showFromPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = state.fromDate?.let { irDateToMs(it) })
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = { TextButton(onClick = { dpState.selectedDateMillis?.let { vm.setFromDate(irMsToDate(it)) }; showFromPicker = false }) { Text("အိုကေ") } },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("မလုပ်တော့ပါ") } }
        ) { DatePicker(state = dpState) }
    }
    if (showToPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = state.toDate?.let { irDateToMs(it) })
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = { TextButton(onClick = { dpState.selectedDateMillis?.let { vm.setToDate(irMsToDate(it)) }; showToPicker = false }) { Text("အိုကေ") } },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("မလုပ်တော့ပါ") } }
        ) { DatePicker(state = dpState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဝင်ငွေ / အမြတ် စာရင်း", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                actions = { IconButton(onClick = { if (state.mode == ReportMode.YEARLY) vm.selectMode(ReportMode.YEARLY) else vm.load() }) { Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF059669), titleContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── Mode chips ────────────────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ReportMode.TODAY to "ယနေ့", ReportMode.MONTHLY to "လစဉ်", ReportMode.YEARLY to "နှစ်စဉ်", ReportMode.CUSTOM to "Custom")
                        .forEach { (mode, label) ->
                            FilterChip(selected = state.mode == mode, onClick = { vm.selectMode(mode) }, label = { Text(label, fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                        }
                }
            }

            // ── Period selector ───────────────────────────────────────────────
            item {
                when (state.mode) {
                    ReportMode.TODAY -> Surface(color = Color(0xFFD1FAE5), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Outlined.Today, null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(state.fromDate ?: "", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                        }
                    }
                    ReportMode.MONTHLY -> IrPeriodNavRow("${monthNamesFull[state.selectedMonth - 1]} ${state.selectedYear}", { vm.prevMonth() }, { vm.nextMonth() })
                    ReportMode.YEARLY  -> IrPeriodNavRow("${state.selectedYear} ခုနှစ်", { vm.prevYear() }, { vm.nextYear() })
                    ReportMode.CUSTOM  -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DateRange, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        FilterChip(state.fromDate != null, { showFromPicker = true }, { Text(state.fromDate ?: "မှ ရက်", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                        Text("—", color = TextMuted, fontSize = 12.sp)
                        FilterChip(state.toDate != null, { showToPicker = true }, { Text(state.toDate ?: "အထိ ရက်", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // ── Loading ───────────────────────────────────────────────────────
            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        AppLoading()
                    }
                }
            } else if (state.mode == ReportMode.YEARLY) {
                // ── Yearly view ───────────────────────────────────────────────
                val ys = state.yearlySummary
                if (ys == null) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("ဒေတာမရှိပါ", color = TextMuted) } }
                } else {
                    item { YearlyTotalCard(ys) }
                    item {
                        val months = ys.months ?: emptyList()
                        if (months.isNotEmpty()) {
                            MonthlyBarChart(months = months, monthLabels = monthNames)
                        }
                    }
                    item { YearlyBreakdownTable(ys, monthNames) }
                }
            } else {
                // ── Period view (Today / Monthly / Custom) ────────────────────
                val s = state.summary
                if (s == null) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("ဒေတာမရှိပါ", color = TextMuted) } }
                } else {
                    item { IncomeSection(s) }
                    item { CostSection(s) }
                    item { ProfitCard(grossProfit = s.grossProfit ?: 0.0, expenses = s.totalExpenses ?: 0.0, netProfit = s.netProfit ?: 0.0) }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Income Section ────────────────────────────────────────────────────────────

@Composable
private fun IncomeSection(s: PeriodSummaryDTO) {
    SectionCard(title = "ဝင်ငွေ", accentColor = Color(0xFF059669)) {
        ReportRow("ရောင်းရငွေ",       s.saleRevenue ?: 0.0,      Primary,           sub = "${s.saleCount ?: 0} ကြိမ်")
        ReportRowMinus("(-) ပြန်ပေး",  s.saleReturnAmount ?: 0.0, Danger)
        DividerRow()
        ReportRow("သားတင် ရောင်းရငွေ", s.netSaleRevenue ?: 0.0,   TextMain,          bold = true)
        Spacer(Modifier.height(6.dp))
        ReportRow("ဝန်ဆောင်မှုရငွေ",   s.serviceRevenue ?: 0.0,   Violet)
        ReportRow("အခြားဝင်ငွေ",       s.otherIncome ?: 0.0,      Color(0xFFD97706))
        DividerRow()
        TotalRow("စုစုပေါင်း ဝင်ငွေ",  s.totalIncome ?: 0.0,      Color(0xFF059669))
    }
}

// ── Cost Section ──────────────────────────────────────────────────────────────

@Composable
private fun CostSection(s: PeriodSummaryDTO) {
    SectionCard(title = "ကုန်ကျစရိတ်", accentColor = Danger) {
        ReportRow("ဝယ်ယူမှု",              s.purchaseAmount ?: 0.0,       Danger)
        ReportRowMinus("(-) ပြန်ပေး",       s.purchaseReturnAmount ?: 0.0, Color(0xFF059669))
        if ((s.stockAdjLoss ?: 0.0) > 0)
            ReportRow("(+) Stock Adj Loss", s.stockAdjLoss ?: 0.0,         Warning)
        DividerRow()
        ReportRow("ကုန်ကျစရိတ် (ရောင်းကုန်)", s.netPurchaseCost ?: 0.0, TextMain, bold = true)
        Spacer(Modifier.height(6.dp))
        TotalRow("လည်ပတ်ကုန်ကျ",           s.totalExpenses ?: 0.0,        Danger)
    }
}

// ── Profit Card ───────────────────────────────────────────────────────────────

@Composable
private fun ProfitCard(grossProfit: Double, expenses: Double, netProfit: Double) {
    val isPositive = netProfit >= 0
    val profitColor = if (isPositive) Color(0xFF059669) else Danger
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = if (isPositive) Color(0xFFD1FAE5) else DangerBg),
        border    = BorderStroke(1.5.dp, profitColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ရောင်းအမြတ် (Gross)",  fontSize = 12.sp, color = TextMuted)
                Text("${irFmt(grossProfit)} Ks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("(-) လည်ပတ်ကုန်ကျ",    fontSize = 12.sp, color = TextMuted)
                Text("${irFmt(expenses)} Ks",   fontSize = 12.sp, color = Danger)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = profitColor.copy(0.3f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("အမြတ် (Net Profit)", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = profitColor)
                Text("${irFmt(netProfit)} Ks", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = profitColor)
            }
        }
    }
}

// ── Yearly Total Card ─────────────────────────────────────────────────────────

@Composable
private fun YearlyTotalCard(ys: com.sspd.servicemgmt.core.network.YearlySummaryDTO) {
    val netProfit   = ys.totalNetProfit ?: 0.0
    val isPositive  = netProfit >= 0
    val profitColor = if (isPositive) Color(0xFF059669) else Danger
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${ys.year} ခုနှစ် ခြုံငုံ", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatCard(Modifier.weight(1f), "စုစုပေါင်းဝင်ငွေ", ys.totalIncome ?: 0.0, Color(0xFF059669), Color(0xFFD1FAE5))
                MiniStatCard(Modifier.weight(1f), "ကုန်ကျစရိတ်",       ys.totalExpenses ?: 0.0, Danger, DangerBg)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = if (isPositive) Color(0xFFD1FAE5) else DangerBg)
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("နှစ်တစ်နှစ် အမြတ်", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = profitColor)
                    Text("${irFmt(netProfit)} Ks", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = profitColor)
                }
            }
        }
    }
}

// ── Monthly Bar Chart ─────────────────────────────────────────────────────────

@Composable
private fun MonthlyBarChart(months: List<MonthlyDataDTO>, monthLabels: Array<String>) {
    val maxIncome = months.maxOf { it.totalIncome ?: 0.0 }.coerceAtLeast(1.0)

    val anim = remember(months) { Animatable(0f) }
    LaunchedEffect(months) {
        anim.snapTo(0f)
        anim.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
    }
    val p = anim.value

    val incomeColor = Color(0xFF059669)
    val profitColor = Primary
    val lossColor   = Danger
    val trackColor  = Color(0xFFE5E7EB)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("လစဉ် ဝင်ငွေ / အမြတ်", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendDot("ဝင်ငွေ", incomeColor)
                LegendDot("အမြတ်", profitColor)
            }
            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                val barW   = 18.dp
                val gapW   = 6.dp
                val groupW = barW * 2 + gapW + 8.dp
                val chartW = groupW * 12

                Column {
                    Canvas(
                        modifier = Modifier
                            .width(chartW)
                            .height(140.dp)
                    ) {
                        val chartH = size.height
                        val groupPx = size.width / 12

                        // Gridlines
                        repeat(4) { i ->
                            val y = chartH * (1 - (i + 1) / 4f)
                            drawLine(trackColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                        }

                        months.forEachIndexed { idx, md ->
                            val cx = groupPx * idx + groupPx / 2

                            val income = (md.totalIncome ?: 0.0).coerceAtLeast(0.0)
                            val profit = md.netProfit ?: 0.0

                            val barWidthPx  = barW.toPx()
                            val incomeH = (income / maxIncome * chartH * p).toFloat()
                            val profitH = (Math.abs(profit) / maxIncome * chartH * p).toFloat()

                            // Income bar
                            val ix = cx - barWidthPx - 2.dp.toPx()
                            drawRoundRect(
                                color        = incomeColor.copy(0.85f),
                                topLeft      = Offset(ix, chartH - incomeH),
                                size         = Size(barWidthPx, incomeH),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )

                            // Profit bar (green if positive, red if negative)
                            val px = cx + 2.dp.toPx()
                            drawRoundRect(
                                color        = if (profit >= 0) profitColor.copy(0.8f) else lossColor.copy(0.8f),
                                topLeft      = Offset(px, chartH - profitH),
                                size         = Size(barWidthPx, profitH),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }
                    }

                    // Month labels
                    Row(modifier = Modifier.width(chartW)) {
                        months.forEachIndexed { idx, md ->
                            val mIdx = (md.month ?: 1) - 1
                            Text(
                                text     = if (mIdx in 0..11) monthLabels[mIdx] else "-",
                                modifier = Modifier.width(groupW),
                                fontSize = 9.sp,
                                color    = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Yearly Breakdown Table ────────────────────────────────────────────────────

@Composable
private fun YearlyBreakdownTable(ys: com.sspd.servicemgmt.core.network.YearlySummaryDTO, monthLabels: Array<String>) {
    val months = ys.months ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("လ အလိုက် အချက်အလက်", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            months.forEach { md ->
                val mIdx = (md.month ?: 1) - 1
                val netP  = md.netProfit ?: 0.0
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(if (mIdx in 0..11) monthLabels[mIdx] else "-", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMain, modifier = Modifier.width(32.dp))
                    Text("${irFmt(md.totalIncome ?: 0.0)} Ks", fontSize = 11.sp, color = Color(0xFF059669))
                    Text(
                        "${irFmt(netP)} Ks",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (netP >= 0) Primary else Danger
                    )
                }
                HorizontalDivider(color = BorderColor)
            }
        }
    }
}

// ── Small reusable composables ────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, accentColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ReportRow(label: String, amount: Double, color: Color, bold: Boolean = false, sub: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(label, fontSize = 12.sp, color = if (bold) TextMain else TextMuted, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
            if (sub != null) Text(sub, fontSize = 10.sp, color = TextMuted)
        }
        Text("${irFmt(amount)} Ks", fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

@Composable
private fun ReportRowMinus(label: String, amount: Double, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = TextMuted)
        Text("- ${irFmt(amount)} Ks", fontSize = 12.sp, color = color)
    }
}

@Composable
private fun TotalRow(label: String, amount: Double, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text("${irFmt(amount)} Ks", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
private fun DividerRow() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = BorderColor)
}

@Composable
private fun MiniStatCard(modifier: Modifier, label: String, amount: Double, color: Color, bg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
            Text("${irFmt(amount)} Ks", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun IrPeriodNavRow(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onPrev) { Icon(Icons.Outlined.ChevronLeft, "ယခင်", tint = Color(0xFF059669)) }
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
            IconButton(onClick = onNext) { Icon(Icons.Outlined.ChevronRight, "နောက်", tint = Color(0xFF059669)) }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color) }
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}

private fun irFmt(v: Double) = String.format("%,.0f", v)

private fun irMsToDate(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(millis))
}

private fun irDateToMs(dateStr: String): Long = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    sdf.parse(dateStr)?.time ?: 0L
} catch (_: Exception) { 0L }
