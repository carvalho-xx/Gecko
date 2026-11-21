package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.core.CoroutineDispatchers
import io.github.carvalhoxx.gecko.core.csv.CsvColumnMapping
import io.github.carvalhoxx.gecko.core.csv.CsvSanitizationReport
import io.github.carvalhoxx.gecko.core.csv.CsvSanitizerParser
import io.github.carvalhoxx.gecko.core.csv.ParsedCsvGameRow
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.model.Playthrough
import io.github.carvalhoxx.gecko.domain.model.ReleasePrecision
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import io.github.carvalhoxx.gecko.domain.repository.PlaythroughRepository
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.withContext

data class CsvImportSummary(val importedCount: Int, val ignoredCount: Int, val report: CsvSanitizationReport)

/**
 * RF09 / RNF08: Ingestão assíncrona resiliente de CSV em lote:
 * - Parsing e sanitização RFC 4180 + Regex executados em [CoroutineDispatchers.default].
 * - Persistência em lote no Room executada em [CoroutineDispatchers.io].
 * - Tempo importado gravado exclusivamente em localLoggedTimeMs (preservando Delta Baseline).
 */
class ImportCsvGamesUseCase(
    private val gameRepository: GameRepository,
    private val playthroughRepository: PlaythroughRepository,
    private val dispatchers: CoroutineDispatchers,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    suspend operator fun invoke(
        rawCsvContent: String,
        customMapping: CsvColumnMapping? = null,
        defaultStatus: GameStatus = GameStatus.COMPLETED,
        defaultLicenseType: LicenseType = LicenseType.OWNED,
        delimiter: Char = ','
    ): CsvImportSummary {
        val report = withContext(dispatchers.default) {
            CsvSanitizerParser.sanitize(
                rawContent = rawCsvContent,
                customMapping = customMapping,
                delimiter = delimiter
            )
        }

        if (report.validRows.isEmpty()) {
            return CsvImportSummary(
                importedCount = 0,
                ignoredCount = report.ignoredRows.size,
                report = report
            )
        }

        val nowMs = clockMs()
        val mappedEntries = withContext(dispatchers.default) {
            report.validRows.map { row ->
                val game = row.toGameItem(
                    defaultStatus = defaultStatus,
                    defaultLicenseType = defaultLicenseType,
                    nowMs = nowMs
                )
                game to row
            }
        }

        withContext(dispatchers.io) {
            gameRepository.upsertGames(mappedEntries.map { it.first })

            mappedEntries.forEach { (game, row) ->
                if (game.status == GameStatus.COMPLETED && row.completionDateMs != null) {
                    val existingPlaythroughs = playthroughRepository.getPlaythroughsForGame(game.id)
                    val alreadyRecorded = existingPlaythroughs.any { it.endTimestampMs == row.completionDateMs }
                    if (!alreadyRecorded) {
                        playthroughRepository.insertPlaythrough(
                            Playthrough(
                                gameId = game.id,
                                startTimestampMs = row.completionDateMs,
                                endTimestampMs = row.completionDateMs,
                                rating = game.userRating,
                                notesArchived = true
                            )
                        )
                    }
                }
            }
        }

        return CsvImportSummary(
            importedCount = mappedEntries.size,
            ignoredCount = report.ignoredRows.size,
            report = report
        )
    }

    private fun ParsedCsvGameRow.toGameItem(
        defaultStatus: GameStatus,
        defaultLicenseType: LicenseType,
        nowMs: Long
    ): GameItem {
        val resolvedStatus = rawStatus?.let { GameStatus.fromStorageValue(it) } ?: defaultStatus
        val resolvedLicense = if (resolvedStatus == GameStatus.WISHLIST) LicenseType.WISHLIST else defaultLicenseType
        val deterministicId = generateDeterministicCsvId(title = title, platform = platform)

        return GameItem(
            id = deterministicId,
            title = title,
            releaseDateMs = releaseDateMs,
            status = resolvedStatus,
            licenseType = resolvedLicense,
            platformReportedTimeMs = 0L,
            localLoggedTimeMs = playtimeMs,
            userRating = rating,
            genres = genres,
            statusUpdatedAtMs = completionDateMs ?: nowMs,
            releasePrecision = if (releaseDateMs != null) ReleasePrecision.EXACT else null
        )
    }

    companion object {
        fun generateDeterministicCsvId(title: String, platform: String?): String {
            val seed = "${title.trim().lowercase()}|${platform?.trim()?.lowercase().orEmpty()}"
            val uuid = UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))
            return "csv-$uuid"
        }
    }
}
