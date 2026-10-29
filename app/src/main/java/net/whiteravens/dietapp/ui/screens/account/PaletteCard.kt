// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Palette
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.theme.colorSchemeOf

/**
 * The colour scheme the application is drawn in: the account's, which the web application sets,
 * until another is chosen here, a choice that stays on this phone. Light or dark follows the phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteCard(state: AppearanceState, onChoose: (Palette?) -> Unit) {
    AppCard {
        Text(
            stringResource(R.string.appearance_palette),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            PaletteChip(
                stringResource(R.string.appearance_palette_account, paletteName(state.account)),
                state.account,
                selected = state.chosen == null,
                onClick = { onChoose(null) },
            )
            Palette.entries.forEach { palette ->
                PaletteChip(paletteName(palette), palette, selected = state.chosen == palette, onClick = { onChoose(palette) })
            }
        }
        Text(
            stringResource(R.string.appearance_palette_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A choice of [palette], with a dot of its primary colour as the phone's mode draws it. */
@Composable
private fun PaletteChip(label: String, palette: Palette, selected: Boolean, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val dot = remember(palette, dark) { colorSchemeOf(palette, dark).primary }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(dot)) },
    )
}

@Composable
private fun paletteName(palette: Palette): String = stringResource(
    when (palette) {
        Palette.DEFAULT -> R.string.appearance_palette_default
        Palette.OCEAN -> R.string.appearance_palette_ocean
        Palette.FOREST -> R.string.appearance_palette_forest
        Palette.SUNSET -> R.string.appearance_palette_sunset
        Palette.MONO -> R.string.appearance_palette_mono
        Palette.ROSE -> R.string.appearance_palette_rose
    },
)
