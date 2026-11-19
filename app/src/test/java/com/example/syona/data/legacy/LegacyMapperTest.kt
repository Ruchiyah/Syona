package com.example.syona.data.legacy

import com.example.syona.data.model.Habit
import com.example.syona.data.model.HabitFrequency
import com.example.syona.data.model.HydrationState
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodType
import com.example.syona.data.model.StepCounterPreferences
import com.example.syona.data.preferences.PreferencesState
import com.example.syona.data.settings.AppSettings
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMapperTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 19)
    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun map(s: PreferencesState) = LegacyMapper.map(s, zone, today)

    @Test
    fun weeklyBecomesTimesPerWeekTarget1() {
        val b = map(
            PreferencesState(
                habits = listOf(
                    Habit(id = ms(LocalDate.of(2026, 11, 1)), name = "w", frequency = HabitFrequency.WEEKLY, order = 3)
                )
            )
        )
        val h = b.habits.single()
        assertEquals("TIMES_PER_WEEK", h.frequency)
        assertEquals(1, h.targetPerWeek)
        assertEquals(3, h.sortOrder)
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), h.createdAtDay)
        assertTrue(h.customDays.isEmpty())
    }

    @Test
    fun completedHabitGetsCompletionOnResetDay() {
        val b = map(
            PreferencesState(
                habits = listOf(Habit(id = ms(LocalDate.of(2026, 11, 1)), name = "d", completed = true)),
                habitsResetDate = ms(LocalDate.of(2026, 11, 17))
            )
        )
        assertEquals(LocalDate.of(2026, 11, 17).toEpochDay(), b.completions.single().day)
    }

    @Test
    fun waterLandsOnItsOwnDayWithFixedId() {
        val b = map(
            PreferencesState(
                hydrationState = HydrationState(intake = 750, lastResetDate = ms(LocalDate.of(2026, 11, 18)))
            )
        )
        val w = b.waterLogs.single()
        assertEquals(-1L, w.id)
        assertEquals(750, w.amountMl)
        assertEquals(LocalDate.of(2026, 11, 18).toEpochDay(), w.day)
    }

    @Test
    fun zeroIntakeAndZeroStepsProduceNoRows() {
        val b = map(
            PreferencesState(
                hydrationState = HydrationState(intake = 0),
                stepCounter = StepCounterPreferences(stepsToday = 0, lastResetDate = ms(today))
            )
        )
        assertTrue(b.waterLogs.isEmpty())
        assertTrue(b.stepDays.isEmpty())
    }

    @Test
    fun stepsLandOnTheirDay() {
        val b = map(
            PreferencesState(stepCounter = StepCounterPreferences(stepsToday = 4000, lastResetDate = ms(today)))
        )
        assertEquals(4000, b.stepDays.single().steps)
        assertEquals(today.toEpochDay(), b.stepDays.single().day)
    }

    @Test
    fun moodScoresMapped() {
        val b = map(
            PreferencesState(
                moodEntries = listOf(
                    MoodEntry(id = 1, mood = MoodType.HAPPY, note = "n", timestamp = 10),
                    MoodEntry(id = 2, mood = MoodType.ANGRY, timestamp = 20)
                )
            )
        )
        assertEquals(listOf(5, 1), b.moods.map { it.score })
        assertEquals("n", b.moods[0].note)
    }

    @Test
    fun settingsCarryLegacyStreak() {
        val b = map(
            PreferencesState(
                longestStreak = 9,
                currentStreak = 4,
                lastCompletionDate = ms(LocalDate.of(2026, 11, 18)),
                onboardingComplete = true,
                moodViewMode = "CALENDAR"
            )
        )
        val s = b.settings(AppSettings())
        assertEquals(9, s.legacyPerfectBest)
        assertEquals(4, s.legacyPerfectCurrent)
        assertEquals(LocalDate.of(2026, 11, 18).toEpochDay(), s.legacyPerfectLastDay)
        assertTrue(s.onboardingComplete)
        assertEquals("CALENDAR", s.moodViewMode.name)
    }

    @Test
    fun emptyStateMapsToEmptyBundle() {
        val b = map(PreferencesState())
        assertTrue(b.habits.isEmpty() && b.completions.isEmpty() && b.moods.isEmpty())
        assertTrue(b.waterLogs.isEmpty() && b.stepDays.isEmpty())
        assertNull(b.profile)
        assertNull(b.photoBase64)
        assertEquals(1, b.goals.size)
        assertEquals(Long.MIN_VALUE, b.settings(AppSettings()).legacyPerfectLastDay)
    }
}
