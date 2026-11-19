package com.example.syona.data.legacy

import com.example.syona.data.preferences.PreferencesManager
import com.example.syona.data.settings.SettingsStore
import com.google.gson.Gson
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

class LegacyMigrator(
    private val readLegacyJson: () -> String?,
    private val sink: MigrationSink,
    private val settings: SettingsStore,
    private val savePhoto: (ByteArray) -> String,
    private val decodeBase64: (String) -> ByteArray,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val today: () -> LocalDate = { LocalDate.now() }
) {
    sealed interface Result {
        data object AlreadyDone : Result
        data object NothingToMigrate : Result
        data object Migrated : Result
        data class Failed(val error: Throwable) : Result
    }

    suspend fun migrateIfNeeded(): Result {
        if (settings.current().migratedV2) return Result.AlreadyDone
        return try {
            val json = readLegacyJson()
            if (json == null) {
                settings.update { it.copy(migratedV2 = true) }
                return Result.NothingToMigrate
            }
            val state = PreferencesManager.parseState(Gson(), json)
            val bundle = LegacyMapper.map(state, zone(), today())
            val photoPath = bundle.photoBase64?.takeIf { it.isNotEmpty() }?.let { b64 ->
                runCatching { savePhoto(decodeBase64(b64)) }.getOrNull()
            }
            sink.writeAll(bundle, photoPath)
            settings.update { current ->
                val legacy = bundle.settings(current)
                if (current.onboardingComplete) {
                    // Retry after a failed run: the user already set the app up again, so their
                    // newer settings win; only carry over the legacy perfect-day streak (R8).
                    current.copy(
                        legacyPerfectBest = legacy.legacyPerfectBest,
                        legacyPerfectCurrent = legacy.legacyPerfectCurrent,
                        legacyPerfectLastDay = legacy.legacyPerfectLastDay,
                        migratedV2 = true
                    )
                } else {
                    legacy.copy(migratedV2 = true)
                }
            }
            Result.Migrated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.Failed(e)
        }
    }
}
