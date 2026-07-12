package net.whiteravens.dietapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens ported from the web design system (apps/web globals.css,
 * default emerald palette). Values are the oklch tokens converted to sRGB —
 * regenerate with a colour converter if the web tokens change.
 */

// Light (:root)
val LightBackground = Color(0xFFFBFCF9)
val LightForeground = Color(0xFF121B13)
val LightCard = Color(0xFFFFFFFF)
val LightMuted = Color(0xFFEEF4ED)
val LightMutedForeground = Color(0xFF616C63)
val LightBorder = Color(0xFFDEE3DD)
val LightPrimary = Color(0xFF0FA05C)
val LightPrimaryForeground = Color(0xFFF7FEF8)
val LightAccent = Color(0xFF00B7B7)
val LightDestructive = Color(0xFFD73431)

// Dark (.dark)
val DarkBackground = Color(0xFF0C140F)
val DarkForeground = Color(0xFFEAF0EB)
val DarkCard = Color(0xFF121E17)
val DarkMuted = Color(0xFF1E2A23)
val DarkMutedForeground = Color(0xFF909C92)
val DarkBorder = Color(0xFF28342D)
val DarkPrimary = Color(0xFF3BB974)
val DarkPrimaryForeground = Color(0xFF0A140F)
val DarkAccent = Color(0xFF25C2C2)
val DarkDestructive = Color(0xFFED4B43)

/** Third macro-chart colour (web: oklch(0.75 0.13 80)); protein/fat use primary/accent. */
val MacroCarbs = Color(0xFFD9A440)
