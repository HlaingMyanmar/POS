package com.sspd.servicemgmt.feature.sale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.network.SaleDTO
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.ui.component.AppListRow
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppPickerSheet
import com.sspd.servicemgmt.core.ui.component.AppSearchField

import com.sspd.servicemgmt.feature.salereturn.SaleReturnListViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleListScreen(
    onBack:        () -> Unit,
    onSaleClick:   (Int) -> Unit = {},
    onNewSale:     () -> Unit    = {},
    onReturnClick: (Int) -> Unit = {},
    onNewReturn:   () -> Unit    = {}
) {
    val vm: SaleListViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    val returnVm: SaleReturnListViewModel = viewModel()
    val returnState by returnVm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) { vm.load(); delay(30_000) }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    var showReturnFromPicker by remember { mutableStateOf(false) }
    var showReturnToPicker   by remember { mutableStateOf(false) }

    LaunchedEffect(state.paySuccess) {
        state.paySuccess?.let { snackbar.showSnackbar("$it — ငွေဆပ်မှု အောင်မြင်ပါသည် ✓"); vm.clearPaySuccess() }
    }
    LaunchedEffect(state.payError) {
        state.payError?.let { snackbar.showSnackbar(it); vm.clearPayError() }
    }

    // ── Pay dialog ───────────────────────────────────────────────────────────
    state.payTargetSale?.let { sale ->
        QuickPayDialog(
            sale           = sale,
            paymentMethods = state.paymentMethods,
            paying         = state.paying,
            onDismiss      = { vm.dismissPayDialog() },
            onPay          = { amount, methodId, note ->
                sale.id?.let { vm.payDue(it, amount, methodId, note) }
            }
        )
    }

    // ── Date pickers ─────────────────────────────────────────────────────────
    if (showFromPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = state.fromDate?.let { dateToMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setFromDate(millisToDate(it)) }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = dpState) }
    }

    if (showToPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = state.toDate?.let { dateToMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setToDate(millisToDate(it)) }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = dpState) }
    }

    if (showReturnFromPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = returnState.fromDate?.let { dateToMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showReturnFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { returnVm.setFromDate(millisToDate(it)) }
                    showReturnFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showReturnFromPicker = false }) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = dpState) }
    }

    if (showReturnToPicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = returnState.toDate?.let { dateToMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showReturnToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { returnVm.setToDate(millisToDate(it)) }
                    showReturnToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showReturnToPicker = false }) { Text("ပယ်ဖျက်") } }
        ) { DatePicker(state = dpState) }
    }

    // ── Filtered lists ───────────────────────────────────────────────────────
    val fromDate = state.fromDate
    val toDate   = state.toDate
    val dateOk: (SaleDTO) -> Boolean = { sale ->
        val d = sale.saleDate?.take(10) ?: ""
        (fromDate == null || d >= fromDate) &&
        (toDate   == null || d <= toDate)
    }
    val searchOk: (SaleDTO) -> Boolean = { sale ->
        state.search.isBlank() ||
        sale.saleCode?.contains(state.search, true) == true ||
        sale.customerName?.contains(state.search, true) == true
    }

    val searching = state.search.isNotBlank()
    val saleList = state.items.filter { searchOk(it) && (searching || dateOk(it)) }
    val dueList  = state.items.filter { (it.dueAmount ?: 0.0) > 0 && searchOk(it) }

    val returnFromDate = returnState.fromDate
    val returnToDate   = returnState.toDate
    val returnDateOk: (com.sspd.servicemgmt.core.network.SaleReturnDTO) -> Boolean = { ret ->
        val d = ret.returnDate?.take(10) ?: ""
        (returnFromDate == null || d >= returnFromDate) &&
        (returnToDate   == null || d <= returnToDate)
    }
    val returnSearchOk: (com.sspd.servicemgmt.core.network.SaleReturnDTO) -> Boolean = { ret ->
        returnState.search.isBlank() ||
        ret.returnCode?.contains(returnState.search, true) == true ||
        ret.customerName?.contains(returnState.search, true) == true ||
        ret.saleCode?.contains(returnState.search, true) == true
    }
    val returnList = returnState.items.filter { returnSearchOk(it) && returnDateOk(it) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("အရောင်းဆိုင်ရာ", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary, titleContentColor = OnPrimary
                )
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> ExtendedFloatingActionButton(
                    onClick        = onNewSale,
                    containerColor = Primary,
                    contentColor   = Color.White,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("New Sale", fontWeight = FontWeight.Bold) }
                )
                2 -> FloatingActionButton(onClick = onNewReturn, containerColor = Danger) {
                    Icon(Icons.Outlined.Add, null, tint = Color.White)
                }
                else -> {}
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {

            // ── Tabs ─────────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = CardBg,
                contentColor     = Primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text = {
                        Text(
                            "ရောင်းချမှု စာရင်း",
                            fontWeight = if (selectedTab == 0) FontWeight.ExtraBold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "အကြွေး",
                                fontWeight = if (selectedTab == 1) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                            if (dueList.isNotEmpty()) {
                                Surface(color = Danger, shape = RoundedCornerShape(10.dp)) {
                                    Text(
                                        "${dueList.size}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick  = { selectedTab = 2 },
                    text = {
                        Text(
                            "ပြန်ပေးခြင်း",
                            fontWeight = if (selectedTab == 2) FontWeight.ExtraBold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            AppSearchField(
                value = if (selectedTab == 2) returnState.search else state.search,
                onValueChange = {
                    if (selectedTab == 2) returnVm.setSearch(it) else vm.setSearch(it)
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = "ရှာဖွေရန်..."
            )

            // ── Date filter row (sales tab only) ────────────────────────────
            if (selectedTab == 0) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.DateRange, null,
                    tint = TextMuted, modifier = Modifier.size(16.dp)
                )
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

            // ── Date filter row (returns tab) ────────────────────────────────
            if (selectedTab == 2) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.DateRange, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                FilterChip(
                    selected  = returnState.fromDate != null,
                    onClick   = { showReturnFromPicker = true },
                    label     = { Text(returnState.fromDate ?: "မှ ရက်", fontSize = 11.sp) },
                    modifier  = Modifier.weight(1f)
                )
                Text("—", color = TextMuted, fontSize = 12.sp)
                FilterChip(
                    selected  = returnState.toDate != null,
                    onClick   = { showReturnToPicker = true },
                    label     = { Text(returnState.toDate ?: "အထိ ရက်", fontSize = 11.sp) },
                    modifier  = Modifier.weight(1f)
                )
                FilterChip(
                    selected  = false,
                    onClick   = { returnVm.setToday() },
                    label     = { Text("Today", fontSize = 11.sp) }
                )
                if (returnState.fromDate != null || returnState.toDate != null) {
                    IconButton(
                        onClick  = { returnVm.clearDateFilter() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Clear, "ရှင်းရန်", tint = Danger, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── Summary bar (sales tab) ──────────────────────────────────────
            if (selectedTab == 0 && saleList.isNotEmpty()) {
                val totalNet  = saleList.sumOf { it.netAmount  ?: 0.0 }
                val totalPaid = saleList.sumOf { it.paidAmount ?: 0.0 }
                val totalDue  = saleList.sumOf { it.dueAmount  ?: 0.0 }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(containerColor = CardBg),
                    border   = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryCol("ရောင်းချမှု", "${saleList.size} ခု", TextMuted)
                        SummaryCol("စုစုပေါင်း",  "${fmtD(totalNet)} Ks",  Primary)
                        SummaryCol("ပေးပြီး",     "${fmtD(totalPaid)} Ks", Success)
                        if (totalDue > 0)
                            SummaryCol("ကျန်ငွေ", "${fmtD(totalDue)} Ks", Danger)
                    }
                }
            }

            // ── List body ────────────────────────────────────────────────────
            if (selectedTab == 2) {
                if (returnState.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppLoading()
                    }
                } else if (returnList.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.AssignmentReturn, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("ပြန်လည်ခံယူမှု မရှိသေးပါ", color = TextMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(returnList, key = { index, it -> it.id ?: "ret-$index-${it.returnCode}" }) { _, ret ->
                            Card(
                                shape    = RoundedCornerShape(12.dp),
                                colors   = CardDefaults.cardColors(containerColor = CardBg),
                                border   = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.clickable { ret.id?.let { onReturnClick(it) } }
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(ret.returnCode ?: "#${ret.id}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                                            Text(ret.customerName ?: "—", fontSize = 13.sp, color = TextMain)
                                        }
                                        Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                                            Text(
                                                "${String.format("%,.0f", ret.totalReturnAmount ?: 0.0)} Ks",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Danger
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Receipt, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                            Text(ret.saleCode ?: "—", fontSize = 11.sp, color = TextMuted)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.CalendarMonth, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                            Text(ret.returnDate?.take(10) ?: "—", fontSize = 11.sp, color = TextMuted)
                                        }
                                    }
                                    if (!ret.reason.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(ret.reason, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            } else {
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppLoading()
                    }
                } else {
                    val displayList = if (selectedTab == 0) saleList else dueList

                    if (displayList.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (selectedTab == 1) Icons.Outlined.CheckCircle else Icons.Outlined.ReceiptLong,
                                    null, tint = TextMuted, modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (selectedTab == 1) "အကြွေး စာရင်း မရှိပါ ✓" else "ဒေတာမရှိပါ",
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedTab == 1) {
                                item {
                                    val totalDue = dueList.sumOf { it.dueAmount ?: 0.0 }
                                    Surface(
                                        color    = DangerBg,
                                        shape    = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "စုစုပေါင်း ကျန်ငွေ",
                                                fontSize = 13.sp, color = Danger, fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "${fmtD(totalDue)} Ks",
                                                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Danger
                                            )
                                        }
                                    }
                                }
                            }

                            itemsIndexed(displayList, key = { index, it -> it.id ?: "sale-$index-${it.saleCode}" }) { _, sale ->
                                SaleCard(
                                    sale        = sale,
                                    showDueOnly = selectedTab == 1,
                                    onClick     = { sale.id?.let { onSaleClick(it) } },
                                    onPayClick  = if (selectedTab == 1 && (sale.dueAmount ?: 0.0) > 0)
                                        { { vm.showPayDialog(sale) } } else null
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ── SaleCard ──────────────────────────────────────────────────────────────────

@Composable
private fun SaleCard(
    sale:        SaleDTO,
    showDueOnly: Boolean       = false,
    onClick:     () -> Unit,
    onPayClick:  (() -> Unit)?  = null
) {
    Card(
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        border   = BorderStroke(
            if (showDueOnly) 1.5.dp else 1.dp,
            if (showDueOnly) Danger else BorderColor
        ),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sale.saleCode ?: "#${sale.id}",
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(sale.customerName ?: "Customer", fontSize = 13.sp, color = TextMain)
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Person, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                        Text(sale.staffName ?: "-", fontSize = 11.sp, color = TextMuted)
                        Text("•", fontSize = 11.sp, color = TextMuted)
                        Text(sale.saleDate?.take(10) ?: "-", fontSize = 11.sp, color = TextMuted)
                    }
                    if ((sale.dueAmount ?: 0.0) > 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Outlined.Warning, null, tint = Danger, modifier = Modifier.size(12.dp))
                            Text(
                                "ကျန်ငွေ: ${fmtD(sale.dueAmount)} Ks",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${fmtD(sale.netAmount)} Ks", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                    Spacer(Modifier.height(4.dp))
                    StatusBadge(sale.paymentStatus)
                }
            }

            if (onPayClick != null) {
                HorizontalDivider(color = BorderColor)
                Button(
                    onClick        = onPayClick,
                    modifier       = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    shape          = RoundedCornerShape(8.dp),
                    colors         = ButtonDefaults.buttonColors(containerColor = Danger),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Outlined.Payment, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ကျန်ငွေ ${fmtD(sale.dueAmount)} Ks ဆပ်မည်",
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// ── QuickPayDialog ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickPayDialog(
    sale:           SaleDTO,
    paymentMethods: List<PaymentMethodDTO>,
    paying:         Boolean,
    onDismiss:      () -> Unit,
    onPay:          (amount: Double, methodId: Int, note: String?) -> Unit
) {
    var amountStr  by remember { mutableStateOf(String.format("%.0f", sale.dueAmount ?: 0.0)) }
    var selectedPm by remember { mutableStateOf<PaymentMethodDTO?>(null) }
    var note       by remember { mutableStateOf("") }
    var showSheet  by remember { mutableStateOf(false) }
    var error      by remember { mutableStateOf("") }

    val dueAmount = sale.dueAmount ?: 0.0

    if (showSheet) {
        AppPickerSheet(
            title = "ငွေပေးချေမှု နည်းလမ်း",
            items = paymentMethods,
            label = { it.methodName },
            onSelect = { selectedPm = it; showSheet = false },
            onDismiss = { showSheet = false },
            key = { i, pm -> pm.id.takeIf { it != 0 } ?: "pm-$i" }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Payment, null, tint = Danger, modifier = Modifier.size(22.dp))
                Text("ကျန်ငွေ ဆပ်မည်", fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = ScreenBg, shape = RoundedCornerShape(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sale.saleCode ?: "#${sale.id}", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Bold)
                        Text(sale.customerName ?: "—", fontSize = 12.sp, color = TextMuted)
                    }
                }
                Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ကျန်ငွေ", fontSize = 12.sp, color = Danger)
                        Text("${fmtD(dueAmount)} Ks", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                    }
                }
                OutlinedTextField(
                    value           = amountStr,
                    onValueChange   = { amountStr = it; error = "" },
                    label           = { Text("ဆပ်မည့် ပမာဏ (Ks)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    shape           = RoundedCornerShape(10.dp),
                    isError         = error.isNotBlank()
                )
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { showSheet = true },
                    shape    = RoundedCornerShape(10.dp),
                    border   = BorderStroke(
                        1.dp,
                        if (selectedPm == null && error.isNotBlank()) MaterialTheme.colorScheme.error else BorderColor
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            selectedPm?.methodName ?: "ငွေပေးချေမှု နည်းလမ်း ရွေးပါ",
                            color    = if (selectedPm != null) TextMain else TextMuted,
                            fontSize = 13.sp
                        )
                        Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                OutlinedTextField(
                    value         = note,
                    onValueChange = { note = it },
                    label         = { Text("မှတ်ချက် (optional)") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(10.dp)
                )
                if (error.isNotBlank())
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    when {
                        amt == null || amt <= 0 -> error = "ပမာဏ မှန်ကန်စွာ ရိုက်ထည့်ပါ"
                        amt > dueAmount + 0.01  -> error = "ဆပ်မည့် ပမာဏ ကျန်ငွေထက် မကျော်ရပါ"
                        selectedPm == null      -> error = "ငွေပေးချေမှု နည်းလမ်း ရွေးပါ"
                        else -> onPay(amt, selectedPm!!.id, note.ifBlank { null })
                    }
                },
                enabled = !paying,
                colors  = ButtonDefaults.buttonColors(containerColor = Danger)
            ) {
                if (paying)
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else
                    Text("ဆပ်မည်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !paying) { Text("ပယ်ဖျက်") }
        }
    )
}

// ── StatusBadge ───────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(status: String?) {
    val (bg, color, label) = when (status?.uppercase()) {
        "PAID"    -> Triple(SuccessBg, Success, "ငွေအပြည့်ချေပြီး")
        "PARTIAL" -> Triple(WarningBg, Warning, "ငွေအနည်းငယ်ချေပြီး")
        "UNPAID"  -> Triple(DangerBg,  Danger,  "ငွေမပေးရသေးပါ")
        else      -> Triple(BorderColor, TextMuted, status ?: "-")
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCol(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = TextMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

private fun fmtD(v: Double?) = String.format("%,.0f", v ?: 0.0)

private fun millisToDate(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(millis))
}

@androidx.compose.ui.tooling.preview.Preview(name = "Sale list row", showBackground = true, widthDp = 390)
@Composable
private fun SaleListRowPreview() {
    AppTheme {
        Surface(color = ScreenBg, modifier = Modifier.padding(16.dp)) {
            AppListRow(title = "SL-1001", subtitle = "မောင်မောင်", trailing = "25,000 Ks", onClick = {})
        }
    }
}

private fun dateToMillis(dateStr: String): Long {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(dateStr)?.time ?: 0L
    } catch (_: Exception) { 0L }
}

