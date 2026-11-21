package io.github.carvalhoxx.gecko.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.carvalhoxx.gecko.core.CoroutineDispatchers
import io.github.carvalhoxx.gecko.core.DefaultCoroutineDispatchers
import io.github.carvalhoxx.gecko.core.ReleaseFeatureFlags
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideCoroutineDispatchers(): CoroutineDispatchers = DefaultCoroutineDispatchers()

    @Provides
    @Singleton
    fun provideReleaseFeatureFlags(): ReleaseFeatureFlags = ReleaseFeatureFlags.DEFAULT
}
