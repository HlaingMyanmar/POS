package com.sspd.servicemgmt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.StaffReportViewModel
import kotlinx.coroutines.delay

private val MM_MONTHS = arrayOf(
    "ဇန်နဝါရီ","ဖေဖော်ဝါရီ","မတ်","ဧပြီ","မေ","ဇွန်",
    "ဇူလိုင်","သြဂုတ်","စက်တင်ဘာ","အောက်တိုဘာ","နိုဝင်ဘာ","ဒီဇင်ဘာ"
)

private fun displayMonth(ym: String): String {
    val (y, m) = ym.split("-").map { it.toInt() }
    return "${MM_MONTHS[m - 1]} $y"
}

private fun Long.fmt() = String.format("%,d", this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffReportScreen(onBack: () -> Unit) {
    val vm: StaffReportViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) { vm.load(); delay(30_000) }
    }

    val canNext = state.month < StaffReportViewModel.nowYM()

    val totalSales   = state.items.sumOf { it.salesAmount }
    val totalService = state.items.sumOf { it.serviceJobsAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဝန်ထမ်းစွမ်းဆောင်ရည်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary, titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {

            // Month navigator
            Surface(color = CardBg) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { vm.prevMonth() }) {
                        Icon(Icons.Outlined.ChevronLeft, "ယခင်", tint = Primary)
                    }
                    Text(displayMonth(state.month), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = { if (canNext) vm.nextMonth() }, enabled = canNext) {
                        Icon(Icons.Outlined.ChevronRight, null, tint = if (canNext) Primary else BorderColor)
                    }
                }
                HorizontalDivider(color = BorderColor)
            }

            // Summary cards
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(Modifier.weight(1f), "စုစုပေါင်း ရောင်းချမှု", "${totalSales.fmt()} Ks", Primary)
                SummaryCard(Modifier.weight(1f), "ဝန်ဆောင်မှု ဝင်ငွေ",  "${totalService.fmt()} Ks", Violet)
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppLoading()
                }
            } else if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ဤလအတွက် ဒေတာမရှိပါ", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items) { r -> StaffCard(r) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.5.dp, color)
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun StaffCard(r: com.sspd.servicemgmt.api.StaffReportDTO) {
    val (roleBg, roleColor, roleMM) = when (r.staffRole.uppercase()) {
        "TECHNICIAN" -> Triple(VioletBg, Violet, "နည်းပညာဆရာ")
        "CASHIER"    -> Triple(SuccessBg, Success, "ငွေကောက်")
        "MANAGER"    -> Triple(PrimaryLight, Primary, "မန်နေဂျာ")
        "ADMIN"      -> Triple(WarningBg, Warning, "Admin")
        else         -> Triple(BorderColor, TextMuted, r.staffRole.ifEmpty { "ဝန်ထမ်း" })
    }
    val hasSales = r.salesCount > 0
    val hasJobs  = r.serviceJobsCount > 0

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (r.staffName.firstOrNull()?.uppercaseChar() ?: '?').toString(),
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold
                    )
                }
                Column {
                    Text(r.staffName, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(3.dp))
                    Surface(color = roleBg, shape = RoundedCornerShape(6.dp)) {
                        Text(roleMM, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = roleColor)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderColor)

            // Sales section
            if (hasSales) {
                SectionHeader("ရောင်းချမှု", Icons.Outlined.Receipt, Primary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox(Modifier.weight(1f), "အရေအတွက်", "${r.salesCount}")
                    StatBox(Modifier.weight(1f), "ရောင်းရငွေ", "${r.salesAmount.fmt()} Ks", bold = true)
                }
            }

            if (hasSales && hasJobs) Spacer(Modifier.height(10.dp))

            // Service section
            if (hasJobs) {
                SectionHeader("ဝန်ဆောင်မှု Jobs", Icons.Outlined.Build, Violet)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox(Modifier.weight(1f), "စုစုပေါင်း",  "${r.serviceJobsCount}")
                    StatBox(Modifier.weight(1f), "ပြီးဆုံး",     "${r.completedJobsCount}",   color = Success)
                    StatBox(Modifier.weight(1f), "လုပ်ဆဲ",       "${r.inProgressJobsCount}",  color = Violet)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox(Modifier.weight(1f), "ပယ်ဖျက်",  "${r.cancelledJobsCount}", color = Danger)
                    StatBox(Modifier.weight(1f), "ပြန်ပြင်",  "${r.reworkJobsCount}",   color = Warning)
                    StatBox(Modifier.weight(1f), "ဝင်ငွေ",    "${r.serviceJobsAmount.fmt()} Ks", bold = true)
                }

                // Progress bar
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ပြီးနှုန်း", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(50.dp))
                    Box(
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(BorderColor)
                    ) {
                        val pct = (r.completionRate.coerceIn(0.0, 100.0) / 100f).toFloat()
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct).background(Success))
                    }
                    Text("${"%.1f".format(r.completionRate)}%", fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold, color = Success,
                        modifier = Modifier.width(40.dp))
                }
            }

            if (!hasSales && !hasJobs) {
                Text("ဤလတွင် လုပ်ဆောင်မှုမရှိပါ",
                    fontSize = 12.sp, color = TextMuted, modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            color = color, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun StatBox(
    modifier: Modifier,
    label: String,
    value: String,
    bold: Boolean = false,
    color: Color = TextMuted
) {
    Surface(modifier = modifier, color = ScreenBg, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(value, fontSize = 12.sp,
                fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (bold) TextMain else color)
        }
    }
}

