package com.example.syona.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HabitFrequencyTest {

    private val allDays = (Calendar.SUNDAY..Calendar.SATURDAY).toList()

    @Test
    fun daily_and_weekly_show_every_day() {
        allDays.forEach { day ->
            assertTrue(HabitFrequency.DAILY.shouldShowOnDay(day))
            assertTrue(HabitFrequency.WEEKLY.shouldShowOnDay(day))
        }
    }

    @Test
    fun weekdays_show_monday_to_friday_only() {
        val shown = allDays.filter { HabitFrequency.WEEKDAYS.shouldShowOnDay(it) }
        assertEquals(
            listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
            shown
        )
    }

    @Test
    fun weekends_show_saturday_and_sunday_only() {
        val shown = allDays.filter { HabitFrequency.WEEKENDS.shouldShowOnDay(it) }.toSet()
        assertEquals(setOf(Calendar.SATURDAY, Calendar.SUNDAY), shown)
    }

    @Test
    fun custom_shows_only_selected_days() {
        val custom = listOf(Calendar.TUESDAY, Calendar.THURSDAY)
        allDays.forEach { day ->
            assertEquals(day in custom, HabitFrequency.CUSTOM.shouldShowOnDay(day, custom))
        }
    }

    @Test
    fun custom_with_no_days_is_never_shown() {
        allDays.forEach { day ->
            assertFalse(HabitFrequency.CUSTOM.shouldShowOnDay(day, null))
            assertFalse(HabitFrequency.CUSTOM.shouldShowOnDay(day, emptyList()))
        }
    }

    @Test
    fun fromDisplayName_is_case_insensitive_and_defaults_to_daily() {
        assertEquals(HabitFrequency.WEEKENDS, HabitFrequency.fromDisplayName("weekends"))
        assertEquals(HabitFrequency.CUSTOM, HabitFrequency.fromDisplayName("Custom"))
        assertEquals(HabitFrequency.DAILY, HabitFrequency.fromDisplayName("nonsense"))
        assertEquals(HabitFrequency.DAILY, HabitFrequency.fromDisplayName(null))
    }

    @Test
    fun moodType_fromLabel_defaults_to_neutral() {
        assertEquals(MoodType.HAPPY, MoodType.fromLabel("happy"))
        assertEquals(MoodType.NEUTRAL, MoodType.fromLabel("unknown"))
    }
}
