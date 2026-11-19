package com.example.syona.data.repository

import com.example.syona.data.db.DailyGoalEntity
import com.example.syona.data.db.HabitCompletionEntity
import com.example.syona.data.model.HabitIcon
import com.example.syona.data.model.MoodType
import com.example.syona.data.settings.AppSettings
import com.example.syona.fakes.FakeGoalDao
import com.example.syona.fakes.FakeHabitDao
import com.example.syona.fakes.FakeMoodDao
import com.example.syona.fakes.FakeProfileDao
import com.example.syona.fakes.FakeStepDao
import com.example.syona.fakes.FakeWaterDao
import com.example.syona.fakes.InMemorySettingsStore
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WellnessRepositoryTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advance(d: Duration) { now = now.plus(d) }
    }

    private val clock = MutableClock(Instant.parse("2026-11-19T10:00:00Z"))
    private val today = LocalDate.of(2026, 11, 19).toEpochDay()
    private val habitDao = FakeHabitDao()
    private val moodDao = FakeMoodDao()
    private val waterDao = FakeWaterDao()
    private val stepDao = FakeStepDao()
    private val goalDao = FakeGoalDao()
    private val profileDao = FakeProfileDao()
    private val settings = InMemorySettingsStore()

    private suspend fun TestScope.repo(): WellnessRepository {
        val r = WellnessRepository(
            habitDao, moodDao, waterDao, stepDao, goalDao, profileDao, settings, backgroundScope, clock
        )
        r.awaitReady()
        return r
    }

    @Test fun addHabitAppearsAndToggleWritesTodayCompletion() = runTest {
        val r = repo()
        r.addHabit("Walk", HabitIcon.HEART, "Daily", null, null)
        val habit = r.observeHabits().value.single()
        assertEquals("Walk", habit.name)
        assertFalse(habit.completed)

        r.toggleHabitCompletion(habit.id)
        assertTrue(r.observeHabits().value.single().completed)
        runCurrent()
        assertEquals(setOf(HabitCompletionEntity(habit.id, today)), habitDao.completions.value)
        assertEquals(today, habitDao.habits.value.getValue(habit.id).createdAtDay)
    }

    @Test fun weeklyIsStoredAsTimesPerWeekWithTargetOne() = runTest {
        val r = repo()
        r.addHabit("Swim", HabitIcon.HEART, "Weekly", null, null)
        runCurrent()
        val stored = habitDao.habits.value.values.single()
        assertEquals("TIMES_PER_WEEK", stored.frequency)
        assertEquals(1, stored.targetPerWeek)
    }

    @Test fun toggleTwiceRemovesCompletion() = runTest {
        val r = repo()
        r.addHabit("Walk", HabitIcon.HEART, "Daily", null, null)
        val id = r.observeHabits().value.single().id
        r.toggleHabitCompletion(id)
        r.toggleHabitCompletion(id)
        assertFalse(r.observeHabits().value.single().completed)
        runCurrent()
        assertTrue(habitDao.completions.value.isEmpty())
    }

    @Test fun midnightRolloverShowsHabitsUnchecked() = runTest {
        val r = repo()
        r.addHabit("Walk", HabitIcon.HEART, "Daily", null, null)
        val id = r.observeHabits().value.single().id
        r.toggleHabitCompletion(id)
        runCurrent()

        clock.advance(Duration.ofDays(1))
        r.refreshAll()
        runCurrent()

        assertFalse(r.observeHabits().value.single().completed)
        assertTrue(HabitCompletionEntity(id, today) in habitDao.completions.value)
    }

    @Test fun waterAddsAndResetsPerDay() = runTest {
        val r = repo()
        r.addWaterIntake(250)
        r.addWaterIntake(500)
        assertEquals(750, r.hydrationState.value.intake)
        runCurrent()

        clock.advance(Duration.ofDays(1))
        r.refreshAll()
        runCurrent()
        assertEquals(0, r.observeHydration().value.intake)

        r.addWaterIntake(100)
        r.resetDailyHydration()
        assertEquals(0, r.hydrationState.value.intake)
        runCurrent()
        assertEquals(750, waterDao.totalForDay(today))
        assertEquals(0, waterDao.totalForDay(today + 1))
        assertEquals(0, r.hydrationSnapshot().intake)
    }

    @Test fun goalChangeWritesDailyGoalRow() = runTest {
        val r = repo()
        r.updateHydrationGoal(2500)
        r.setStepCounterGoal(10000)
        assertEquals(2500, r.hydrationState.value.dailyGoal)
        runCurrent()
        assertEquals(DailyGoalEntity(today, 2500, 10000), goalDao.goals.value[today])
        assertEquals(2500, settings.current().waterGoalMl)
        assertEquals(10000, settings.current().stepGoal)
    }

    @Test fun stepReadingDeltaAndRebootReset() = runTest {
        val r = repo()
        r.applyStepCounterReading(1000f)
        assertEquals(0, r.observeStepCounter().value.stepsToday)
        r.applyStepCounterReading(1100f)
        assertEquals(100, r.observeStepCounter().value.stepsToday)
        r.applyStepCounterReading(50f) // reboot: sensor total restarted
        assertEquals(100, r.observeStepCounter().value.stepsToday)
        r.applyStepCounterReading(80f)
        assertEquals(130, r.observeStepCounter().value.stepsToday)
        r.incrementStepDetector(5)
        assertEquals(135, r.observeStepCounter().value.stepsToday)
        runCurrent()
        assertEquals(135, stepDao.days.value.getValue(today).steps)
        assertEquals(80f, settings.current().lastSensorTotal)
        assertEquals(today, settings.current().stepCounterDay)
    }

    @Test fun clearAllDataKeepsOnboardingAndMigrationFlag() = runTest {
        val r = repo()
        r.setOnboardingComplete(true)
        r.addHabit("Walk", HabitIcon.HEART, "Daily", null, null)
        r.addMoodEntry(MoodType.HAPPY, "yay")
        r.addWaterIntake(300)
        r.updateHydrationGoal(3000)
        runCurrent()

        r.clearAllData()
        assertTrue(r.observeHabits().value.isEmpty())
        assertEquals(0, r.hydrationState.value.intake)
        assertTrue(r.isOnboardingComplete())
        runCurrent()
        assertEquals(AppSettings(onboardingComplete = true, migratedV2 = true), settings.current())
        assertTrue(habitDao.habits.value.isEmpty())
        assertTrue(moodDao.entries.value.isEmpty())
        assertTrue(waterDao.logs.value.isEmpty())
        assertTrue(goalDao.goals.value.isEmpty())
    }

    @Test fun perfectDayStreakInStats() = runTest {
        val r = repo()
        r.addHabit("Walk", HabitIcon.HEART, "Daily", null, null)
        runCurrent()
        val id = r.observeHabits().value.single().id
        // Simulate an older habit with yesterday's completion already stored.
        habitDao.upsert(habitDao.habits.value.getValue(id).copy(createdAtDay = today - 1))
        habitDao.insertCompletion(HabitCompletionEntity(id, today - 1))
        val r2 = repo()

        r2.toggleHabitCompletion(id)
        val stats = r2.observeStats().value
        assertEquals(2, stats.currentStreak)
        assertTrue(stats.longestStreak >= 2)
        assertEquals(1, stats.habitsCompletedToday)
    }

    @Test fun deleteHabitRemovesFromTodayAndStats() = runTest {
        val r = repo()
        r.addHabit("Walk", HabitIcon.HEART, "Daily", null, null)
        val id = r.observeHabits().value.single().id
        r.toggleHabitCompletion(id)
        assertEquals(1, r.observeStats().value.currentStreak)

        r.deleteHabit(id)
        assertTrue(r.observeHabits().value.isEmpty())
        assertEquals(0, r.observeStats().value.totalHabits)
        assertEquals(0, r.observeStats().value.currentStreak)
        runCurrent()
        assertTrue(habitDao.habits.value.isEmpty())
        assertTrue(habitDao.completions.value.isEmpty())
    }

    @Test fun moodAddDeleteNewestFirst() = runTest {
        val r = repo()
        r.addMoodEntry(MoodType.SAD, null)
        clock.advance(Duration.ofMinutes(1))
        r.addMoodEntry(MoodType.HAPPY, "better")
        val entries = r.observeMoodEntries().value
        assertEquals(listOf(MoodType.HAPPY, MoodType.SAD), entries.map { it.mood })
        runCurrent()
        assertEquals(setOf(2, 5), moodDao.entries.value.values.map { it.score }.toSet())

        r.deleteMoodEntry(entries.first().id)
        assertEquals(listOf(MoodType.SAD), r.observeMoodEntries().value.map { it.mood })
        runCurrent()
        assertEquals(listOf(2), moodDao.entries.value.values.map { it.score })
    }
}
