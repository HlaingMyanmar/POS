package com.sspd.servicemgmt.feature.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.*

private val CapColor = Color(0xFF0369A1)
private val CapBg    = Color(0xFFE0F2FE)

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun Double.fmtKs() = String.format("%,.0f", this) + " Ks"

private fun typeLabel(type: String) = when (type) {
    "ASSET"     -> "ပိုင်ဆိုင်မှု"
    "LIABILITY" -> "ပေးဆပ်ရမည်"
    "EQUITY"    -> "ရင်းနှီးငွေ"
    "INCOME"    -> "ဝင်ငွေ"
    "EXPENSE"   -> "ကုန်ကျ"
    else        -> type
}

private fun typeColor(type: String) = when (type) {
    "ASSET"     -> Color(0xFF16A34A) to Color(0xFFF0FDF4)
    "LIABILITY" -> Danger            to DangerBg
    "EQUITY"    -> Primary           to PrimaryLight
    "INCOME"    -> Color(0xFF0891B2) to Color(0xFFECFEFF)
    "EXPENSE"   -> Warning           to WarningBg
    else        -> TextMuted         to Color(0xFFF1F5F9)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpeningBalanceScreen(onBack: () -> Unit) {
    val vm: OpeningBalanceViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    // ── Edit bottom sheet ─────────────────────────────────────────────────────
    state.editRow?.let { row ->
        EditBalanceSheet(
            row       = row,
            vm        = vm,
            state     = state,
            onDismiss = vm::dismissEdit
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opening Balance / လုပ်ငန်း Capital", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = CapColor,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }

        val groups  = state.accounts.groupBy { it.accountType }
        val summary = vm.capitalSummary(state.accounts)

        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding  = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Error banner ───────────────────────────────────────────────
            state.error?.let { err ->
                item {
                    Surface(color = DangerBg, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Danger.copy(0.3f))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(err, color = Danger, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = vm::clearError, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Close, null, tint = Danger, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // ── Capital Summary Card ───────────────────────────────────────
            item {
                Card(
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = CapColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.AccountBalance, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Text("လုပ်ငန်း Capital အကျဉ်းချုပ်", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color.White)
                        }

                        HorizontalDivider(color = Color.White.copy(0.25f))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            CapitalMetric("ပိုင်ဆိုင်မှု",  summary.totalAssets,      Color.White, Color.White.copy(0.7f))
                            VerticalDivider(modifier = Modifier.height(50.dp), color = Color.White.copy(0.25f))
                            CapitalMetric("ပေးဆပ်ရမည်", summary.totalLiabilities,  Color(0xFFFCA5A5), Color.White.copy(0.7f))
                            VerticalDivider(modifier = Modifier.height(50.dp), color = Color.White.copy(0.25f))
                            CapitalMetric("Net Capital",  summary.netCapital,         Color(0xFF86EFAC), Color.White.copy(0.7f))
                        }

                        // Net capital bar
                        if (summary.totalAssets > 0) {
                            val fraction = (summary.netCapital / summary.totalAssets).coerceIn(0.0, 1.0).toFloat()
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Capital Ratio", fontSize = 10.sp, color = Color.White.copy(0.65f))
                                LinearProgressIndicator(
                                    progress    = { fraction },
                                    modifier    = Modifier.fillMaxWidth().height(6.dp),
                                    color       = Color(0xFF86EFAC),
                                    trackColor  = Color.White.copy(0.2f)
                                )
                                Text(
                                    "${String.format("%.1f", fraction * 100)}% — ပိုင်ဆိုင်မှုတွင် ကိုယ်ပိုင်ငွေ ပါဝင်မှု",
                                    fontSize = 10.sp,
                                    color    = Color.White.copy(0.65f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Instructions ───────────────────────────────────────────────
            item {
                Surface(
                    color  = Color(0xFFFFF7ED),
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFED7AA))
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Info, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Opening Balance ထည့်နည်း", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF92400E))
                            Text("• ကိုယ်ပိုင် Account တစ်ခုချင်းဆီကို နှိပ်ပြီး Opening Balance ထည့်ပါ", fontSize = 11.sp, color = Color(0xFFB45309))
                            Text("• ASSET = ငွေသား, ဘဏ်, ကုန်ပစ္စည်းတန်ဖိုး, ဖောက်သည်ကြွေး", fontSize = 11.sp, color = Color(0xFFB45309))
                            Text("• LIABILITY = Supplier ကြွေး, ချေးငွေ", fontSize = 11.sp, color = Color(0xFFB45309))
                        }
                    }
                }
            }

            // ── Accounts grouped by type ───────────────────────────────────
            val orderedTypes = listOf("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE")
            orderedTypes.forEach { type ->
                val rows = groups[type] ?: return@forEach
                item {
                    val (color, bg) = typeColor(type)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    typeLabel(type),
                                    modifier  = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize  = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color     = color
                                )
                            }
                            Text("(${rows.size} accounts)", fontSize = 10.sp, color = TextMuted)
                        }
                        val typeTotal = rows.sumOf { it.current }
                        Text(typeTotal.fmtKs(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
                    }
                }
                items(rows) { row ->
                    AccountBalanceCard(row = row, onClick = { vm.openEdit(row) })
                }
            }

            if (state.accounts.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.AccountBalance, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Text("Chart of Accounts မရှိသေးပါ", color = TextMuted)
                            Text("Settings > Chart of Accounts မှ Account များ ဖန်တီးပါ", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Capital Metric widget ─────────────────────────────────────────────────────
@Composable
private fun CapitalMetric(label: String, amount: Double, valueColor: Color, labelColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 10.sp, color = labelColor)
        Text(
            amount.fmtKs(),
            fontSize   = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = valueColor,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
    }
}

// ── Account balance row card ──────────────────────────────────────────────────
@Composable
private fun AccountBalanceCard(
    row:     OpeningBalanceViewModel.AccountRow,
    onClick: () -> Unit
) {
    val (color, bg) = typeColor(row.accountType)

    Card(
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        border   = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier         = Modifier.size(36.dp).background(bg, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(row.accountCode.take(3).ifEmpty { row.accountName.take(1) }, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = color)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.accountName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Opening: ${row.opening.fmtKs()}", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.current.fmtKs(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = color)
                Surface(color = CapBg, shape = RoundedCornerShape(4.dp)) {
                    Text("ပြင်ရန်", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = CapColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Edit bottom sheet ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBalanceSheet(
    row:      OpeningBalanceViewModel.AccountRow,
    vm:       OpeningBalanceViewModel,
    state:    OpeningBalanceViewModel.UiState,
    onDismiss: () -> Unit
) {
    var showStaffSheet by remember { mutableStateOf(false) }
    var showPmSheet    by remember { mutableStateOf(false) }

    if (showStaffSheet) {
        ModalBottomSheet(onDismissRequest = { showStaffSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                Text("ဝန်ထမ်း ရွေးပါ", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                state.staffList.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.selectStaff(s); showStaffSheet = false }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(s.name, fontSize = 13.sp, color = TextMain)
                        if (state.selectedStaff?.id == s.id) Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(16.dp))
                    }
                    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showPmSheet) {
        ModalBottomSheet(onDismissRequest = { showPmSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                Text("ငွေပေးချေနည်း ရွေးပါ", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                state.paymentMethods.forEach { pm ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.selectPm(pm); showPmSheet = false }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(pm.methodName, fontSize = 13.sp, color = TextMain)
                        if (state.selectedPm?.id == pm.id) Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(16.dp))
                    }
                    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (color, bg) = typeColor(row.accountType)
                Box(Modifier.size(36.dp).background(bg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text(row.accountCode.take(3).ifEmpty { row.accountName.take(1) }, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = color)
                }
                Column {
                    Text(row.accountName, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = TextMain)
                    Text("${typeLabel(row.accountType)} — Opening Balance ပြင်ဆင်ရန်", fontSize = 11.sp, color = TextMuted)
                }
            }

            // Save error
            state.saveError?.let { err ->
                Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                    Text(err, modifier = Modifier.fillMaxWidth().padding(10.dp), color = Danger, fontSize = 12.sp)
                }
            }

            // Amount field
            OutlinedTextField(
                value         = state.editAmountStr,
                onValueChange = vm::setEditAmount,
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Opening Balance (Ks)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine    = true,
                shape         = RoundedCornerShape(10.dp)
            )

            // Staff picker
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showStaffSheet = true },
                shape    = RoundedCornerShape(10.dp),
                border   = BorderStroke(1.dp, BorderColor)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("ဝန်ထမ်း", fontSize = 11.sp, color = TextMuted)
                        Text(state.selectedStaff?.name ?: "ရွေးပါ...", fontSize = 13.sp, color = if (state.selectedStaff != null) TextMain else TextMuted)
                    }
                    Icon(Icons.Outlined.ExpandMore, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
            }

            // Payment method picker
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showPmSheet = true },
                shape    = RoundedCornerShape(10.dp),
                border   = BorderStroke(1.dp, BorderColor)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("ငွေပေးချေနည်း", fontSize = 11.sp, color = TextMuted)
                        Text(state.selectedPm?.methodName ?: "ရွေးပါ...", fontSize = 13.sp, color = if (state.selectedPm != null) TextMain else TextMuted)
                    }
                    Icon(Icons.Outlined.ExpandMore, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
            }

            // Save button
            Button(
                onClick  = vm::save,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = CapColor),
                enabled  = !state.saving
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Opening Balance သိမ်းရန်", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
