package io.github.carvalhoxx.gecko.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garante a invariante ANIR [ARCH: CLEAN(MVVM) ! ANDROID_SDK_IN_DOMAIN]:
 * nenhum arquivo dentro de domain/ pode importar android.*, androidx.*, Room, Context ou data.*.
 */
class DomainPurityArchitectureTest {

    private fun resolveDomainDir(): File {
        val relativePath = "src/main/java/io/github/carvalhoxx/gecko/domain"
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Domain directory not found. Searched: ${candidates.map { it.absolutePath }}")
    }

    @Test
    fun `domain package must have zero android, androidx, Room, or data layer imports`() {
        val domainDir = resolveDomainDir()
        val kotlinFiles = domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue("Expected at least 10 Kotlin files in domain/", kotlinFiles.size >= 10)

        val forbiddenImportPrefixes = listOf(
            "import android.",
            "import androidx.",
            "import dagger.",
            "import com.google.dagger.",
            "import io.github.carvalhoxx.gecko.data."
        )

        val violations = mutableListOf<String>()
        kotlinFiles.forEach { file ->
            file.readLines().forEachIndexed { index, rawLine ->
                val line = rawLine.trim()
                if (forbiddenImportPrefixes.any { line.startsWith(it) }) {
                    violations += "${file.name}:${index + 1} -> $line"
                }
            }
        }

        assertTrue(
            "ANIR purity violation in domain/:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }
}
