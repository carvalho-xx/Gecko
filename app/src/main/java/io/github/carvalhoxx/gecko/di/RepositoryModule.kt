package io.github.carvalhoxx.gecko.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.carvalhoxx.gecko.data.repository.GameRepositoryImpl
import io.github.carvalhoxx.gecko.data.repository.HltbRepositoryImpl
import io.github.carvalhoxx.gecko.data.repository.PlaythroughRepositoryImpl
import io.github.carvalhoxx.gecko.data.repository.UserPreferencesRepositoryImpl
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import io.github.carvalhoxx.gecko.domain.repository.HltbRepository
import io.github.carvalhoxx.gecko.domain.repository.PlaythroughRepository
import io.github.carvalhoxx.gecko.domain.repository.UserPreferencesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds
    @Singleton
    abstract fun bindHltbRepository(impl: HltbRepositoryImpl): HltbRepository

    @Binds
    @Singleton
    abstract fun bindPlaythroughRepository(impl: PlaythroughRepositoryImpl): PlaythroughRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository
}
