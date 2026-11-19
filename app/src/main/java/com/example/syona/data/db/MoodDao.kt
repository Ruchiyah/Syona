package com.example.syona.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_entry ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MoodEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<MoodEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MoodEntryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(list: List<MoodEntryEntity>)

    @Query("DELETE FROM mood_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM mood_entry")
    suspend fun clear()
}
