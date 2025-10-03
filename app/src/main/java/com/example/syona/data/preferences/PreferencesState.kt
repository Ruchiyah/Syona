package com.example.syona.data.preferences

import com.example.syona.data.model.Habit
import com.example.syona.data.model.HydrationState
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.model.ProfileData
import com.example.syona.data.model.StepCounterPreferences

internal data class PreferencesState(
    val habits: List<Habit> = emptyList(),
    val moodEntries: List<MoodEntry> = emptyList(),
    val hydrationState: HydrationState = HydrationState(),
    val profileData: ProfileData = ProfileData(),
    val onboardingComplete: Boolean = false,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletionDate: Long = 0L,
    val habitsResetDate: Long = 0L,
    val moodViewMode: String = MoodViewMode.LIST.name,
    val moodInsightsDismissed: Boolean = false,
    val stepCounter: StepCounterPreferences = StepCounterPreferences()
)
