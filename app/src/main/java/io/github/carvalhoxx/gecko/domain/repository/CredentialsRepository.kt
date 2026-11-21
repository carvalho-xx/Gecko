package io.github.carvalhoxx.gecko.domain.repository

import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.ByokProviderStatus

interface CredentialsRepository {
    fun getProviderStatuses(): List<ByokProviderStatus>

    fun hasAnyConfiguredProvider(): Boolean

    fun saveIgdbCredentials(clientId: String, clientSecret: String)

    fun saveRawgApiKey(apiKey: String)

    fun saveSteamCredentials(apiKey: String, steamId64: String)

    fun savePsnNpsso(npsso: String)

    fun saveRetroAchievementsCredentials(username: String, apiKey: String)

    fun clearProvider(provider: ByokProvider)

    fun clearAll()
}
