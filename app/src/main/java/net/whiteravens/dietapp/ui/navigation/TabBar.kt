// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import net.whiteravens.dietapp.ui.components.Text

/** A tab of the bar at the bottom of the screen: its picture and its name. */
internal data class Tab(val icon: ImageVector, val label: String)

/**
 * The bar of the tabs at the bottom of the screen, the one at [selected] chosen. Its labels share one size and keep
 * their words whole: the size the phone's font setting gives them, unless a label would not fit its tab at it, as
 * with a large font on a narrow phone; then the largest size at which every label fits.
 */
@Composable
internal fun TabBar(tabs: List<Tab>, selected: Int, onPick: (Int) -> Unit) {
    BoxWithConstraints {
        val sides = NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val direction = LocalLayoutDirection.current
        val room = maxWidth - sides.calculateLeftPadding(direction) - sides.calculateRightPadding(direction)
        val style = tabLabelStyle(tabs.map { it.label }, (room - TAB_GAP * (tabs.size - 1)) / tabs.size)
        NavigationBar {
            tabs.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = index == selected,
                    onClick = { onPick(index) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    // One line: a second one would lift this item's icon above the others'.
                    label = { Text(tab.label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    // The chosen tab's label in the primary colour, which is held to AA on the bar; the default, the
                    // secondary colour, is the web's accent, which is not text.
                    colors = NavigationBarItemDefaults.colors(selectedTextColor = MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** How far apart Material's bar sets its tabs. */
private val TAB_GAP = 8.dp

/** How much smaller a label gets at a time while the labels do not fit. */
private const val STEP = 0.5f

/**
 * The style of [labels] in tabs of [width]: the bar's own, at the size the phone's font setting gives it, while every
 * label fits a tab whole; otherwise the largest size at which they all do. It never goes below the size the labels
 * have at the phone's default font: past that, a label is cut short rather than made smaller than ordinary text.
 */
@Composable
private fun tabLabelStyle(labels: List<String>, width: Dp): TextStyle {
    val base = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, width, base, density) {
        val room = with(density) { width.toPx() }
        val full = base.fontSize.value
        val least = full / maxOf(1f, density.fontScale)
        fun sized(size: Float): TextStyle {
            val ratio = size / full
            return base.copy(
                fontSize = size.sp,
                lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * ratio else base.lineHeight,
                letterSpacing = if (base.letterSpacing.isSpecified) base.letterSpacing * ratio else base.letterSpacing,
            )
        }
        fun fits(style: TextStyle) = labels.all { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width <= room }
        var size = full
        while (size - STEP >= least && !fits(sized(size))) size -= STEP
        if (size == full) base else sized(size)
    }
}
