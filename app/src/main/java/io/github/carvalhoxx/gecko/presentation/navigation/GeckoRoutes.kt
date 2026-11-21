package io.github.carvalhoxx.gecko.presentation.navigation

import io.github.carvalhoxx.gecko.core.ReleaseFeatureFlags

/**
 * Catálogo central de rotas e abas da Bottom Navigation Bar do Gecko.
 * Governado por [ReleaseFeatureFlags]:
 * - MVP v1.0: 4 abas (Início, Biblioteca, Agenda, Configurações).
 * - Expansão v1.1+: 5 abas (adiciona Análises / AnalyticsHubScreen).
 */
object GeckoRoutes {
    const val WELCOME_ONBOARDING = "welcome_onboarding"
    const val DASHBOARD = "dashboard"
    const val LIBRARY = "library"
    const val SCHEDULER = "scheduler"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"

    const val ARG_GAME_ID = "gameId"
    const val GAME_DETAIL = "game_detail/{$ARG_GAME_ID}"
    const val SEARCH_ADD = "search_add"
    const val CSV_IMPORT = "csv_import"
    const val VITRINE_RELEASES = "vitrine_releases"

    const val ARG_COLLECTION_TYPE = "type"
    const val ARG_COLLECTION_ID = "id"
    const val COLLECTION_DETAIL = "collection/{$ARG_COLLECTION_TYPE}/{$ARG_COLLECTION_ID}"

    fun gameDetail(gameId: Long): String = "game_detail/$gameId"

    fun collectionDetail(type: String, id: Long): String = "collection/$type/$id"

    fun topLevelDestinations(
        featureFlags: ReleaseFeatureFlags = ReleaseFeatureFlags.DEFAULT
    ): List<GeckoTopLevelDestination> = GeckoTopLevelDestination.entries.filter { destination ->
        !destination.requiresV11 || featureFlags.isAnalyticsHubEnabled
    }

    fun isTopLevelRoute(route: String?, featureFlags: ReleaseFeatureFlags = ReleaseFeatureFlags.DEFAULT): Boolean =
        topLevelDestinations(featureFlags).any { it.route == route }
}

enum class GeckoTopLevelDestination(
    val route: String,
    val label: String,
    val iconGlyph: String,
    val testTag: String,
    val requiresV11: Boolean = false
) {
    DASHBOARD(
        route = GeckoRoutes.DASHBOARD,
        label = "Início",
        iconGlyph = "⌂",
        testTag = "nav_tab_dashboard"
    ),
    LIBRARY(
        route = GeckoRoutes.LIBRARY,
        label = "Biblioteca",
        iconGlyph = "▤",
        testTag = "nav_tab_library"
    ),
    SCHEDULER(
        route = GeckoRoutes.SCHEDULER,
        label = "Agenda",
        iconGlyph = "⏱",
        testTag = "nav_tab_scheduler"
    ),
    ANALYTICS(
        route = GeckoRoutes.ANALYTICS,
        label = "Análises",
        iconGlyph = "📊",
        testTag = "nav_tab_analytics",
        requiresV11 = true
    ),
    SETTINGS(
        route = GeckoRoutes.SETTINGS,
        label = "Ajustes",
        iconGlyph = "⚙",
        testTag = "nav_tab_settings"
    )
}
