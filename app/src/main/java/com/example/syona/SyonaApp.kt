package com.example.syona

import android.app.Application
import android.util.Log
import com.example.syona.data.legacy.LegacyMigrator
import com.example.syona.data.repository.WellnessRepository
import com.example.syona.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SyonaApp : Application() {

    // Injected during super.onCreate(); available to callers from then on.
    @Inject
    lateinit var repository: WellnessRepository

    @Inject
    lateinit var migrator: LegacyMigrator

    private val startup = CompletableDeferred<Unit>()

    /**
     * Completes once migration has run and the repository has loaded. Any non-UI entry point that
     * may run before Application.onCreate completes (e.g. a WorkManager worker started by
     * androidx.startup) must call this before touching [repository].
     */
    suspend fun awaitStartup() = startup.await()

    override fun onCreate() {
        super.onCreate()
        // Onboarding/Main read state synchronously, so load it before any UI exists.
        // Migration must run BEFORE awaitReady(): the repository loads exactly once.
        // Phase 8 replaces this with a SplashScreen keep-on-screen condition.
        runBlocking {
            val result = migrator.migrateIfNeeded()
            if (result is LegacyMigrator.Result.Failed) {
                // Legacy blob is untouched and the flag stays false, so the next launch retries.
                Log.w("SyonaApp", "Legacy migration failed", result.error)
            }
            repository.awaitReady()
            startup.complete(Unit)
        }
        NotificationChannels.createAll(this)
    }
}
