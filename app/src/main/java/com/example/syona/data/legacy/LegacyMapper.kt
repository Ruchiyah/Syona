package com.example.syona.data.legacy

import com.example.syona.data.db.DailyGoalEntity
import com.example.syona.data.db.HabitCompletionEntity
import com.example.syona.data.db.HabitEntity
import com.example.syona.data.db.MoodEntryEntity
import com.example.syona.data.db.ProfileEntity
import com.example.syona.data.db.StepDayEntity
import com.example.syona.data.db.WaterLogEntity
import com.example.syona.data.model.HabitFrequency
import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.preferences.PreferencesState
import com.example.syona.data.settings.AppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class MigrationBundle(
    val habits: List<HabitEntity>,
    val completions: List<HabitCompletionEntity>,
    val moods: List<MoodEntryEntity>,
    val waterLogs: List<WaterLogEntity>,
    val stepDays: List<StepDayEntity>,
    val goals: List<DailyGoalEntity>,
    val profile: ProfileEntity?,
    val photoBase64: String?,
    val settings: (AppSettings) -> AppSettings
)

internal object LegacyMapper {
    private const val MIN_PLAUSIBLE_CREATION_MILLIS = 1_000_000_000_000L

    fun map(state: PreferencesState, zone: ZoneId, today: LocalDate): MigrationBundle {
        fun dayOf(ms: Long): Long =
            Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

        val todayDay = today.toEpochDay()
        val resetDay = if (state.habitsResetDate == 0L) todayDay else dayOf(state.habitsResetDate)

        val habits = state.habits.map { h ->
            val weekly = h.frequency == HabitFrequency.WEEKLY
            val created = if (h.id < MIN_PLAUSIBLE_CREATION_MILLIS) todayDay else minOf(todayDay, dayOf(h.id))
            HabitEntity(
                id = h.id,
                name = h.name,
                icon = h.icon.name,
                frequency = if (weekly) HabitFrequency.TIMES_PER_WEEK.name else h.frequency.name,
                customDays = h.customDays ?: emptyList(),
                targetPerWeek = if (weekly) 1 else 0,
                notes = h.notes,
                sortOrder = h.order,
                createdAtDay = created
            )
        }
        val completions = state.habits.filter { it.completed }.map { HabitCompletionEntity(it.id, resetDay) }
        val moods = state.moodEntries.map {
            MoodEntryEntity(id = it.id, score = it.mood.score, note = it.note, timestamp = it.timestamp)
        }

        val hydration = state.hydrationState
        val waterLogs = if (hydration.intake > 0) {
            listOf(
                WaterLogEntity(
                    id = -1,
                    amountMl = hydration.intake,
                    timestamp = hydration.lastResetDate,
                    day = dayOf(hydration.lastResetDate)
                )
            )
        } else emptyList()

        val steps = state.stepCounter
        val stepDays = if (steps.stepsToday > 0 && steps.lastResetDate > 0) {
            listOf(StepDayEntity(dayOf(steps.lastResetDate), steps.stepsToday))
        } else emptyList()

        val goals = listOf(DailyGoalEntity(todayDay, hydration.dailyGoal, steps.dailyGoal))

        val p = state.profileData
        val profileEmpty = p.name.isBlank() && p.age == null && p.gender.isBlank() &&
            p.wellnessGoal.isBlank() && p.photoUri.isNullOrEmpty()
        val profile = if (profileEmpty) null else ProfileEntity(
            name = p.name, age = p.age, gender = p.gender, wellnessGoal = p.wellnessGoal, photoPath = null
        )

        val viewMode = runCatching { MoodViewMode.valueOf(state.moodViewMode) }.getOrDefault(MoodViewMode.LIST)
        val lastStepDay = dayOf(steps.lastResetDate)
        val lastCompletionDay = if (state.lastCompletionDate == 0L) Long.MIN_VALUE else dayOf(state.lastCompletionDate)

        return MigrationBundle(
            habits = habits,
            completions = completions,
            moods = moods,
            waterLogs = waterLogs,
            stepDays = stepDays,
            goals = goals,
            profile = profile,
            photoBase64 = p.photoUri,
            settings = { s ->
                s.copy(
                    onboardingComplete = state.onboardingComplete,
                    waterGoalMl = hydration.dailyGoal,
                    remindersEnabled = hydration.remindersEnabled,
                    reminderIntervalMinutes = hydration.reminderIntervalMinutes,
                    stepEnabled = steps.enabled,
                    stepGoal = steps.dailyGoal,
                    stepSensorType = steps.sensorType,
                    lastSensorTotal = steps.lastSensorTotal,
                    stepCounterDay = lastStepDay,
                    moodViewMode = viewMode,
                    moodInsightsDismissed = state.moodInsightsDismissed,
                    legacyPerfectBest = state.longestStreak,
                    legacyPerfectCurrent = state.currentStreak,
                    legacyPerfectLastDay = lastCompletionDay
                )
            }
        )
    }
}
