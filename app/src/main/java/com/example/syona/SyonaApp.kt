package com.example.syona

import android.app.Application
import android.util.Log
import com.example.syona.data.legacy.LegacyMigrator
import com.example.syona.data.repository.WellnessRepository
import com.example.syona.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SyonaApp : Application() {

    // Injected during super.onCreate(); available to callers from then on.
    @Inject
    lateinit var repository: WellnessRepository

    @Inject
    lateinit var migrator: LegacyMigrator

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
        }
        NotificationChannels.createAll(this)
    }
}
