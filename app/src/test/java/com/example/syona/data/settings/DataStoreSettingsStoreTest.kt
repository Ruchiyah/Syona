package com.example.syona.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.model.StepSensorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun newStore(file: java.io.File): Pair<DataStore<Preferences>, CoroutineScope> {
        val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        return PreferenceDataStoreFactory.create(scope = scope) { file } to scope
    }

    @Test fun defaultsWhenEmpty() = runTest {
        val (ds, scope) = newStore(tmp.newFile("t.preferences_pb").also { it.delete() })
        assertEquals(AppSettings(), DataStoreSettingsStore(ds).current())
        scope.cancel()
    }

    @Test fun updatePersistsAllFields() = runTest {
        val file = tmp.newFile("t.preferences_pb").also { it.delete() }
        val full = AppSettings(
            onboardingComplete = true, migratedV2 = true, waterGoalMl = 3000,
            remindersEnabled = true, reminderIntervalMinutes = 90, stepEnabled = true,
            stepGoal = 10000, stepSensorType = StepSensorType.COUNTER, lastSensorTotal = 123.5f,
            stepCounterDay = 20000L, moodViewMode = MoodViewMode.CALENDAR,
            moodInsightsDismissed = true, legacyPerfectBest = 7, legacyPerfectCurrent = 3,
            legacyPerfectLastDay = 19999L
        )
        val (ds1, scope1) = newStore(file)
        DataStoreSettingsStore(ds1).update { full }
        scope1.cancel()
        val (ds2, scope2) = newStore(file)
        assertEquals(full, DataStoreSettingsStore(ds2).current())
        scope2.cancel()
    }

    @Test fun badEnumFallsBack() = runTest {
        val (ds, scope) = newStore(tmp.newFile("t.preferences_pb").also { it.delete() })
        ds.edit { it[stringPreferencesKey("step_sensor_type")] = "GYRO" }
        assertEquals(StepSensorType.AUTO, DataStoreSettingsStore(ds).current().stepSensorType)
        scope.cancel()
    }
}
