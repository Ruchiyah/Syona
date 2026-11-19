package com.example.syona.ui.mood

import com.example.syona.data.model.MoodEntry
import java.util.Calendar

/**
 * Aggregates mood entries into chart points (timestamp to mood score) for the
 * "Days" / "Weeks" / "Months" tabs. Shared by the Mood chart dialog and the Profile chart.
 */
object MoodChartData {

    const val RANGE_DAYS = "Days"
    const val RANGE_WEEKS = "Weeks"
    const val RANGE_MONTHS = "Months"

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun aggregate(
        entries: List<MoodEntry>,
        range: String,
        now: Long = System.currentTimeMillis(),
        calendar: Calendar = Calendar.getInstance()
    ): List<Pair<Long, Float>> {
        if (entries.isEmpty()) return emptyList()
        return when (range) {
            // Last 7 days, one point per entry
            RANGE_DAYS -> entries
                .filter { it.timestamp >= now - 7 * DAY_MS }
                .sortedBy { it.timestamp }
                .map { it.timestamp to it.mood.score.toFloat() }

            // Last 12 weeks, averaged per week. Key includes the week-based year so a
            // range spanning New Year never merges two different weeks.
            RANGE_WEEKS -> averageBy(entries.filter { it.timestamp >= now - 12 * 7 * DAY_MS }) {
                calendar.timeInMillis = it
                calendar.weekYear * 100 + calendar.get(Calendar.WEEK_OF_YEAR)
            }

            // Last ~12 months, averaged per calendar month. Key includes the year so e.g.
            // Nov 2025 and Nov 2026 (both inside a 360-day window) stay separate.
            RANGE_MONTHS -> averageBy(entries.filter { it.timestamp >= now - 12 * 30 * DAY_MS }) {
                calendar.timeInMillis = it
                calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
            }

            else -> emptyList()
        }
    }

    private inline fun averageBy(
        entries: List<MoodEntry>,
        key: (Long) -> Int
    ): List<Pair<Long, Float>> = entries
        .groupBy { key(it.timestamp) }
        .map { (_, group) ->
            // X position/label uses the earliest entry in the bucket
            group.minOf { it.timestamp } to group.map { it.mood.score }.average().toFloat()
        }
        .sortedBy { it.first }
}
