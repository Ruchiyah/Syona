package com.example.syona.data.model

data class HydrationState(
    val dailyGoal: Int = 2000,
    val intake: Int = 0,
    val remindersEnabled: Boolean = false,
    val reminderIntervalMinutes: Int = 60,
    val lastResetDate: Long = System.currentTimeMillis()
)
