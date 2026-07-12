package net.whiteravens.dietapp.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.screens.auth.AuthScreen
import net.whiteravens.dietapp.ui.screens.dashboard.DashboardScreen
import net.whiteravens.dietapp.ui.screens.plans.MealPlansScreen
import net.whiteravens.dietapp.ui.screens.plans.PlanDetailScreen
import net.whiteravens.dietapp.ui.screens.profile.ProfileScreen
import net.whiteravens.dietapp.ui.screens.recipes.RecipeDetailScreen
import net.whiteravens.dietapp.ui.screens.recipes.RecipesScreen
import net.whiteravens.dietapp.ui.screens.shopping.ShoppingScreen
import javax.inject.Inject
import kotlin.reflect.KClass

// ── Routes ───────────────────────────────────────────────────────────────────

@Serializable object DashboardRoute
@Serializable object PlansRoute
@Serializable data class PlanDetailRoute(val planId: String)
@Serializable object RecipesRoute
@Serializable data class RecipeDetailRoute(val recipeId: String)
@Serializable object ShoppingRoute
@Serializable object ProfileRoute

/** Signed-in state for the root switch; null while the token store loads. */
@HiltViewModel
class RootViewModel @Inject constructor(auth: AuthRepository) : ViewModel() {
    val isSignedIn: StateFlow<Boolean?> = auth.isSignedIn
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Root switch: auth screen for signed-out, tabbed shell for signed-in. The
 * token store drives it reactively, so sign-in, token expiry and sign-out all
 * land on the right flow without explicit navigation.
 */
@Composable
fun DietAppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    when (signedIn) {
        null -> Box(Modifier.fillMaxSize()) // token store still loading; next frame decides
        false -> AuthScreen()
        true -> MainScaffold()
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
    TopLevelDestination(ProfileRoute, ProfileRoute::class, Icons.Default.Person, R.string.nav_profile),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTopLevel = topLevelDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
            composable<DashboardRoute> { DashboardScreen() }
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
            composable<ProfileRoute> { ProfileScreen() }
        }
    }
}
