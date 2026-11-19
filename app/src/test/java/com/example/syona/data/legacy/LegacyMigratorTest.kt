package com.example.syona.data.legacy

import com.example.syona.data.db.HabitCompletionEntity
import com.example.syona.data.db.HabitEntity
import com.example.syona.data.db.WaterLogEntity
import com.example.syona.data.model.Habit
import com.example.syona.data.model.HydrationState
import com.example.syona.data.model.ProfileData
import com.example.syona.data.preferences.PreferencesState
import com.example.syona.fakes.InMemorySettingsStore
import com.google.gson.Gson
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMigratorTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 19)

    private class FakeSink(var fail: Boolean = false) : MigrationSink {
        val habits = linkedMapOf<Long, HabitEntity>()
        val completions = linkedMapOf<Pair<Long, Long>, HabitCompletionEntity>()
        val water = linkedMapOf<Long, WaterLogEntity>()
        var lastPhotoPath: String? = "unset"
        var writes = 0

        override suspend fun writeAll(bundle: MigrationBundle, photoPath: String?) {
            if (fail) throw IllegalStateException("boom")
            writes++
            bundle.habits.forEach { habits[it.id] = it }
            bundle.completions.forEach { completions[it.habitId to it.day] = it }
            bundle.waterLogs.forEach { water[it.id] = it }
            lastPhotoPath = photoPath
        }
    }

    private fun json(state: PreferencesState) = Gson().toJson(state)

    private fun state(photo: String? = null) = PreferencesState(
        habits = listOf(
            Habit(id = 1_700_000_000_000L, name = "a", completed = true),
            Habit(id = 1_700_000_100_000L, name = "b")
        ),
        hydrationState = HydrationState(intake = 500, lastResetDate = 1_700_000_000_000L),
        profileData = ProfileData(name = "Sam", photoUri = photo),
        currentStreak = 4,
        longestStreak = 9
    )

    private fun migrator(
        jsonValue: String?,
        sink: MigrationSink,
        settings: InMemorySettingsStore,
        decode: (String) -> ByteArray = { java.util.Base64.getMimeDecoder().decode(it) },
        save: (ByteArray) -> String = { "/files/profile.jpg" }
    ) = LegacyMigrator(
        readLegacyJson = { jsonValue },
        sink = sink,
        settings = settings,
        savePhoto = save,
        decodeBase64 = decode,
        zone = { zone },
        today = { today }
    )

    @Test fun alreadyMigratedDoesNothing() = runTest {
        val settings = InMemorySettingsStore(com.example.syona.data.settings.AppSettings(migratedV2 = true))
        val sink = FakeSink()
        val r = migrator(json(state()), sink, settings).migrateIfNeeded()
        assertEquals(LegacyMigrator.Result.AlreadyDone, r)
        assertEquals(0, sink.writes)
    }

    @Test fun noLegacyDataMarksDone() = runTest {
        val settings = InMemorySettingsStore()
        val sink = FakeSink()
        val r = migrator(null, sink, settings).migrateIfNeeded()
        assertEquals(LegacyMigrator.Result.NothingToMigrate, r)
        assertTrue(settings.current().migratedV2)
        assertEquals(0, sink.writes)
    }

    @Test fun migratesAndSetsFlagAfterWrite() = runTest {
        val settings = InMemorySettingsStore()
        val sink = FakeSink()
        val r = migrator(json(state()), sink, settings).migrateIfNeeded()
        assertEquals(LegacyMigrator.Result.Migrated, r)
        assertEquals(1, sink.writes)
        assertEquals(2, sink.habits.size)
        assertTrue(settings.current().migratedV2)
        assertEquals(4, settings.current().legacyPerfectCurrent)
        assertEquals(9, settings.current().legacyPerfectBest)
    }

    @Test fun sinkFailureLeavesFlagUnset() = runTest {
        val settings = InMemorySettingsStore()
        val r = migrator(json(state()), FakeSink(fail = true), settings).migrateIfNeeded()
        assertTrue(r is LegacyMigrator.Result.Failed)
        assertTrue(!settings.current().migratedV2)
    }

    @Test fun rerunAfterCrashIsIdempotent() = runTest {
        val settings = InMemorySettingsStore()
        val sink = FakeSink()
        val m = migrator(json(state()), sink, settings)
        m.migrateIfNeeded()
        settings.update { it.copy(migratedV2 = false) } // simulate crash before flag persisted
        assertEquals(LegacyMigrator.Result.Migrated, m.migrateIfNeeded())
        assertEquals(2, sink.writes)
        assertEquals(1, sink.water.size)
        assertEquals(2, sink.habits.size)
        assertEquals(1, sink.completions.size)
    }

    @Test fun corruptPhotoDoesNotBlockMigration() = runTest {
        val settings = InMemorySettingsStore()
        val sink = FakeSink()
        val r = migrator(
            json(state(photo = "%%%")), sink, settings,
            decode = { throw IllegalArgumentException("bad base64") }
        ).migrateIfNeeded()
        assertEquals(LegacyMigrator.Result.Migrated, r)
        assertNull(sink.lastPhotoPath)
        assertTrue(settings.current().migratedV2)
    }

    @Test fun validPhotoIsSavedAndPathPassed() = runTest {
        val sink = FakeSink()
        migrator(json(state(photo = "AAEC")), sink, InMemorySettingsStore()).migrateIfNeeded()
        assertEquals("/files/profile.jpg", sink.lastPhotoPath)
    }
}
