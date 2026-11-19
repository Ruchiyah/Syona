package com.example.syona.data.preferences

import com.example.syona.data.model.Habit
import com.example.syona.data.model.HabitFrequency
import com.example.syona.data.model.HabitIcon
import com.example.syona.data.model.HydrationState
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodType
import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.model.ProfileData
import com.example.syona.data.model.StepCounterPreferences
import com.example.syona.data.model.StepSensorType

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

/**
 * Gson bypasses Kotlin null-safety: classes without a no-arg constructor (e.g. [Habit],
 * [MoodEntry]) are allocated without running their constructor, so a field missing from older
 * stored JSON — or an enum constant that no longer exists — arrives as `null` in a non-null
 * property and crashes later (e.g. `habit.frequency.shouldShowToday()`).
 * This repairs such values right after deserialization.
 */
internal fun PreferencesState.sanitized(): PreferencesState {
    // Upcasts to nullable types so the compiler cannot assume the checks below are always false
    val rawHabits: List<Habit?> = (habits as List<Habit?>?).orEmpty()
    val rawMoods: List<MoodEntry?> = (moodEntries as List<MoodEntry?>?).orEmpty()
    return copy(
        habits = rawHabits
            .filterNotNull()
            .filter { (it.name as String?) != null }
            .map { habit ->
                val frequency = habit.frequency as HabitFrequency?
                val icon = habit.icon as HabitIcon?
                if (frequency == null || icon == null) {
                    habit.copy(
                        frequency = frequency ?: HabitFrequency.DAILY,
                        icon = icon ?: HabitIcon.HEART
                    )
                } else habit
            },
        moodEntries = rawMoods.filterNotNull().filter { (it.mood as MoodType?) != null },
        hydrationState = (hydrationState as HydrationState?) ?: HydrationState(),
        profileData = (profileData as ProfileData?) ?: ProfileData(),
        moodViewMode = (moodViewMode as String?) ?: MoodViewMode.LIST.name,
        stepCounter = (stepCounter as StepCounterPreferences?)?.let {
            if ((it.sensorType as StepSensorType?) == null) it.copy(sensorType = StepSensorType.AUTO) else it
        } ?: StepCounterPreferences()
    )
}
