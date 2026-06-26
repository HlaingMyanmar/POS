package com.sspd.servicemgmt.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.ReportMode
import com.sspd.servicemgmt.ui.viewmodel.ReportViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(onBack: () -> Unit) {
    val vm: ReportViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    val monthNames = remember {
        arrayOf("ဇန်နဝါရီ","ဖေဖော်ဝါရီ","မတ်","ဧပြီ","မေ","ဇွန်","ဇူလိုင်","သြဂုတ်","စက်တင်ဘာ","အောက်တိုဘာ","နိုဝင်ဘာ","ဒီဇင်ဘာ")
    }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    if (showFromPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = state.fromDate?.let { dateToMs(it) })
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = { dpState.selectedDateMillis?.let { vm.setFromDate(msToDate(it)) }; showFromPicker = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    if (showToPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = state.toDate?.let { dateToMs(it) })
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = { dpState.selectedDateMillis?.let { vm.setToDate(msToDate(it)) }; showToPicker = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    val salesTotal = state.sales.sumOf { it.netAmount ?: 0.0 }
    val jobsTotal  = state.jobs.sumOf  { it.netAmount ?: 0.0 }
    val grandTotal = salesTotal + jobsTotal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ကိန်းဂဏာန်း", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                actions = { IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
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
                    listOf(
                        ReportMode.TODAY   to "ယနေ့",
                        ReportMode.MONTHLY to "လစဉ်",
                        ReportMode.YEARLY  to "နှစ်စဉ်",
                        ReportMode.CUSTOM  to "Custom"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = state.mode == mode,
                            onClick  = { vm.selectMode(mode) },
                            label    = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Period selector ───────────────────────────────────────────────
            item {
                when (state.mode) {
                    ReportMode.TODAY -> Surface(
                        color    = PrimaryLight,
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Outlined.Today, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(state.fromDate ?: "", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Primary)
                        }
                    }
                    ReportMode.MONTHLY -> PeriodNavRow(
                        label  = "${monthNames[state.selectedMonth - 1]} ${state.selectedYear}",
                        onPrev = { vm.prevMonth() },
                        onNext = { vm.nextMonth() }
                    )
                    ReportMode.YEARLY -> PeriodNavRow(
                        label  = "${state.selectedYear} ခုနှစ်",
                        onPrev = { vm.prevYear() },
                        onNext = { vm.nextYear() }
                    )
                    ReportMode.CUSTOM -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.DateRange, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        FilterChip(
                            selected = state.fromDate != null,
                            onClick  = { showFromPicker = true },
                            label    = { Text(state.fromDate ?: "မှ ရက်", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        Text("—", color = TextMuted, fontSize = 12.sp)
                        FilterChip(
                            selected = state.toDate != null,
                            onClick  = { showToPicker = true },
                            label    = { Text(state.toDate ?: "အထိ ရက်", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
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
            } else {

                // ── KPI cards ─────────────────────────────────────────────────
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IncomeCard(Modifier.weight(1f), "အရောင်းရငွေ",     Icons.Outlined.Receipt, state.sales.size, salesTotal, Primary, PrimaryLight)
                        IncomeCard(Modifier.weight(1f), "ဝန်ဆောင်မှုရငွေ", Icons.Outlined.Build,   state.jobs.size,  jobsTotal,  Violet,  VioletBg)
                    }
                }

                // ── Donut chart ───────────────────────────────────────────────
                item {
                    DonutChartCard(salesTotal = salesTotal, jobsTotal = jobsTotal, grandTotal = grandTotal)
                }

                // ── Grand total card ──────────────────────────────────────────
                item {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = CardDefaults.cardColors(containerColor = Primary),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("စုစုပေါင်း ဝင်ငွေ", fontSize = 12.sp, color = Color.White.copy(0.75f))
                                Text("${state.sales.size + state.jobs.size} ကြိမ်", fontSize = 11.sp, color = Color.White.copy(0.6f))
                            }
                            Text("${fmtD(grandTotal)} Ks", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }

                // ── Rankings header ───────────────────────────────────────────
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("ရောင်းအကောင်းဆုံး", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, letterSpacing = 0.9.sp)
                        Text(state.fromDate?.take(7) ?: "", fontSize = 11.sp, color = TextMuted)
                    }
                }

                if (state.rankings.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("ဒေတာမရှိပါ", color = TextMuted)
                        }
                    }
                } else {
                    val maxAmount = state.rankings.maxOf { (it.totalAmount ?: 0L).toDouble() }.coerceAtLeast(1.0)
                    itemsIndexed(state.rankings) { idx, item ->
                        val medal  = when (idx) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> null }
                        val rankBg = when (idx) { 0 -> Color(0xFFFFFBEB); 1 -> Color(0xFFF8FAFC); 2 -> Color(0xFFFFF7ED); else -> CardBg }
                        val barFraction = ((item.totalAmount ?: 0L).toDouble() / maxAmount).toFloat()

                        Card(
                            shape     = RoundedCornerShape(14.dp),
                            colors    = CardDefaults.cardColors(containerColor = rankBg),
                            border    = BorderStroke(if (idx < 3) 1.5.dp else 1.dp, if (idx < 3) Color(0xFFD97706) else BorderColor),
                            elevation = CardDefaults.cardElevation(if (idx == 0) 4.dp else 1.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (medal != null) {
                                        Text(medal, fontSize = 28.sp)
                                    } else {
                                        Box(
                                            Modifier.size(36.dp).clip(CircleShape).background(BorderColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${idx + 1}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(item.staffName ?: "-", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                                        Text("${item.salesCount ?: 0} ကြိမ်", fontSize = 11.sp, color = TextMuted)
                                    }
                                    Text("${(item.totalAmount ?: 0L).fmtL()} Ks", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                                }
                                Spacer(Modifier.height(8.dp))
                                RankBar(fraction = barFraction, color = if (idx == 0) Color(0xFFD97706) else Primary)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Donut Chart Card ──────────────────────────────────────────────────────────

@Composable
private fun DonutChartCard(salesTotal: Double, jobsTotal: Double, grandTotal: Double) {
    val total = salesTotal + jobsTotal
    if (total <= 0.0) return

    val salesFrac = (salesTotal / total).toFloat()
    val jobsFrac  = (jobsTotal  / total).toFloat()

    val anim = remember(salesTotal, jobsTotal) { Animatable(0f) }
    LaunchedEffect(salesTotal, jobsTotal) {
        anim.snapTo(0f)
        anim.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
    }
    val p = anim.value

    val salesColor   = Primary
    val serviceColor = Violet
    val trackColor   = Color(0xFFE5E7EB)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ဝင်ငွေ ခွဲခြမ်းချက်", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Donut
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(150.dp)) {
                        val strokeW = 28.dp.toPx()
                        val inset   = strokeW / 2f
                        val arcSize = Size(size.width - strokeW, size.height - strokeW)
                        val topLeft = Offset(inset, inset)

                        // Track
                        drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokeW))

                        val salesSweep = 360f * salesFrac * p
                        val jobsSweep  = 360f * jobsFrac  * p

                        if (salesSweep > 0.5f)
                            drawArc(color = salesColor, startAngle = -90f, sweepAngle = salesSweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokeW, cap = StrokeCap.Butt))
                        if (jobsSweep > 0.5f)
                            drawArc(color = serviceColor, startAngle = -90f + salesSweep, sweepAngle = jobsSweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokeW, cap = StrokeCap.Butt))
                    }

                    // Center label
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("စုစုပေါင်း", fontSize = 9.sp, color = TextMuted)
                        Text("${fmtD(grandTotal)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                        Text("Ks", fontSize = 9.sp, color = TextMuted)
                    }
                }

                // Legend
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem(
                        label   = "အရောင်းရငွေ",
                        pct     = "${(salesFrac * 100).toInt()}%",
                        amount  = fmtD(salesTotal),
                        color   = salesColor
                    )
                    LegendItem(
                        label   = "ဝန်ဆောင်မှုရငွေ",
                        pct     = "${(jobsFrac * 100).toInt()}%",
                        amount  = fmtD(jobsTotal),
                        color   = serviceColor
                    )
                }
            }
        }
    }
}

// ── Animated rank bar ─────────────────────────────────────────────────────────

@Composable
private fun RankBar(fraction: Float, color: Color) {
    val anim = remember(fraction) { Animatable(0f) }
    LaunchedEffect(fraction) {
        anim.snapTo(0f)
        anim.animateTo(fraction, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }
    val p = anim.value

    val trackColor = Color(0xFFE5E7EB)

    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        val r = 3.dp.toPx()
        drawRoundRect(color = trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        if (p > 0f)
            drawRoundRect(
                color       = color,
                size        = Size(size.width * p, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────

@Composable
private fun LegendItem(label: String, pct: String, amount: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column {
            Text(label, fontSize = 11.sp, color = TextMain, fontWeight = FontWeight.SemiBold)
            Text("$pct  •  $amount Ks", fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
private fun PeriodNavRow(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onPrev) { Icon(Icons.Outlined.ChevronLeft, "ယခင်", tint = Primary) }
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
            IconButton(onClick = onNext) { Icon(Icons.Outlined.ChevronRight, "နောက်", tint = Primary) }
        }
    }
}

@Composable
private fun IncomeCard(modifier: Modifier, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, amount: Double, color: Color, bg: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
            Text("$count ကြိမ်", fontSize = 11.sp, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text("${fmtD(amount)} Ks", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

private fun fmtD(v: Double)  = String.format("%,.0f", v)
private fun Long.fmtL()      = String.format("%,d", this)

private fun msToDate(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(millis))
}

private fun dateToMs(dateStr: String): Long = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    sdf.parse(dateStr)?.time ?: 0L
} catch (_: Exception) { 0L }

