package com.example.syona.domain

import com.example.syona.data.db.HabitCompletionEntity
import com.example.syona.data.db.HabitEntity
import com.example.syona.data.model.HabitFrequency
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfectDaysTest {
    private val monday = LocalDate.of(2026, 11, 16).toEpochDay()
    private val saturday = monday + 5
    private val t = monday + 100

    private fun habit(
        id: Long = 1,
        frequency: HabitFrequency = HabitFrequency.DAILY,
        customDays: List<Int> = emptyList(),
        archived: Boolean = false,
        createdAtDay: Long = 0
    ) = HabitEntity(
        id = id, name = "h$id", icon = "i", frequency = frequency.name,
        customDays = customDays, sortOrder = 0, archived = archived, createdAtDay = createdAtDay
    )

    private fun done(habitId: Long, day: Long) = HabitCompletionEntity(habitId, day)

    @Test fun weekdaysHabitNotRequiredOnSaturday() {
        val h = habit(frequency = HabitFrequency.WEEKDAYS)
        assertTrue(h.isRequiredOn(monday))
        assertFalse(h.isRequiredOn(saturday))
    }

    @Test fun habitNotRequiredBeforeCreated() {
        val h = habit(createdAtDay = monday)
        assertFalse(h.isRequiredOn(monday - 1))
        assertTrue(h.isRequiredOn(monday))
    }

    @Test fun timesPerWeekNeverRequired() {
        assertFalse(habit(frequency = HabitFrequency.TIMES_PER_WEEK).isRequiredOn(monday))
    }

    @Test fun archivedNeverRequired() {
        assertFalse(habit(archived = true).isRequiredOn(monday))
    }

    @Test fun dayWithNoRequiredHabitsIsNotPerfect() {
        assertEquals(emptySet<Long>(), PerfectDays.perfectDays(emptyList(), emptyList(), monday, monday + 2))
    }

    @Test fun perfectRequiresAllRequiredHabits() {
        val habits = listOf(habit(1), habit(2))
        val c = listOf(done(1, monday), done(2, monday), done(1, monday + 1))
        assertEquals(setOf(monday), PerfectDays.perfectDays(habits, c, monday, monday + 1))
    }

    @Test fun currentStreakAllowsTodayIncomplete() {
        val r = PerfectDays.streak(setOf(t - 3, t - 2, t - 1), t, 0, Long.MIN_VALUE, 0)
        assertEquals(3, r.current)
    }

    @Test fun currentStreakBrokenByGap() {
        val r = PerfectDays.streak(setOf(t - 4, t - 2, t - 1), t, 0, Long.MIN_VALUE, 0)
        assertEquals(2, r.current)
        assertEquals(2, r.best)
    }

    @Test fun legacyStreakJoinsHistory() {
        val r = PerfectDays.streak(setOf(t - 2, t - 1), t, 12, t - 3, 0)
        assertEquals(14, r.current)
        assertEquals(14, r.best)
    }

    @Test fun legacyStreakAloneStillCurrentYesterday() {
        val r = PerfectDays.streak(emptySet(), t, 5, t - 1, 0)
        assertEquals(5, r.current)
    }

    @Test fun legacyBestIsFloor() {
        val r = PerfectDays.streak(setOf(t), t, 0, Long.MIN_VALUE, 30)
        assertEquals(30, r.best)
        assertEquals(1, r.current)
    }

    @Test fun deletedHabitRemovedFromHistory() {
        val a = habit(1)
        val b = habit(2)
        val c = listOf(done(1, monday), done(2, monday), done(1, monday + 1))
        assertEquals(setOf(monday), PerfectDays.perfectDays(listOf(a, b), c, monday, monday + 1))
        assertEquals(setOf(monday, monday + 1), PerfectDays.perfectDays(listOf(a), c, monday, monday + 1))
    }

    @Test fun legacyLastDayInsideRun() {
        val r = PerfectDays.streak(setOf(t - 1), t, 5, t - 1, 0)
        assertEquals(5, r.current)
        assertEquals(5, r.best)
    }

    @Test fun legacyLastDayInsideLongerRun() {
        assertEquals(6, PerfectDays.streak(setOf(t - 1, t), t, 5, t - 1, 0).current)
    }

    @Test fun legacyTodayAndTodayPerfect() {
        assertEquals(3, PerfectDays.streak(setOf(t), t, 3, t, 0).current)
    }

    @Test fun legacyJoinedRunNotCurrentCountsForBest() {
        val r = PerfectDays.streak(setOf(t - 20, t - 19), t, 10, t - 21, 0)
        assertEquals(0, r.current)
        assertEquals(12, r.best)
    }
}
