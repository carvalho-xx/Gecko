package io.github.carvalhoxx.gecko.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.carvalhoxx.gecko.data.local.AnnualGoalDao
import io.github.carvalhoxx.gecko.data.local.CustomListDao
import io.github.carvalhoxx.gecko.data.local.GameDao
import io.github.carvalhoxx.gecko.data.local.GeckoDatabase
import io.github.carvalhoxx.gecko.data.local.HltbFallbackCallback
import io.github.carvalhoxx.gecko.data.local.HltbFallbackDao
import io.github.carvalhoxx.gecko.data.local.JournalEntryDao
import io.github.carvalhoxx.gecko.data.local.PlatformDao
import io.github.carvalhoxx.gecko.data.local.PlaythroughDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGeckoDatabase(@ApplicationContext context: Context): GeckoDatabase = Room.databaseBuilder(
        context,
        GeckoDatabase::class.java,
        GeckoDatabase.DATABASE_NAME
    )
        .addMigrations(GeckoDatabase.MIGRATION_1_2)
        .addCallback(HltbFallbackCallback(context))
        .build()

    @Provides
    fun provideGameDao(database: GeckoDatabase): GameDao = database.gameDao()

    @Provides
    fun providePlaythroughDao(database: GeckoDatabase): PlaythroughDao = database.playthroughDao()

    @Provides
    fun provideJournalEntryDao(database: GeckoDatabase): JournalEntryDao = database.journalEntryDao()

    @Provides
    fun providePlatformDao(database: GeckoDatabase): PlatformDao = database.platformDao()

    @Provides
    fun provideCustomListDao(database: GeckoDatabase): CustomListDao = database.customListDao()

    @Provides
    fun provideHltbFallbackDao(database: GeckoDatabase): HltbFallbackDao = database.hltbFallbackDao()

    @Provides
    fun provideAnnualGoalDao(database: GeckoDatabase): AnnualGoalDao = database.annualGoalDao()
}
