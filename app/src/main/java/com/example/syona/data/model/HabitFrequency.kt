package com.example.syona.data.model

import java.util.Calendar

enum class HabitFrequency(val displayName: String) {
    DAILY("Daily"),
    WEEKDAYS("Weekdays"),
    WEEKENDS("Weekends"),
    WEEKLY("Weekly");

    companion object {
        fun fromDisplayName(displayName: String?): HabitFrequency = values()
            .firstOrNull { it.displayName.equals(displayName, true) }
            ?: DAILY
    }

    /**
     * Check if a habit with this frequency should be shown on the given day
     * @param dayOfWeek Calendar day constant (Calendar.SUNDAY = 1, Calendar.MONDAY = 2, etc.)
     */
    fun shouldShowOnDay(dayOfWeek: Int): Boolean {
        return when (this) {
            DAILY -> true
            WEEKDAYS -> dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
            WEEKENDS -> dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            WEEKLY -> true // Show weekly habits every day
        }
    }

    /**
     * Check if a habit with this frequency should be shown today
     */
    fun shouldShowToday(): Boolean {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return shouldShowOnDay(today)
    }
}
