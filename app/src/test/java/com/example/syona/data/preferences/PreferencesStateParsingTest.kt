package com.example.syona.data.preferences

import com.example.syona.data.model.Habit
import com.example.syona.data.model.HabitFrequency
import com.example.syona.data.model.HabitIcon
import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodType
import com.example.syona.data.model.StepSensorType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesStateParsingTest {

    private val gson = Gson()

    @Test
    fun legacy_habit_without_frequency_or_icon_gets_defaults_instead_of_null() {
        // Shape of a habit stored before `frequency`/`icon` existed
        val json = """{"habits":[{"id":1,"name":"Drink water","completed":true,"order":0}]}"""

        val state = PreferencesManager.parseState(gson, json)

        val habit = state.habits.single()
        assertEquals("Drink water", habit.name)
        assertEquals(HabitFrequency.DAILY, habit.frequency)
        assertEquals(HabitIcon.HEART, habit.icon)
        assertTrue(habit.completed)
        // Must not throw (this NPE'd before sanitising)
        habit.frequency.shouldShowToday(habit.customDays)
    }

    @Test
    fun unknown_enum_values_are_repaired_or_dropped() {
        val json = """
            {"habits":[{"id":1,"name":"Run","frequency":"FORTNIGHTLY","icon":"ROCKET"}],
             "moodEntries":[{"id":1,"mood":"EXCITED","timestamp":5},{"id":2,"mood":"CALM","timestamp":6}],
             "stepCounter":{"enabled":true,"sensorType":"GYRO"}}
        """.trimIndent()

        val state = PreferencesManager.parseState(gson, json)

        assertEquals(HabitFrequency.DAILY, state.habits.single().frequency)
        assertEquals(HabitIcon.HEART, state.habits.single().icon)
        assertEquals(listOf(MoodType.CALM), state.moodEntries.map { it.mood })
        assertTrue(state.stepCounter.enabled)
        assertEquals(StepSensorType.AUTO, state.stepCounter.sensorType)
    }

    @Test
    fun habits_without_a_name_and_null_list_items_are_dropped() {
        val json = """{"habits":[null,{"id":2},{"id":3,"name":"Read"}],"moodEntries":[null]}"""

        val state = PreferencesManager.parseState(gson, json)

        assertEquals(listOf("Read"), state.habits.map { it.name })
        assertTrue(state.moodEntries.isEmpty())
    }

    @Test
    fun missing_sections_fall_back_to_defaults() {
        val state = PreferencesManager.parseState(gson, "{}")
        assertEquals(PreferencesState(), state.copy(hydrationState = PreferencesState().hydrationState))
        assertEquals(2000, state.hydrationState.dailyGoal)
        assertEquals(8000, state.stepCounter.dailyGoal)
    }

    @Test
    fun json_null_literal_returns_default_state() {
        assertEquals(PreferencesState().habits, PreferencesManager.parseState(gson, "null").habits)
    }

    @Test
    fun round_trip_preserves_current_data() {
        val original = PreferencesState(
            habits = listOf(
                Habit(id = 7, name = "Stretch", icon = HabitIcon.DUMBBELL, frequency = HabitFrequency.CUSTOM,
                    customDays = listOf(2, 4), completed = true, order = 3, notes = "5 min")
            ),
            moodEntries = listOf(MoodEntry(id = 9, mood = MoodType.SAD, note = "tired", timestamp = 123L)),
            onboardingComplete = true,
            longestStreak = 4
        )

        val restored = PreferencesManager.parseState(gson, gson.toJson(original))

        assertEquals(original, restored)
    }
}
