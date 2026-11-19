package com.example.syona.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit ORDER BY sortOrder, id")
    suspend fun getAll(): List<HabitEntity>

    @Upsert
    suspend fun upsert(vararg h: HabitEntity)

    @Upsert
    suspend fun upsertAll(list: List<HabitEntity>)

    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT habitId FROM habit_completion WHERE day = :day")
    fun observeCompletionsForDay(day: Long): Flow<List<Long>>

    @Query("SELECT * FROM habit_completion")
    fun observeAllCompletions(): Flow<List<HabitCompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(c: HabitCompletionEntity)

    @Query("DELETE FROM habit_completion WHERE habitId = :habitId AND day = :day")
    suspend fun deleteCompletion(habitId: Long, day: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletions(list: List<HabitCompletionEntity>)

    @Query("DELETE FROM habit")
    suspend fun clear()
}
