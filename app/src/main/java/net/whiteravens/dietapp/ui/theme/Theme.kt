package net.whiteravens.dietapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 theme mirroring the web design system. Token → role mapping:
 * card → surfaceContainer*, muted → surfaceVariant, border → outline(Variant),
 * accent → secondary, the amber macro colour → tertiary. Compose components
 * then pick the web colours up through their default token roles (e.g. the
 * NavigationBar indicator uses secondaryContainer = the primary/10 tint the
 * web sidebar uses for the active item).
 */

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightPrimaryForeground,
    primaryContainer = Color(0xFFD9F0E3),
    onPrimaryContainer = Color(0xFF07502D),
    secondary = LightAccent,
    onSecondary = Color(0xFFF7FEFE),
    secondaryContainer = Color(0xFFDCF2E7),
    onSecondaryContainer = Color(0xFF0A5F38),
    tertiary = MacroCarbs,
    onTertiary = Color(0xFF3D2E00),
    background = LightBackground,
    onBackground = LightForeground,
    surface = LightBackground,
    onSurface = LightForeground,
    surfaceVariant = LightMuted,
    onSurfaceVariant = LightMutedForeground,
    surfaceContainerLowest = LightCard,
    surfaceContainerLow = LightCard,
    surfaceContainer = LightCard,
    surfaceContainerHigh = LightMuted,
    surfaceContainerHighest = LightMuted,
    outline = LightMutedForeground,
    outlineVariant = LightBorder,
    error = LightDestructive,
    onError = Color.White,
    errorContainer = Color(0xFFFCE4E3),
    onErrorContainer = Color(0xFF7A1613),
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkPrimaryForeground,
    primaryContainer = Color(0xFF1B4230),
    onPrimaryContainer = Color(0xFFA9E8C6),
    secondary = DarkAccent,
    onSecondary = Color(0xFF042A2A),
    secondaryContainer = Color(0xFF1D3B2C),
    onSecondaryContainer = Color(0xFFA9E8C6),
    tertiary = MacroCarbs,
    onTertiary = Color(0xFF3D2E00),
    background = DarkBackground,
    onBackground = DarkForeground,
    surface = DarkBackground,
    onSurface = DarkForeground,
    surfaceVariant = DarkMuted,
    onSurfaceVariant = DarkMutedForeground,
    surfaceContainerLowest = DarkCard,
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkMuted,
    surfaceContainerHighest = DarkMuted,
    outline = DarkMutedForeground,
    outlineVariant = DarkBorder,
    error = DarkDestructive,
    onError = Color(0xFF340705),
    errorContainer = Color(0xFF5C1512),
    onErrorContainer = Color(0xFFFFD9D6),
)

/** Web radii: --radius 0.85rem (lg), −0.25rem (md), −0.4rem (sm). */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Defaults with the web's tighter, semibold headings. */
private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** App-wide Material 3 theme. */
@Composable
fun DietAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
