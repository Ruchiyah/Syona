package com.example.syona.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class HydrationReminderManager(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun scheduleReminders(intervalMinutes: Int) {
        // Ensure minimum interval is 15 minutes for WorkManager
        val actualInterval = if (intervalMinutes < 15) 15 else intervalMinutes

        // Send immediate notification first
        val immediateWork = OneTimeWorkRequestBuilder<HydrationReminderWorker>()
            .build()
        workManager.enqueue(immediateWork)

        // Schedule periodic work
        val periodicWork = PeriodicWorkRequestBuilder<HydrationReminderWorker>(
            actualInterval.toLong(), TimeUnit.MINUTES
        )
            .setInitialDelay(actualInterval.toLong(), TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            HydrationReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWork
        )
    }

    fun cancelReminders() {
        workManager.cancelUniqueWork(HydrationReminderWorker.WORK_NAME)
    }
}