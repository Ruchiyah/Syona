package com.example.syona

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        runBlocking { repository.awaitReady() }
        NotificationChannels.createAll(this)
    }
}
