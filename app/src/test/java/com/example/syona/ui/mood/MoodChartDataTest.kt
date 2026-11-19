package com.example.syona.ui.mood

import com.example.syona.data.model.MoodEntry
import com.example.syona.data.model.MoodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MoodChartDataTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private fun calendar() = Calendar.getInstance(utc, Locale.UK)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        calendar().apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis

    private fun entry(ts: Long, mood: MoodType) = MoodEntry(id = ts, mood = mood, timestamp = ts)

    @Test
    fun empty_input_gives_no_points() {
        listOf("Days", "Weeks", "Months").forEach {
            assertTrue(MoodChartData.aggregate(emptyList(), it, now = at(2026, 11, 19)).isEmpty())
        }
    }

    @Test
    fun days_keeps_last_seven_days_in_chronological_order() {
        val now = at(2026, 11, 19)
        val entries = listOf(
            entry(at(2026, 11, 18), MoodType.HAPPY),   // newest first, like the repository
            entry(at(2026, 11, 14), MoodType.SAD),
            entry(at(2026, 11, 1), MoodType.ANGRY)     // older than 7 days
        )

        val points = MoodChartData.aggregate(entries, "Days", now, calendar())

        assertEquals(listOf(at(2026, 11, 14) to 2f, at(2026, 11, 18) to 5f), points)
    }

    @Test
    fun weeks_averages_entries_within_the_same_week() {
        val now = at(2026, 11, 19)
        val entries = listOf(
            entry(at(2026, 11, 17), MoodType.HAPPY),   // Tue
            entry(at(2026, 11, 16), MoodType.NEUTRAL), // Mon, same ISO week
            entry(at(2026, 11, 10), MoodType.SAD)      // previous week
        )

        val points = MoodChartData.aggregate(entries, "Weeks", now, calendar())

        assertEquals(listOf(at(2026, 11, 10) to 2f, at(2026, 11, 16) to 4f), points)
    }

    @Test
    fun week_crossing_new_year_is_one_bucket() {
        val now = at(2026, 1, 20)
        // ISO week 1 of 2026 runs Mon 29 Dec 2025 – Sun 4 Jan 2026 (UK locale = ISO weeks).
        // Grouping by calendar YEAR + week would wrongly split it; week-based year keeps it whole.
        val entries = listOf(
            entry(at(2026, 1, 1), MoodType.HAPPY),
            entry(at(2025, 12, 30), MoodType.NEUTRAL),
            entry(at(2025, 12, 17), MoodType.ANGRY)    // a different week
        )

        val points = MoodChartData.aggregate(entries, "Weeks", now, calendar())

        assertEquals(listOf(at(2025, 12, 17) to 1f, at(2025, 12, 30) to 4f), points)
    }

    @Test
    fun months_does_not_merge_same_month_from_different_years() {
        val now = at(2026, 11, 19)
        // Both within the ~360 day window; previously both grouped under MONTH = NOVEMBER
        val entries = listOf(
            entry(at(2026, 11, 2), MoodType.HAPPY),
            entry(at(2025, 11, 25), MoodType.ANGRY)
        )

        val points = MoodChartData.aggregate(entries, "Months", now, calendar())

        assertEquals(listOf(at(2025, 11, 25) to 1f, at(2026, 11, 2) to 5f), points)
    }

    @Test
    fun months_averages_within_a_month() {
        val now = at(2026, 11, 19)
        val entries = listOf(
            entry(at(2026, 10, 30), MoodType.HAPPY),
            entry(at(2026, 10, 3), MoodType.CALM),
            entry(at(2026, 10, 1), MoodType.NEUTRAL)
        )

        val points = MoodChartData.aggregate(entries, "Months", now, calendar())

        assertEquals(listOf(at(2026, 10, 1) to 4f), points)
    }

    @Test
    fun unknown_range_gives_no_points() {
        val e = listOf(entry(at(2026, 11, 18), MoodType.HAPPY))
        assertTrue(MoodChartData.aggregate(e, "Years", at(2026, 11, 19)).isEmpty())
    }
}
