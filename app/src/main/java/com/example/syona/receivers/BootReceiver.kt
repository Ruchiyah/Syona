package com.example.syona.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.syona.SyonaApp
import com.example.syona.services.StepCounterService

/**
 * BroadcastReceiver that restarts the step counter service after device reboot.
 * This ensures continuous step tracking even after the device is restarted.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if step counter was enabled before reboot
            val app = context.applicationContext as? SyonaApp
            val repository = app?.repository
            val stepPrefs = repository?.observeStepCounter()?.value

            // If step counter was enabled, restart the service
            // StepCounterService.start() is a no-op if ACTIVITY_RECOGNITION was revoked
            if (stepPrefs?.enabled == true) {
                StepCounterService.start(context)
            }
        }
    }
}

