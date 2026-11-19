package com.example.syona.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import android.util.Base64
import androidx.room.Room
import com.example.syona.data.legacy.LegacyMigrator
import com.example.syona.data.legacy.MigrationSink
import com.example.syona.data.legacy.RoomMigrationSink
import com.example.syona.data.settings.DataStoreSettingsStore
import com.example.syona.data.settings.SettingsStore
import com.example.syona.data.db.SyonaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-lifetime scope for background persistence work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SyonaDatabase =
        Room.databaseBuilder(context, SyonaDatabase::class.java, "syona.db").build()

    @Provides fun provideHabitDao(db: SyonaDatabase) = db.habitDao()
    @Provides fun provideMoodDao(db: SyonaDatabase) = db.moodDao()
    @Provides fun provideWaterDao(db: SyonaDatabase) = db.waterDao()
    @Provides fun provideStepDao(db: SyonaDatabase) = db.stepDao()
    @Provides fun provideGoalDao(db: SyonaDatabase) = db.goalDao()
    @Provides fun provideProfileDao(db: SyonaDatabase) = db.profileDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun provideSettingsStore(dataStore: DataStore<Preferences>): SettingsStore =
        DataStoreSettingsStore(dataStore)

    @Provides
    @Singleton
    fun provideMigrationSink(sink: RoomMigrationSink): MigrationSink = sink

    @Provides
    @Singleton
    fun provideLegacyMigrator(
        @ApplicationContext context: Context,
        sink: MigrationSink,
        settings: SettingsStore
    ): LegacyMigrator = LegacyMigrator(
        readLegacyJson = {
            context.getSharedPreferences("syona_prefs", Context.MODE_PRIVATE)
                .getString("wellness_state", null)
        },
        sink = sink,
        settings = settings,
        savePhoto = { bytes ->
            // Separate name: a retry must not overwrite a photo the user picked since (R8).
            val f = File(context.filesDir, "profile_legacy.jpg")
            f.writeBytes(bytes)
            f.absolutePath
        },
        decodeBase64 = { Base64.decode(it, Base64.DEFAULT) }
    )

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemZoneClock
}

/** Reads the device zone on every call so a timezone change is picked up without a restart. */
private object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
