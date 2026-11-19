package com.example.syona.data.settings

import kotlinx.coroutines.flow.Flow

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
