package com.example.syona.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val icon: String,
    val frequency: String,
    val customDays: List<Int>,
    val targetPerWeek: Int = 0,
    val reminderMinuteOfDay: Int? = null,
    val colorArgb: Int? = null,
    val notes: String? = null,
    val sortOrder: Int,
    val archived: Boolean = false,
    val createdAtDay: Long
)

@Entity(
    tableName = "habit_completion",
    primaryKeys = ["habitId", "day"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("day")]
)
data class HabitCompletionEntity(val habitId: Long, val day: Long)

@Entity(tableName = "mood_entry")
data class MoodEntryEntity(
    @PrimaryKey val id: Long,
    val score: Int,
    val note: String?,
    val timestamp: Long,
    val tags: List<String> = emptyList()
)

@Entity(tableName = "water_log", indices = [Index("day")])
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMl: Int,
    val timestamp: Long,
    val day: Long
)

@Entity(tableName = "step_day")
data class StepDayEntity(@PrimaryKey val day: Long, val steps: Int)

@Entity(tableName = "daily_goal")
data class DailyGoalEntity(@PrimaryKey val day: Long, val waterGoalMl: Int, val stepGoal: Int)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val age: Int?,
    val gender: String,
    val wellnessGoal: String,
    val photoPath: String?
)
