// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import net.whiteravens.dietapp.domain.Palette
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** A colour as the web's tokens write it: OKLCH lightness, chroma, and hue in degrees. */
internal data class Oklch(val lightness: Double, val chroma: Double, val hue: Double) {
    /** The colour in sRGB, each channel cut to its range, as the web's test of its tokens makes it. */
    val color: Color
        get() {
            val h = Math.toRadians(hue)
            val a = chroma * cos(h)
            val b = chroma * sin(h)
            val l = cube(lightness + 0.3963377774 * a + 0.2158037573 * b)
            val m = cube(lightness - 0.1055613458 * a - 0.0638541728 * b)
            val s = cube(lightness - 0.0894841775 * a - 1.2914855480 * b)
            return Color(
                red = encoded(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
                green = encoded(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
                blue = encoded(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
            )
        }

    private fun cube(x: Double) = x * x * x

    private fun encoded(linear: Double): Float {
        val x = linear.coerceIn(0.0, 1.0)
        return (if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055).toFloat()
    }
}

/** One scheme in one mode, by the names of the web's tokens (apps/web globals.css). */
internal data class SchemeTokens(
    val background: Oklch,
    val foreground: Oklch,
    val card: Oklch,
    val muted: Oklch,
    val mutedForeground: Oklch,
    val border: Oklch,
    val primary: Oklch,
    val primaryForeground: Oklch,
    val accent: Oklch,
    val destructive: Oklch,
    val destructiveForeground: Oklch,
    val warning: Oklch,
)

private val defaultLight = SchemeTokens(
    background = Oklch(0.99, 0.005, 120.0),
    foreground = Oklch(0.21, 0.02, 150.0),
    card = Oklch(1.0, 0.0, 0.0),
    muted = Oklch(0.96, 0.01, 140.0),
    mutedForeground = Oklch(0.52, 0.02, 150.0),
    border = Oklch(0.91, 0.01, 140.0),
    primary = Oklch(0.515, 0.15, 155.0),
    primaryForeground = Oklch(0.99, 0.01, 150.0),
    accent = Oklch(0.7, 0.13, 195.0),
    destructive = Oklch(0.495, 0.2, 27.0),
    destructiveForeground = Oklch(0.99, 0.01, 27.0),
    warning = Oklch(0.49, 0.12, 70.0),
)

private val defaultDark = SchemeTokens(
    background = Oklch(0.18, 0.015, 160.0),
    foreground = Oklch(0.95, 0.01, 150.0),
    card = Oklch(0.22, 0.02, 160.0),
    muted = Oklch(0.27, 0.02, 160.0),
    mutedForeground = Oklch(0.68, 0.02, 150.0),
    border = Oklch(0.31, 0.02, 160.0),
    primary = Oklch(0.7, 0.15, 155.0),
    primaryForeground = Oklch(0.18, 0.02, 160.0),
    accent = Oklch(0.74, 0.12, 195.0),
    destructive = Oklch(0.78, 0.14, 27.0),
    destructiveForeground = Oklch(0.18, 0.02, 27.0),
    warning = Oklch(0.8, 0.13, 80.0),
)

/** The tokens of [palette] in light or [dark] mode: the scheme's own, over the default scheme's where it has none, as on the web. */
internal fun tokensOf(palette: Palette, dark: Boolean): SchemeTokens = when (palette) {
    Palette.DEFAULT -> if (dark) defaultDark else defaultLight
    Palette.OCEAN -> if (dark) {
        defaultDark.copy(
            background = Oklch(0.19, 0.02, 240.0),
            card = Oklch(0.23, 0.02, 240.0),
            muted = Oklch(0.28, 0.02, 240.0),
            mutedForeground = Oklch(0.7, 0.02, 240.0),
            border = Oklch(0.32, 0.02, 240.0),
            primary = Oklch(0.7, 0.15, 240.0),
            primaryForeground = Oklch(0.18, 0.02, 240.0),
            accent = Oklch(0.74, 0.12, 200.0),
        )
    } else {
        defaultLight.copy(
            background = Oklch(0.99, 0.01, 240.0),
            muted = Oklch(0.96, 0.015, 240.0),
            border = Oklch(0.91, 0.015, 240.0),
            primary = Oklch(0.525, 0.15, 240.0),
            primaryForeground = Oklch(0.99, 0.01, 240.0),
            accent = Oklch(0.72, 0.12, 200.0),
        )
    }
    Palette.FOREST -> if (dark) {
        defaultDark.copy(
            background = Oklch(0.17, 0.02, 145.0),
            card = Oklch(0.21, 0.02, 145.0),
            muted = Oklch(0.26, 0.02, 145.0),
            mutedForeground = Oklch(0.67, 0.02, 145.0),
            border = Oklch(0.3, 0.02, 145.0),
            primary = Oklch(0.66, 0.14, 145.0),
            primaryForeground = Oklch(0.17, 0.02, 145.0),
            accent = Oklch(0.7, 0.13, 95.0),
        )
    } else {
        defaultLight.copy(
            background = Oklch(0.98, 0.01, 140.0),
            muted = Oklch(0.95, 0.02, 140.0),
            border = Oklch(0.89, 0.02, 140.0),
            primary = Oklch(0.51, 0.13, 145.0),
            primaryForeground = Oklch(0.99, 0.01, 140.0),
            accent = Oklch(0.65, 0.13, 95.0),
        )
    }
    Palette.SUNSET -> if (dark) {
        defaultDark.copy(
            background = Oklch(0.19, 0.02, 50.0),
            card = Oklch(0.23, 0.02, 50.0),
            muted = Oklch(0.28, 0.02, 50.0),
            mutedForeground = Oklch(0.7, 0.02, 50.0),
            border = Oklch(0.33, 0.02, 50.0),
            primary = Oklch(0.74, 0.15, 55.0),
            primaryForeground = Oklch(0.19, 0.02, 50.0),
            accent = Oklch(0.76, 0.14, 25.0),
        )
    } else {
        defaultLight.copy(
            background = Oklch(0.99, 0.01, 65.0),
            muted = Oklch(0.96, 0.02, 60.0),
            border = Oklch(0.91, 0.02, 60.0),
            primary = Oklch(0.55, 0.16, 50.0),
            primaryForeground = Oklch(0.99, 0.01, 60.0),
            accent = Oklch(0.7, 0.15, 25.0),
        )
    }
    Palette.MONO -> if (dark) {
        defaultDark.copy(
            background = Oklch(0.17, 0.0, 0.0),
            foreground = Oklch(0.95, 0.0, 0.0),
            card = Oklch(0.21, 0.0, 0.0),
            muted = Oklch(0.27, 0.0, 0.0),
            mutedForeground = Oklch(0.68, 0.0, 0.0),
            border = Oklch(0.31, 0.0, 0.0),
            primary = Oklch(0.85, 0.0, 0.0),
            primaryForeground = Oklch(0.17, 0.0, 0.0),
            accent = Oklch(0.65, 0.0, 0.0),
        )
    } else {
        defaultLight.copy(
            background = Oklch(0.99, 0.0, 0.0),
            foreground = Oklch(0.2, 0.0, 0.0),
            card = Oklch(1.0, 0.0, 0.0),
            muted = Oklch(0.96, 0.0, 0.0),
            mutedForeground = Oklch(0.5, 0.0, 0.0),
            border = Oklch(0.9, 0.0, 0.0),
            primary = Oklch(0.3, 0.0, 0.0),
            primaryForeground = Oklch(0.99, 0.0, 0.0),
            accent = Oklch(0.55, 0.0, 0.0),
        )
    }
    Palette.ROSE -> if (dark) {
        defaultDark.copy(
            background = Oklch(0.19, 0.015, 10.0),
            card = Oklch(0.23, 0.02, 10.0),
            muted = Oklch(0.28, 0.02, 10.0),
            mutedForeground = Oklch(0.7, 0.02, 10.0),
            border = Oklch(0.32, 0.02, 10.0),
            primary = Oklch(0.72, 0.16, 5.0),
            primaryForeground = Oklch(0.19, 0.015, 10.0),
            accent = Oklch(0.76, 0.13, 330.0),
        )
    } else {
        defaultLight.copy(
            background = Oklch(0.99, 0.005, 10.0),
            muted = Oklch(0.96, 0.015, 10.0),
            border = Oklch(0.91, 0.015, 10.0),
            primary = Oklch(0.55, 0.17, 5.0),
            primaryForeground = Oklch(0.99, 0.01, 10.0),
            accent = Oklch(0.72, 0.13, 330.0),
        )
    }
}

/**
 * The Material colour scheme of [palette] in light or [dark] mode. Token to role: card →
 * surfaceContainer*, muted → surfaceVariant, border → outlineVariant, accent → secondary, warning →
 * tertiary, which the app writes its notes in. A notice is the web's: the warning's colour on a tenth
 * of it over the page (tertiaryContainer). The other containers, which the web has no tokens for, are
 * tints of the scheme's primary at the lightness and share of its chroma the default scheme's were
 * picked at, so that every scheme draws them alike.
 */
fun colorSchemeOf(palette: Palette, dark: Boolean): ColorScheme {
    val tokens = tokensOf(palette, dark)
    val primary = tokens.primary
    fun tint(lightness: Double, share: Double) = Oklch(lightness, primary.chroma * share, primary.hue).color
    fun accentTint(lightness: Double, share: Double) = Oklch(lightness, tokens.accent.chroma * share, tokens.accent.hue).color
    val notice = tokens.warning.color.copy(alpha = 0.1f).compositeOver(tokens.background.color)
    return if (dark) {
        darkColorScheme(
            primary = primary.color,
            onPrimary = tokens.primaryForeground.color,
            primaryContainer = tint(0.345, 0.37),
            onPrimaryContainer = tint(0.879, 0.53),
            secondary = tokens.accent.color,
            onSecondary = accentTint(0.26, 0.34),
            secondaryContainer = tint(0.324, 0.3),
            onSecondaryContainer = tint(0.879, 0.53),
            tertiary = tokens.warning.color,
            onTertiary = tokens.background.color,
            tertiaryContainer = notice,
            onTertiaryContainer = tokens.warning.color,
            background = tokens.background.color,
            onBackground = tokens.foreground.color,
            surface = tokens.background.color,
            onSurface = tokens.foreground.color,
            surfaceVariant = tokens.muted.color,
            onSurfaceVariant = tokens.mutedForeground.color,
            surfaceContainerLowest = tokens.card.color,
            surfaceContainerLow = tokens.card.color,
            surfaceContainer = tokens.card.color,
            surfaceContainerHigh = tokens.muted.color,
            surfaceContainerHighest = tokens.muted.color,
            outline = tokens.mutedForeground.color,
            outlineVariant = tokens.border.color,
            error = tokens.destructive.color,
            onError = tokens.destructiveForeground.color,
            errorContainer = Color(0xFF5C1512),
            onErrorContainer = Color(0xFFFFD9D6),
        )
    } else {
        lightColorScheme(
            primary = primary.color,
            onPrimary = tokens.primaryForeground.color,
            primaryContainer = tint(0.935, 0.2),
            onPrimaryContainer = tint(0.381, 0.6),
            secondary = tokens.accent.color,
            onSecondary = accentTint(0.992, 0.06),
            secondaryContainer = tint(0.942, 0.18),
            onSecondaryContainer = tint(0.429, 0.66),
            tertiary = tokens.warning.color,
            onTertiary = tokens.background.color,
            tertiaryContainer = notice,
            onTertiaryContainer = tokens.warning.color,
            background = tokens.background.color,
            onBackground = tokens.foreground.color,
            surface = tokens.background.color,
            onSurface = tokens.foreground.color,
            surfaceVariant = tokens.muted.color,
            onSurfaceVariant = tokens.mutedForeground.color,
            surfaceContainerLowest = tokens.card.color,
            surfaceContainerLow = tokens.card.color,
            surfaceContainer = tokens.card.color,
            surfaceContainerHigh = tokens.muted.color,
            surfaceContainerHighest = tokens.muted.color,
            outline = tokens.mutedForeground.color,
            outlineVariant = tokens.border.color,
            error = tokens.destructive.color,
            onError = tokens.destructiveForeground.color,
            errorContainer = Color(0xFFFCE4E3),
            onErrorContainer = Color(0xFF7A1613),
        )
    }
}
