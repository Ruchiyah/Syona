package com.example.syona.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(g: DailyGoalEntity)

    @Query("DELETE FROM daily_goal")
    suspend fun clear()
}
