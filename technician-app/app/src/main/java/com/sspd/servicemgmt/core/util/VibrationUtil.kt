package com.sspd.servicemgmt.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationUtil {

    // Distinct pattern for new service job alerts (pause, buzz, pause, buzz, pause, long buzz)
    private val JOB_ALERT_PATTERN = longArrayOf(0, 300, 150, 300, 150, 450)

    fun vibrateNewJob(context: Context) {
        triggerVibration(context, JOB_ALERT_PATTERN)
    }

    fun vibrateShort(context: Context) {
        triggerVibration(context, longArrayOf(0, 200))
    }

    private fun triggerVibration(context: Context, pattern: LongArray) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        }
    }
}
