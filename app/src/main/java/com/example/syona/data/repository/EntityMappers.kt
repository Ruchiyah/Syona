package com.example.syona.data.repository

import com.example.syona.data.db.HabitEntity
import com.example.syona.data.db.MoodEntryEntity
import com.example.syona.data.db.ProfileEntity
import com.example.syona.data.model.Habit
import com.example.syona.data.model.HabitFrequency
import com.example.syona.data.model.HabitIcon
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodType
import com.example.syona.data.model.ProfileData
import java.time.LocalDate

internal fun HabitEntity.frequencyEnum(): HabitFrequency =
    runCatching { HabitFrequency.valueOf(frequency) }.getOrDefault(HabitFrequency.DAILY)

/** Legacy WEEKLY is stored as TIMES_PER_WEEK with targetPerWeek = 1 (ruling R1). */
internal fun HabitEntity.withFrequency(freq: HabitFrequency): HabitEntity = when (freq) {
    HabitFrequency.WEEKLY -> copy(frequency = HabitFrequency.TIMES_PER_WEEK.name, targetPerWeek = 1)
    HabitFrequency.TIMES_PER_WEEK -> copy(frequency = freq.name, targetPerWeek = targetPerWeek.coerceAtLeast(1))
    else -> copy(frequency = freq.name)
}

internal fun HabitEntity.isShownOn(day: Long): Boolean =
    frequencyEnum().shouldShowOnDay(calendarDayOfWeek(day), customDays.ifEmpty { null })

internal fun HabitEntity.toHabit(completed: Boolean) = Habit(
    id = id,
    name = name,
    icon = HabitIcon.fromName(icon),
    frequency = frequencyEnum(),
    customDays = customDays.ifEmpty { null },
    completed = completed,
    order = sortOrder,
    notes = notes
)

internal fun MoodEntryEntity.toMoodEntry() = MoodEntry(
    id = id,
    mood = MoodType.entries.firstOrNull { it.score == score } ?: MoodType.NEUTRAL,
    note = note,
    timestamp = timestamp
)

internal fun ProfileEntity?.toProfileData(): ProfileData = if (this == null) ProfileData() else ProfileData(
    name = name,
    age = age,
    gender = gender,
    wellnessGoal = wellnessGoal,
    photoUri = photoPath
)

internal fun ProfileData.toEntity() = ProfileEntity(
    name = name,
    age = age,
    gender = gender,
    wellnessGoal = wellnessGoal,
    photoPath = photoUri
)

/** Maps an epoch day to a java.util.Calendar DAY_OF_WEEK constant (Sunday = 1 ... Saturday = 7). */
private fun calendarDayOfWeek(epochDay: Long): Int =
    LocalDate.ofEpochDay(epochDay).dayOfWeek.value % 7 + 1
