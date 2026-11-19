package com.example.syona.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE day = :day")
    fun observeTotalForDay(day: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE day = :day")
    suspend fun totalForDay(day: Long): Int

    @Insert
    suspend fun insert(log: WaterLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<WaterLogEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(list: List<WaterLogEntity>)

    @Query("DELETE FROM water_log WHERE day = :day")
    suspend fun deleteDay(day: Long)

    /** Atomically replaces a day's logs with a single entry (none when [log] is null). */
    @Transaction
    suspend fun replaceDay(day: Long, log: WaterLogEntity?) {
        deleteDay(day)
        if (log != null) insert(log)
    }

    @Query("DELETE FROM water_log")
    suspend fun clear()
}
