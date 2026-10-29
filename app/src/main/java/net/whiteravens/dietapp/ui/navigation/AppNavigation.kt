// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.navigation.toRoute
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
import net.whiteravens.dietapp.data.auth.BrowserCodes
import net.whiteravens.dietapp.data.config.LocalNetworkAccess
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.config.webAppAddress
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.ServerVersion
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.ChangesViewModel
import net.whiteravens.dietapp.ui.components.ConfirmEmailNotice
import net.whiteravens.dietapp.ui.components.LocalWaitingChanges
import net.whiteravens.dietapp.ui.components.RefusedChangesNotice
import net.whiteravens.dietapp.ui.components.ConfirmEmailViewModel
import net.whiteravens.dietapp.ui.components.LocalBrowserCode
import net.whiteravens.dietapp.ui.components.LocalNetworkDialog
import net.whiteravens.dietapp.ui.components.LocalWebApp
import net.whiteravens.dietapp.ui.components.ServerVersionNotice
import net.whiteravens.dietapp.ui.screens.about.AboutScreen
import net.whiteravens.dietapp.ui.screens.about.LibrariesScreen
import net.whiteravens.dietapp.ui.screens.account.AccountScreen
import net.whiteravens.dietapp.ui.screens.auth.AuthScreen
import net.whiteravens.dietapp.ui.screens.dashboard.DashboardScreen
import net.whiteravens.dietapp.ui.screens.favorites.FavoritesScreen
import net.whiteravens.dietapp.ui.screens.more.MoreScreen
import net.whiteravens.dietapp.ui.screens.notifications.NotificationsScreen
import net.whiteravens.dietapp.ui.screens.pantry.PantryScreen
import net.whiteravens.dietapp.ui.screens.plans.MealPlansScreen
import net.whiteravens.dietapp.ui.screens.plans.NewPlanScreen
import net.whiteravens.dietapp.ui.screens.plans.PlanDetailScreen
import net.whiteravens.dietapp.ui.screens.profiles.ProfileFormScreen
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesScreen
import net.whiteravens.dietapp.ui.screens.recipes.RecipeDetailScreen
import net.whiteravens.dietapp.ui.screens.recipes.RecipeFormScreen
import net.whiteravens.dietapp.ui.screens.recipes.RecipesScreen
import net.whiteravens.dietapp.ui.screens.shopping.ShoppingScreen
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import kotlin.reflect.KClass

// ── Routes ───────────────────────────────────────────────────────────────────

@Serializable object DashboardRoute
@Serializable object PlansRoute
@Serializable data class PlanDetailRoute(val planId: String, val day: String? = null)
@Serializable object RecipesRoute
@Serializable data class RecipeDetailRoute(val recipeId: String)
@Serializable data class RecipeFormRoute(val recipeId: String? = null)
@Serializable object ShoppingRoute
@Serializable object MoreRoute
@Serializable object AccountRoute
@Serializable object AboutRoute
@Serializable object LibrariesRoute
@Serializable object NotificationsRoute
@Serializable object ProfilesRoute
@Serializable data class ProfileFormRoute(val profileId: String? = null)
@Serializable object FavoritesRoute
@Serializable object PantryRoute
@Serializable data class NewPlanRoute(val profileId: String, val startDate: String? = null)

/** The profile whose weights the dashboard brings into view, which a weight reminder opened leaves it. */
private const val SHOW_WEIGHT_OF = "showWeightOf"

