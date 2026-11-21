package io.github.carvalhoxx.gecko.core

/**
 * Validadores sintáticos puros (JVM) para credenciais BYOK e ritmo diário.
 * Nenhum segredo bruto é incluído em mensagens de erro ou exceções (RNF03).
 */
sealed interface CredentialValidationResult {
    data object Valid : CredentialValidationResult

    data class Invalid(val reason: CredentialValidationError) : CredentialValidationResult
}

enum class CredentialValidationError {
    BLANK_VALUE,
    CONTAINS_WHITESPACE,
    INVALID_NPSSO_FORMAT,
    INVALID_STEAM_ID64_FORMAT,
    INVALID_STEAM_API_KEY_FORMAT,
    INVALID_IGDB_CLIENT_ID_FORMAT,
    INVALID_IGDB_CLIENT_SECRET_FORMAT,
    INVALID_RAWG_API_KEY_FORMAT,
    INVALID_RETRO_ACHIEVEMENTS_USERNAME,
    INVALID_RETRO_ACHIEVEMENTS_API_KEY,
    INVALID_DAILY_AVAILABILITY_MS,
    INVALID_CALIBRATION_FACTOR
}

object ByokCredentialValidator {

    private const val MAX_DAILY_AVAILABILITY_MS = 86_400_000L // 24h
    private const val MIN_CALIBRATION_FACTOR = 0.25f
    private const val MAX_CALIBRATION_FACTOR = 4.0f

    private val npssoRegex = Regex("^[A-Za-z0-9]{64}$")
    private val steamId64Regex = Regex("^[0-9]{17}$")
    private val steamApiKeyRegex = Regex("^[A-Fa-f0-9]{32}$")
    private val genericTokenRegex = Regex("^[A-Za-z0-9_-]{8,128}$")
    private val rawgKeyRegex = Regex("^[A-Za-z0-9_-]{16,128}$")
    private val raUsernameRegex = Regex("^[A-Za-z0-9_.-]{2,64}$")
    private val raApiKeyRegex = Regex("^[A-Za-z0-9]{16,128}$")

    fun validatePsnNpsso(npsso: String): CredentialValidationResult = when {
        npsso.isBlank() -> CredentialValidationResult.Invalid(CredentialValidationError.BLANK_VALUE)
        npsso.any { it.isWhitespace() } ->
            CredentialValidationResult.Invalid(CredentialValidationError.CONTAINS_WHITESPACE)
        !npssoRegex.matches(npsso) ->
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_NPSSO_FORMAT)
        else -> CredentialValidationResult.Valid
    }

    fun validateSteamId64(steamId64: String): CredentialValidationResult = when {
        steamId64.isBlank() -> CredentialValidationResult.Invalid(CredentialValidationError.BLANK_VALUE)
        steamId64.any { it.isWhitespace() } ->
            CredentialValidationResult.Invalid(CredentialValidationError.CONTAINS_WHITESPACE)
        !steamId64Regex.matches(steamId64) ->
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_STEAM_ID64_FORMAT)
        else -> CredentialValidationResult.Valid
    }

    fun validateSteamApiKey(apiKey: String): CredentialValidationResult = when {
        apiKey.isBlank() -> CredentialValidationResult.Invalid(CredentialValidationError.BLANK_VALUE)
        apiKey.any { it.isWhitespace() } ->
            CredentialValidationResult.Invalid(CredentialValidationError.CONTAINS_WHITESPACE)
        !steamApiKeyRegex.matches(apiKey) ->
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_STEAM_API_KEY_FORMAT)
        else -> CredentialValidationResult.Valid
    }

    fun validateIgdbCredentials(clientId: String, clientSecret: String): CredentialValidationResult = when {
        clientId.isBlank() || clientSecret.isBlank() ->
            CredentialValidationResult.Invalid(CredentialValidationError.BLANK_VALUE)
        clientId.any { it.isWhitespace() } || clientSecret.any { it.isWhitespace() } ->
            CredentialValidationResult.Invalid(CredentialValidationError.CONTAINS_WHITESPACE)
        !genericTokenRegex.matches(clientId) ->
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_IGDB_CLIENT_ID_FORMAT)
        !genericTokenRegex.matches(clientSecret) ->
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_IGDB_CLIENT_SECRET_FORMAT)
        else -> CredentialValidationResult.Valid
    }

    fun validateRawgApiKey(apiKey: String): CredentialValidationResult = when {
        apiKey.isBlank() -> CredentialValidationResult.Invalid(CredentialValidationError.BLANK_VALUE)
        apiKey.any { it.isWhitespace() } ->
            CredentialValidationResult.Invalid(CredentialValidationError.CONTAINS_WHITESPACE)
        !rawgKeyRegex.matches(apiKey) ->
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_RAWG_API_KEY_FORMAT)
        else -> CredentialValidationResult.Valid
    }

    fun validateRetroAchievements(username: String, apiKey: String): CredentialValidationResult = when {
        username.isBlank() || apiKey.isBlank() ->
            CredentialValidationResult.Invalid(CredentialValidationError.BLANK_VALUE)
        username.any { it.isWhitespace() } || apiKey.any { it.isWhitespace() } ->
            CredentialValidationResult.Invalid(CredentialValidationError.CONTAINS_WHITESPACE)
        !raUsernameRegex.matches(username) ->
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_RETRO_ACHIEVEMENTS_USERNAME)
        !raApiKeyRegex.matches(apiKey) ->
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_RETRO_ACHIEVEMENTS_API_KEY)
        else -> CredentialValidationResult.Valid
    }

    fun validateDailyAvailabilityMs(dailyAvailabilityMs: Long): CredentialValidationResult =
        if (dailyAvailabilityMs in 1L..MAX_DAILY_AVAILABILITY_MS) {
            CredentialValidationResult.Valid
        } else {
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_DAILY_AVAILABILITY_MS)
        }

    fun validateCalibrationFactor(factor: Float): CredentialValidationResult =
        if (factor.isFinite() && factor in MIN_CALIBRATION_FACTOR..MAX_CALIBRATION_FACTOR) {
            CredentialValidationResult.Valid
        } else {
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_CALIBRATION_FACTOR)
        }
}
