package com.example.syona.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        HabitEntity::class,
        HabitCompletionEntity::class,
        MoodEntryEntity::class,
        WaterLogEntity::class,
        StepDayEntity::class,
        DailyGoalEntity::class,
        ProfileEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SyonaDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun moodDao(): MoodDao
    abstract fun waterDao(): WaterDao
    abstract fun stepDao(): StepDao
    abstract fun goalDao(): GoalDao
    abstract fun profileDao(): ProfileDao
}
