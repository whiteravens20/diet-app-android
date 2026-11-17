// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.SplitLimits
import net.whiteravens.dietapp.domain.gramsAt
import net.whiteravens.dietapp.domain.insideLimits
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.numberProblem
import net.whiteravens.dietapp.ui.common.refusalText
import net.whiteravens.dietapp.ui.components.Text
import kotlin.math.roundToInt

/**
 * The custom diet's targets, as the profile's form and the form of a plan ask for them: the way they are set, the
 * split in per cent or in grams, or protein per kilogram with fat's share, and what they come to at [target] kcal and
 * [weightKg], said while they are typed. A field says what is wrong with it: no number ([notNumbers]), or the API's
 * refusal ([refusals]); the split's refusal as a whole is said under the split.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OwnTargetsFields(
    own: OwnTargetsTyped,
    target: Int?,
    weightKg: Double?,
    notNumbers: Set<ProfileField>,
    refusals: Map<ProfileField, FieldRefusal>,
    onEdit: ((OwnTargetsTyped) -> OwnTargetsTyped) -> Unit,
) {
    val marks = OwnMarks(notNumbers, refusals)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.profile_custom_split), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.profile_custom_split_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.profile_custom_way), style = MaterialTheme.typography.labelLarge)
        val ways = OwnWay.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ways.forEachIndexed { index, way ->
                SegmentedButton(
                    selected = own.way == way,
                    onClick = { onEdit { it.inWay(way) } },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ways.size),
                ) {
                    Text(stringResource(if (way == OwnWay.SPLIT) R.string.profile_custom_way_split else R.string.profile_custom_way_per_kg))
                }
            }
        }
        when (own.way) {
            OwnWay.SPLIT -> {
                Text(stringResource(R.string.profile_custom_unit), style = MaterialTheme.typography.labelLarge)
                val units = SplitUnit.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    units.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = own.unit == unit,
                            onClick = { onEdit { it.inUnit(unit, target) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = units.size),
                        ) {
                            Text(stringResource(if (unit == SplitUnit.PERCENT) R.string.profile_custom_unit_percent else R.string.profile_custom_unit_grams))
                        }
                    }
                }
                when (own.unit) {
                    SplitUnit.PERCENT -> PercentFields(own, marks, onEdit)
                    SplitUnit.GRAMS -> GramsFields(own, target, marks, onEdit)
                }
                // Refused as a whole, the split is said to have to add up to a hundred.
                refusals[ProfileField.SPLIT]?.let { refused ->
                    Text(
                        refusalText(refused, other = stringResource(R.string.refusal_split_sum)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            OwnWay.PER_KG -> PerKgFields(own, target, weightKg, marks, onEdit)
        }
    }
}

/** What the fields of the targets say is wrong with them: no number, or the API's refusal. */
private class OwnMarks(val notNumbers: Set<ProfileField>, val refusals: Map<ProfileField, FieldRefusal>)

/** What [field] says is wrong with it; a refusal of no bound says [other]. Protein per kilogram and grams take fractions. */
@Composable
private fun OwnMarks.of(field: ProfileField, other: String = stringResource(R.string.refusal_other)): String? = when {
    field in notNumbers -> numberProblem(true, null, whole = field != ProfileField.PER_KG && field !in GRAMS)
    else -> refusals[field]?.let { refusalText(it, other = other) }
}

/** The fields of the grams. */
private val GRAMS = setOf(ProfileField.GRAMS_PROTEIN, ProfileField.GRAMS_FAT, ProfileField.GRAMS_CARBS)

