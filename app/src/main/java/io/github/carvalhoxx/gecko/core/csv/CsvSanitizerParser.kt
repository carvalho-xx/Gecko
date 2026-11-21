package io.github.carvalhoxx.gecko.core.csv

import java.time.LocalDate
import java.time.ZoneOffset

data class CsvColumnMapping(
    val titleColumnIndex: Int,
    val playtimeColumnIndex: Int? = null,
    val ratingColumnIndex: Int? = null,
    val genreColumnIndex: Int? = null,
    val platformColumnIndex: Int? = null,
    val releaseDayMonthColumnIndex: Int? = null,
    val releaseYearColumnIndex: Int? = null,
    val completionDayMonthColumnIndex: Int? = null,
    val completionYearColumnIndex: Int? = null,
    val statusColumnIndex: Int? = null,
    val headerRowIndex: Int = 0
)

enum class IgnoredRowReason {
    HEADER_ROW,
    EMPTY_ROW,
    MISSING_TITLE,
    FLOATING_BLOCK_OUTSIDE_1FN
}

data class IgnoredCsvRow(val sourceRowNumber: Int, val reason: IgnoredRowReason, val rawPreview: String)

data class ParsedCsvGameRow(
    val sourceRowNumber: Int,
    val title: String,
    val playtimeMs: Long,
    val rating: Float?,
    val genres: List<String>,
    val platform: String?,
    val releaseDateMs: Long?,
    val completionDateMs: Long?,
    val rawStatus: String?
)

data class CsvSanitizationReport(
    val headers: List<String>,
    val detectedMapping: CsvColumnMapping,
    val validRows: List<ParsedCsvGameRow>,
    val ignoredRows: List<IgnoredCsvRow>,
    val totalRawRows: Int
)

/**
 * Parser RFC 4180 com máquina de estados em Kotlin puro (proibido String.split(",")),
 * remoção de BOM UTF-8, suporte a vírgulas internas entre aspas, aspas dobradas ("")
 * e filtro heurístico de blocos flutuantes fora da 1FN (RF09, RNF08, TU-04).
 */
object CsvSanitizerParser {

    private const val UTF8_BOM = '\uFEFF'
    private val dayMonthRegex = Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?$")
    private val isoDateRegex = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
    private val yearOnlyRegex = Regex("^(\\d{4})$")

    private val floatingBlockKeywords = listOf(
        "ATUALIZAÇÃO",
        "ATUALIZACAO",
        "INÍCIO",
        "INICIO",
        "TEMPO ANUAL",
        "TEMPO TOTAL",
        "JOGOS ZERADOS",
        "JOGO MAIS LONGO",
        "JOGO MAIS CURTO",
        "TOP 10",
        "TOP 5",
        "DESAFIOS",
        "JOGANDO ATUALMENTE",
        "RESUMO",
        "ESTATÍSTICAS",
        "ESTATISTICAS"
    )

