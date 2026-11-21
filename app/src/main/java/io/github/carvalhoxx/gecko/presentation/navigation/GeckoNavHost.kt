package io.github.carvalhoxx.gecko.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.github.carvalhoxx.gecko.ui.theme.GeckoBottomNavActiveContent
import io.github.carvalhoxx.gecko.ui.theme.GeckoBottomNavActivePill
import io.github.carvalhoxx.gecko.ui.theme.GeckoBottomNavBg
import io.github.carvalhoxx.gecko.ui.theme.GeckoBottomNavBorder
import io.github.carvalhoxx.gecko.ui.theme.GeckoBottomNavInactiveContent
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextSecondary

/**
 * Barra de navegação inferior compacta alinhada ao protótipo FigCraft (`BottomNavClean1`, id 270):
 * - Container `#0B0F0D` com divisor superior `1.dp` `#18221E`.
 * - Altura útil de conteúdo de `56.dp` (+ `navigationBarsPadding()`), evitando excesso vertical.
 * - Pílula ativa `56x28.dp` (`cornerRadius = 14.dp`, `#10291F`), ícone `17.sp` e rótulo `11.sp` (`#34D399` ativo / `#8E9E96` inativo).
 * - Alvo de toque mínimo garantido `>= 48x48.dp` por aba.
 */
@Composable
fun GeckoBottomNavigationBar(
    currentRoute: String?,
    featureFlags: ReleaseFeatureFlags,
    onNavigateToDestination: (GeckoTopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = GeckoRoutes.topLevelDestinations(featureFlags)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gecko_bottom_nav_bar"),
        color = GeckoBottomNavBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                thickness = 1.dp,
                color = GeckoBottomNavBorder
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .selectableGroup()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    val isSelected = currentRoute == destination.route
                    val contentColor = if (isSelected) {
                        GeckoBottomNavActiveContent
                    } else {
                        GeckoBottomNavInactiveContent
                    }
                    val pillColor = if (isSelected) {
                        GeckoBottomNavActivePill
                    } else {
                        Color.Transparent
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onNavigateToDestination(destination) }
                            .semantics(mergeDescendants = true) {
                                this.role = Role.Tab
                                this.selected = isSelected
                                this.contentDescription = destination.label
                            }
                            .testTag(destination.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 28.dp)
                                .background(
                                    color = pillColor,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = destination.iconGlyph,
                                fontSize = if (isSelected) 18.sp else 17.sp,
                                lineHeight = 20.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                        Text(
                            text = destination.label,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
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
