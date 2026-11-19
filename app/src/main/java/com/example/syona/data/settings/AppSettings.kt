package com.example.syona.data.settings

import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.model.StepSensorType

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val migratedV2: Boolean = false,
    val waterGoalMl: Int = 2000,
    val remindersEnabled: Boolean = false,
    val reminderIntervalMinutes: Int = 60,
    val stepEnabled: Boolean = false,
    val stepGoal: Int = 8000,
    val stepSensorType: StepSensorType = StepSensorType.AUTO,
    val lastSensorTotal: Float = 0f,
    val stepCounterDay: Long = 0L,
    val moodViewMode: MoodViewMode = MoodViewMode.LIST,
    val moodInsightsDismissed: Boolean = false,
    val legacyPerfectBest: Int = 0,
    val legacyPerfectCurrent: Int = 0,
    val legacyPerfectLastDay: Long = Long.MIN_VALUE
)