    /**
     * Máquina de estados RFC 4180 pura: jamais lança IndexOutOfBoundsException.
     */
    fun parseMatrix(rawContent: String, delimiter: Char = ','): List<List<String>> {
        if (rawContent.isEmpty()) return emptyList()

        val content = if (rawContent.first() == UTF8_BOM) rawContent.substring(1) else rawContent
        if (content.isEmpty()) return emptyList()

        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var index = 0
        val length = content.length

        while (index < length) {
            val ch = content[index]
            if (inQuotes) {
                if (ch == '"') {
                    if (index + 1 < length && content[index + 1] == '"') {
                        currentField.append('"')
                        index += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> {
                        if (currentField.isEmpty()) {
                            inQuotes = true
                        } else {
                            currentField.append(ch)
                        }
                    }

                    delimiter -> {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                    }

                    '\r' -> {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                        if (index + 1 < length && content[index + 1] == '\n') {
                            index++
                        }
                    }

                    '\n' -> {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }

                    else -> currentField.append(ch)
                }
            }
            index++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            rows.add(currentRow)
        }

        return rows
    }

    fun detectColumnMapping(rows: List<List<String>>): CsvColumnMapping {
        if (rows.isEmpty()) return CsvColumnMapping(titleColumnIndex = 0)

        val headerRowIndex = rows.indexOfFirst { row -> row.any { it.isNotBlank() } }.coerceAtLeast(0)
        val headerRow = rows.getOrNull(headerRowIndex).orEmpty()
        val sampleDataRows = rows.drop(headerRowIndex + 1)
            .filter { row -> row.any { it.isNotBlank() } }
            .take(10)

        val normalizedHeaders = headerRow.map { it.trim().lowercase() }

        var titleCol = findHeaderIndex(
            normalizedHeaders,
            listOf("games zerados", "título", "titulo", "title", "jogo", "game", "nome", "name")
        ) ?: 0

        // Trata cabeçalho mesclado (ex.: "Games Zerados,,Lançamento" com índice na col 0 e título na col 1)
        if (headerRow.getOrNull(titleCol + 1).isNullOrBlank() &&
            sampleDataRows.isNotEmpty() &&
            sampleDataRows.all { row ->
                val firstCell = row.getOrNull(titleCol)?.trim().orEmpty()
                val nextCell = row.getOrNull(titleCol + 1)?.trim().orEmpty()
                firstCell.toIntOrNull() != null && nextCell.isNotBlank()
            }
        ) {
            titleCol += 1
        }

        val playtimeCol = findHeaderIndex(
            normalizedHeaders,
            listOf("tempo", "playtime", "horas", "hours", "duration", "duração", "duracao")
        )
        val ratingCol = findHeaderIndex(
            normalizedHeaders,
            listOf("nota", "rating", "score", "avaliação", "avaliacao")
        )
        val genreCol = findHeaderIndex(
            normalizedHeaders,
            listOf("genero", "gênero", "genre", "genres")
        )
        val platformCol = findHeaderIndex(
            normalizedHeaders,
            listOf("consoles", "console", "plataforma", "platform")
        )
        val statusCol = findHeaderIndex(
            normalizedHeaders,
            listOf("status", "estado", "situação", "situacao")
        )

        val releaseHeaderCol = findHeaderIndex(
            normalizedHeaders,
            listOf("lançamento", "lancamento", "release", "ano")
        )
        val (releaseDayMonthCol, releaseYearCol) = resolveDateColumns(
            headerIndex = releaseHeaderCol,
            headerRow = headerRow,
            sampleRows = sampleDataRows
        )

        val completionHeaderCol = findHeaderIndex(
            normalizedHeaders,
            listOf("conclusão", "conclusao", "completed", "zerado em", "finished")
        )
        val (completionDayMonthCol, completionYearCol) = resolveDateColumns(
            headerIndex = completionHeaderCol,
            headerRow = headerRow,
            sampleRows = sampleDataRows
        )

        return CsvColumnMapping(
            titleColumnIndex = titleCol,
            playtimeColumnIndex = playtimeCol,
            ratingColumnIndex = ratingCol,
            genreColumnIndex = genreCol,
            platformColumnIndex = platformCol,
            releaseDayMonthColumnIndex = releaseDayMonthCol,
            releaseYearColumnIndex = releaseYearCol,
            completionDayMonthColumnIndex = completionDayMonthCol,
            completionYearColumnIndex = completionYearCol,
            statusColumnIndex = statusCol,
            headerRowIndex = headerRowIndex
        )
    }

    fun sanitize(
        rawContent: String,
        customMapping: CsvColumnMapping? = null,
        delimiter: Char = ','
    ): CsvSanitizationReport {
        val matrix = parseMatrix(rawContent, delimiter = delimiter)
        val mapping = customMapping ?: detectColumnMapping(matrix)
        val headers = matrix.getOrNull(mapping.headerRowIndex)
            .orEmpty()
            .dropLastWhile { it.isBlank() }

        val validRows = mutableListOf<ParsedCsvGameRow>()
        val ignoredRows = mutableListOf<IgnoredCsvRow>()

        matrix.forEachIndexed { rowIndex, row ->
            val sourceRowNumber = rowIndex + 1

            if (rowIndex == mapping.headerRowIndex) {
                ignoredRows += IgnoredCsvRow(
                    sourceRowNumber = sourceRowNumber,
                    reason = IgnoredRowReason.HEADER_ROW,
                    rawPreview = buildPreview(row)
                )
                return@forEachIndexed
            }

            if (row.all { it.isBlank() }) {
                ignoredRows += IgnoredCsvRow(
                    sourceRowNumber = sourceRowNumber,
                    reason = IgnoredRowReason.EMPTY_ROW,
                    rawPreview = ""
                )
                return@forEachIndexed
            }

            val rawTitle = row.getOrNull(mapping.titleColumnIndex)?.trim().orEmpty()
            if (rawTitle.isBlank()) {
                val reason = if (containsFloatingBlockSignal(row)) {
                    IgnoredRowReason.FLOATING_BLOCK_OUTSIDE_1FN
                } else {
                    IgnoredRowReason.MISSING_TITLE
                }
                ignoredRows += IgnoredCsvRow(
                    sourceRowNumber = sourceRowNumber,
                    reason = reason,
                    rawPreview = buildPreview(row)
                )
                return@forEachIndexed
            }

            if (isFloatingBlockTitle(rawTitle)) {
                ignoredRows += IgnoredCsvRow(
                    sourceRowNumber = sourceRowNumber,
                    reason = IgnoredRowReason.FLOATING_BLOCK_OUTSIDE_1FN,
                    rawPreview = buildPreview(row)
                )
                return@forEachIndexed
            }

            val playtimeMs = mapping.playtimeColumnIndex
                ?.let { idx -> DurationRegexParser.parseToMillisOrNull(row.getOrNull(idx)) }
                ?: 0L

            val rating = mapping.ratingColumnIndex
                ?.let { idx -> RatingNormalizer.normalizeOrNull(row.getOrNull(idx)) }

            val genres = mapping.genreColumnIndex
                ?.let { idx -> parseGenres(row.getOrNull(idx)) }
                .orEmpty()

            val platform = mapping.platformColumnIndex
                ?.let { idx -> row.getOrNull(idx)?.trim()?.takeIf { it.isNotEmpty() } }

            val releaseDateMs = parseDateMillis(
                dayMonthRaw = mapping.releaseDayMonthColumnIndex?.let { row.getOrNull(it) },
                yearRaw = mapping.releaseYearColumnIndex?.let { row.getOrNull(it) }
            )

            val completionDateMs = parseDateMillis(
                dayMonthRaw = mapping.completionDayMonthColumnIndex?.let { row.getOrNull(it) },
                yearRaw = mapping.completionYearColumnIndex?.let { row.getOrNull(it) }
            )

            val rawStatus = mapping.statusColumnIndex
                ?.let { idx -> row.getOrNull(idx)?.trim()?.takeIf { it.isNotEmpty() } }

            validRows += ParsedCsvGameRow(
                sourceRowNumber = sourceRowNumber,
                title = rawTitle,
                playtimeMs = playtimeMs,
                rating = rating,
                genres = genres,
                platform = platform,
                releaseDateMs = releaseDateMs,
                completionDateMs = completionDateMs,
                rawStatus = rawStatus
            )
        }

        return CsvSanitizationReport(
            headers = headers,
            detectedMapping = mapping,
            validRows = validRows,
            ignoredRows = ignoredRows,
            totalRawRows = matrix.size
        )
    }

    private fun findHeaderIndex(normalizedHeaders: List<String>, candidates: List<String>): Int? {
        for (candidate in candidates) {
            val exactIndex = normalizedHeaders.indexOfFirst { it == candidate }
            if (exactIndex >= 0) return exactIndex
        }
        for (candidate in candidates) {
            val containsIndex = normalizedHeaders.indexOfFirst { it.contains(candidate) }
            if (containsIndex >= 0) return containsIndex
        }
        return null
    }

    private fun resolveDateColumns(
        headerIndex: Int?,
        headerRow: List<String>,
        sampleRows: List<List<String>>
    ): Pair<Int?, Int?> {
        if (headerIndex == null) return null to null
        val nextHeaderBlank = headerRow.getOrNull(headerIndex + 1).isNullOrBlank()
        val hasSplitDayMonthAndYear = nextHeaderBlank &&
            sampleRows.any { row ->
                val first = row.getOrNull(headerIndex)?.trim().orEmpty()
                val second = row.getOrNull(headerIndex + 1)?.trim().orEmpty()
                dayMonthRegex.matches(first) && yearOnlyRegex.matches(second)
            }
        return if (hasSplitDayMonthAndYear) {
            headerIndex to (headerIndex + 1)
        } else {
            headerIndex to null
        }
    }

    private fun parseGenres(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('/', '|', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun parseDateMillis(dayMonthRaw: String?, yearRaw: String?): Long? {
        val primary = dayMonthRaw?.trim().orEmpty()
        val secondaryYear = yearRaw?.trim()?.toIntOrNull()

        if (primary.isEmpty() && secondaryYear == null) return null

        isoDateRegex.matchEntire(primary)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            val day = match.groupValues[3].toIntOrNull() ?: return null
            return safeDateToUtcMs(year, month, day)
        }

        dayMonthRegex.matchEntire(primary)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            val year = match.groupValues[3].toIntOrNull() ?: secondaryYear ?: return null
            return safeDateToUtcMs(year, month, day)
        }

        val fallbackYear = primary.toIntOrNull() ?: secondaryYear ?: return null
        return safeDateToUtcMs(fallbackYear, 12, 31)
    }

    private fun safeDateToUtcMs(year: Int, month: Int, day: Int): Long? = try {
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) {
        null
    }

    private fun isFloatingBlockTitle(title: String): Boolean {
        val upper = title.trim().uppercase()
        return floatingBlockKeywords.any { keyword ->
            upper == keyword || upper.startsWith("$keyword ") || upper.startsWith("$keyword(")
        }
    }

    private fun containsFloatingBlockSignal(row: List<String>): Boolean = row.any { cell ->
        val upper = cell.trim().uppercase()
        upper.isNotEmpty() && floatingBlockKeywords.any { keyword -> upper.contains(keyword) }
    }

    private fun buildPreview(row: List<String>): String = row
        .filter { it.isNotBlank() }
        .take(4)
        .joinToString(" | ")
        .take(80)
}
