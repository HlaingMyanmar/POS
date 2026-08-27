package com.sspd.servicemgmt.core.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.AuthEventBus
import com.sspd.servicemgmt.feature.auth.*
import com.sspd.servicemgmt.feature.booking.*
import com.sspd.servicemgmt.feature.chat.*
import com.sspd.servicemgmt.feature.customer.*
import com.sspd.servicemgmt.feature.finance.*
import com.sspd.servicemgmt.feature.home.*
import com.sspd.servicemgmt.feature.inventory.*
import com.sspd.servicemgmt.feature.product.*
import com.sspd.servicemgmt.feature.purchase.*
import com.sspd.servicemgmt.feature.purchasereturn.*
import com.sspd.servicemgmt.feature.report.*
import com.sspd.servicemgmt.feature.sale.*
import com.sspd.servicemgmt.feature.salereturn.*
import com.sspd.servicemgmt.feature.service.catalog.*
import com.sspd.servicemgmt.feature.service.job.*
import com.sspd.servicemgmt.feature.settings.*
import com.sspd.servicemgmt.feature.web.*
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.connectivity.ServerStatusViewModel
import com.sspd.servicemgmt.core.util.AlertSound
import com.sspd.servicemgmt.core.util.PreferenceManager
import com.sspd.servicemgmt.core.realtime.DataEventBus

// EaseOutExpo — snappy deceleration, feels responsive
private val ExpoOut  = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private const val ANIM_MS   = 280   // push/pop slide
private const val FADE_MS   = 180   // tab cross-fade

private data class BottomNavItem(
    val route: String,
    val icon:  ImageVector,
    val label: String
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route,        Icons.Default.Home,          "ပင်မ"),
    BottomNavItem(Screen.Sales.route,       Icons.Default.Receipt,       "ရောင်း"),
    BottomNavItem(Screen.Purchases.route,   Icons.Default.ShoppingCart,  "ဝယ်ယူ"),
    BottomNavItem(Screen.Bookings.route,    Icons.Default.CalendarMonth, "လက်ခံ"),
    BottomNavItem(Screen.ServiceJobs.route, Icons.Default.Build,         "ပြင်ဆင်"),
    BottomNavItem(Screen.Products.route,    Icons.Default.Inventory2,    "ပစ္စည်း")
)

private val technicianBottomNavItems = listOf(
    BottomNavItem(Screen.Home.route,        Icons.Default.Home,                    "ပင်မ"),
    BottomNavItem(Screen.Products.route,    Icons.Default.Inventory2,              "ပစ္စည်း"),
    BottomNavItem(Screen.ServiceJobs.route, Icons.Default.Build,                   "ပြင်ဆင်"),
    BottomNavItem(Screen.ServiceMgmt.route, Icons.Default.MiscellaneousServices,   "ဝန်ဆောင်မှု"),
    BottomNavItem(Screen.Chat.route,        Icons.Default.Chat,                    "စကားဝိုင်း")
)

// ── Custom bottom nav with raised centre Booking button ───────────────────────
@Composable
private fun CustomBottomNav(
    items:        List<BottomNavItem>,
    currentRoute: String?,
    onNavigate:   (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {

        // ── Bar background ────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .navigationBarsPadding()
                    .height(68.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    FlatNavItem(
                        item = item,
                        selected = currentRoute == item.route,
                        modifier = Modifier.weight(1f)
                    ) { onNavigate(item.route) }
                }
            }
        }

        // ── Raised centre button ──────────────────────────────────────────
    }
}

@Composable
private fun FlatNavItem(
    item: BottomNavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .heightIn(min = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 34.dp else 30.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(if (selected) Primary.copy(0.10f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint     = if (selected) Primary else TextMuted,
                modifier = Modifier.size(if (selected) 22.dp else 20.dp)
            )
        }
        Text(
            item.label,
            fontSize   = 11.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
            color      = if (selected) Primary else TextMuted
        )
    }
}

