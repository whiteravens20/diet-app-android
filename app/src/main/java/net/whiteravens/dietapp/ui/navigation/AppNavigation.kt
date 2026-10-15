// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.config.LocalNetworkAccess
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.ServerVersion
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.LocalNetworkDialog
import net.whiteravens.dietapp.ui.components.ServerVersionNotice
import net.whiteravens.dietapp.ui.screens.account.AccountScreen
import net.whiteravens.dietapp.ui.screens.auth.AuthScreen
import net.whiteravens.dietapp.ui.screens.dashboard.DashboardScreen
import net.whiteravens.dietapp.ui.screens.more.MoreScreen
import net.whiteravens.dietapp.ui.screens.plans.MealPlansScreen
import net.whiteravens.dietapp.ui.screens.plans.PlanDetailScreen
import net.whiteravens.dietapp.ui.screens.profiles.CalculatorScreen
import net.whiteravens.dietapp.ui.screens.profiles.ProfileFormScreen
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesScreen
import net.whiteravens.dietapp.ui.screens.recipes.RecipeDetailScreen
import net.whiteravens.dietapp.ui.screens.recipes.RecipesScreen
import net.whiteravens.dietapp.ui.screens.shopping.ShoppingScreen
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import kotlin.reflect.KClass

// ── Routes ───────────────────────────────────────────────────────────────────

@Serializable object DashboardRoute
@Serializable object PlansRoute
@Serializable data class PlanDetailRoute(val planId: String)
@Serializable object RecipesRoute
@Serializable data class RecipeDetailRoute(val recipeId: String)
@Serializable object ShoppingRoute
@Serializable object MoreRoute
@Serializable object AccountRoute
@Serializable object ProfilesRoute
@Serializable data class ProfileFormRoute(val profileId: String? = null)
@Serializable object CalculatorRoute

/**
 * Signed-in state for the root switch (null while the token store loads), and the version
 * of the server, asked when the app starts and again whenever the server's address changes.
 * At the start, a session signed out of offline is revoked on the server if it can be now.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    auth: AuthRepository,
    private val serverConfig: ServerConfigStore,
    private val server: ServerRepository,
    private val localNetwork: LocalNetworkAccess,
) : ViewModel() {
    val isSignedIn: StateFlow<Boolean?> = auth.isSignedIn
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val serverVersion: StateFlow<ServerVersion> = server.version

    private val _localNetworkMissing = MutableStateFlow(false)

    /** The server is on the local network, which the app may not reach yet (Android 17). */
    val localNetworkMissing: StateFlow<Boolean> = _localNetworkMissing.asStateFlow()

    init {
        viewModelScope.launch {
            serverConfig.baseUrl.distinctUntilChanged().collectLatest {
                checkLocalNetwork()
                server.refresh()
            }
        }
        viewModelScope.launch { auth.retryRevocation() }
    }

    /** Asked again when the app comes to the front: the permission may have been given in the settings since. */
    fun checkLocalNetwork() {
        viewModelScope.launch {
            val host = serverConfig.current().toHttpUrlOrNull()?.host
            _localNetworkMissing.value = host != null && !localNetwork.allowed() && localNetwork.isLocal(host)
        }
    }
}

/**
 * Root switch: auth screen for signed-out, tabbed shell for signed-in. The
 * token store drives it reactively, so sign-in, token expiry and sign-out all
 * land on the right flow without explicit navigation.
 */
@Composable
fun DietAppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val serverVersion by viewModel.serverVersion.collectAsStateWithLifecycle()
    val localNetworkMissing by viewModel.localNetworkMissing.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.checkLocalNetwork()
        onPauseOrDispose {}
    }
    if (localNetworkMissing) LocalNetworkDialog(onAnswered = viewModel::checkLocalNetwork)
    when (signedIn) {
        null -> Box(Modifier.fillMaxSize()) // token store still loading; next frame decides
        false -> AuthScreen(serverVersion = serverVersion)
        true -> MainScaffold(serverVersion)
    }
}

// ── Signed-in shell ──────────────────────────────────────────────────────────

private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    @StringRes val label: Int,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(DashboardRoute, DashboardRoute::class, Icons.Default.Home, R.string.nav_dashboard),
    TopLevelDestination(PlansRoute, PlansRoute::class, Icons.Default.DateRange, R.string.nav_meal_plans),
    TopLevelDestination(RecipesRoute, RecipesRoute::class, AppIcons.Restaurant, R.string.nav_recipes),
    TopLevelDestination(ShoppingRoute, ShoppingRoute::class, Icons.Default.ShoppingCart, R.string.nav_shopping),
    TopLevelDestination(MoreRoute, MoreRoute::class, Icons.Default.Menu, R.string.nav_more),
)

@Composable
private fun MainScaffold(serverVersion: ServerVersion) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTopLevel = topLevelDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ServerVersionNotice(
                serverVersion,
                Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        bottomBar = {
            if (onTopLevel) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.hasRoute(dest.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<DashboardRoute> { DashboardScreen(onCreateProfile = { navController.navigate(ProfilesRoute) }) }
            composable<PlansRoute> {
                MealPlansScreen(onOpenPlan = { navController.navigate(PlanDetailRoute(it)) })
            }
            composable<PlanDetailRoute> {
                PlanDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { navController.navigate(RecipeDetailRoute(it)) },
                )
            }
            composable<RecipesRoute> {
                RecipesScreen(onOpenRecipe = { navController.navigate(RecipeDetailRoute(it)) })
            }
            composable<RecipeDetailRoute> {
                RecipeDetailScreen(onBack = { navController.popBackStack() })
            }
            composable<ShoppingRoute> { ShoppingScreen() }
            composable<MoreRoute> { MoreScreen(onOpen = { navController.navigate(it) }) }
            composable<AccountRoute> { AccountScreen(onBack = { navController.popBackStack() }) }
            composable<ProfilesRoute> {
                ProfilesScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(ProfileFormRoute()) },
                    onEdit = { navController.navigate(ProfileFormRoute(it)) },
                    onFirstPlan = {
                        navController.navigate(PlansRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<ProfileFormRoute> {
                ProfileFormScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
            }
            composable<CalculatorRoute> {
                CalculatorScreen(onBack = { navController.popBackStack() }, onProfiles = { navController.navigate(ProfilesRoute) })
            }
        }
    }
}
