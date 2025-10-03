package com.example.syona.data.model

data class WellnessStats(
    val habitsCompletedToday: Int = 0,
    val totalHabits: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val averageMoodScore: Double = 0.0,
    val hydrationPercentage: Int = 0,
    val stepsToday: Int = 0,
    val stepsGoal: Int = 8000
)