/**
 * Signed-in state for the root switch (null while the token store loads), the version of the
 * server, asked when the app starts and again whenever the server's address changes, and the
 * address of its web application. At the start, a session signed out of offline is revoked on
 * the server if it can be now.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    auth: AuthRepository,
    private val serverConfig: ServerConfigStore,
    private val server: ServerRepository,
    private val localNetwork: LocalNetworkAccess,
    browserCodes: BrowserCodes,
) : ViewModel() {
    val isSignedIn: StateFlow<Boolean?> = auth.isSignedIn
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val serverVersion: StateFlow<ServerVersion> = server.version

    /** Where the server's web application is, which the app opens for what it leaves to it; null before a server is chosen. */
    val webApp: StateFlow<String?> = serverConfig.baseUrl
        .map(::webAppAddress)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A code that signs the browser in to the account, for the pages of the web application the app opens. */
    val browserCode: () -> String? = browserCodes::next

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
fun DietAppRoot(
    openNotifications: Boolean = false,
    onNotificationsOpened: () -> Unit = {},
    viewModel: RootViewModel = hiltViewModel(),
) {
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val serverVersion by viewModel.serverVersion.collectAsStateWithLifecycle()
    val localNetworkMissing by viewModel.localNetworkMissing.collectAsStateWithLifecycle()
    val webApp by viewModel.webApp.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.checkLocalNetwork()
        onPauseOrDispose {}
    }
    if (localNetworkMissing) LocalNetworkDialog(onAnswered = viewModel::checkLocalNetwork)
    CompositionLocalProvider(LocalWebApp provides webApp, LocalBrowserCode provides viewModel.browserCode) {
        when (signedIn) {
            null -> Box(Modifier.fillMaxSize()) // token store still loading; next frame decides
            false -> AuthScreen(serverVersion = serverVersion)
            true -> MainScaffold(serverVersion, openNotifications, onNotificationsOpened)
        }
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
    TopLevelDestination(DashboardRoute, DashboardRoute::class, Icons.Default.Home, R.string.tab_dashboard),
    TopLevelDestination(PlansRoute, PlansRoute::class, Icons.Default.DateRange, R.string.tab_meal_plans),
    TopLevelDestination(RecipesRoute, RecipesRoute::class, AppIcons.Restaurant, R.string.nav_recipes),
    TopLevelDestination(ShoppingRoute, ShoppingRoute::class, Icons.Default.ShoppingCart, R.string.nav_shopping),
    TopLevelDestination(MoreRoute, MoreRoute::class, Icons.Default.Menu, R.string.nav_more),
)

/**
 * The tabs and what opens from them. With [openNotifications], a tap on a phone's notification
 * asked for the list of notifications: it is opened, and [onNotificationsOpened] says so.
 */
