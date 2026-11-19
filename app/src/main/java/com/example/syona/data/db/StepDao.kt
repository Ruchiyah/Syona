package com.example.syona.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Query("SELECT * FROM step_day WHERE day = :day")
    fun observeDay(day: Long): Flow<StepDayEntity?>

    @Query("SELECT * FROM step_day WHERE day = :day")
    suspend fun get(day: Long): StepDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: StepDayEntity)

    @Query("DELETE FROM step_day")
    suspend fun clear()
}
