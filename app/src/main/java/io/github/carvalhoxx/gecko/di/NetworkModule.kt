package io.github.carvalhoxx.gecko.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.carvalhoxx.gecko.data.remote.hltb.HltbApiClient
import io.github.carvalhoxx.gecko.data.remote.igdb.IgdbApiClient
import io.github.carvalhoxx.gecko.data.remote.psn.PsnApiClient
import io.github.carvalhoxx.gecko.data.remote.rawg.RawgApiClient
import io.github.carvalhoxx.gecko.data.remote.steam.SteamApiClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Infraestrutura de rede HTTP BYOK (RNF03: nenhum interceptor imprime segredos em logs).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideHltbApiClient(okHttpClient: OkHttpClient, json: Json): HltbApiClient = HltbApiClient(
        okHttpClient = okHttpClient,
        json = json
    )

    @Provides
    @Singleton
    fun provideIgdbApiClient(okHttpClient: OkHttpClient, json: Json): IgdbApiClient = IgdbApiClient(
        okHttpClient = okHttpClient,
        json = json
    )

    @Provides
    @Singleton
    fun provideSteamApiClient(okHttpClient: OkHttpClient, json: Json): SteamApiClient = SteamApiClient(
        okHttpClient = okHttpClient,
        json = json
    )

    @Provides
    @Singleton
    fun providePsnApiClient(okHttpClient: OkHttpClient, json: Json): PsnApiClient = PsnApiClient(
        okHttpClient = okHttpClient,
        json = json
    )

    @Provides
    @Singleton
    fun provideRawgApiClient(okHttpClient: OkHttpClient, json: Json): RawgApiClient = RawgApiClient(
        okHttpClient = okHttpClient,
        json = json
    )
}
