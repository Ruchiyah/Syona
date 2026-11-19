package com.example.syona.data.legacy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syona.data.db.DailyGoalEntity
import com.example.syona.data.db.HabitCompletionEntity
import com.example.syona.data.db.HabitEntity
import com.example.syona.data.db.MoodEntryEntity
import com.example.syona.data.db.ProfileEntity
import com.example.syona.data.db.StepDayEntity
import com.example.syona.data.db.SyonaDatabase
import com.example.syona.data.db.WaterLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** R8: the legacy import is add-only, so a retry never overwrites newer user data. */
@RunWith(AndroidJUnit4::class)
class RoomMigrationSinkTest {
    private lateinit var db: SyonaDatabase
    private lateinit var sink: RoomMigrationSink

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SyonaDatabase::class.java).build()
        sink = RoomMigrationSink(
            db, db.habitDao(), db.moodDao(), db.waterDao(), db.stepDao(), db.goalDao(), db.profileDao()
        )
    }

    @After
    fun tearDown() = db.close()

    private fun habit(id: Long, name: String) = HabitEntity(
        id = id, name = name, icon = "WATER", frequency = "DAILY",
        customDays = emptyList(), sortOrder = 0, createdAtDay = 1
    )

    private val legacyDay = 20_000L

    private fun bundle() = MigrationBundle(
        habits = listOf(habit(1, "legacy-a"), habit(2, "legacy-b")),
        completions = listOf(HabitCompletionEntity(2, legacyDay)),
        moods = listOf(MoodEntryEntity(id = 10, score = 4, note = null, timestamp = 100)),
        waterLogs = listOf(WaterLogEntity(id = -1, amountMl = 500, timestamp = 100, day = legacyDay)),
        stepDays = listOf(StepDayEntity(day = legacyDay, steps = 3000)),
        goals = listOf(DailyGoalEntity(day = legacyDay, waterGoalMl = 2000, stepGoal = 8000)),
        profile = ProfileEntity(name = "Legacy", age = 30, gender = "", wellnessGoal = "", photoPath = null),
        photoBase64 = null,
        settings = { it }
    )

    @Test
    fun retryNeverOverwritesNewerDataAndAddsLegacyRowsOnce() = runBlocking {
        val userProfile = ProfileEntity(name = "Newer", age = 31, gender = "F", wellnessGoal = "x", photoPath = null)
        db.profileDao().upsert(userProfile)
        db.habitDao().upsert(habit(1, "edited by user"))
        db.stepDao().upsert(StepDayEntity(day = legacyDay, steps = 9000))

        sink.writeAll(bundle(), "/files/profile_legacy.jpg")
        sink.writeAll(bundle(), "/files/profile_legacy.jpg")

        assertEquals(userProfile, db.profileDao().get())
        val habits = db.habitDao().getAll().associateBy { it.id }
        assertEquals("edited by user", habits.getValue(1).name)
        assertEquals(9000, db.stepDao().get(legacyDay)?.steps)

        // Legacy-only rows were added exactly once.
        assertEquals("legacy-b", habits.getValue(2).name)
        assertEquals(2, habits.size)
        assertEquals(listOf(10L), db.moodDao().observeAll().first().map { it.id })
        assertEquals(500, db.waterDao().totalForDay(legacyDay))
        assertEquals(listOf(HabitCompletionEntity(2, legacyDay)), db.habitDao().observeAllCompletions().first())
    }

    @Test
    fun lowerExistingStepsAreRaisedToLegacyValue() = runBlocking {
        db.stepDao().upsert(StepDayEntity(day = legacyDay, steps = 100))
        sink.writeAll(bundle(), null)
        assertEquals(3000, db.stepDao().get(legacyDay)?.steps)
    }
}
