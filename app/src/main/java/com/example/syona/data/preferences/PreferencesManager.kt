package com.example.syona.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.syona.data.model.Habit
import com.example.syona.data.model.HydrationState
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodPreferences
import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.model.ProfileData
import com.example.syona.data.model.StepCounterPreferences
import com.google.gson.Gson

class PreferencesManager(context: Context) {

    private val gson = Gson()
    private val lock = Any()
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var state: PreferencesState = readState()

    init {
        if (!sharedPreferences.contains(KEY_STATE)) {
            writeState(state)
        }
    }

    fun getHabits(): MutableList<Habit> = accessState {
        it.habits.sortedBy { habit -> habit.order }
    }.toMutableList()

    fun saveHabits(habits: List<Habit>) {
        updateState { copy(habits = habits) }
    }

    fun getMoodEntries(): MutableList<MoodEntry> = accessState {
        it.moodEntries.sortedByDescending { entry -> entry.timestamp }
    }.toMutableList()

    fun saveMoodEntries(entries: List<MoodEntry>) {
        updateState { copy(moodEntries = entries) }
    }

    fun getMoodPreferences(): MoodPreferences = accessState {
        MoodPreferences(
            viewMode = runCatching { MoodViewMode.valueOf(it.moodViewMode) }.getOrDefault(MoodViewMode.LIST),
            insightsDismissed = it.moodInsightsDismissed
        )
    }

    fun setMoodViewMode(mode: MoodViewMode) {
        updateState { copy(moodViewMode = mode.name) }
    }

    fun setMoodInsightsDismissed(dismissed: Boolean) {
        updateState { copy(moodInsightsDismissed = dismissed) }
    }

    fun getHydrationState(): HydrationState = accessState { it.hydrationState }

    fun saveHydrationState(state: HydrationState) {
        updateState { copy(hydrationState = state) }
    }

    fun getProfile(): ProfileData = accessState { it.profileData }

    fun saveProfile(profileData: ProfileData) {
        updateState { copy(profileData = profileData) }
    }

    fun getStepCounterPreferences(): StepCounterPreferences = accessState { it.stepCounter }

    fun saveStepCounterPreferences(preferences: StepCounterPreferences) {
        updateState { copy(stepCounter = preferences) }
    }

    fun isOnboardingComplete(): Boolean = accessState { it.onboardingComplete }

    fun setOnboardingComplete(value: Boolean) {
        updateState { copy(onboardingComplete = value) }
    }

    fun getCurrentStreak(): Int = accessState { it.currentStreak }

    fun setCurrentStreak(value: Int) {
        updateState { copy(currentStreak = value) }
    }

    fun getLongestStreak(): Int = accessState { it.longestStreak }

    fun setLongestStreak(value: Int) {
        updateState { copy(longestStreak = value) }
    }

    fun getLastCompletionDate(): Long = accessState { it.lastCompletionDate }

    fun setLastCompletionDate(value: Long) {
        updateState { copy(lastCompletionDate = value) }
    }

    fun getHabitsResetDate(): Long = accessState { it.habitsResetDate }

    fun setHabitsResetDate(value: Long) {
        updateState { copy(habitsResetDate = value) }
    }

    fun clearAllData() {
        updateState { PreferencesState() }
    }

    private fun <T> accessState(block: (PreferencesState) -> T): T {
        return synchronized(lock) { block(state) }
    }

    private fun updateState(transform: PreferencesState.() -> PreferencesState) {
        synchronized(lock) {
            val updated = state.transform()
            state = updated
            writeState(updated)
        }
    }

    private fun readState(): PreferencesState {
        val json = sharedPreferences.getString(KEY_STATE, null) ?: return PreferencesState()
        return runCatching {
            gson.fromJson(json, PreferencesState::class.java)
        }.getOrElse { PreferencesState() }
    }

    private fun writeState(state: PreferencesState) {
        val json = gson.toJson(state)
        sharedPreferences.edit { putString(KEY_STATE, json) }
    }

    companion object {
        private const val PREFS_NAME = "syona_prefs"
        private const val KEY_STATE = "wellness_state"
    }
}
