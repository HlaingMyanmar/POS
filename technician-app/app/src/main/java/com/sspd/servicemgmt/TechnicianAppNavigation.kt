package com.sspd.servicemgmt

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MiscellaneousServices
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.sspd.servicemgmt.core.connectivity.ServerStatusViewModel
import com.sspd.servicemgmt.core.navigation.AUTH_GRAPH
import com.sspd.servicemgmt.core.navigation.LocalServerStatus
import com.sspd.servicemgmt.core.navigation.MAIN_GRAPH
import com.sspd.servicemgmt.core.navigation.Screen
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.AuthEventBus
import com.sspd.servicemgmt.core.realtime.DataEventBus
import com.sspd.servicemgmt.core.tracking.VisitTracker
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.util.AlertSound
import com.sspd.servicemgmt.core.util.PreferenceManager
import com.sspd.servicemgmt.feature.auth.LoginScreen
import com.sspd.servicemgmt.feature.chat.ChatScreen
import com.sspd.servicemgmt.feature.customer.CustomerHistoryScreen
import com.sspd.servicemgmt.feature.home.HomeScreen
import com.sspd.servicemgmt.feature.product.ProductDetailScreen
import com.sspd.servicemgmt.feature.product.ProductListScreen
import com.sspd.servicemgmt.feature.service.catalog.ServiceManagementScreen
import com.sspd.servicemgmt.feature.service.job.ServiceJobDetailScreen
import com.sspd.servicemgmt.feature.service.job.ServiceJobFormScreen
import com.sspd.servicemgmt.feature.service.job.ServiceJobListScreen
import com.sspd.servicemgmt.feature.service.job.ServiceJobPrintScreen
import com.sspd.servicemgmt.feature.settings.AboutScreen
import com.sspd.servicemgmt.feature.settings.AccountSettingsScreen
import com.sspd.servicemgmt.feature.settings.SoftwareUpdateScreen

private val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private const val ANIM_MS = 280
private const val FADE_MS = 180

private data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

private val technicianNavItems = listOf(
    BottomNavItem(Screen.Home.route,        Icons.Default.Home,                  "ပင်မ"),
    BottomNavItem(Screen.ServiceJobs.route, Icons.Default.Build,                 "ပြင်ဆင်"),
    BottomNavItem(Screen.Products.route,    Icons.Default.Inventory2,            "ပစ္စည်း"),
    BottomNavItem(Screen.Chat.route,        Icons.Default.Chat,                  "စကားဝိုင်း")
)

@Composable
private fun TechnicianBottomNav(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
            NavigationBar(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(76.dp),
                containerColor = Color.White,
                tonalElevation = 0.dp
            ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(23.dp)
                        )
                    },
                    label = {
                        Text(
                            item.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        indicatorColor = PrimaryLight,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
            }
            }
        }
    }
}

private fun NavGraphBuilder.screen(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit
) = composable(route = route) { entry -> content(entry) }

