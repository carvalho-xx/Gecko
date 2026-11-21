package io.github.carvalhoxx.gecko.data.local

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TI-03: Validação estática do schema Room v2 exportado pelo KSP.
 * Garante ausência de kotlin.time.Duration, tipagem estrita INTEGER/Long para colunas temporais
 * e integridade referencial ON DELETE CASCADE nas entidades filhas do DER.
 */
class GeckoDatabaseSchemaTest {

    private fun loadSchemaV2File(): File {
        val relativePath = "schemas/io.github.carvalhoxx.gecko.data.local.GeckoDatabase/2.json"
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Schema v2 file not found. Searched: ${candidates.map { it.absolutePath }}")
    }

    @Test
    fun `TI-03 schema v2 should contain all 9 DER tables and zero kotlin time Duration references`() {
        val rawSchema = loadSchemaV2File().readText()
        assertFalse("Schema must not reference kotlin.time.Duration", rawSchema.contains("Duration"))
        assertFalse("Schema must not reference kotlin.time", rawSchema.contains("kotlin.time"))

        val root = Json.parseToJsonElement(rawSchema).jsonObject
        val database = root.getValue("database").jsonObject
        assertEquals(2, database.getValue("version").jsonPrimitive.int)

        val entities = database.getValue("entities").jsonArray
        val tableNames = entities.map { it.jsonObject.getValue("tableName").jsonPrimitive.content }.toSet()

        val expectedTables = setOf(
            "GAME",
            "PLAYTHROUGH",
            "JOURNAL_ENTRY",
            "PLATFORM",
            "GAME_PLATFORM_REF",
            "CUSTOM_LIST",
            "GAME_CUSTOM_LIST_REF",
            "HLTB_FALLBACK",
            "ANNUAL_GOAL"
        )
        assertEquals(expectedTables, tableNames)
    }

    @Test
    fun `TI-03 all temporal columns ending with _ms must map exclusively to INTEGER affinity`() {
        val root = Json.parseToJsonElement(loadSchemaV2File().readText()).jsonObject
        val entities = root.getValue("database").jsonObject.getValue("entities").jsonArray

        val temporalColumns = mutableListOf<String>()
        entities.forEach { entityElement ->
            val entityObj = entityElement.jsonObject
            val tableName = entityObj.getValue("tableName").jsonPrimitive.content
            entityObj.getValue("fields").jsonArray.forEach { fieldElement ->
                val fieldObj = fieldElement.jsonObject
                val columnName = fieldObj.getValue("columnName").jsonPrimitive.content
                val affinity = fieldObj.getValue("affinity").jsonPrimitive.content
                if (columnName.endsWith("_ms")) {
                    temporalColumns += "$tableName.$columnName"
                    assertEquals(
                        "Temporal column $tableName.$columnName must use INTEGER affinity",
                        "INTEGER",
                        affinity
                    )
                }
            }
        }

        assertTrue("Expected temporal columns in schema v2", temporalColumns.size >= 15)
    }

    @Test
    fun `TI-03 child tables must declare ForeignKey CASCADE onDelete`() {
        val root = Json.parseToJsonElement(loadSchemaV2File().readText()).jsonObject
        val entitiesByName = root.getValue("database").jsonObject
            .getValue("entities").jsonArray
            .associateBy { it.jsonObject.getValue("tableName").jsonPrimitive.content }

        val cascadeTables = listOf(
            "PLAYTHROUGH",
            "JOURNAL_ENTRY",
            "GAME_PLATFORM_REF",
            "GAME_CUSTOM_LIST_REF"
        )

        cascadeTables.forEach { tableName ->
            val foreignKeys = entitiesByName.getValue(tableName).jsonObject
                .getValue("foreignKeys").jsonArray
            assertTrue("$tableName must define at least one foreign key", foreignKeys.isNotEmpty())
            foreignKeys.forEach { fkElement ->
                val onDelete = fkElement.jsonObject.getValue("onDelete").jsonPrimitive.content
                assertEquals("$tableName foreign key must use CASCADE onDelete", "CASCADE", onDelete)
            }
        }
    }
}
