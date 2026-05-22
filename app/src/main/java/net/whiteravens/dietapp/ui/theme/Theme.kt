package net.whiteravens.dietapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mirrors the web design system: an emerald-forward Material 3 palette.
private val Emerald = Color(0xFF2F9E6F)
private val EmeraldDark = Color(0xFF6FD3A3)

private val LightColors = lightColorScheme(primary = Emerald)
private val DarkColors = darkColorScheme(primary = EmeraldDark)

/** App-wide Material 3 theme. */
@Composable
fun DietAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
