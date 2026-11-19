package com.example.syona.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.syona.MainActivity
import com.example.syona.R
import com.example.syona.SyonaApp
import com.example.syona.notifications.NotificationChannels

class HydrationReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SyonaApp
        app.awaitStartup() // may run before Application.onCreate finishes (R9)
        val hydrationState = app.repository.hydrationSnapshot()

        // Check if reminders are enabled
        if (!hydrationState.remindersEnabled) {
            return Result.success()
        }

        // Calculate completion percentage
        val completionPercentage = if (hydrationState.dailyGoal > 0) {
            (hydrationState.intake.toDouble() / hydrationState.dailyGoal.toDouble()) * 100
        } else {
            0.0
        }

        // Only send notification if goal is not completed (less than 100%)
        if (completionPercentage >= 100.0) {
            return Result.success()
        }

        // Goal not met, send reminder
        NotificationChannels.createAll(applicationContext)
        showHydrationReminder()
        return Result.success()
    }

    private fun showHydrationReminder() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "hydration")
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle("💧 Time to hydrate!")
            .setContentText("Don't forget to drink some water to stay healthy")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500, 200, 500))
            .setDefaults(NotificationCompat.DEFAULT_SOUND)

        // Trigger vibration manually
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = applicationContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 200, 500, 200, 500, 200, 500),
                    -1
                )
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500, 200, 500), -1)
            }
        } catch (_: Exception) {
            // Vibration failed, continue without it
        }

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            with(NotificationManagerCompat.from(applicationContext)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "hydration_reminders"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "hydration_reminder_work"
    }
}