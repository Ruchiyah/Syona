package com.example.syona.data.model

enum class StepSensorType {
    AUTO,
    COUNTER,
    DETECTOR
}

data class StepCounterPreferences(
    val enabled: Boolean = false,
    val dailyGoal: Int = 8000,
    val sensorType: StepSensorType = StepSensorType.AUTO,
    val stepsToday: Int = 0,
    val lastSensorTotal: Float = 0f,
    val lastResetDate: Long = 0L
)
