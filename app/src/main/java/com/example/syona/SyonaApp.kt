package com.example.syona

import android.app.Application
import com.example.syona.data.preferences.PreferencesManager
import com.example.syona.data.repository.WellnessRepository
import com.example.syona.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SyonaApp : Application() {

    lateinit var repository: WellnessRepository
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            repository = WellnessRepository(PreferencesManager(this))
            NotificationChannels.createAll(this)
        } catch (e: Exception) {
            // Still initialize repository with a fallback to prevent crashes
            if (!::repository.isInitialized) {
                repository = WellnessRepository(PreferencesManager(this))
            }
        }
    }
}
