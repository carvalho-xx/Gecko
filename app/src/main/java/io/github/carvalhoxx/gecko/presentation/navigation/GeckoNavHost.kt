package io.github.carvalhoxx.gecko.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.carvalhoxx.gecko.core.ReleaseFeatureFlags
import io.github.carvalhoxx.gecko.presentation.components.GeckoSurfaceCard
import io.github.carvalhoxx.gecko.ui.theme.GeckoBackground
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimaryContainer
import io.github.carvalhoxx.gecko.ui.theme.GeckoSurface
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextSecondary

/**
 * Barra de navegação inferior M3 OLED Dark adaptativa (4 abas no MVP v1.0, 5 abas na v1.1).
 */
@Composable
fun GeckoBottomNavigationBar(
    currentRoute: String?,
    featureFlags: ReleaseFeatureFlags,
    onNavigateToDestination: (GeckoTopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = GeckoRoutes.topLevelDestinations(featureFlags)

    NavigationBar(
        modifier = modifier.testTag("gecko_bottom_nav_bar"),
        containerColor = GeckoSurface,
        tonalElevation = 3.dp
    ) {
        destinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                modifier = Modifier.testTag(destination.testTag),
                icon = {
                    Text(
                        text = destination.iconGlyph,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) GeckoPrimary else GeckoTextSecondary
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GeckoPrimary,
                    selectedTextColor = GeckoPrimary,
                    indicatorColor = GeckoPrimaryContainer,
                    unselectedIconColor = GeckoTextSecondary,
                    unselectedTextColor = GeckoTextSecondary
                )
            )
        }
    }
}

/**
 * Scaffold global com `GeckoNavHost` e `GeckoBottomNavigationBar` integrando `ReleaseFeatureFlags`
 * e redirecionamento direto a partir da notificação de cronômetro ativo (RF21).
 */
@Composable
fun GeckoAppScaffold(
    featureFlags: ReleaseFeatureFlags = ReleaseFeatureFlags.DEFAULT,
    startDestination: String = GeckoRoutes.DASHBOARD,
    initialNavigateGameId: Long? = null,
    navController: NavHostController = rememberNavController(),
    welcomeOnboardingContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Bem-vindo ao Gecko", routeTag = "screen_welcome_onboarding")
    },
    dashboardContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Dashboard Principal", routeTag = "screen_dashboard")
    },
    libraryContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Biblioteca de Jogos", routeTag = "screen_library")
    },
    schedulerContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Agenda Preditiva & Wishlist", routeTag = "screen_scheduler")
    },
    analyticsContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Análises & Year in Review", routeTag = "screen_analytics")
    },
    settingsContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Configurações & Cofre BYOK", routeTag = "screen_settings")
    },
    gameDetailContent: @Composable (NavHostController, Long) -> Unit = { _, gameId ->
        RoutePlaceholderScreen(title = "Detalhes do Jogo #$gameId", routeTag = "screen_game_detail")
    },
    searchAddContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Busca Global e Cadastro", routeTag = "screen_search_add")
    },
    csvImportContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Importador de Planilha CSV", routeTag = "screen_csv_import")
    }
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = GeckoRoutes.isTopLevelRoute(currentRoute, featureFlags)

    LaunchedEffect(initialNavigateGameId) {
        if (initialNavigateGameId != null && initialNavigateGameId > 0L) {
            navController.navigate(GeckoRoutes.gameDetail(initialNavigateGameId)) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = GeckoBackground,
        bottomBar = {
            if (showBottomBar) {
                GeckoBottomNavigationBar(
                    currentRoute = currentRoute,
                    featureFlags = featureFlags,
                    onNavigateToDestination = { destination ->
                        if (currentRoute != destination.route) {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        GeckoNavHost(
            navController = navController,
            featureFlags = featureFlags,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            welcomeOnboardingContent = welcomeOnboardingContent,
            dashboardContent = dashboardContent,
            libraryContent = libraryContent,
            schedulerContent = schedulerContent,
            analyticsContent = analyticsContent,
            settingsContent = settingsContent,
            gameDetailContent = gameDetailContent,
            searchAddContent = searchAddContent,
            csvImportContent = csvImportContent
        )
    }
}

@Composable
fun GeckoNavHost(
    navController: NavHostController,
    featureFlags: ReleaseFeatureFlags,
    modifier: Modifier = Modifier,
    startDestination: String = GeckoRoutes.DASHBOARD,
    welcomeOnboardingContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Bem-vindo ao Gecko", routeTag = "screen_welcome_onboarding")
    },
    dashboardContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Dashboard Principal", routeTag = "screen_dashboard")
    },
    libraryContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Biblioteca de Jogos", routeTag = "screen_library")
    },
    schedulerContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Agenda Preditiva & Wishlist", routeTag = "screen_scheduler")
    },
    analyticsContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Análises & Year in Review", routeTag = "screen_analytics")
    },
    settingsContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Configurações & Cofre BYOK", routeTag = "screen_settings")
    },
    gameDetailContent: @Composable (NavHostController, Long) -> Unit = { _, gameId ->
        RoutePlaceholderScreen(title = "Detalhes do Jogo #$gameId", routeTag = "screen_game_detail")
    },
    searchAddContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Busca Global e Cadastro", routeTag = "screen_search_add")
    },
    csvImportContent: @Composable (NavHostController) -> Unit = {
        RoutePlaceholderScreen(title = "Importador de Planilha CSV", routeTag = "screen_csv_import")
    }
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.testTag("gecko_nav_host")
    ) {
        composable(GeckoRoutes.WELCOME_ONBOARDING) {
            welcomeOnboardingContent(navController)
        }
        composable(GeckoRoutes.DASHBOARD) {
            dashboardContent(navController)
        }
        composable(GeckoRoutes.LIBRARY) {
            libraryContent(navController)
        }
        composable(GeckoRoutes.SCHEDULER) {
            schedulerContent(navController)
        }
        if (featureFlags.isAnalyticsHubEnabled) {
            composable(GeckoRoutes.ANALYTICS) {
                analyticsContent(navController)
            }
        }
        composable(GeckoRoutes.SETTINGS) {
            settingsContent(navController)
        }
        composable(
            route = GeckoRoutes.GAME_DETAIL,
            arguments = listOf(navArgument(GeckoRoutes.ARG_GAME_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong(GeckoRoutes.ARG_GAME_ID) ?: 0L
            gameDetailContent(navController, gameId)
        }
        composable(GeckoRoutes.SEARCH_ADD) {
            searchAddContent(navController)
        }
        composable(GeckoRoutes.CSV_IMPORT) {
            csvImportContent(navController)
        }
    }
}

@Composable
private fun RoutePlaceholderScreen(title: String, routeTag: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(routeTag),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GeckoSurfaceCard {
            Text(
                text = title,
                color = GeckoTextPrimary,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Gecko Offline-First M3 OLED",
                color = GeckoTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
