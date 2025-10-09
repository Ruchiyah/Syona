package com.example.syona.data.repository

import com.example.syona.data.model.Habit
import com.example.syona.data.model.HabitIcon
import com.example.syona.data.model.HydrationState
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodPreferences
import com.example.syona.data.model.MoodType
import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.model.ProfileData
import com.example.syona.data.model.StepCounterPreferences
import com.example.syona.data.model.StepSensorType
import com.example.syona.data.model.WellnessStats
import com.example.syona.data.preferences.PreferencesManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WellnessRepository(private val preferencesManager: PreferencesManager) {

    private val habitsFlow = MutableStateFlow<List<Habit>>(emptyList())
    private val moodFlow = MutableStateFlow<List<MoodEntry>>(emptyList())
    private val hydrationFlow = MutableStateFlow(HydrationState())
    private val profileFlow = MutableStateFlow(ProfileData())
    private val statsFlow = MutableStateFlow(WellnessStats())
    private val moodPreferencesFlow = MutableStateFlow(preferencesManager.getMoodPreferences())
    private val stepCounterFlow = MutableStateFlow(preferencesManager.getStepCounterPreferences())
    private val stepLock = Any()

    init {
        refreshAll()
        // Sample mood data removed - users start with empty mood journal
    }

    fun refreshAll() {
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        refreshHabits(todayStart)
        refreshMood()
        refreshHydration(todayStart)
        refreshProfile()
        refreshStepCounter(todayStart)
        recomputeStats()
    }

    private fun refreshHabits(todayStart: Long) {
        val lastReset = preferencesManager.getHabitsResetDate()
        val allHabits = preferencesManager.getHabits()
        if (lastReset == 0L || lastReset < todayStart) {
            val resetHabits = allHabits.map { it.copy(completed = false) }
            preferencesManager.saveHabits(resetHabits)
            preferencesManager.setHabitsResetDate(todayStart)
            // Filter habits by frequency for today
            habitsFlow.value = resetHabits.filter { it.frequency.shouldShowToday() }
        } else {
            // Filter habits by frequency for today
            habitsFlow.value = allHabits.filter { it.frequency.shouldShowToday() }
        }
    }

    private fun refreshMood() {
        moodFlow.value = preferencesManager.getMoodEntries()
        moodPreferencesFlow.value = preferencesManager.getMoodPreferences()
    }

    private fun refreshHydration(todayStart: Long) {
        val state = preferencesManager.getHydrationState()
        val resetState = if (state.lastResetDate < todayStart) {
            state.copy(intake = 0, lastResetDate = todayStart)
        } else state
        hydrationFlow.value = resetState
        if (resetState != state) {
            preferencesManager.saveHydrationState(resetState)
        }
    }

    private fun refreshProfile() {
        profileFlow.value = preferencesManager.getProfile()
    }

    private fun refreshStepCounter(todayStart: Long) {
        val prefs = preferencesManager.getStepCounterPreferences()
        val needsReset = prefs.lastResetDate < todayStart
        val resetPrefs = if (needsReset) {
            prefs.copy(
                stepsToday = 0,
                lastSensorTotal = 0f,
                lastResetDate = todayStart
            )
        } else {
            prefs
        }
        stepCounterFlow.value = resetPrefs
        if (needsReset) {
            preferencesManager.saveStepCounterPreferences(resetPrefs)
        }
    }

    fun observeHabits(): StateFlow<List<Habit>> = habitsFlow.asStateFlow()

    fun observeMoodEntries(): StateFlow<List<MoodEntry>> = moodFlow.asStateFlow()

    fun observeMoodPreferences(): StateFlow<MoodPreferences> = moodPreferencesFlow.asStateFlow()

    fun observeHydration(): StateFlow<HydrationState> = hydrationFlow.asStateFlow()

    fun observeProfile(): StateFlow<ProfileData> = profileFlow.asStateFlow()

    fun observeStats(): StateFlow<WellnessStats> = statsFlow.asStateFlow()

    fun observeStepCounter(): StateFlow<StepCounterPreferences> = stepCounterFlow.asStateFlow()

    fun addHabit(name: String, icon: HabitIcon, frequency: String, notes: String?) {
        val current = habitsFlow.value.toMutableList()
        val order = current.size
        val habit = Habit(
            name = name,
            icon = icon,
            frequency = com.example.syona.data.model.HabitFrequency.fromDisplayName(frequency),
            order = order,
            notes = notes
        )
        current.add(habit)
        persistHabits(current)
    }

    fun updateHabit(updated: Habit) {
        val current = habitsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            current[index] = updated
            persistHabits(current)
        }
    }

    fun toggleHabitCompletion(habitId: Long) {
        val current = habitsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == habitId }
        if (index != -1) {
            val habit = current[index]
            val newCompleted = !habit.completed
            current[index] = habit.copy(completed = newCompleted)
            persistHabits(current)
            handleStreaks()
        }
    }

    fun reorderHabits(fromPosition: Int, toPosition: Int) {
        val current = habitsFlow.value.toMutableList()
        if (fromPosition in current.indices && toPosition in current.indices) {
            val habit = current.removeAt(fromPosition)
            current.add(toPosition, habit)
            val reordered = current.mapIndexed { index, item ->
                item.copy(order = index)
            }
            persistHabits(reordered)
        }
    }

    fun deleteHabit(habitId: Long) {
        val current = habitsFlow.value.filterNot { it.id == habitId }
        persistHabits(current.mapIndexed { index, habit -> habit.copy(order = index) })
    }

    private fun persistHabits(habits: List<Habit>) {
        // Get all habits from preferences to maintain the complete list
        val allStoredHabits = preferencesManager.getHabits()

        // Create a map of existing habits by ID
        val existingHabitsMap = allStoredHabits.associateBy { it.id }

        // Merge the incoming habits with existing ones
        val mergedHabits = habits.map { updatedHabit ->
            // If this habit exists, use the updated version; otherwise keep the original
            updatedHabit
        }.toMutableList()

        // Add habits that exist in storage but not in the incoming list
        allStoredHabits.forEach { storedHabit ->
            if (habits.none { it.id == storedHabit.id }) {
                mergedHabits.add(storedHabit)
            }
        }

        // Save all habits (including those not shown today)
        preferencesManager.saveHabits(mergedHabits)

        // Only show habits that should be displayed today
        habitsFlow.value = mergedHabits
            .filter { it.frequency.shouldShowToday() }
            .sortedBy { it.order }

        recomputeStats()
    }

    fun addMoodEntry(moodType: MoodType, note: String?) {
        val current = moodFlow.value.toMutableList()
        current.add(0, MoodEntry(mood = moodType, note = note))
        persistMood(current)
        if (moodPreferencesFlow.value.insightsDismissed) {
            preferencesManager.setMoodInsightsDismissed(false)
            moodPreferencesFlow.value = moodPreferencesFlow.value.copy(insightsDismissed = false)
        }
    }

    fun deleteMoodEntry(entryId: Long) {
        val current = moodFlow.value.filterNot { it.id == entryId }
        persistMood(current)
    }

    private fun persistMood(entries: List<MoodEntry>) {
        preferencesManager.saveMoodEntries(entries)
        moodFlow.value = entries.sortedByDescending { it.timestamp }
        if (entries.isEmpty() && moodPreferencesFlow.value.insightsDismissed) {
            preferencesManager.setMoodInsightsDismissed(false)
            moodPreferencesFlow.value = moodPreferencesFlow.value.copy(insightsDismissed = false)
        }
        recomputeStats()
    }

    fun setMoodViewMode(mode: MoodViewMode) {
        if (moodPreferencesFlow.value.viewMode == mode) return
        preferencesManager.setMoodViewMode(mode)
        moodPreferencesFlow.value = moodPreferencesFlow.value.copy(viewMode = mode)
    }

    fun setMoodInsightsDismissed(dismissed: Boolean) {
        if (moodPreferencesFlow.value.insightsDismissed == dismissed) return
        preferencesManager.setMoodInsightsDismissed(dismissed)
        moodPreferencesFlow.value = moodPreferencesFlow.value.copy(insightsDismissed = dismissed)
    }

    fun updateHydrationState(update: (HydrationState) -> HydrationState) {
        val oldState = hydrationFlow.value
        val newState = update(oldState)
        android.util.Log.d("WellnessRepo", "Hydration updated: ${oldState.intake}ml -> ${newState.intake}ml (goal: ${newState.dailyGoal}ml)")
        hydrationFlow.value = newState
        preferencesManager.saveHydrationState(newState)
        recomputeStats()
    }

    fun updateProfile(profileData: ProfileData) {
        profileFlow.value = profileData
        preferencesManager.saveProfile(profileData)
    }

    fun clearAllData() {
        val onboarding = preferencesManager.isOnboardingComplete()
        preferencesManager.clearAllData()
        if (onboarding) {
            preferencesManager.setOnboardingComplete(true)
        }
        refreshAll()
    }

    private fun handleStreaks() {
        val habits = habitsFlow.value
        if (habits.isEmpty() || habits.any { !it.completed }) {
            return
        }
        val today = LocalDate.now()
        val todayEpoch = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val lastCompletion = preferencesManager.getLastCompletionDate()
        if (lastCompletion >= todayEpoch) {
            return
        }
        val yesterdayEpoch = today.minusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val currentStreak = if (lastCompletion in yesterdayEpoch until todayEpoch) {
            preferencesManager.getCurrentStreak() + 1
        } else {
            1
        }
        preferencesManager.setCurrentStreak(currentStreak)
        val longest = preferencesManager.getLongestStreak()
        if (currentStreak > longest) {
            preferencesManager.setLongestStreak(currentStreak)
        }
        preferencesManager.setLastCompletionDate(todayEpoch)
        recomputeStats()
    }

    private fun recomputeStats() {
        val habits = habitsFlow.value
        val hydration = hydrationFlow.value
        val moodEntries = moodFlow.value
        val steps = stepCounterFlow.value
        val completedCount = habits.count { it.completed }
        val total = habits.size
        val currentStreak = preferencesManager.getCurrentStreak()
        val longest = preferencesManager.getLongestStreak()
        val averageMood = if (moodEntries.isEmpty()) {
            0.0
        } else {
            val recentMoods = moodEntries.take(30)
            val sum = recentMoods.sumOf { it.mood.score }
            sum.toDouble() / recentMoods.size
        }
        val hydrationPercentage = if (hydration.dailyGoal == 0) 0 else ((hydration.intake.toDouble() / hydration.dailyGoal.toDouble()) * 100).toInt()
        
        statsFlow.value = WellnessStats(
            habitsCompletedToday = completedCount,
            totalHabits = total,
            currentStreak = currentStreak,
            longestStreak = longest,
            averageMoodScore = averageMood,
            hydrationPercentage = hydrationPercentage.coerceIn(0, 100),
            stepsToday = steps.stepsToday,
            stepsGoal = steps.dailyGoal
        )
    }

    fun setStepCounterEnabled(enabled: Boolean) {
        updateStepCounter {
            copy(enabled = enabled)
        }
    }

    fun setStepCounterGoal(goal: Int) {
        val safeGoal = goal.coerceAtLeast(1000)
        updateStepCounter {
            copy(dailyGoal = safeGoal)
        }
    }

    fun setStepSensorType(type: StepSensorType) {
        updateStepCounter {
            copy(sensorType = type)
        }
    }

    fun resetTodaySteps() {
        val todayStart = startOfTodayMillis()
        updateStepCounter {
            copy(
                stepsToday = 0,
                lastSensorTotal = 0f,
                lastResetDate = todayStart
            )
        }
    }

    fun applyStepCounterReading(totalSteps: Float) {
        updateStepCounter {
            val todayStart = startOfTodayMillis()
            var updated = this
            if (lastResetDate < todayStart) {
                updated = updated.copy(
                    stepsToday = 0,
                    lastSensorTotal = totalSteps,
                    lastResetDate = todayStart
                )
            }

            val previousTotal = updated.lastSensorTotal
            val delta = when {
                totalSteps < previousTotal -> 0
                previousTotal == 0f -> 0
                else -> (totalSteps - previousTotal).toInt()
            }

            updated.copy(
                stepsToday = (updated.stepsToday + delta).coerceAtLeast(0),
                lastSensorTotal = totalSteps
            )
        }
    }

    fun incrementStepDetector(count: Int) {
        if (count <= 0) return
        updateStepCounter {
            val todayStart = startOfTodayMillis()
            val updated = if (lastResetDate < todayStart) {
                copy(
                    stepsToday = 0,
                    lastResetDate = todayStart
                )
            } else this

            updated.copy(stepsToday = (updated.stepsToday + count).coerceAtLeast(0))
        }
    }

    private fun updateStepCounter(block: StepCounterPreferences.() -> StepCounterPreferences) {
        val updated = synchronized(stepLock) {
            val current = stepCounterFlow.value
            val result = block(current)
            stepCounterFlow.value = result
            preferencesManager.saveStepCounterPreferences(result)
            result
        }
        recomputeStats()
    }

    private fun startOfTodayMillis(): Long {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    fun isOnboardingComplete(): Boolean = preferencesManager.isOnboardingComplete()

    fun setOnboardingComplete(value: Boolean) = preferencesManager.setOnboardingComplete(value)

    fun updateHabitsResetDate() {
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        preferencesManager.setHabitsResetDate(today)
    }

    // Hydration Management Methods
    val hydrationState: StateFlow<HydrationState> = hydrationFlow.asStateFlow()

    fun addWaterIntake(amount: Int) {
        updateHydrationState { current ->
            current.copy(intake = current.intake + amount)
        }
    }

    fun updateHydrationGoal(goalMl: Int) {
        updateHydrationState { current ->
            current.copy(dailyGoal = goalMl)
        }
    }

    fun updateHydrationReminders(enabled: Boolean, intervalMinutes: Int) {
        updateHydrationState { current ->
            current.copy(
                remindersEnabled = enabled,
                reminderIntervalMinutes = intervalMinutes
            )
        }
    }

    fun resetDailyHydration() {
        updateHydrationState { current ->
            current.copy(
                intake = 0,
                lastResetDate = System.currentTimeMillis()
            )
        }
    }
}
