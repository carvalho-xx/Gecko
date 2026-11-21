package io.github.carvalhoxx.gecko.core

import io.github.carvalhoxx.gecko.data.local.IgdbCredentials
import io.github.carvalhoxx.gecko.data.local.PsnCredentials
import io.github.carvalhoxx.gecko.data.local.RawgCredentials
import io.github.carvalhoxx.gecko.data.local.RetroAchievementsCredentials
import io.github.carvalhoxx.gecko.data.local.SteamCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ByokCredentialValidatorTest {

    @Test
    fun `validatePsnNpsso accepts exact 64 alphanumeric characters`() {
        val validNpsso = "aB3dE6gH9jK2mN5pQ8rS1tU4vW7xY0zA" + "1bC4eF7hI0kL3nO6qR9sT2uV5wX8yZ99"
        assertEquals(64, validNpsso.length)
        assertEquals(CredentialValidationResult.Valid, ByokCredentialValidator.validatePsnNpsso(validNpsso))
    }

    @Test
    fun `validatePsnNpsso rejects blank, whitespace, wrong length, and special characters`() {
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.BLANK_VALUE),
            ByokCredentialValidator.validatePsnNpsso("   ")
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.CONTAINS_WHITESPACE),
            ByokCredentialValidator.validatePsnNpsso("a".repeat(31) + " " + "b".repeat(32))
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_NPSSO_FORMAT),
            ByokCredentialValidator.validatePsnNpsso("a".repeat(63))
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_NPSSO_FORMAT),
            ByokCredentialValidator.validatePsnNpsso("a".repeat(65))
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_NPSSO_FORMAT),
            ByokCredentialValidator.validatePsnNpsso("a".repeat(63) + "-")
        )
    }

    @Test
    fun `validateSteamId64 accepts 17 numeric digits and rejects invalid formats`() {
        assertEquals(
            CredentialValidationResult.Valid,
            ByokCredentialValidator.validateSteamId64("76561198012345678")
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.BLANK_VALUE),
            ByokCredentialValidator.validateSteamId64("")
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.CONTAINS_WHITESPACE),
            ByokCredentialValidator.validateSteamId64("765611980 1234567")
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_STEAM_ID64_FORMAT),
            ByokCredentialValidator.validateSteamId64("7656119801234567")
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_STEAM_ID64_FORMAT),
            ByokCredentialValidator.validateSteamId64("7656119801234567A")
        )
    }

    @Test
    fun `validateSteamApiKey validates 32 hex characters`() {
        assertEquals(
            CredentialValidationResult.Valid,
            ByokCredentialValidator.validateSteamApiKey("0123456789ABCDEFabcdef0123456789")
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_STEAM_API_KEY_FORMAT),
            ByokCredentialValidator.validateSteamApiKey("short_key")
        )
    }

    @Test
    fun `validateIgdbCredentials, validateRawgApiKey, and validateRetroAchievements enforce syntax`() {
        assertEquals(
            CredentialValidationResult.Valid,
            ByokCredentialValidator.validateIgdbCredentials("clientId1234", "clientSecret5678")
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.CONTAINS_WHITESPACE),
            ByokCredentialValidator.validateIgdbCredentials("client id", "clientSecret5678")
        )
        assertEquals(
            CredentialValidationResult.Valid,
            ByokCredentialValidator.validateRawgApiKey("rawg_api_key_1234567890")
        )
        assertEquals(
            CredentialValidationResult.Valid,
            ByokCredentialValidator.validateRetroAchievements("player_one", "AbCdEf1234567890")
        )
    }

    @Test
    fun `validateDailyAvailabilityMs rejects zero, negative, and above 24h`() {
        assertEquals(
            CredentialValidationResult.Valid,
            ByokCredentialValidator.validateDailyAvailabilityMs(1L)
        )
        assertEquals(
            CredentialValidationResult.Valid,
            ByokCredentialValidator.validateDailyAvailabilityMs(7_200_000L)
        )
        assertEquals(
            CredentialValidationResult.Valid,
            ByokCredentialValidator.validateDailyAvailabilityMs(86_400_000L)
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_DAILY_AVAILABILITY_MS),
            ByokCredentialValidator.validateDailyAvailabilityMs(0L)
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_DAILY_AVAILABILITY_MS),
            ByokCredentialValidator.validateDailyAvailabilityMs(-3_600_000L)
        )
        assertEquals(
            CredentialValidationResult.Invalid(CredentialValidationError.INVALID_DAILY_AVAILABILITY_MS),
            ByokCredentialValidator.validateDailyAvailabilityMs(86_400_001L)
        )
    }

    @Test
    fun `validateCalibrationFactor rejects non-positive and non-finite values`() {
        assertEquals(CredentialValidationResult.Valid, ByokCredentialValidator.validateCalibrationFactor(0.8f))
        assertEquals(CredentialValidationResult.Valid, ByokCredentialValidator.validateCalibrationFactor(1.25f))
        assertTrue(ByokCredentialValidator.validateCalibrationFactor(0f) is CredentialValidationResult.Invalid)
        assertTrue(ByokCredentialValidator.validateCalibrationFactor(Float.NaN) is CredentialValidationResult.Invalid)
        assertTrue(
            ByokCredentialValidator.validateCalibrationFactor(Float.POSITIVE_INFINITY) is
                CredentialValidationResult.Invalid
        )
    }

    @Test
    fun `credential models mask all secrets in toString`() {
        val igdb = IgdbCredentials("super_secret_id", "super_secret_key")
        val rawg = RawgCredentials("rawg_secret_token_value")
        val steam = SteamCredentials("steam_secret_key_value", "76561198012345678")
        val psn = PsnCredentials(
            npsso = "N".repeat(64),
            accessToken = "access_secret_jwt",
            refreshToken = "refresh_secret_jwt",
            expiresAtMs = 1_800_000_000_000L
        )
        val ra = RetroAchievementsCredentials("secret_user", "ra_secret_key_value")

        assertFalse(igdb.toString().contains("super_secret_id"))
        assertFalse(igdb.toString().contains("super_secret_key"))
        assertFalse(rawg.toString().contains("rawg_secret_token_value"))
        assertFalse(steam.toString().contains("steam_secret_key_value"))
        assertFalse(steam.toString().contains("76561198012345678"))
        assertFalse(psn.toString().contains("N".repeat(10)))
        assertFalse(psn.toString().contains("access_secret_jwt"))
        assertFalse(psn.toString().contains("refresh_secret_jwt"))
        assertFalse(ra.toString().contains("secret_user"))
        assertFalse(ra.toString().contains("ra_secret_key_value"))
    }
}
