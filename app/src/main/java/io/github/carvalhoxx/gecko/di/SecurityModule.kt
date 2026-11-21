package io.github.carvalhoxx.gecko.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.carvalhoxx.gecko.data.local.SecureCredentialsStorage
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EncryptedByokPrefs

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    @EncryptedByokPrefs
    fun provideEncryptedSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        SecureCredentialsStorage.createEncryptedPreferences(context)

    @Provides
    @Singleton
    fun provideSecureCredentialsStorage(@EncryptedByokPrefs prefs: SharedPreferences): SecureCredentialsStorage =
        SecureCredentialsStorage(prefs)
}
