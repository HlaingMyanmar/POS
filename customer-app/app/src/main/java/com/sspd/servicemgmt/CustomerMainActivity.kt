package com.sspd.servicemgmt

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.AuthEventBus
import com.sspd.servicemgmt.core.network.CustomerOrderAlerts
import com.sspd.servicemgmt.core.network.CustomerPushBridge
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.util.PreferenceManager
import com.sspd.servicemgmt.feature.auth.AuthScreen
import com.sspd.servicemgmt.feature.auth.CompleteProfileScreen
import com.sspd.servicemgmt.feature.auth.BiometricAuthHelper
import com.sspd.servicemgmt.feature.auth.CustomerAuthPolicy
import com.sspd.servicemgmt.feature.auth.SessionStartAction
import com.sspd.servicemgmt.feature.cart.CartStore
import com.sspd.servicemgmt.feature.home.HomeScaffold

class CustomerMainActivity : androidx.fragment.app.FragmentActivity() {
    private lateinit var prefs: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager(this)
        CartStore.init(this)
        if (prefs.serverUrl.isNotBlank()) ApiClient.setBaseUrl(prefs.serverUrl)
        CustomerPushBridge.initialize(this)
        applyOrderIntent(intent)
        setContent {
            AppTheme {
                val biometric = remember { BiometricAuthHelper(this@CustomerMainActivity) }
                val initialAction = remember {
                    CustomerAuthPolicy.startupAction(
                        hasToken = prefs.authToken.isNotBlank(),
                        idle = prefs.isSessionIdle(),
                        biometricEnabled = prefs.biometricEnabled
                    )
                }
                var loggedIn by remember {
                    mutableStateOf(initialAction == SessionStartAction.OPEN_SESSION)
                }
                var needsProfile by remember { mutableStateOf(prefs.needsProfile) }
                val expired by AuthEventBus.tokenExpired.collectAsState()

                fun forceLogout() {
                    CustomerPushBridge.unregister(this@CustomerMainActivity)
                    prefs.clearSession()
                    CartStore.detach()
                    needsProfile = false
                    loggedIn = false
                    AuthEventBus.reset()
                }

                // Cold start after idle → clear session once
                LaunchedEffect(Unit) {
                    when (CustomerAuthPolicy.startupAction(
                        hasToken = prefs.authToken.isNotBlank(),
                        idle = prefs.isSessionIdle(),
                        biometricEnabled = prefs.biometricEnabled
                    )) {
                        SessionStartAction.LOGOUT_IDLE_SESSION -> forceLogout()
                        SessionStartAction.REQUIRE_BIOMETRIC -> {
                            if (biometric.canAuthenticate()) {
                                biometric.authenticate(
                                    onSuccess = {
                                        prefs.touchSession()
                                        loggedIn = true
                                    },
                                    onError = {
                                        // Never fall through to a saved-token session when
                                        // biometric verification is cancelled or unavailable.
                                        forceLogout()
                                    }
                                )
                            } else {
                                // Enrollment removed, hardware unavailable, or lockout:
                                // invalidate the saved session and require password login.
                                forceLogout()
                            }
                        }
                        SessionStartAction.OPEN_SESSION -> {
                            prefs.touchSession()
                            loggedIn = true
                        }
                        SessionStartAction.SHOW_LOGIN -> Unit
                    }
                }

                LaunchedEffect(expired) {
                    if (expired) forceLogout()
                }

                when {
                    !loggedIn -> AuthScreen(onSuccess = {
                        prefs.touchSession()
                        CustomerPushBridge.initialize(this@CustomerMainActivity)
                        loggedIn = true
                        needsProfile = prefs.needsProfile
                    })
                    needsProfile -> CompleteProfileScreen(onDone = {
                        prefs.touchSession()
                        needsProfile = false
                    })
                    else -> HomeScaffold(
                        onLogout = { forceLogout() },
                        onIdleLogout = { forceLogout() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyOrderIntent(intent)
    }

    private fun applyOrderIntent(intent: Intent?) {
        if (intent == null) return
        val fromExtra = intent.getIntExtra(CustomerOrderAlerts.EXTRA_ORDER_ID, 0)
        val fromFcm = intent.getStringExtra("orderId")?.toIntOrNull()
            ?: intent.getStringExtra(CustomerOrderAlerts.EXTRA_ORDER_ID)?.toIntOrNull()
            ?: 0
        val orderId = if (fromExtra > 0) fromExtra else fromFcm
        if (orderId > 0) prefs.focusOrderId = orderId
        if (orderId > 0 ||
            intent.getBooleanExtra(CustomerOrderAlerts.EXTRA_OPEN_ORDERS, false) ||
            intent.getStringExtra(CustomerOrderAlerts.EXTRA_OPEN_ORDERS) == "true"
        ) {
            prefs.openOrdersTab = true
        }
        intent.removeExtra(CustomerOrderAlerts.EXTRA_ORDER_ID)
        intent.removeExtra("orderId")
        intent.removeExtra(CustomerOrderAlerts.EXTRA_OPEN_ORDERS)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::prefs.isInitialized && prefs.authToken.isNotBlank()) {
            prefs.touchSession()
        }
    }
}