@Composable
private fun PercentFields(
    own: OwnTargetsTyped,
    marks: OwnMarks,
    onEdit: ((OwnTargetsTyped) -> OwnTargetsTyped) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField(own.percent.protein, { typed -> onEdit { it.copy(percent = it.percent.copy(protein = typed)) } }, stringResource(R.string.profile_split_protein), Modifier.weight(1f), problem = marks.of(ProfileField.SPLIT_PROTEIN))
        FormField(own.percent.fat, { typed -> onEdit { it.copy(percent = it.percent.copy(fat = typed)) } }, stringResource(R.string.profile_split_fat), Modifier.weight(1f), problem = marks.of(ProfileField.SPLIT_FAT))
        FormField(own.percent.carbs, { typed -> onEdit { it.copy(percent = it.percent.copy(carbs = typed)) } }, stringResource(R.string.profile_split_carbs), Modifier.weight(1f), problem = marks.of(ProfileField.SPLIT_CARBS))
    }
    Text(
        stringResource(R.string.profile_split_sum, own.percent.sum),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun GramsFields(
    own: OwnTargetsTyped,
    target: Int?,
    marks: OwnMarks,
    onEdit: ((OwnTargetsTyped) -> OwnTargetsTyped) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField(own.grams.protein, { typed -> onEdit { it.copy(grams = it.grams.copy(protein = typed)) } }, stringResource(R.string.profile_split_grams_protein), Modifier.weight(1f), problem = marks.of(ProfileField.GRAMS_PROTEIN), keyboardType = KeyboardType.Decimal)
        FormField(own.grams.fat, { typed -> onEdit { it.copy(grams = it.grams.copy(fat = typed)) } }, stringResource(R.string.profile_split_grams_fat), Modifier.weight(1f), problem = marks.of(ProfileField.GRAMS_FAT), keyboardType = KeyboardType.Decimal)
        FormField(own.grams.carbs, { typed -> onEdit { it.copy(grams = it.grams.copy(carbs = typed)) } }, stringResource(R.string.profile_split_grams_carbs), Modifier.weight(1f), problem = marks.of(ProfileField.GRAMS_CARBS), keyboardType = KeyboardType.Decimal)
    }
    val shares = own.ofGrams()
    val kcal = own.kcalOfGrams()
    if (shares != null && kcal != null) {
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(
                stringResource(R.string.profile_grams_shares, formatNumber(kcal.roundToInt()), shares.protein, shares.fat, shares.carbs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                !insideLimits(shares) -> Text(
                    stringResource(
                        R.string.profile_split_out_of_limits,
                        SplitLimits.PROTEIN.first, SplitLimits.PROTEIN.last,
                        SplitLimits.FAT.first, SplitLimits.FAT.last,
                        SplitLimits.CARBS.first, SplitLimits.CARBS.last,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                target != null && target > 0 -> {
                    val at = gramsAt(shares, target)
                    Text(
                        stringResource(R.string.profile_grams_at_target, formatNumber(target), formatNumber(at.protein), formatNumber(at.fat), formatNumber(at.carbs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PerKgFields(
    own: OwnTargetsTyped,
    target: Int?,
    weightKg: Double?,
    marks: OwnMarks,
    onEdit: ((OwnTargetsTyped) -> OwnTargetsTyped) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField(
            own.perKg,
            { typed -> onEdit { it.copy(perKg = typed) } },
            stringResource(R.string.profile_per_kg_protein),
            Modifier.weight(1f),
            problem = marks.of(ProfileField.PER_KG, stringResource(R.string.refusal_per_kg)),
            keyboardType = KeyboardType.Decimal,
        )
        FormField(
            own.perKgFat,
            { typed -> onEdit { it.copy(perKgFat = typed) } },
            stringResource(R.string.profile_per_kg_fat),
            Modifier.weight(1f),
            problem = marks.of(ProfileField.SPLIT_FAT),
        )
    }
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        val at = own.perKgAt(target, weightKg)
        if (at == null || target == null) {
            Text(
                stringResource(R.string.profile_per_kg_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.profile_per_kg_line, formatNumber(at.protein.roundToInt()), at.split.protein, formatNumber(target), at.split.carbs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (at.held) {
                Text(
                    at.fits?.let { stringResource(R.string.profile_per_kg_fits, formatNumber(it.start, 1), formatNumber(it.endInclusive, 1)) }
                        ?: stringResource(R.string.profile_per_kg_no_fit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
