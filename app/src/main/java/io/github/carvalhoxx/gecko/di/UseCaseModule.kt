package io.github.carvalhoxx.gecko.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.carvalhoxx.gecko.core.CoroutineDispatchers
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import io.github.carvalhoxx.gecko.domain.repository.PlaythroughRepository
import io.github.carvalhoxx.gecko.domain.usecase.CalculateSmartScheduleUseCase
import io.github.carvalhoxx.gecko.domain.usecase.GetDashboardKpisUseCase
import io.github.carvalhoxx.gecko.domain.usecase.GetEligibleBacklogGamesUseCase
import io.github.carvalhoxx.gecko.domain.usecase.GetVisibleBacklogGamesUseCase
import io.github.carvalhoxx.gecko.domain.usecase.ImportCsvGamesUseCase
import io.github.carvalhoxx.gecko.domain.usecase.MergeDeltaBaselineUseCase
import io.github.carvalhoxx.gecko.domain.usecase.PreviewCsvImportUseCase
import io.github.carvalhoxx.gecko.domain.usecase.SortGamesWithTieBreakerUseCase
import io.github.carvalhoxx.gecko.domain.usecase.TransitionGameStatusUseCase
import io.github.carvalhoxx.gecko.domain.usecase.UpdatePlayStyleUseCase
import io.github.carvalhoxx.gecko.domain.usecase.ValidateAndSaveRatingUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideCalculateSmartScheduleUseCase(
        gameRepository: GameRepository,
        getEligibleBacklogGamesUseCase: GetEligibleBacklogGamesUseCase,
        mergeDeltaBaselineUseCase: MergeDeltaBaselineUseCase
    ): CalculateSmartScheduleUseCase = CalculateSmartScheduleUseCase(
        gameRepository = gameRepository,
        getEligibleBacklogGamesUseCase = getEligibleBacklogGamesUseCase,
        mergeDeltaBaselineUseCase = mergeDeltaBaselineUseCase
    )

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

    @Provides
    @Singleton
    fun provideValidateAndSaveRatingUseCase(gameRepository: GameRepository): ValidateAndSaveRatingUseCase =
        ValidateAndSaveRatingUseCase(gameRepository)

    @Provides
    @Singleton
    fun provideSortGamesWithTieBreakerUseCase(
        mergeDeltaBaselineUseCase: MergeDeltaBaselineUseCase
    ): SortGamesWithTieBreakerUseCase = SortGamesWithTieBreakerUseCase(mergeDeltaBaselineUseCase)

    @Provides
    @Singleton
    fun provideGetDashboardKpisUseCase(
        gameRepository: GameRepository,
        mergeDeltaBaselineUseCase: MergeDeltaBaselineUseCase
    ): GetDashboardKpisUseCase = GetDashboardKpisUseCase(
        gameRepository = gameRepository,
        mergeDeltaBaselineUseCase = mergeDeltaBaselineUseCase
    )

    @Provides
    @Singleton
    fun providePreviewCsvImportUseCase(dispatchers: CoroutineDispatchers): PreviewCsvImportUseCase =
        PreviewCsvImportUseCase(dispatchers)

    @Provides
    @Singleton
    fun provideImportCsvGamesUseCase(
        gameRepository: GameRepository,
        playthroughRepository: PlaythroughRepository,
        dispatchers: CoroutineDispatchers
    ): ImportCsvGamesUseCase = ImportCsvGamesUseCase(
        gameRepository = gameRepository,
        playthroughRepository = playthroughRepository,
        dispatchers = dispatchers
    )
}
