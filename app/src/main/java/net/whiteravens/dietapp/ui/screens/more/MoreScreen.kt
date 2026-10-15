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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.navigation.AccountRoute
import net.whiteravens.dietapp.ui.navigation.ProfilesRoute

/** A place of the app the bottom bar has no room for, as the "More" tab lists it. */
data class MoreEntry(val route: Any, val icon: ImageVector, @StringRes val label: Int)

/** The places of the app beyond the four of the bottom bar, as the web application's menu has them. */
val moreEntries: List<MoreEntry>
    get() = listOf(
        MoreEntry(ProfilesRoute, Icons.Default.Person, R.string.profiles_title),
        MoreEntry(AccountRoute, Icons.Default.Settings, R.string.account_title),
    )

/** What the bottom bar's "More" opens: the rest of the app, a tap each. */
@Composable
fun MoreScreen(onOpen: (Any) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.nav_more))
        AppCard {
            moreEntries.forEach { entry ->
                ListItem(
                    headlineContent = { Text(stringResource(entry.label)) },
                    leadingContent = { Icon(entry.icon, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(role = Role.Button) { onOpen(entry.route) },
                )
            }
        }
    }
}
