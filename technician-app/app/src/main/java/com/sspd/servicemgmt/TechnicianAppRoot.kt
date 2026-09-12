package com.sspd.servicemgmt

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.sspd.servicemgmt.core.security.AppLockScreen
import com.sspd.servicemgmt.core.security.TechnicianLocalSettings
import com.sspd.servicemgmt.core.security.InactivityAction
import com.sspd.servicemgmt.core.realtime.DataEventBus
import com.sspd.servicemgmt.core.security.ThemeMode
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.launch

@Composable
fun TechnicianAppRoot() {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val settings = remember { TechnicianLocalSettings(context.applicationContext) }
    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    // Existing feature screens still contain light-only card tokens. Keep the
    // app on the verified high-contrast palette until each screen is migrated.
    val useDarkTheme = false
    var locked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var sessionGeneration by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (PreferenceManager(context).authToken.isNotEmpty()) {
                        scope.launch { settings.markBackgrounded() }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (PreferenceManager(context).authToken.isNotEmpty()) {
scope.launch {
                            when (settings.inactivityAction()) {
                                InactivityAction.LOCK -> locked = true
                                InactivityAction.LOGOUT -> {
                                    locked = false
                                    PreferenceManager(context).clear()
                                    DataEventBus.disconnect()
                                    sessionGeneration++
                                }
                                InactivityAction.NONE -> Unit
                            }
                        }
                    } else {
                        scope.launch { settings.clearBackgroundTime() }
                    }
                }
                else -> Unit
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun requestUnlock() {
        error = null
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                    scope.launch { settings.clearBackgroundTime() }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) error = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    error = "Authentication မအောင်မြင်ပါ။ ထပ်မံစမ်းကြည့်ပါ။"
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("SSPD Technician ကို Unlock လုပ်ပါ")
            .setSubtitle("Fingerprint, Face သို့မဟုတ် Device PIN ကိုသုံးပါ")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    AppTheme(darkTheme = useDarkTheme) {
        Box(Modifier.fillMaxSize()) {
            key(sessionGeneration) { TechnicianAppNavigation() }
            if (locked) {
                AppLockScreen(
                    onUnlock = {
                        locked = false
                        scope.launch { settings.clearBackgroundTime() }
                    },
                    onBiometricClick = ::requestUnlock,
                    onLogoutClick = {
                        locked = false
                        PreferenceManager(context).clear()
                        DataEventBus.disconnect()
                        sessionGeneration++
                    },
                    error = error
                )
            }
        }
    }
}