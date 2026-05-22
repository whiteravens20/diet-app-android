package net.whiteravens.dietapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import net.whiteravens.dietapp.ui.screens.DashboardScreen
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host. Navigation is Compose-based (see ui/). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DietAppTheme {
                Surface {
                    // Phase 3: replace with a NavHost across the screen set.
                    DashboardScreen()
                }
            }
        }
    }
}
