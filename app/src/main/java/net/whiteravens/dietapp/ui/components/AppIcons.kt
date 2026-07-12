package net.whiteravens.dietapp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material Symbols the app needs that are missing from material-icons-core
 * (the extended artifact is ~10 MB of dex — not worth four glyphs). Path data
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
                "11.99 2zm.01 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z" +
                "m.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
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
