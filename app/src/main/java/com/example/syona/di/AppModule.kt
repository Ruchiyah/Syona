package com.example.syona.di

import android.content.Context
import androidx.room.Room
import com.example.syona.data.db.SyonaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
}
