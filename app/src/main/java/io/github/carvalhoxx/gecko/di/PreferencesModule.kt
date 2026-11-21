package io.github.carvalhoxx.gecko.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.carvalhoxx.gecko.data.local.UserPreferencesDataStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = {
                context.preferencesDataStoreFile(UserPreferencesDataStore.DATA_STORE_FILE_NAME)
            }
        )

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(dataStore: DataStore<Preferences>): UserPreferencesDataStore =
        UserPreferencesDataStore(dataStore)
}
