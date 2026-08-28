package com.sspd.servicemgmt

import android.Manifest
import android.content.Intent
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
import com.sspd.servicemgmt.core.tracking.LocationPermission
import com.sspd.servicemgmt.core.tracking.PendingPingSyncWorker
import com.sspd.servicemgmt.core.tracking.TrackingBootReceiver
import com.sspd.servicemgmt.core.tracking.VisitTracker
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TechnicianMainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleTrackingResumeIntent(intent, recoverNow = false)
        PendingPingSyncWorker.enqueue(this)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTrackingResumeIntent(intent, recoverNow = true)
    }

    private fun handleTrackingResumeIntent(intent: Intent?, recoverNow: Boolean) {
        if (intent?.action != TrackingBootReceiver.ACTION_RESUME_AFTER_REBOOT) return
        PreferenceManager(this).trackingPaused = false
        intent.action = null
        if (recoverNow && LocationPermission.granted(this)) {
            lifecycleScope.launch {
                VisitTracker.recover(this@TechnicianMainActivity)
                VisitTracker.resumeTracking(this@TechnicianMainActivity)
            }
        }
    }
}
