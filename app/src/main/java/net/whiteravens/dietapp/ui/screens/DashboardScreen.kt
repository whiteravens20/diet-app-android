package net.whiteravens.dietapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.ui.theme.DietAppTheme

/**
 * Dashboard — placeholder screen. Phase 3 builds out the full screen set
 * (dashboard, profile, meal plans, recipes, shopping list) backed by Hilt
 * ViewModels over the offline-first repositories.
 */
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Diet App", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Companion app — Phase 3 scaffold. Offline-first; consumes the same API " +
                "as the web platform.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    DietAppTheme { DashboardScreen() }
}