@Composable
fun TechnicianAppNavigation() {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context) }
    val nav = rememberNavController()
    val allowedSession = prefs.authToken.isNotEmpty() && prefs.isTechnician()
    val start = if (allowedSession) MAIN_GRAPH else AUTH_GRAPH

    val serverVm: ServerStatusViewModel = viewModel()
    val serverStatus by serverVm.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (prefs.authToken.isNotEmpty() && !prefs.isTechnician()) {
            prefs.clear()
            DataEventBus.disconnect()
            nav.navigate(AUTH_GRAPH) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

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

    LaunchedEffect(prefs.authToken) {
        if (prefs.authToken.isNotEmpty() && prefs.isTechnician()) {
            DataEventBus.connect(ApiClient.wsNativeUrl, prefs.authToken)
            VisitTracker.recover(context)
        } else {
            DataEventBus.disconnect()
        }
    }

    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val navRoutes = technicianNavItems.map { it.route }.toSet()
    val showBottomBar = currentRoute in navRoutes

    LaunchedEffect(Unit) {
        DataEventBus.jobCreated.collect { AlertSound.play(context) }
    }

    fun navigateTab(route: String) {
        nav.navigate(route) {
            popUpTo(Screen.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalServerStatus provides serverStatus) {
        Scaffold(
            containerColor = if (currentRoute == Screen.Login.route || currentRoute == AUTH_GRAPH)
                ScreenBg
            else
                MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar) {
                    TechnicianBottomNav(
                        items = technicianNavItems,
                        currentRoute = currentRoute,
                        onNavigate = { navigateTab(it) }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = nav,
                startDestination = start,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (currentRoute == Screen.Login.route || currentRoute == AUTH_GRAPH)
                            Modifier
                        else
                            Modifier.padding(innerPadding)
                    ),
                enterTransition = {
                    val isTab = initialState.destination.route in navRoutes &&
                        targetState.destination.route in navRoutes
                    if (isTab) fadeIn(tween(FADE_MS))
                    else slideInHorizontally(
                        animationSpec = tween(ANIM_MS, easing = ExpoOut),
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
                        animationSpec = tween(ANIM_MS, easing = ExpoOut),
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
                navigation(startDestination = Screen.Login.route, route = AUTH_GRAPH) {
                    screen(Screen.Login.route) {
                        LoginScreen(onSuccess = {
                            nav.navigate(MAIN_GRAPH) {
                                popUpTo(AUTH_GRAPH) { inclusive = true }
                            }
                        })
                    }
                }

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

                    screen(Screen.Products.route) {
                        ProductListScreen(
                            onBack = { nav.popBackStack() },
                            onProductClick = { id -> nav.navigate(Screen.ProductDetail.createRoute(id)) },
                            onScanNavigate = { id, serial ->
                                nav.navigate(Screen.ProductDetail.createRoute(id, serial))
                            },
                            canCreateProduct = false
                        )
                    }
                    composable(
                        route = Screen.ProductDetail.route,
                        arguments = listOf(
                            navArgument("productId") { type = NavType.IntType },
                            navArgument("serialNumber") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) {
                        ProductDetailScreen(onBack = { nav.popBackStack() })
                    }

                    screen(Screen.ServiceJobs.route) {
                        ServiceJobListScreen(
                            onBack = { nav.popBackStack() },
                            onJobClick = { id -> nav.navigate(Screen.ServiceJobDetail.createRoute(id)) },
                            onNewJob = { nav.navigate(Screen.NewServiceJob.route) }
                        )
                    }
                    screen(Screen.CustomerHistory.route) {
                        CustomerHistoryScreen(
                            onBack = { nav.popBackStack() },
                            onJobClick = { id -> nav.navigate(Screen.ServiceJobDetail.createRoute(id)) }
                        )
                    }
                    screen(Screen.NewServiceJob.route) {
                        ServiceJobFormScreen(
                            onBack = { nav.popBackStack() },
                            onSuccess = { job ->
                                nav.navigate(Screen.ServiceJobDetail.createRoute(job.id!!)) {
                                    popUpTo(Screen.NewServiceJob.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.EditServiceJob.route,
                        arguments = listOf(navArgument("jobId") { type = NavType.IntType })
                    ) {
                        ServiceJobFormScreen(
                            onBack = { nav.popBackStack() },
                            onSuccess = { nav.popBackStack() }
                        )
                    }
                    composable(
                        route = Screen.ServiceJobDetail.route,
                        arguments = listOf(navArgument("jobId") { type = NavType.IntType })
                    ) { entry ->
                        val jobId = entry.arguments?.getInt("jobId") ?: 0
                        ServiceJobDetailScreen(
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate(Screen.EditServiceJob.createRoute(jobId)) },
                            onPrint = { nav.navigate(Screen.ServiceJobPrint.createRoute(jobId)) },
                            onOpenActiveVisit = { activeJobId ->
                                nav.navigate(Screen.ServiceJobDetail.createRoute(activeJobId)) {
                                    launchSingleTop = true
                                }
                            },
                            onDeleted = {
                                nav.navigate(Screen.ServiceJobs.route) {
                                    popUpTo(Screen.ServiceJobDetail.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = Screen.ServiceJobPrint.route,
                        arguments = listOf(navArgument("jobId") { type = NavType.IntType })
                    ) {
                        ServiceJobPrintScreen(onBack = { nav.popBackStack() })
                    }

                    screen(Screen.ServiceMgmt.route) { ServiceManagementScreen { nav.popBackStack() } }
                    screen(Screen.Chat.route) { ChatScreen { nav.popBackStack() } }
                    screen(Screen.Account.route) { AccountSettingsScreen { nav.popBackStack() } }
                    screen(Screen.SoftwareUpdate.route) { SoftwareUpdateScreen { nav.popBackStack() } }
                    screen(Screen.About.route) { AboutScreen { nav.popBackStack() } }
                }
            }
        }
    }
}
