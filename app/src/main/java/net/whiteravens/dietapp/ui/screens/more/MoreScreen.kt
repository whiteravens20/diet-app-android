// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.more

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.rememberWebPages
import net.whiteravens.dietapp.ui.navigation.AboutRoute
import net.whiteravens.dietapp.ui.navigation.AccountRoute
import net.whiteravens.dietapp.ui.navigation.FavoritesRoute
import net.whiteravens.dietapp.ui.navigation.PantryRoute
import net.whiteravens.dietapp.ui.navigation.ProfilesRoute

/** A place of the app the bottom bar has no room for, as the "More" tab lists it. */
data class MoreEntry(val route: Any, val icon: ImageVector, @StringRes val label: Int)

/** The places of the app beyond the four of the bottom bar, as the web application's menu has them. */
val moreEntries: List<MoreEntry>
    get() = listOf(
        MoreEntry(ProfilesRoute, Icons.Default.Person, R.string.profiles_title),
        MoreEntry(FavoritesRoute, Icons.Default.Star, R.string.favorites_title),
        MoreEntry(PantryRoute, AppIcons.Inventory, R.string.pantry_title),
        MoreEntry(AccountRoute, Icons.Default.Settings, R.string.account_title),
    )

/**
 * What the bottom bar's "More" opens: the rest of the app, a tap each, the calculator, which is the
 * web application's and opens in the browser, and last what the app says of the instance and of
 * itself.
 */
@Composable
fun MoreScreen(onOpen: (Any) -> Unit) {
    val web = rememberWebPages()
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.nav_more))
        AppCard {
            moreEntries.forEach { entry ->
                Entry(entry.icon, stringResource(entry.label)) { onOpen(entry.route) }
            }
            Entry(AppIcons.Target, stringResource(R.string.calculator_title), stringResource(R.string.web_opens_in_the_browser)) {
                web.open("calculator")
            }
            Entry(Icons.Default.Info, stringResource(R.string.about_title)) { onOpen(AboutRoute) }
        }
    }
}

/** A place in the list: its icon and name, and where it opens elsewhere than in the app, where that is. */
@Composable
private fun Entry(icon: ImageVector, label: String, elsewhere: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = elsewhere?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}