@Composable
private fun MainScaffold(
    serverVersion: ServerVersion,
    openNotifications: Boolean,
    onNotificationsOpened: () -> Unit,
    confirmEmail: ConfirmEmailViewModel = hiltViewModel(),
    changes: ChangesViewModel = hiltViewModel(),
) {
    val confirmation by confirmEmail.state.collectAsStateWithLifecycle()
    val waiting by changes.waiting.collectAsStateWithLifecycle()
    val refused by changes.refused.collectAsStateWithLifecycle()
    // Read again whenever the app comes to the front: an address confirmed in the browser takes the notice away.
    LifecycleResumeEffect(confirmEmail) {
        confirmEmail.load()
        onPauseOrDispose {}
    }
    val navController = rememberNavController()
    LaunchedEffect(openNotifications) {
        if (openNotifications) {
            navController.navigate(NotificationsRoute) { launchSingleTop = true }
            onNotificationsOpened()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTopLevel = topLevelDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                ServerVersionNotice(serverVersion, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                ConfirmEmailNotice(confirmation, onResend = confirmEmail::resend, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                RefusedChangesNotice(refused, onSeen = changes::dismiss, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
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
                            // One line: a second one would lift this item's icon above the others'.
                            label = { Text(stringResource(dest.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            // The chosen tab's label in the primary colour, which is held to AA on the bar; the
                            // default, the secondary colour, is the web's accent, which is not text.
                            colors = NavigationBarItemDefaults.colors(selectedTextColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Each screen's bar for when it is offline says how many changes made on the phone wait to be sent.
        CompositionLocalProvider(LocalWaitingChanges provides waiting) {
            NavHost(
                navController = navController,
                startDestination = DashboardRoute,
                // An open keyboard shortens every screen, so that what is being typed stays above it.
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            ) {
                composable<DashboardRoute> { entry ->
                    val weightOf by entry.savedStateHandle.getStateFlow<String?>(SHOW_WEIGHT_OF, null).collectAsStateWithLifecycle()
                    DashboardScreen(
                        onCreateProfile = { navController.navigate(ProfilesRoute) },
                        onOpenPlan = { planId -> navController.navigate(PlanDetailRoute(planId)) },
                        onNewPlan = { profileId -> navController.navigate(NewPlanRoute(profileId)) },
                        onOpenNotifications = { navController.navigate(NotificationsRoute) },
                        showWeightOf = weightOf,
                        onWeightShown = { entry.savedStateHandle[SHOW_WEIGHT_OF] = null },
                    )
                }
                composable<PlansRoute> {
                    MealPlansScreen(
                        onOpenPlan = { planId, day -> navController.navigate(PlanDetailRoute(planId, day)) },
                        onNewPlan = { profileId, startDate -> navController.navigate(NewPlanRoute(profileId, startDate)) },
                        onCreateProfile = { navController.navigate(ProfilesRoute) },
                    )
                }
                composable<NewPlanRoute> {
                    NewPlanScreen(
                        onBack = { navController.popBackStack() },
                        // The plan made opens in place of the form.
                        onMade = { planId ->
                            navController.navigate(PlanDetailRoute(planId)) { popUpTo<NewPlanRoute> { inclusive = true } }
                        },
                    )
                }
                composable<PlanDetailRoute> {
                    PlanDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenRecipe = { navController.navigate(RecipeDetailRoute(it)) },
                        onTurnAiOn = { navController.navigate(AccountRoute) },
                    )
                }
                composable<RecipesRoute> {
                    RecipesScreen(
                        onOpenRecipe = { navController.navigate(RecipeDetailRoute(it)) },
                        onTurnAiOn = { navController.navigate(AccountRoute) },
                        onWrite = { navController.navigate(RecipeFormRoute()) },
                    )
                }
                composable<RecipeDetailRoute> {
                    RecipeDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(RecipeFormRoute(it)) },
                    )
                }
                composable<RecipeFormRoute> { entry ->
                    val edited = entry.toRoute<RecipeFormRoute>().recipeId
                    RecipeFormScreen(
                        onBack = { navController.popBackStack() },
                        // The recipe saved opens in place of the form; an edit's old version is gone, and its view with it.
                        onSaved = { recipeId ->
                            navController.navigate(RecipeDetailRoute(recipeId)) {
                                if (edited == null) popUpTo<RecipeFormRoute> { inclusive = true } else popUpTo<RecipeDetailRoute> { inclusive = true }
                            }
                        },
                        onOpenRecipe = { navController.navigate(RecipeDetailRoute(it)) },
                    )
                }
                composable<ShoppingRoute> {
                    ShoppingScreen(
                        onOpenPlan = { planId, day -> navController.navigate(PlanDetailRoute(planId, day)) },
                        onTurnAiOn = { navController.navigate(AccountRoute) },
                    )
                }
                composable<MoreRoute> { MoreScreen(onOpen = { navController.navigate(it) }) }
                composable<AccountRoute> { AccountScreen(onBack = { navController.popBackStack() }) }
                composable<AboutRoute> {
                    AboutScreen(onBack = { navController.popBackStack() }, onLibraries = { navController.navigate(LibrariesRoute) })
                }
                composable<LibrariesRoute> { LibrariesScreen(onBack = { navController.popBackStack() }) }
                composable<NotificationsRoute> {
                    NotificationsScreen(
                        onBack = { navController.popBackStack() },
                        onWeight = { profileId ->
                            navController.getBackStackEntry<DashboardRoute>().savedStateHandle[SHOW_WEIGHT_OF] = profileId
                            navController.popBackStack<DashboardRoute>(inclusive = false)
                        },
                        onPreferences = { navController.navigate(ProfilesRoute) { popUpTo<NotificationsRoute> { inclusive = true } } },
                    )
                }
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
                composable<FavoritesRoute> {
                    FavoritesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenRecipe = { navController.navigate(RecipeDetailRoute(it)) },
                        onRecipes = {
                            navController.navigate(RecipesRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onProfiles = { navController.navigate(ProfilesRoute) },
                    )
                }
                composable<PantryRoute> {
                    PantryScreen(onBack = { navController.popBackStack() }, onProfiles = { navController.navigate(ProfilesRoute) })
                }
            }
        }
    }
}
