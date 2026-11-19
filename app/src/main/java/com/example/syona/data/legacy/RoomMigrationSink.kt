package com.example.syona.data.legacy

import androidx.room.withTransaction
import com.example.syona.data.db.GoalDao
import com.example.syona.data.db.HabitDao
import com.example.syona.data.db.MoodDao
import com.example.syona.data.db.ProfileDao
import com.example.syona.data.db.StepDao
import com.example.syona.data.db.SyonaDatabase
import com.example.syona.data.db.WaterDao
import javax.inject.Inject

class RoomMigrationSink @Inject constructor(
    private val db: SyonaDatabase,
    private val habitDao: HabitDao,
    private val moodDao: MoodDao,
    private val waterDao: WaterDao,
    private val stepDao: StepDao,
    private val goalDao: GoalDao,
    private val profileDao: ProfileDao
) : MigrationSink {
    override suspend fun writeAll(bundle: MigrationBundle, photoPath: String?) {
        db.withTransaction {
            // Habits first: completions have an FK to habit. @Upsert keeps existing completions (R4).
            habitDao.upsertAll(bundle.habits)
            habitDao.insertCompletions(bundle.completions)
            moodDao.upsertAll(bundle.moods)
            waterDao.upsertAll(bundle.waterLogs)
            bundle.stepDays.forEach { stepDao.upsert(it) }
            bundle.goals.forEach { goalDao.upsert(it) }
            bundle.profile?.let { profileDao.upsert(it.copy(photoPath = photoPath)) }
        }
    }
}
