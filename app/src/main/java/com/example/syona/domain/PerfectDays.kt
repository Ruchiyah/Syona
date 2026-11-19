package com.example.syona.domain

import com.example.syona.data.db.HabitCompletionEntity
import com.example.syona.data.db.HabitEntity

data class StreakResult(val current: Int, val best: Int)

object PerfectDays {
    /** Days in [from]..[to] (inclusive) with at least one required habit, all completed. */
    fun perfectDays(
        habits: List<HabitEntity>,
        completions: List<HabitCompletionEntity>,
        from: Long,
        to: Long
    ): Set<Long> {
        val doneByDay = completions.groupBy({ it.day }, { it.habitId }).mapValues { it.value.toSet() }
        val result = mutableSetOf<Long>()
        for (day in from..to) {
            val required = habits.filter { it.isRequiredOn(day) }
            if (required.isEmpty()) continue
            val done = doneByDay[day] ?: continue
            if (required.all { it.id in done }) result += day
        }
        return result
    }

    fun streak(
        perfect: Set<Long>,
        today: Long,
        legacyCurrent: Int,
        legacyLastDay: Long,
        legacyBest: Int
    ): StreakResult {
        // Effective length of the run [s, e]: the legacy streak is folded in when its last day
        // is the day before the run or inside it (R5).
        fun effective(s: Long, e: Long): Int {
            val plain = (e - s + 1).toInt()
            return if (legacyLastDay >= s - 1 && legacyLastDay <= e) {
                maxOf(plain, legacyCurrent + (e - legacyLastDay).toInt())
            } else plain
        }

        val end = if (today in perfect) today else today - 1
        var run = 0
        while ((end - run) in perfect) run++
        val current = when {
            run > 0 -> effective(end - run + 1, end)
            legacyLastDay == today - 1 || legacyLastDay == today -> legacyCurrent
            else -> 0
        }

        var longest = 0
        for (day in perfect) {
            if (day - 1 in perfect) continue // not the start of a run
            var len = 0
            while (day + len in perfect) len++
            longest = maxOf(longest, effective(day, day + len - 1))
        }
        return StreakResult(current, maxOf(legacyBest, longest, current))
    }
}
