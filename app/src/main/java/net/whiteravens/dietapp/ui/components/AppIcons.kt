// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material Symbols the app needs that are missing from material-icons-core
 * (the extended artifact is ~10 MB of dex — not worth six glyphs). Path data
 * is the standard 24dp Material icon set; fill colour is irrelevant, Icon()
 * tints vectors via LocalContentColor.
 */
object AppIcons {
    /** `restaurant` — fork & knife; recipes and meals. */
    val Restaurant: ImageVector by lazy {
        icon(
            "Restaurant",
            "M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 " +
                "13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z",
        )
    }

    /** `whatshot` — flame; maintenance calories. */
    val Flame: ImageVector by lazy {
        icon(
            "Flame",
            "M13.5.67s.74 2.65.74 4.8c0 2.06-1.35 3.73-3.41 3.73-2.07 0-3.63-1.67-3.63-3.73" +
                "l.03-.36C5.21 7.51 4 10.62 4 14c0 4.42 3.58 8 8 8s8-3.58 8-8C20 8.61 17.41 3.8 " +
                "13.5.67zM11.71 19c-1.78 0-3.22-1.4-3.22-3.14 0-1.62 1.05-2.76 2.81-3.12 " +
                "1.77-.36 3.6-1.21 4.62-2.58.39 1.29.59 2.65.59 4.04 0 2.65-2.15 4.8-4.8 4.8z",
        )
    }

    /** Bullseye — the daily calorie target. */
    val Target: ImageVector by lazy {
        icon(
            "Target",
            "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20z" +
                "M12 4a8 8 0 1 0 0 16 8 8 0 1 0 0-16z" +
                "M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6z",
            fillType = PathFillType.EvenOdd,
        )
    }

    /** `schedule` — clock; prep/cook time. */
    val Schedule: ImageVector by lazy {
        icon(
            "Schedule",
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 " +
                "11.99 2zm.01 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 " +
                "3.15.75-1.23-4.5-2.67z",
        )
    }

    /** `star_border` — a star's outline; an ingredient not marked as a favourite. */
    val StarOutline: ImageVector by lazy {
        icon(
            "StarOutline",
            "M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 " +
                "2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z",
        )
    }

    /** `block` — a circle struck through; an ingredient to avoid. */
    val Block: ImageVector by lazy {
        icon(
            "Block",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM4 12c0-4.42 " +
                "3.58-8 8-8 1.85 0 3.55.63 4.9 1.69L5.69 16.9C4.63 15.55 4 13.85 4 12zm8 8c-1.85 " +
                "0-3.55-.63-4.9-1.69L18.31 7.1C19.37 8.45 20 10.15 20 12c0 4.42-3.58 8-8 8z",
        )
    }

    /** `undo` — an arrow turning back; the last weight taken back. */
    val Undo: ImageVector by lazy {
        icon(
            "Undo",
            "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 " +
                "7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z",
        )
    }

    /** `inventory_2` — a box with its lid; the pantry. */
    val Inventory: ImageVector by lazy {
        icon(
            "Inventory",
            "M20 2H4C3 2 2 2.9 2 4v3.01C2 7.73 2.43 8.35 3 8.7V20c0 1.1 1.1 2 2 2h14c0.9 0 2-0.9 2-2V8.7c0.57-0.35 " +
                "1-0.97 1-1.69V4C22 2.9 21 2 20 2zM15 14H9v-2h6V14zM20 7H4V4h16V7z",
        )
    }

    /** `auto_awesome` — three sparks; AI, as the web's sparkles mark it. */
    val Sparkles: ImageVector by lazy {
        icon(
            "Sparkles",
            "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 " +
                "2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z",
        )
    }

    private fun icon(name: String, pathData: String, fillType: PathFillType = PathFillType.NonZero): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            pathFillType = fillType,
            fill = SolidColor(Color.Black),
        ).build()
}
