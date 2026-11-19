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
            // Add-only (R8): a retry must never overwrite rows the user created or edited since.
            // Habits first: completions have an FK to habit.
            habitDao.insertAllIfAbsent(bundle.habits)
            habitDao.insertCompletions(bundle.completions)
            moodDao.insertAllIfAbsent(bundle.moods)
            waterDao.insertAllIfAbsent(bundle.waterLogs)
            bundle.stepDays.forEach { legacy ->
                val existing = stepDao.get(legacy.day)?.steps ?: 0
                stepDao.upsert(legacy.copy(steps = maxOf(existing, legacy.steps)))
            }
            goalDao.insertAllIfAbsent(bundle.goals)
            bundle.profile?.let { profileDao.insertIfAbsent(it.copy(photoPath = photoPath)) }
        }
    }
}
