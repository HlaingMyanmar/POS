package com.sspd.servicemgmt.feature.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.R
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.navigation.LocalServerStatus
import com.sspd.servicemgmt.core.navigation.Screen
import com.sspd.servicemgmt.core.tracking.VisitTracker
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.ui.component.UpdateDialog
import com.sspd.servicemgmt.core.connectivity.ServerStatus
import com.sspd.servicemgmt.feature.settings.VersionCheckViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

// ── Hero gradient colours ──────────────────────────────────────────────────────
private val HeroTop    = Color(0xFF0F2A3D)
private val HeroBottom = Color(0xFF1E5A6F)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onLogout:   () -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Version update check — runs once per session after login
    val versionVm: VersionCheckViewModel = viewModel()
    val versionState by versionVm.state.collectAsStateWithLifecycle()
    val activeVisit by VisitTracker.visit.collectAsStateWithLifecycle()
    val visitBusy by VisitTracker.busy.collectAsStateWithLifecycle()
    val pendingResume by VisitTracker.pendingResume.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()

    LaunchedEffect(state.isLoggedOut) { if (state.isLoggedOut) onLogout() }
    LaunchedEffect(Unit) {
        versionVm.check()
        while (true) { vm.loadStats(); delay(30_000) }
    }

    versionState.update?.let { update ->
        UpdateDialog(
            update           = update,
            downloadProgress = versionState.downloadProgress,
            apkFile          = versionState.apkFile,
            downloadError    = versionState.downloadError,
            onDownload       = versionVm::downloadAndInstall,
            onInstall        = { versionVm.triggerInstall(context) },
            onDismiss        = versionVm::dismiss
        )
    }

    val cal      = remember { Calendar.getInstance() }
    val hour     = remember { cal.get(Calendar.HOUR_OF_DAY) }
    val greeting = remember {
        when {
            hour < 12 -> "မင်္ဂလာ နံနက်ခင်းပါ"
            hour < 17 -> "မင်္ဂလာ နေ့လည်ပိုင်းပါ"
            else      -> "မင်္ဂလာ ညနေပိုင်းပါ"
        }
    }
    val days   = remember { arrayOf("တနင်္ဂနွေ","တနင်္လာ","အင်္ဂါ","ဗုဒ္ဓဟူး","ကြာသပတေး","သောကြာ","စနေ") }
    val months = remember { arrayOf("ဇန်နဝါရီ","ဖေဖော်ဝါရီ","မတ်","ဧပြီ","မေ","ဇွန်","ဇူလိုင်","သြဂုတ်","စက်တင်ဘာ","အောက်တိုဘာ","နိုဝင်ဘာ","ဒီဇင်ဘာ") }
    val dateStr = remember {
        "${days[cal.get(Calendar.DAY_OF_WEEK)-1]}၊ ${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            icon = { Icon(Icons.Outlined.Logout, contentDescription = null, tint = Danger) },
            title = { Text("Logout လုပ်မလား?") },
            text = { Text("ဒီစက်ထဲက Technician account မှ ထွက်ပါမယ်။") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        vm.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("မလုပ်တော့ပါ") }
            }
        )
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                modifier = Modifier.fillMaxWidth(0.78f)
            ) {
                DrawerContent(
                    username     = state.username,
                    displayName  = state.displayName,
                    isTechnician = state.isTechnician,
                    onNavigate   = { route -> scope.launch { drawerState.close() }; onNavigate(route) },
                    onLogout     = { scope.launch { drawerState.close() }; vm.logout() }
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().background(ScreenBg)) {

            // ── Hero Banner ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(Brush.verticalGradient(listOf(HeroTop, HeroBottom)))
                    .statusBarsPadding()
            ) {
                Column(modifier = Modifier.padding(bottom = 20.dp)) {

                    // Top bar: logo | status+refresh
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {

Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(6.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.logo),
                                contentDescription = "SSPD Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            ServerStatusChip()
                            IconButton(onClick = { vm.loadStats() }) {
                                Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White.copy(0.85f))
                            }
                            IconButton(onClick = { showLogoutConfirm = true }) {
                                Icon(Icons.Outlined.Logout, "Logout", tint = Color.White.copy(0.90f))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Greeting
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            "$greeting,",
                            fontSize   = 12.sp,
                            color      = Color.White.copy(0.75f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            state.displayName.ifEmpty { state.username },
                            fontSize   = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.CalendarToday, null,
                                tint     = Color.White.copy(0.60f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                dateStr,
                                fontSize = 11.sp,
                                color    = Color.White.copy(0.65f)
                            )
                        }
                    }
                }
            }

            // ── Body ──────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(20.dp))

                TechnicianHomeBody(
                    pendingJobs = state.stats.pendingServiceJobs ?: 0,
                    visit = activeVisit,
                    visitBusy = visitBusy,
                    pendingResume = pendingResume,
                    canOutdoorVisit = state.canOutdoorVisit,
                    onResumeTracking = { vm.resumeVisitTracking() },
                    onNavigate = onNavigate
                )

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ── Data holder ───────────────────────────────────────────────────────────────
private data class QuadItem(
    val label: String,
    val icon:  ImageVector,
    val color: Color,
    val route: String
)

@Composable
private fun TechnicianHomeBody(
    pendingJobs: Long,
    visit: com.sspd.servicemgmt.core.network.TechnicianVisitDTO?,
    visitBusy: Boolean,
    pendingResume: Boolean,
    canOutdoorVisit: Boolean,
    onResumeTracking: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onNavigate(Screen.ServiceJobs.route) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
        border = BorderStroke(1.dp, Color(0xFFA7F3D0))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Outdoor Visit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
            if (visit != null) {
                Text(visit.jobNo ?: "Job", fontWeight = FontWeight.ExtraBold, color = TextMain)
                Text(
                    listOfNotNull(visit.customerName, visit.status, visit.motionStatus).joinToString(" · "),
                    fontSize = 12.sp,
                    color = TextMuted
                )
                if (pendingResume) {
                    Button(onClick = onResumeTracking, enabled = !visitBusy) {
                        Text("Tracking ပြန်စမည်")
                    }
                }
            } else if (canOutdoorVisit) {
                Text("အပြင်ထွက် Visit", fontWeight = FontWeight.ExtraBold, color = TextMain)
                Text("Job တစ်ခုဖွင့်ပြီး «ထွက်ခွာပြီ» နှိပ်ပါ", fontSize = 12.sp, color = TextMuted)
            } else {
                Text("Visit ခွင့်မရှိသေးပါ", fontWeight = FontWeight.ExtraBold, color = TextMain)
                Text("Admin မှ CAN_ACCESS_TECHNICIAN_VISIT_START ပေးပြီး logout/login ပြန်လုပ်ပါ", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    StatCard(
        modifier = Modifier.fillMaxWidth(),
        label    = "ဆိုင်ခင်းအလုပ်",
        value    = "$pendingJobs ခု",
        icon     = Icons.Outlined.Build,
        color    = Violet,
        bg       = VioletBg
    ) { onNavigate(Screen.ServiceJobs.route) }

    Spacer(Modifier.height(24.dp))
    Text(
        "အမြန် လုပ်ဆောင်ချက်",
        fontSize     = 13.sp,
        fontWeight   = FontWeight.ExtraBold,
        color        = TextMain
    )
    Spacer(Modifier.height(12.dp))
    val actions = listOf(
        QuadItem("ပစ္စည်း",           Icons.Outlined.Inventory2,            Color(0xFF0891B2), Screen.Products.route),
        QuadItem("ပြင်ဆင်",           Icons.Outlined.Build,                 Color(0xFF059669), Screen.ServiceJobs.route),
        QuadItem("ဝန်ဆောင်မှုများ",  Icons.Outlined.MiscellaneousServices, Color(0xFFD97706), Screen.ServiceMgmt.route),
        QuadItem("အဖွဲ့ စကားဝိုင်း",     Icons.Outlined.Chat,                  Color(0xFF4F46E5), Screen.Chat.route),
    )
    ActionGrid(actions, onNavigate)
}

@Composable
private fun FullHomeBody(
    state: HomeViewModel.HomeUiState,
    onNavigate: (String) -> Unit,
    onDismissAlert: (Int) -> Unit,
    onDismissAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            "ယနေ့ ခြုံငုံ",
            fontSize     = 13.sp,
            fontWeight   = FontWeight.ExtraBold,
            color        = TextMain
        )
        if (state.loading)
            CircularProgressIndicator(
                modifier    = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color       = Primary
            )
    }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label    = "ရောင်းရငွေ",
            value    = "${(state.stats.todaySalesAmount ?: 0).fmt()} Ks",
            icon     = Icons.Outlined.Payments,
            color    = Primary,
            bg       = PrimaryLight
        ) { onNavigate(Screen.Sales.route) }

        StatCard(
            modifier = Modifier.weight(1f),
            label    = "ရောင်းမှု",
            value    = "${state.stats.todaySalesCount ?: 0} ခု",
            icon     = Icons.Outlined.Receipt,
            color    = Success,
            bg       = SuccessBg
        ) { onNavigate(Screen.Sales.route) }
    }

    Spacer(Modifier.height(10.dp))

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label    = "ကုန်နည်းနေ",
            value    = "${state.stats.lowStockCount ?: 0} မျိုး",
            icon     = Icons.Outlined.Warning,
            color    = Warning,
            bg       = WarningBg
        ) { onNavigate(Screen.Products.route) }

        StatCard(
            modifier = Modifier.weight(1f),
            label    = "ဆိုင်ခင်းအလုပ်",
            value    = "${state.stats.pendingServiceJobs ?: 0} ခု",
            icon     = Icons.Outlined.Build,
            color    = Violet,
            bg       = VioletBg
        ) { onNavigate(Screen.ServiceJobs.route) }
    }

    Spacer(Modifier.height(12.dp))

    OperationsPulseCard(
        pendingAmount = state.stats.totalPendingAR ?: 0,
        overdueAmount = state.stats.totalOverdueAR ?: 0,
        pendingCount = state.stats.pendingARCount ?: 0,
        overdueCount = state.stats.overdueARCount ?: 0,
        onCreditClick = { onNavigate(Screen.CreditDesk.route) },
        onCustomerClick = { onNavigate(Screen.Customers.route) }
    )

    AnimatedVisibility(
        visible = state.bookingAlerts.isNotEmpty(),
        enter   = fadeIn() + expandVertically(),
        exit    = fadeOut() + shrinkVertically(),
    ) {
        Column {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.NotificationsActive, null,
                        tint     = Color(0xFFD97706),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "ချိန်းဆိုမှု သတိပေးချက် (${state.bookingAlerts.size} ခု)",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color(0xFFD97706)
                    )
                }
                TextButton(onClick = onDismissAll) {
                    Text("အားလုံးပိတ်", fontSize = 11.sp, color = TextMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
            state.bookingAlerts.forEach { booking ->
                BookingAlertCard(
                    booking    = booking,
                    onNavigate = { onNavigate(Screen.Bookings.route) },
                    onDismiss  = { onDismissAlert(booking.id ?: return@BookingAlertCard) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(
        "အမြန် လုပ်ဆောင်ချက်",
        fontSize     = 13.sp,
        fontWeight   = FontWeight.ExtraBold,
        color        = TextMain
    )
    Spacer(Modifier.height(12.dp))
    val actions = listOf(
        QuadItem("ကုန်ပစ္စည်းများ",         Icons.Outlined.Inventory2,            Color(0xFF0891B2), Screen.Products.route),
        QuadItem("အရောင်းဆိုင်ရာ",           Icons.Outlined.Receipt,               Primary,          Screen.Sales.route),
        QuadItem("ပစ္စည်းလက်ခံ",                  Icons.Outlined.CalendarMonth,         Color(0xFF0369A1),Screen.Bookings.route),
        QuadItem("ဝန်ဆောင်မှုအလုပ်",          Icons.Outlined.Build,                 Color(0xFF059669),Screen.ServiceJobs.route),
        QuadItem("ဖောက်သည်များ",              Icons.Outlined.Groups,                Color(0xFF16A34A),Screen.Customers.route),
        QuadItem("Credit Desk",              Icons.Outlined.CreditCard,            Color(0xFF7C3AED),Screen.CreditDesk.route),
        QuadItem("ကုန်ကျစရိတ်",              Icons.Outlined.AccountBalanceWallet,  Color(0xFFB45309),Screen.Expenses.route),
        QuadItem("ကိန်းဂဏာန်း",              Icons.Outlined.BarChart,              Color(0xFF0891B2),Screen.Report.route),
        QuadItem("ဝင်ငွေ/အမြတ်",             Icons.Outlined.TrendingUp,            Color(0xFF059669),Screen.IncomeReport.route),
        QuadItem("ဝန်ဆောင်မှုများ",          Icons.Outlined.MiscellaneousServices, Color(0xFFD97706),Screen.ServiceMgmt.route),
        QuadItem("ကုန်ပမာဏ ပြင်ဆင်မှု",      Icons.Outlined.Inventory,             Color(0xFF0891B2),Screen.StockAdjustments.route),
        QuadItem("ဝယ်ယူရေး",                 Icons.Outlined.ShoppingCart,          Color(0xFF0F766E),Screen.Purchases.route),
        QuadItem("အဝယ်အော်ဒါ",                Icons.Outlined.Assignment,            Color(0xFF0F766E),Screen.PurchaseOrders.route),
        QuadItem("Supplier Payment",           Icons.Outlined.Payments,              Color(0xFF2563EB),Screen.SupplierPayments.route),
        QuadItem("ဝယ်ပြန်ပို့",                 Icons.Outlined.AssignmentReturn,     Color(0xFF0F766E),Screen.PurchaseReturns.route),
        QuadItem("ရောင်းပြန်လက်ခံ",             Icons.Outlined.AssignmentReturn,     Danger,          Screen.SaleReturns.route),
        QuadItem("Serial Registry",           Icons.Outlined.QrCode2,               Color(0xFF7C3AED),Screen.SerialRegistry.route),
        QuadItem("Opening Balance",           Icons.Outlined.AccountBalance,         Color(0xFF0369A1),Screen.OpeningBalance.route),
        QuadItem("ငွေပြောင်းလဲမှု",           Icons.Outlined.SwapHoriz,              Color(0xFF4F46E5),Screen.Transfer.route),
    )
    ActionGrid(actions, onNavigate)
}

@Composable
private fun ActionGrid(actions: List<QuadItem>, onNavigate: (String) -> Unit) {
    actions.chunked(2).forEach { row ->
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row.forEach { item ->
                ActionGridCard(
                    modifier = Modifier.weight(1f),
                    label    = item.label,
                    icon     = item.icon,
                    color    = item.color,
                    onClick  = { onNavigate(item.route) }
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
}

// ── Stat Card ─────────────────────────────────────────────────────────────────
@Composable
private fun OperationsPulseCard(
    pendingAmount: Long,
    overdueAmount: Long,
    pendingCount: Long,
    overdueCount: Long,
    onCreditClick: () -> Unit,
    onCustomerClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("လုပ်ငန်းအခြေအနေ", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                    Text("အကြွေး၊ လက်ကျန်နှင့် follow-up", fontSize = 11.sp, color = TextMuted)
                }
                AssistChip(
                    onClick = onCreditClick,
                    label = { Text("Credit Desk", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Outlined.CreditCard, null, modifier = Modifier.size(16.dp)) }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulseMetric("လက်ကျန်", "${pendingAmount.fmt()} Ks", "$pendingCount invoices", Icons.Outlined.PendingActions, Primary, Modifier.weight(1f))
                PulseMetric("ကျော်လွန်", "${overdueAmount.fmt()} Ks", "$overdueCount overdue", Icons.Outlined.EventBusy, Danger, Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCustomerClick, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Outlined.Groups, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ဖောက်သည်များ")
                }
                Button(
                    onClick = onCreditClick,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Outlined.Payments, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ငွေကောက်ရန်")
                }
            }
        }
    }
}

@Composable
private fun PulseMetric(
    label: String,
    value: String,
    count: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(0.08f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(0.18f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 15.sp, color = TextMain, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, fontSize = 10.sp, color = TextMuted, maxLines = 1)
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label:    String,
    value:    String,
    icon:     ImageVector,
    color:    Color,
    bg:       Color,
    onClick:  () -> Unit
) {
    Card(
        modifier  = modifier.clickable { onClick() },
        shape     = RoundedCornerShape(8.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        border    = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = TextMuted)
        }
    }
}

// ── Action Grid Card ──────────────────────────────────────────────────────────
@Composable
private fun ActionGridCard(
    modifier: Modifier,
    label:    String,
    icon:     ImageVector,
    color:    Color,
    onClick:  () -> Unit
) {
    Card(
        modifier  = modifier.clickable { onClick() },
        shape     = RoundedCornerShape(8.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        border    = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Text(
                label,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = TextMain,
                maxLines   = 2,
                lineHeight = 16.sp,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Booking alert card ────────────────────────────────────────────────────────

@Composable
private fun BookingAlertCard(
    booking:    BookingDTO,
    onNavigate: () -> Unit,
    onDismiss:  () -> Unit,
) {
    val deviceLine = listOfNotNull(booking.brand, booking.model)
        .joinToString(" ")
        .ifEmpty { booking.deviceType }

    Card(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border    = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier            = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment   = Alignment.Top
        ) {
            // Bell icon
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.NotificationsActive, null,
                    tint     = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    booking.customerName ?: "ဖောက်သည်",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    color      = Color(0xFF92400E)
                )
                if (!deviceLine.isNullOrBlank()) {
                    Text(
                        deviceLine,
                        fontSize = 11.sp,
                        color    = Color(0xFFB45309)
                    )
                }
                Text(
                    "ချိန်းဆိုချိန်: ${booking.appointmentDate?.take(16)?.replace("T", " ") ?: "-"}",
                    fontSize = 11.sp,
                    color    = Color(0xFF78716C)
                )
            }

            // Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End
            ) {
                TextButton(
                    onClick      = onNavigate,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("ကြည့်", fontSize = 11.sp, color = Color(0xFF0369A1))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.Close, "ပိတ်ရန်",
                        tint     = Color(0xFF9CA3AF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ── Drawer ────────────────────────────────────────────────────────────────────
@Composable
fun DrawerContent(
    username:     String,
    displayName:  String,
    isTechnician: Boolean,
    onNavigate:   (String) -> Unit,
    onLogout:     () -> Unit
) {
    val initial = (username.firstOrNull()?.uppercaseChar() ?: 'U').toString()

    Column(modifier = Modifier.fillMaxSize()) {

        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HeroTop, HeroBottom)))
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 24.dp)
        ) {
            Column {
                // Logo badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter            = painterResource(R.drawable.logo),
                        contentDescription = "SSPD လိုဂို",
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.size(56.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    BuildConfig.APP_DISPLAY_NAME,
                    fontSize   = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color.White
                )
                Text(
                    "IT ဖြေရှင်းရေးစင်တာ",
                    fontSize = 11.sp,
                    color    = Color.White.copy(0.65f)
                )

                Spacer(Modifier.height(16.dp))

                // User row
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.20f))
                            .border(1.5.dp, Color.White.copy(0.40f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initial, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Column {
                        Text(
                            displayName.ifEmpty { username },
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                        Text(
                            username,
                            fontSize = 11.sp,
                            color    = Color.White.copy(0.6f)
                        )
                    }
                }
            }
        }

        // Menu items
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            DrawerSection("Technician")
            DrawerMenuItem("ပင်မ", Icons.Outlined.Home, Screen.Home.route, onNavigate)
            DrawerMenuItem("Jobs / Outdoor Visit", Icons.Outlined.NearMe, Screen.ServiceJobs.route, onNavigate)
            DrawerMenuItem("ကုန်ပစ္စည်း", Icons.Outlined.Inventory2, Screen.Products.route, onNavigate)
            DrawerMenuItem("Customer History", Icons.Outlined.History, Screen.CustomerHistory.route, onNavigate)
            DrawerMenuItem("ဝန်ဆောင်မှု", Icons.Outlined.MiscellaneousServices, Screen.ServiceMgmt.route, onNavigate)
            DrawerMenuItem("Chat", Icons.Outlined.Chat, Screen.Chat.route, onNavigate)
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        DrawerMenuItem(
            label     = "ထွက်ရန်",
            icon      = Icons.Outlined.Logout,
            route     = "",
            onNavigate = { onLogout() },
            iconTint  = Danger,
            textColor = Danger
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(
        text     = title.uppercase(),
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
        fontSize = 10.sp,
        fontWeight   = FontWeight.ExtraBold,
        color        = TextMuted,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun DrawerMenuItem(
    label:     String,
    icon:      ImageVector,
    route:     String,
    onNavigate: (String) -> Unit,
    iconTint:  Color = Primary,
    textColor: Color = TextMain
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onNavigate(route) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (iconTint == Danger) Color(0xFFFFF1F2) else PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Text(
            label,
            modifier   = Modifier.weight(1f),
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = textColor
        )
        if (iconTint != Danger)
            Icon(Icons.Default.ChevronRight, null, tint = BorderColor, modifier = Modifier.size(16.dp))
    }
}

// ── Server status chip ────────────────────────────────────────────────────────
@Composable
private fun ServerStatusChip() {
    val status = LocalServerStatus.current

    val infiniteTransition = rememberInfiniteTransition(label = "ping")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOut), RepeatMode.Reverse),
        label = "alpha"
    )

    val (icon, dotColor, label) = when (status) {
        ServerStatus.ONLINE   -> Triple(Icons.Outlined.Wifi,    Color(0xFF4ADE80), "အွန်လိုင်း")
        ServerStatus.OFFLINE  -> Triple(Icons.Outlined.WifiOff, Color(0xFFF87171), "အော့ဖ်လိုင်း")
        ServerStatus.CHECKING -> Triple(Icons.Outlined.Wifi,    Color.White.copy(pulseAlpha), "...")
    }

    Surface(
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, tint = dotColor, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 10.sp, color = dotColor, fontWeight = FontWeight.Bold)
        }
    }
}

private fun Long.fmt() = String.format("%,d", this)

@Preview(name = "Technician Dashboard", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TechnicianDashboardPreview() {
    AppTheme {
        Column(Modifier.fillMaxSize().background(ScreenBg)) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(HeroTop, HeroBottom)))
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(4.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.logo),
                                contentDescription = "SSPD Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                            Text("●  Online", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("မင်္ဂလာပါ", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                    Text("Field Technician", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Outdoor Service Dashboard", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(18.dp))
                TechnicianHomeBody(
                    pendingJobs = 6,
                    visit = null,
                    visitBusy = false,
                    pendingResume = false,
                    canOutdoorVisit = true,
                    onResumeTracking = {},
                    onNavigate = {}
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

