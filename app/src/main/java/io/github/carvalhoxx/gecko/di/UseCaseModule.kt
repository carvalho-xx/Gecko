package io.github.carvalhoxx.gecko.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import io.github.carvalhoxx.gecko.domain.repository.PlaythroughRepository
import io.github.carvalhoxx.gecko.domain.usecase.GetEligibleBacklogGamesUseCase
import io.github.carvalhoxx.gecko.domain.usecase.GetVisibleBacklogGamesUseCase
import io.github.carvalhoxx.gecko.domain.usecase.MergeDeltaBaselineUseCase
import io.github.carvalhoxx.gecko.domain.usecase.TransitionGameStatusUseCase
import io.github.carvalhoxx.gecko.domain.usecase.UpdatePlayStyleUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetEligibleBacklogGamesUseCase(gameRepository: GameRepository): GetEligibleBacklogGamesUseCase =
        GetEligibleBacklogGamesUseCase(gameRepository)

    @Provides
    @Singleton
    fun provideGetVisibleBacklogGamesUseCase(gameRepository: GameRepository): GetVisibleBacklogGamesUseCase =
        GetVisibleBacklogGamesUseCase(gameRepository)

    @Provides
    @Singleton
    fun provideMergeDeltaBaselineUseCase(): MergeDeltaBaselineUseCase = MergeDeltaBaselineUseCase()

    @Provides
    @Singleton
    fun provideTransitionGameStatusUseCase(
        gameRepository: GameRepository,
        playthroughRepository: PlaythroughRepository
    ): TransitionGameStatusUseCase = TransitionGameStatusUseCase(
        gameRepository = gameRepository,
        playthroughRepository = playthroughRepository
    )

    @Provides
    @Singleton
    fun provideUpdatePlayStyleUseCase(gameRepository: GameRepository): UpdatePlayStyleUseCase =
        UpdatePlayStyleUseCase(gameRepository)
}
