package io.github.carvalhoxx.gecko.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.carvalhoxx.gecko.data.sync.AndroidForegroundServiceBridge
import io.github.carvalhoxx.gecko.data.sync.HltbRetryScheduler
import io.github.carvalhoxx.gecko.data.sync.LibraryDeltaSyncScheduler
import io.github.carvalhoxx.gecko.data.sync.WorkManagerHltbRetryScheduler
import io.github.carvalhoxx.gecko.data.sync.WorkManagerLibraryDeltaSyncScheduler
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import io.github.carvalhoxx.gecko.domain.repository.PlaythroughRepository
import io.github.carvalhoxx.gecko.domain.usecase.ActiveTimerCoordinator
import io.github.carvalhoxx.gecko.domain.usecase.ForegroundServiceBridge
import io.github.carvalhoxx.gecko.domain.usecase.SessionTimerController
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideForegroundServiceBridge(impl: AndroidForegroundServiceBridge): ForegroundServiceBridge = impl

    @Provides
    @Singleton
    fun provideHltbRetryScheduler(impl: WorkManagerHltbRetryScheduler): HltbRetryScheduler = impl

    @Provides
    @Singleton
    fun provideLibraryDeltaSyncScheduler(impl: WorkManagerLibraryDeltaSyncScheduler): LibraryDeltaSyncScheduler = impl

    @Provides
    @Singleton
    fun provideSessionTimerController(
        gameRepository: GameRepository,
        playthroughRepository: PlaythroughRepository,
        foregroundServiceBridge: ForegroundServiceBridge
    ): SessionTimerController = ActiveTimerCoordinator(
        gameRepository = gameRepository,
        playthroughRepository = playthroughRepository,
        foregroundServiceBridge = foregroundServiceBridge
    )
}
