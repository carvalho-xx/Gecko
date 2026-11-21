package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.core.CoroutineDispatchers
import io.github.carvalhoxx.gecko.core.csv.CsvColumnMapping
import io.github.carvalhoxx.gecko.core.csv.CsvSanitizationReport
import io.github.carvalhoxx.gecko.core.csv.CsvSanitizerParser
import kotlinx.coroutines.withContext

/**
 * RF09 / RNF08: Gera prévia de sanitização CSV assíncrona em [CoroutineDispatchers.default]
 * sem bloquear a thread principal.
 */
class PreviewCsvImportUseCase(private val dispatchers: CoroutineDispatchers) {

    suspend operator fun invoke(
        rawCsvContent: String,
        customMapping: CsvColumnMapping? = null,
        delimiter: Char = ','
    ): CsvSanitizationReport = withContext(dispatchers.default) {
        CsvSanitizerParser.sanitize(
            rawContent = rawCsvContent,
            customMapping = customMapping,
            delimiter = delimiter
        )
    }
}
