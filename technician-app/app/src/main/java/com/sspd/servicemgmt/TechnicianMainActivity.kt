package com.sspd.servicemgmt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TechnicianMainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        TechnicianJobAlertWorker.schedule(this)
        TechnicianJobAlerts.createChannel(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    TechnicianJobAlerts.checkAndNotify(this@TechnicianMainActivity)
                    delay(30_000)
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            AppTheme {
                TechnicianAppNavigation()
            }
        }
    }
}
