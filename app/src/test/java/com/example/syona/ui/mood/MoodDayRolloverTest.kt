package com.example.syona.ui.mood

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodDayRolloverTest {
    private val yesterday = LocalDate.of(2026, 11, 18)
    private val today = LocalDate.of(2026, 11, 19)

    @Test fun followsTodayWhenSelectionWasTheOldToday() {
        assertEquals(today, rolledSelectedDate(selected = yesterday, lastToday = yesterday, today = today))
    }

    @Test fun keepsAnExplicitlyPickedOtherDay() {
        val picked = LocalDate.of(2026, 11, 1)
        assertEquals(picked, rolledSelectedDate(selected = picked, lastToday = yesterday, today = today))
    }
}