private fun NavGraphBuilder.screen(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit
) = composable(route = route) { entry -> content(entry) }

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs   = remember { PreferenceManager(context) }
    val nav     = rememberNavController()
    val start   = if (prefs.authToken.isNotEmpty()) MAIN_GRAPH else AUTH_GRAPH

    val serverVm: ServerStatusViewModel = viewModel()
    val serverStatus by serverVm.status.collectAsStateWithLifecycle()

    // Auto-logout: redirect to login when server returns 401 (token expired)
    val tokenExpired by AuthEventBus.tokenExpired.collectAsStateWithLifecycle()
    LaunchedEffect(tokenExpired) {
        if (tokenExpired) {
            AuthEventBus.reset()
            prefs.clear()
            DataEventBus.disconnect()
            nav.navigate(AUTH_GRAPH) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Connect DataEventBus when a valid token is present; disconnect on logout.
    LaunchedEffect(prefs.authToken) {
        if (prefs.authToken.isNotEmpty()) {
            DataEventBus.connect(ApiClient.wsNativeUrl, prefs.authToken)
        } else {
            DataEventBus.disconnect()
        }
    }

    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTechnician = prefs.isTechnician()
    val navItems = if (isTechnician) technicianBottomNavItems else bottomNavItems
    val navRoutes = navItems.map { it.route }.toSet()

    val showBottomBar = currentRoute in navRoutes

    LaunchedEffect(Unit) {
        DataEventBus.jobCreated.collect { AlertSound.play(context) }
    }

    fun navigateTab(route: String) {
        nav.navigate(route) {
            popUpTo(Screen.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }

    CompositionLocalProvider(LocalServerStatus provides serverStatus) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    CustomBottomNav(
                        items       = navItems,
                        currentRoute = currentRoute,
                        onNavigate  = { navigateTab(it) }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController    = nav,
                startDestination = start,
                modifier         = Modifier.padding(innerPadding),
                enterTransition = {
                    val isTab = initialState.destination.route in navRoutes &&
                                targetState.destination.route in navRoutes
                    if (isTab) fadeIn(tween(FADE_MS))
                    else slideInHorizontally(
                             animationSpec  = tween(ANIM_MS, easing = ExpoOut),
                             initialOffsetX = { (it * 0.08f).toInt() }
                         ) + fadeIn(tween(ANIM_MS, easing = ExpoOut))
                },
                exitTransition = {
                    val isTab = initialState.destination.route in navRoutes &&
                                targetState.destination.route in navRoutes
                    if (isTab) fadeOut(tween(FADE_MS))
                    else slideOutHorizontally(
                             animationSpec = tween(ANIM_MS, easing = ExpoOut),
                             targetOffsetX = { -(it * 0.08f).toInt() }
                         ) + fadeOut(tween(ANIM_MS / 2))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        animationSpec  = tween(ANIM_MS, easing = ExpoOut),
                        initialOffsetX = { -(it * 0.08f).toInt() }
                    ) + fadeIn(tween(ANIM_MS, easing = ExpoOut))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        animationSpec = tween(ANIM_MS, easing = ExpoOut),
                        targetOffsetX = { (it * 0.08f).toInt() }
                    ) + fadeOut(tween(ANIM_MS / 2))
                }
            ) {

                // ── Auth Graph ──────────────────────────────────────────────
                navigation(startDestination = Screen.Login.route, route = AUTH_GRAPH) {
                    screen(Screen.Login.route) {
                        LoginScreen(onSuccess = {
                            nav.navigate(MAIN_GRAPH) {
                                popUpTo(AUTH_GRAPH) { inclusive = true }
                            }
                        })
                    }
                }

                // ── Main Graph ──────────────────────────────────────────────
                navigation(startDestination = Screen.Home.route, route = MAIN_GRAPH) {

                    screen(Screen.Home.route) {
                        HomeScreen(
                            onNavigate = { route ->
                                if (route in navRoutes) navigateTab(route)
                                else nav.navigate(route)
                            },
                            onLogout = {
                                nav.navigate(AUTH_GRAPH) {
                                    popUpTo(MAIN_GRAPH) { inclusive = true }
                                }
                            }
                        )
                    }

                    // ── Sales ───────────────────────────────────────────────
                    screen(Screen.Sales.route) {
                        SaleListScreen(
                            onBack        = { nav.popBackStack() },
                            onSaleClick   = { id -> nav.navigate(Screen.SaleDetail.createRoute(id)) },
                            onNewSale     = { nav.navigate(Screen.NewSale.route) },
                            onReturnClick = { id -> nav.navigate(Screen.SaleReturnDetail.createRoute(id)) },
                            onNewReturn   = { nav.navigate(Screen.NewSaleReturn.route) }
                        )
                    }
                    screen(Screen.NewSale.route) {
                        NewSaleScreen(
                            onBack    = { nav.popBackStack() },
                            onSuccess = { id ->
                                nav.navigate(Screen.SaleDetail.createRoute(id)) {
                                    popUpTo(Screen.NewSale.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.SaleDetail.route,
                        arguments = listOf(navArgument("saleId") { type = NavType.IntType }),
                    ) { entry ->
                        val saleId = entry.arguments?.getInt("saleId") ?: 0
                        SaleDetailScreen(
                            onBack  = { nav.popBackStack() },
                            onPrint = { nav.navigate(Screen.SalePrint.createRoute(saleId)) }
                        )
                    }
                    composable(
                        route = Screen.SalePrint.route,
                        arguments = listOf(navArgument("saleId") { type = NavType.IntType }),
                    ) { entry ->
                        val saleId = entry.arguments?.getInt("saleId") ?: 0
                        SalePrintScreen(saleId = saleId, onBack = { nav.popBackStack() })
                    }

                    // ── Products ────────────────────────────────────────────
                    screen(Screen.Products.route) {
                        ProductListScreen(
                            onBack         = { nav.popBackStack() },
                            onProductClick = { id -> nav.navigate(Screen.ProductDetail.createRoute(id)) },
                            onScanNavigate = { id, serial -> nav.navigate(Screen.ProductDetail.createRoute(id, serial)) },
                            onNewProduct   = { nav.navigate(Screen.NewProduct.route) }
                        )
                    }
                    screen(Screen.NewProduct.route) {
                        ProductFormScreen(
                            onBack = { nav.popBackStack() },
                            onSaved = { id ->
                                nav.navigate(Screen.ProductDetail.createRoute(id)) {
                                    popUpTo(Screen.NewProduct.route) { inclusive = true }
                                }
                            },
                            onDeleted = { nav.navigate(Screen.Products.route) }
                        )
                    }
                    composable(
                        route = Screen.EditProduct.route,
                        arguments = listOf(navArgument("productId") { type = NavType.IntType })
                    ) {
                        ProductFormScreen(
                            onBack = { nav.popBackStack() },
                            onSaved = { id ->
                                nav.navigate(Screen.ProductDetail.createRoute(id)) {
                                    popUpTo(Screen.EditProduct.route) { inclusive = true }
                                }
                            },
                            onDeleted = {
                                nav.navigate(Screen.Products.route) {
                                    popUpTo(Screen.Products.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.ProductDetail.route,
                        arguments = listOf(
                            navArgument("productId") { type = NavType.IntType },
                            navArgument("serialNumber") { type = NavType.StringType; nullable = true; defaultValue = null }
                        ),
                    ) {
                        val productId = it.arguments?.getInt("productId") ?: 0
                        ProductDetailScreen(
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate(Screen.EditProduct.createRoute(productId)) }
                        )
                    }

                    // ── Bookings ────────────────────────────────────────────
                    screen(Screen.InventorySetup.route) {
                        InventorySetupScreen { nav.popBackStack() }
                    }

                    screen(Screen.Purchases.route) {
                        PurchaseListScreen(
                            onBack = { nav.popBackStack() },
                            onPurchaseClick = { id -> nav.navigate(Screen.PurchaseDetail.createRoute(id)) },
                            onNewPurchase = { nav.navigate(Screen.NewPurchase.route) }
                        )
                    }
                    screen(Screen.PurchaseOrders.route) {
                        PurchaseOrderListScreen(onBack = { nav.popBackStack() })
                    }
                    screen(Screen.SupplierPayments.route) {
                        SupplierPaymentScreen(onBack = { nav.popBackStack() })
                    }
                    screen(Screen.NewPurchase.route) {
                        PurchaseFormScreen(
                            onBack = { nav.popBackStack() },
                            onSuccess = { purchase ->
                                nav.navigate(Screen.PurchaseDetail.createRoute(purchase.id!!)) {
                                    popUpTo(Screen.NewPurchase.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.PurchaseDetail.route,
                        arguments = listOf(navArgument("purchaseId") { type = NavType.IntType })
                    ) {
                        PurchaseDetailScreen(onBack = { nav.popBackStack() })
                    }

                    screen(Screen.PurchaseReturns.route) {
                        PurchaseReturnListScreen(
                            onBack = { nav.popBackStack() },
                            onReturnClick = { id -> nav.navigate(Screen.PurchaseReturnDetail.createRoute(id)) },
                            onNewReturn = { nav.navigate(Screen.NewPurchaseReturn.route) }
                        )
                    }
                    screen(Screen.NewPurchaseReturn.route) {
                        PurchaseReturnFormScreen(
                            onBack = { nav.popBackStack() },
                            onSuccess = { id ->
                                nav.navigate(Screen.PurchaseReturnDetail.createRoute(id)) {
                                    popUpTo(Screen.NewPurchaseReturn.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.PurchaseReturnDetail.route,
                        arguments = listOf(navArgument("returnId") { type = NavType.IntType })
                    ) {
                        PurchaseReturnDetailScreen(onBack = { nav.popBackStack() })
                    }

                    screen(Screen.Bookings.route) {
                        BookingListScreen(
                            onBack         = { nav.popBackStack() },
                            onBookingClick = { id -> nav.navigate(Screen.BookingDetail.createRoute(id)) },
                            onNewBooking   = { nav.navigate(Screen.NewBooking.route) },
                            onEditBooking  = { id -> nav.navigate(Screen.EditBooking.createRoute(id)) }
                        )
                    }
                    screen(Screen.NewBooking.route) {
                        BookingFormScreen(
                            onBack    = { nav.popBackStack() },
                            onSuccess = {
                                nav.navigate(Screen.Bookings.route) {
                                    popUpTo(Screen.NewBooking.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.EditBooking.route,
                        arguments = listOf(navArgument("bookingId") { type = NavType.IntType }),
                    ) {
                        BookingFormScreen(
                            onBack    = { nav.popBackStack() },
                            onSuccess = { nav.popBackStack() }
                        )
                    }
                    composable(
                        route = Screen.BookingDetail.route,
                        arguments = listOf(navArgument("bookingId") { type = NavType.IntType }),
                    ) { entry ->
                        val bookingId = entry.arguments?.getInt("bookingId") ?: -1
                        BookingDetailScreen(
                            onBack       = { nav.popBackStack() },
                            onJobCreated = { nav.navigate(Screen.ServiceJobs.route) },
                            onEdit       = { nav.navigate(Screen.EditBooking.createRoute(bookingId)) },
                            onPrint      = { nav.navigate(Screen.BookingPrint.createRoute(bookingId)) }
                        )
                    }
                    composable(
                        route = Screen.BookingPrint.route,
                        arguments = listOf(navArgument("bookingId") { type = NavType.IntType }),
                    ) {
                        BookingPrintScreen(onBack = { nav.popBackStack() })
                    }

                    // ── Service Jobs ────────────────────────────────────────
                    screen(Screen.ServiceJobs.route) {
                        ServiceJobListScreen(
                            onBack     = { nav.popBackStack() },
                            onJobClick = { id -> nav.navigate(Screen.ServiceJobDetail.createRoute(id)) },
                            onNewJob   = { nav.navigate(Screen.NewServiceJob.route) }
                        )
                    }
                    screen(Screen.NewServiceJob.route) {
                        ServiceJobFormScreen(
                            onBack    = { nav.popBackStack() },
                            onSuccess = { job ->
                                nav.navigate(Screen.ServiceJobDetail.createRoute(job.id!!)) {
                                    popUpTo(Screen.NewServiceJob.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.EditServiceJob.route,
                        arguments = listOf(navArgument("jobId") { type = NavType.IntType }),
                    ) {
                        ServiceJobFormScreen(
                            onBack    = { nav.popBackStack() },
                            onSuccess = { nav.popBackStack() }
                        )
                    }
                    composable(
                        route = Screen.ServiceJobDetail.route,
                        arguments = listOf(navArgument("jobId") { type = NavType.IntType }),
                    ) { entry ->
                        val jobId = entry.arguments?.getInt("jobId") ?: 0
                        ServiceJobDetailScreen(
                            onBack    = { nav.popBackStack() },
                            onEdit    = { nav.navigate(Screen.EditServiceJob.createRoute(jobId)) },
                            onPrint   = { nav.navigate(Screen.ServiceJobPrint.createRoute(jobId)) },
                            onDeleted = { nav.navigate(Screen.ServiceJobs.route) {
                                popUpTo(Screen.ServiceJobDetail.route) { inclusive = true }
                            }}
                        )
                    }
                    composable(
                        route = Screen.ServiceJobPrint.route,
                        arguments = listOf(navArgument("jobId") { type = NavType.IntType }),
                    ) {
                        ServiceJobPrintScreen(onBack = { nav.popBackStack() })
                    }

                    // ── Sale Returns ────────────────────────────────────────
                    screen(Screen.SaleReturns.route) {
                        SaleReturnListScreen(
                            onBack        = { nav.popBackStack() },
                            onReturnClick = { id -> nav.navigate(Screen.SaleReturnDetail.createRoute(id)) },
                            onNewReturn   = { nav.navigate(Screen.NewSaleReturn.route) }
                        )
                    }
                    screen(Screen.NewSaleReturn.route) {
                        SaleReturnFormScreen(
                            onBack    = { nav.popBackStack() },
                            onSuccess = {
                                nav.navigate(Screen.SaleReturns.route) {
                                    popUpTo(Screen.NewSaleReturn.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.EditSaleReturn.route,
                        arguments = listOf(navArgument("returnId") { type = NavType.IntType }),
                    ) {
                        SaleReturnFormScreen(
                            onBack    = { nav.popBackStack() },
                            onSuccess = { nav.popBackStack() }
                        )
                    }
                    composable(
                        route = Screen.SaleReturnDetail.route,
                        arguments = listOf(navArgument("returnId") { type = NavType.IntType }),
                    ) { entry ->
                        val returnId = entry.arguments?.getInt("returnId") ?: 0
                        SaleReturnDetailScreen(
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate(Screen.EditSaleReturn.createRoute(returnId)) }
                        )
                    }

                    // ── Opening Balance ──────────────────────────────────────
                    screen(Screen.OpeningBalance.route) {
                        OpeningBalanceScreen(onBack = { nav.popBackStack() })
                    }

                    // ── Transfer (KBZ / Cash / Bank) ─────────────────────────
                    screen(Screen.Transfer.route) {
                        TransferScreen(onBack = { nav.popBackStack() })
                    }

                    // ── Serial Registry ─────────────────────────────────────
                    screen(Screen.SerialRegistry.route) {
                        SerialRegistryScreen(
                            onBack         = { nav.popBackStack() },
                            onProductClick = { productId, serial ->
                                nav.navigate(Screen.ProductDetail.createRoute(productId, serial))
                            }
                        )
                    }

                    // ── Stock Adjustments ───────────────────────────────────
                    screen(Screen.StockAdjustments.route) {
                        StockAdjListScreen(
                            onBack     = { nav.popBackStack() },
                            onAdjClick = { id -> nav.navigate(Screen.StockAdjDetail.createRoute(id)) },
                            onNewAdj   = { nav.navigate(Screen.NewStockAdj.route) }
                        )
                    }
                    screen(Screen.NewStockAdj.route) {
                        StockAdjFormScreen(
                            onBack    = { nav.popBackStack() },
                            onSuccess = { adj ->
                                nav.navigate(Screen.StockAdjDetail.createRoute(adj.id!!)) {
                                    popUpTo(Screen.NewStockAdj.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route     = Screen.StockAdjDetail.route,
                        arguments = listOf(navArgument("adjId") { type = NavType.IntType })
                    ) {
                        StockAdjDetailScreen(
                            onBack    = { nav.popBackStack() },
                            onDeleted = {
                                nav.navigate(Screen.StockAdjustments.route) {
                                    popUpTo(Screen.StockAdjDetail.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    // ── Other screens ───────────────────────────────────────
                    screen(Screen.Customers.route)      { CustomerManagementScreen { nav.popBackStack() } }
                    screen(Screen.CreditDesk.route)     { CreditOperationsScreen    { nav.popBackStack() } }
                    screen(Screen.ServiceMgmt.route)    { ServiceManagementScreen   { nav.popBackStack() } }
                    screen(Screen.ShelfLocations.route) { ShelfLocationScreen     { nav.popBackStack() } }
                    screen(Screen.StaffReport.route)    { StaffReportScreen       { nav.popBackStack() } }
                    screen(Screen.Expenses.route) {
                        ExpenseScreen(
                            onBack     = { nav.popBackStack() },
                            onNewEntry = { type -> nav.navigate(Screen.NewExpenseEntry.createRoute(type)) }
                        )
                    }
                    composable(
                        route = Screen.NewExpenseEntry.route,
                        arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = "EXPENSE" }),
                    ) {
                        ExpenseFormScreen(
                            onBack    = { nav.popBackStack() },
                            onSuccess = { nav.popBackStack() }
                        )
                    }
                    screen(Screen.Report.route)         { ReportScreen            { nav.popBackStack() } }
                    screen(Screen.IncomeReport.route)   { IncomeReportScreen       { nav.popBackStack() } }
                    screen(Screen.SalesRanking.route)   { SalesRankingScreen      { nav.popBackStack() } }
                    screen(Screen.AuditLog.route)       { AuditLogScreen          { nav.popBackStack() } }
                    screen(Screen.JournalEntries.route) { JournalEntryScreen      { nav.popBackStack() } }
                    screen(Screen.Chat.route)           { ChatScreen              { nav.popBackStack() } }
                    screen(Screen.Account.route)        { AccountSettingsScreen   { nav.popBackStack() } }
                    screen(Screen.About.route)          { AboutScreen             { nav.popBackStack() } }
                    screen(Screen.SoftwareUpdate.route) { SoftwareUpdateScreen    { nav.popBackStack() } }
                }
            }
        }
    }
}
