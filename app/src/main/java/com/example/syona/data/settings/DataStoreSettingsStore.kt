package com.example.syona.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.syona.data.model.MoodViewMode
import com.example.syona.data.model.StepSensorType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {

    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val migratedV2 = booleanPreferencesKey("migrated_v2")
        val waterGoalMl = intPreferencesKey("water_goal_ml")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val reminderIntervalMinutes = intPreferencesKey("reminder_interval_minutes")
        val stepEnabled = booleanPreferencesKey("step_enabled")
        val stepGoal = intPreferencesKey("step_goal")
        val stepSensorType = stringPreferencesKey("step_sensor_type")
        val lastSensorTotal = floatPreferencesKey("last_sensor_total")
        val stepCounterDay = longPreferencesKey("step_counter_day")
        val moodViewMode = stringPreferencesKey("mood_view_mode")
        val moodInsightsDismissed = booleanPreferencesKey("mood_insights_dismissed")
        val legacyPerfectBest = intPreferencesKey("legacy_perfect_best")
        val legacyPerfectCurrent = intPreferencesKey("legacy_perfect_current")
        val legacyPerfectLastDay = longPreferencesKey("legacy_perfect_last_day")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingComplete = this[Keys.onboardingComplete] ?: d.onboardingComplete,
            migratedV2 = this[Keys.migratedV2] ?: d.migratedV2,
            waterGoalMl = this[Keys.waterGoalMl] ?: d.waterGoalMl,
            remindersEnabled = this[Keys.remindersEnabled] ?: d.remindersEnabled,
            reminderIntervalMinutes = this[Keys.reminderIntervalMinutes] ?: d.reminderIntervalMinutes,
            stepEnabled = this[Keys.stepEnabled] ?: d.stepEnabled,
            stepGoal = this[Keys.stepGoal] ?: d.stepGoal,
            stepSensorType = this[Keys.stepSensorType].toEnumOr(d.stepSensorType),
            lastSensorTotal = this[Keys.lastSensorTotal] ?: d.lastSensorTotal,
            stepCounterDay = this[Keys.stepCounterDay] ?: d.stepCounterDay,
            moodViewMode = this[Keys.moodViewMode].toEnumOr(d.moodViewMode),
            moodInsightsDismissed = this[Keys.moodInsightsDismissed] ?: d.moodInsightsDismissed,
            legacyPerfectBest = this[Keys.legacyPerfectBest] ?: d.legacyPerfectBest,
            legacyPerfectCurrent = this[Keys.legacyPerfectCurrent] ?: d.legacyPerfectCurrent,
            legacyPerfectLastDay = this[Keys.legacyPerfectLastDay] ?: d.legacyPerfectLastDay
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: AppSettings) {
        this[Keys.onboardingComplete] = s.onboardingComplete
        this[Keys.migratedV2] = s.migratedV2
        this[Keys.waterGoalMl] = s.waterGoalMl
        this[Keys.remindersEnabled] = s.remindersEnabled
        this[Keys.reminderIntervalMinutes] = s.reminderIntervalMinutes
        this[Keys.stepEnabled] = s.stepEnabled
        this[Keys.stepGoal] = s.stepGoal
        this[Keys.stepSensorType] = s.stepSensorType.name
        this[Keys.lastSensorTotal] = s.lastSensorTotal
        this[Keys.stepCounterDay] = s.stepCounterDay
        this[Keys.moodViewMode] = s.moodViewMode.name
        this[Keys.moodInsightsDismissed] = s.moodInsightsDismissed
        this[Keys.legacyPerfectBest] = s.legacyPerfectBest
        this[Keys.legacyPerfectCurrent] = s.legacyPerfectCurrent
        this[Keys.legacyPerfectLastDay] = s.legacyPerfectLastDay
    }
}
