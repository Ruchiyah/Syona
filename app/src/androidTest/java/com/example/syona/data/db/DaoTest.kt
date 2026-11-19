package com.example.syona.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: SyonaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SyonaDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun habit(id: Long, customDays: List<Int> = emptyList()) = HabitEntity(
        id = id, name = "h$id", icon = "WATER", frequency = "DAILY",
        customDays = customDays, sortOrder = 0, createdAtDay = 1
    )

    @Test
    fun deletingHabitCascadesCompletions() = runBlocking {
        val dao = db.habitDao()
        dao.upsert(habit(1))
        dao.insertCompletions(listOf(HabitCompletionEntity(1, 10), HabitCompletionEntity(1, 11)))
        dao.delete(1)
        assertTrue(dao.observeAllCompletions().first().isEmpty())
    }

    @Test
    fun waterTotalSumsOnlyThatDay() = runBlocking {
        val dao = db.waterDao()
        dao.insert(WaterLogEntity(amountMl = 250, timestamp = 1, day = 5))
        dao.insert(WaterLogEntity(amountMl = 500, timestamp = 2, day = 5))
        dao.insert(WaterLogEntity(amountMl = 750, timestamp = 3, day = 6))
        assertEquals(750, dao.totalForDay(5))
        assertEquals(0, dao.totalForDay(7))
    }

    @Test
    fun completionInsertIsIdempotent() = runBlocking {
        val dao = db.habitDao()
        dao.upsert(habit(1))
        dao.insertCompletion(HabitCompletionEntity(1, 10))
        dao.insertCompletion(HabitCompletionEntity(1, 10))
        assertEquals(1, dao.observeAllCompletions().first().size)
    }

    @Test
    fun listConvertersRoundTrip() = runBlocking {
        val h = habit(1, customDays = listOf(1, 7))
        val m = MoodEntryEntity(1, 3, null, 100, tags = listOf("work", "sleep, late"))
        db.habitDao().upsert(h)
        db.moodDao().insert(m)
        assertEquals(listOf(h), db.habitDao().getAll())
        assertEquals(listOf(m), db.moodDao().observeAll().first())
    }
}
