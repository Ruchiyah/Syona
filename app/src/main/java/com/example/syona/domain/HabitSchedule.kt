package com.example.syona.domain

import com.example.syona.data.db.HabitEntity
import com.example.syona.data.model.HabitFrequency
import java.time.LocalDate

/** True if this habit must be completed on [day] (epoch day) for the day to count as perfect. */
fun HabitEntity.isRequiredOn(day: Long): Boolean {
    if (archived || createdAtDay > day) return false
    val freq = runCatching { HabitFrequency.valueOf(frequency) }.getOrDefault(HabitFrequency.DAILY)
    if (freq == HabitFrequency.TIMES_PER_WEEK) return false
    return freq.shouldShowOnDay(calendarDayOfWeek(day), customDays)
}

/** Maps an epoch day to a java.util.Calendar DAY_OF_WEEK constant (Sunday = 1 ... Saturday = 7). */
private fun calendarDayOfWeek(epochDay: Long): Int =
    LocalDate.ofEpochDay(epochDay).dayOfWeek.value % 7 + 1
