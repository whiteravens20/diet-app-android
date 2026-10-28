// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.CaloriePreview
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.SupplyLevel
import net.whiteravens.dietapp.domain.notes
import net.whiteravens.dietapp.ui.common.Bound
import net.whiteravens.dietapp.ui.common.activityLevelLabel
import net.whiteravens.dietapp.ui.common.allergenLabel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.noteText
import net.whiteravens.dietapp.ui.common.numberProblem
import net.whiteravens.dietapp.ui.common.proteinKindLabel
import net.whiteravens.dietapp.ui.common.refusalText
import net.whiteravens.dietapp.ui.common.reminderCadenceLabel
import net.whiteravens.dietapp.ui.common.sexLabel
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.rememberWebPages

/*
 * The fields a profile is made of, as every form that asks for them shows them: the form of a
 * profile, the first profile's steps and the calculator. Each shows the form as it is and hands a
 * change of it to `onEdit`; a field says what is wrong with it as `marks` say.
 */

/** How a field hands on a change of the form. */
internal typealias FormEdit = ((ProfileForm) -> ProfileForm) -> Unit

/**
 * What the fields of a profile's form say is wrong with them: what holds no number where one is
 * needed ([notNumbers], and by kind of protein [kindNotNumbers]), once going on or saving was
 * refused for it; and what the API refused ([refusals]), with its reasons.
 */
data class ProfileMarks(
    val notNumbers: Set<ProfileField> = emptySet(),
    val kindNotNumbers: Map<String, Set<KindPart>> = emptyMap(),
    val refusals: ProfileRefusals = ProfileRefusals(),
) {
    operator fun contains(field: ProfileField): Boolean = field in notNumbers || field in refusals.fields
}

/** What [field] says is wrong with it: no number where one is needed, or the API's refusal with its bound said as [bound]. */
@Composable
private fun ProfileMarks.of(field: ProfileField, bound: Bound = Bound.Number(), whole: Boolean = true): String? =
    numberProblem(field in notNumbers, refusals.fields[field], bound, whole)

/** A field typed into, with its hint, or what is wrong with it once that is said. */
@Composable
internal fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    problem: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = (problem ?: hint)?.let { { Text(it) } },
        isError = problem != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = ImeAction.Next),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A choice among a few, as the web's select is: a field that opens its options, where [enabled];
 * [description] says what it chooses where the label alone does not, as the web's `aria-label` does.
 */
@Composable
internal fun <T> ChoiceField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    description: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it && enabled }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = hint?.let { { Text(it) } },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth()
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** What the profile is called. */
@Composable
internal fun NameField(form: ProfileForm, marks: ProfileMarks, onEdit: FormEdit) {
    FormField(
        value = form.name,
        onValueChange = { typed -> onEdit { it.copy(name = typed) } },
        label = stringResource(R.string.profile_name),
        hint = stringResource(R.string.profile_name_hint),
        problem = marks.of(ProfileField.NAME, Bound.Characters),
        keyboardType = KeyboardType.Text,
        capitalization = KeyboardCapitalization.Words,
    )
}

/**
 * The body a calorie target is worked out from: age, sex, height, weight and how much it moves.
 * The weight is typed in the account's unit.
 */
@Composable
internal fun BodyFields(form: ProfileForm, unit: WeightUnit, marks: ProfileMarks, onEdit: FormEdit) {
    FormField(
        value = form.age,
        onValueChange = { typed -> onEdit { it.copy(age = typed) } },
        label = stringResource(R.string.profile_age),
        problem = marks.of(ProfileField.AGE),
    )
    ChoiceField(
        label = stringResource(R.string.profile_sex),
        options = listOf(null, "male", "female"),
        selected = form.sex,
        optionLabel = { sexLabel(it) },
        onSelect = { chosen -> onEdit { it.copy(sex = chosen) } },
        hint = stringResource(R.string.profile_sex_hint),
    )
    FormField(
        value = form.height,
        onValueChange = { typed -> onEdit { it.copy(height = typed) } },
        label = stringResource(R.string.profile_height_cm),
        problem = marks.of(ProfileField.HEIGHT, Bound.Number(decimals = 1), whole = false),
        keyboardType = KeyboardType.Decimal,
    )
    FormField(
        value = form.weight,
        onValueChange = { typed -> onEdit { it.copy(weight = typed) } },
        label = stringResource(R.string.profile_weight, unit.code),
        problem = marks.of(ProfileField.WEIGHT, unit.refusalBound, whole = false),
        keyboardType = KeyboardType.Decimal,
    )
    ChoiceField(
        label = stringResource(R.string.profile_activity_level),
        options = ProfileChoices.ACTIVITY_LEVELS,
        selected = form.activityLevel,
        optionLabel = { activityLevelLabel(it) },
        onSelect = { chosen -> onEdit { it.copy(activityLevel = chosen) } },
        hint = activityHint(form.activityLevel),
    )
}

/** What an activity level means, under the field that chooses it. */
@Composable
private fun activityHint(level: String): String? = when (level) {
    "sedentary" -> stringResource(R.string.profile_activity_hint_sedentary)
    "light" -> stringResource(R.string.profile_activity_hint_light)
    "moderate" -> stringResource(R.string.profile_activity_hint_moderate)
    "active" -> stringResource(R.string.profile_activity_hint_active)
    "very_active" -> stringResource(R.string.profile_activity_hint_very_active)
    else -> null
}

/** A pace that a limit slowed: more than a quarter of maintenance, or under the floor. */
private fun CalorieTarget.slowed(): Boolean = deficitCapped || safetyFloorApplied || maintenanceUnderFloor

/**
 * The pace of loss, chosen among all of them, each saying what it comes to in a day for the body
 * the form describes ([paces], in the order of the choices, once they are known). A pace that a
 * limit slowed, or that is held until a risk is acknowledged, says so on its row.
 */
@Composable
internal fun PaceChoice(form: ProfileForm, unit: WeightUnit, paces: List<CalorieTarget>?, onEdit: FormEdit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.profile_pace), style = MaterialTheme.typography.titleSmall)
        ProfileChoices.PACES.forEachIndexed { index, pace ->
            val at = paces?.getOrNull(index)
            val chosen = pace == form.weeklyLossTarget
            Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                color = if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = chosen, role = Role.RadioButton, onClick = { onEdit { it.copy(weeklyLossTarget = pace) } }),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = chosen, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(paceLabel(pace, unit), style = MaterialTheme.typography.bodyMedium)
                        if (at != null) {
                            val mark = when {
                                pace != null && at.goalHeld -> " · " + stringResource(R.string.profile_pace_held)
                                pace != null && at.slowed() -> " · " + stringResource(R.string.profile_pace_slowed)
                                else -> ""
                            }
                            Text(
                                stringResource(R.string.profile_pace_comes_to, formatNumber(at.dailyTarget)) + mark,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        val anySlowed = paces?.withIndex()?.any { (index, at) -> ProfileChoices.PACES.getOrNull(index) != null && !at.goalHeld && at.slowed() } == true
        if (anySlowed) {
            Text(
                stringResource(R.string.profile_pace_slowed_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A pace by name: keeping the weight, or losing an amount a week. A quarter of a kilogram is said
 * to the hundredth; in pounds, where no pace is a round number, to the tenth.
 */
@Composable
private fun paceLabel(pace: String?, unit: WeightUnit): String {
    if (pace == null) return stringResource(R.string.profile_pace_none)
    val kg = pace.toDouble()
    val amount = if (unit == WeightUnit.LB) "${formatNumber(unit.shown(kg), 1)} lb" else "${formatNumber(kg, 2)} kg"
    return stringResource(R.string.profile_pace_lose, amount)
}

/** The weight the profile works towards, in the account's unit; the chart of weights draws it. */
@Composable
internal fun TargetWeightField(form: ProfileForm, unit: WeightUnit, marks: ProfileMarks, onEdit: FormEdit) {
    FormField(
        value = form.targetWeight,
        onValueChange = { typed -> onEdit { it.copy(targetWeight = typed) } },
        label = stringResource(R.string.profile_target_weight, unit.code),
        hint = stringResource(R.string.profile_target_weight_hint),
        problem = marks.of(ProfileField.TARGET_WEIGHT, unit.refusalBound, whole = false),
        keyboardType = KeyboardType.Decimal,
    )
}

/** A calorie target of one's own, in place of the one the pace comes to. */
@Composable
internal fun OwnTargetField(form: ProfileForm, marks: ProfileMarks, onEdit: FormEdit) {
    FormField(
        value = form.ownTarget,
        onValueChange = { typed -> onEdit { it.copy(ownTarget = typed) } },
        label = stringResource(R.string.profile_own_target),
        hint = stringResource(R.string.profile_own_target_hint),
        placeholder = stringResource(R.string.profile_auto),
        problem = marks.of(ProfileField.OWN_TARGET),
    )
}

/** A box tinted as the web's warnings are, for what is to be heeded. */
@Composable
internal fun WarningBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/**
 * Why losing weight is a risk for the body the form describes, and the box by which somebody says
 * they understand it. Until it is ticked the goal is kept and plans follow maintenance. A form that
 * keeps nothing ([kept] false) says what the box does to the figures it shows, and no more.
 */
@Composable
internal fun RiskBox(reasons: List<String>, acknowledged: Boolean, onAcknowledge: (Boolean) -> Unit, kept: Boolean = true) {
    if (reasons.isEmpty()) return
    WarningBox {
        reasons.forEach { reason ->
            Text(
                when (reason) {
                    "minor" -> stringResource(R.string.profile_risk_minor)
                    else -> stringResource(R.string.profile_risk_underweight)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(if (kept) R.string.profile_risk_held else R.string.profile_risk_held_here),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().toggleable(value = acknowledged, role = Role.Checkbox, onValueChange = onAcknowledge),
        ) {
            Checkbox(checked = acknowledged, onCheckedChange = null)
            Text(
                stringResource(if (kept) R.string.profile_risk_acknowledge else R.string.profile_risk_acknowledge_here),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * The diet, and with [meals] the number of meals a day. With the meals, each diet is marked when
 * the catalogue has no recipe, or few, for one of that many meals, and the chosen pair gets a line
 * saying what that means for a plan. The custom diet asks for its split: how much of a day's
 * energy comes from protein, fat and carbohydrate.
 */
@Composable
internal fun DietFields(form: ProfileForm, marks: ProfileMarks, rules: InstanceRules?, onEdit: FormEdit, meals: Boolean = true) {
    ChoiceField(
        label = stringResource(R.string.profile_diet_type),
        options = ProfileChoices.DIETS,
        selected = form.dietType,
        optionLabel = { diet ->
            val name = dietTypeLabel(diet)
            when (if (meals) rules?.supplyOf(diet, form.mealCount)?.level else null) {
                SupplyLevel.NONE -> stringResource(R.string.catalogue_diet_none, name)
                SupplyLevel.THIN -> stringResource(R.string.catalogue_diet_thin, name)
                else -> name
            }
        },
        onSelect = { chosen -> onEdit { it.copy(dietType = chosen) } },
    )
    if (meals) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.profile_meals_per_day), style = MaterialTheme.typography.titleSmall)
            val counts = ProfileChoices.MEAL_COUNT.toList()
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                counts.forEachIndexed { index, count ->
                    SegmentedButton(
                        selected = form.mealCount == count,
                        onClick = { onEdit { it.copy(mealCount = count) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = counts.size),
                    ) { Text(count.toString()) }
                }
            }
            SupplyNote(rules, form.dietType, form.mealCount)
        }
    }
    if (form.dietType == "custom") SplitFields(form, marks, onEdit)
}

/**
 * A warning when the catalogue has no recipe, or too few, for one of the meals of a diet at a
 * number of meals a day; nothing when it has enough or has not said. It speaks of the catalogue
 * only: a recipe of the account's own can fill a gap, and the planner counts it.
 */
@Composable
internal fun SupplyNote(rules: InstanceRules?, diet: String, mealCount: Int) {
    val found = rules?.supplyOf(diet, mealCount) ?: return
    val name = dietTypeLabel(diet)
    val text = when (found.level) {
        SupplyLevel.ENOUGH -> return
        SupplyLevel.NONE -> found.fewerMeals?.let { fewer -> stringResource(R.string.catalogue_none_fewer, name, mealCount, fewer) }
            ?: stringResource(R.string.catalogue_none, name, mealCount)
        SupplyLevel.THIN -> pluralStringResource(R.plurals.catalogue_thin, found.recipes, found.recipes, name, mealCount)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
}

/** The three shares of the custom diet, and what they add up to, said while they are typed, with the API's refusal of the three together. */
@Composable
private fun SplitFields(form: ProfileForm, marks: ProfileMarks, onEdit: FormEdit) {
    val split = form.split
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.profile_custom_split), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.profile_custom_split_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareField(split.protein, R.string.profile_split_protein, marks.of(ProfileField.SPLIT_PROTEIN), Modifier.weight(1f)) { typed ->
                onEdit { it.copy(split = it.split.copy(protein = typed)) }
            }
            ShareField(split.fat, R.string.profile_split_fat, marks.of(ProfileField.SPLIT_FAT), Modifier.weight(1f)) { typed ->
                onEdit { it.copy(split = it.split.copy(fat = typed)) }
            }
            ShareField(split.carbs, R.string.profile_split_carbs, marks.of(ProfileField.SPLIT_CARBS), Modifier.weight(1f)) { typed ->
                onEdit { it.copy(split = it.split.copy(carbs = typed)) }
            }
        }
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(R.string.profile_split_sum, split.sum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Refused as a whole, the three are said to have to add up to a hundred.
            marks.refusals.fields[ProfileField.SPLIT]?.let { refused ->
                Text(
                    refusalText(refused, other = stringResource(R.string.refusal_split_sum)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** One share of the custom diet's split, with what is wrong with it. */
@Composable
private fun ShareField(value: String, label: Int, problem: String?, modifier: Modifier, onValueChange: (String) -> Unit) {
    FormField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(label),
        problem = problem,
        modifier = modifier,
    )
}

/**
 * Allergens, each a chip to tick, under [title] and [hint]: by default those of a profile, which a
 * recipe planned for it never holds.
 */
@Composable
internal fun AllergenChoice(
    selected: Collection<String>,
    onChange: (List<String>) -> Unit,
    enabled: Boolean = true,
    title: String = stringResource(R.string.profile_allergens),
    hint: String = stringResource(R.string.profile_allergens_hint),
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileChoices.ALLERGENS.forEach { allergen ->
                val on = allergen in selected
                FilterChip(
                    selected = on,
                    onClick = { onChange(ProfileChoices.ALLERGENS.filter { if (it == allergen) !on else it in selected }) },
                    label = { Text(allergenLabel(allergen)) },
                    enabled = enabled,
                )
            }
        }
    }
}

/** How often the app nudges to log a weight. */
@Composable
internal fun ReminderField(form: ProfileForm, onEdit: FormEdit) {
    ChoiceField(
        label = stringResource(R.string.profile_reminder),
        options = ProfileChoices.REMINDER_CADENCES,
        selected = form.weightReminderCadence,
        optionLabel = { reminderCadenceLabel(it) },
        onSelect = { chosen -> onEdit { it.copy(weightReminderCadence = chosen) } },
        hint = stringResource(R.string.profile_reminder_hint),
    )
}

/** How often a plan of the profile may repeat itself. */
@Composable
internal fun LimitFields(form: ProfileForm, marks: ProfileMarks, onEdit: FormEdit) {
    val limits = form.limits
    FormField(
        value = limits.consecutiveDays,
        onValueChange = { typed -> onEdit { it.copy(limits = it.limits.copy(consecutiveDays = typed)) } },
        label = stringResource(R.string.profile_max_consecutive_days),
        hint = stringResource(R.string.profile_max_consecutive_days_hint),
        problem = marks.of(ProfileField.CONSECUTIVE_DAYS),
    )
    FormField(
        value = limits.timesPerWeek,
        onValueChange = { typed -> onEdit { it.copy(limits = it.limits.copy(timesPerWeek = typed)) } },
        label = stringResource(R.string.profile_max_times_per_week),
        hint = stringResource(R.string.profile_max_times_per_week_hint),
        problem = marks.of(ProfileField.TIMES_PER_WEEK),
    )
    FormField(
        value = limits.kindPerDay,
        onValueChange = { typed -> onEdit { it.copy(limits = it.limits.copy(kindPerDay = typed)) } },
        label = stringResource(R.string.profile_max_meals_per_day_same_kind),
        placeholder = stringResource(R.string.profile_no_limit),
        problem = marks.of(ProfileField.KIND_PER_DAY),
    )
    FormField(
        value = limits.kindPerWeek,
        onValueChange = { typed -> onEdit { it.copy(limits = it.limits.copy(kindPerWeek = typed)) } },
        label = stringResource(R.string.profile_max_meals_per_week_same_kind),
        placeholder = stringResource(R.string.profile_no_limit),
        problem = marks.of(ProfileField.KIND_PER_WEEK),
    )
    Text(
        stringResource(R.string.profile_same_kind_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.profile_same_kind_blocks_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    KindLimitRows(limits.kindLimits, marks, onEdit)
    FormField(
        value = limits.pantryCoolDown,
        onValueChange = { typed -> onEdit { it.copy(limits = it.limits.copy(pantryCoolDown = typed)) } },
        label = stringResource(R.string.profile_pantry_cool_down),
        hint = stringResource(R.string.profile_pantry_cool_down_hint),
        problem = marks.of(ProfileField.PANTRY_COOL_DOWN),
    )
}

/**
 * The kinds of protein held to numbers of their own: a row for each, with the kind, a number for a
 * day and one for seven days, empty for the profile's own, and a button for another row while a
 * kind is left. A kind another row has is not offered again.
 */
@Composable
private fun KindLimitRows(rows: List<KindLimitTyped>, marks: ProfileMarks, onEdit: FormEdit) {
    val change = { index: Int, edit: (KindLimitTyped) -> KindLimitTyped ->
        onEdit { form -> form.copy(limits = form.limits.copy(kindLimits = form.limits.kindLimits.mapIndexed { at, row -> if (at == index) edit(row) else row })) }
    }
    Text(stringResource(R.string.profile_kind_limits), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.profile_kind_limits_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rows.forEachIndexed { index, row ->
        Surface(
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChoiceField(
                    label = stringResource(R.string.profile_kind_limit_kind),
                    options = ProfileChoices.PROTEIN_KINDS.filter { kind -> kind == row.kind || rows.none { it.kind == kind } },
                    selected = row.kind,
                    optionLabel = { proteinKindLabel(it) },
                    onSelect = { kind -> change(index) { it.copy(kind = kind) } },
                )
                FormField(
                    value = row.perDay,
                    onValueChange = { typed -> change(index) { it.copy(perDay = typed) } },
                    label = stringResource(R.string.profile_kind_limit_per_day),
                    placeholder = stringResource(R.string.profile_same_as_above),
                    problem = numberProblem(KindPart.DAY in marks.kindNotNumbers[row.kind].orEmpty(), marks.refusals.kinds[row.kind]?.get(KindPart.DAY)),
                )
                FormField(
                    value = row.perWeek,
                    onValueChange = { typed -> change(index) { it.copy(perWeek = typed) } },
                    label = stringResource(R.string.profile_kind_limit_per_week),
                    placeholder = stringResource(R.string.profile_same_as_above),
                    problem = numberProblem(KindPart.WEEK in marks.kindNotNumbers[row.kind].orEmpty(), marks.refusals.kinds[row.kind]?.get(KindPart.WEEK)),
                )
                val removing = stringResource(R.string.profile_remove_kind_limit, proteinKindLabel(row.kind))
                TextButton(
                    onClick = { onEdit { form -> form.copy(limits = form.limits.copy(kindLimits = form.limits.kindLimits.filterIndexed { at, _ -> at != index })) } },
                    modifier = Modifier.semantics { contentDescription = removing },
                ) { Text(stringResource(R.string.profile_remove_kind_limit_short)) }
            }
        }
    }
    if (rows.size < ProfileChoices.PROTEIN_KINDS.size) {
        OutlinedButton(
            onClick = {
                onEdit { form ->
                    val kept = form.limits.kindLimits
                    val kind = ProfileChoices.PROTEIN_KINDS.first { kind -> kept.none { it.kind == kind } }
                    form.copy(limits = form.limits.copy(kindLimits = kept + KindLimitTyped(kind)))
                }
            },
            shape = MaterialTheme.shapes.medium,
        ) { Text(stringResource(R.string.profile_add_kind_limit)) }
    }
}

/**
 * What the form's figures come to: the target of a day, maintenance, what the body uses at rest,
 * the grams of protein, fat and carbohydrate, and what stands behind a target that is not simply
 * maintenance less the pace, and that the figures are no medical advice, with the instance's terms
 * that say more. The warnings for somebody under 18 or underweight are the form's own to give, with
 * the box that answers them. What is shown dims while it belongs to figures typed a moment ago;
 * figures the API [refused] wait for the marked fields to be corrected.
 */
@Composable
internal fun CaloriePreviewCard(preview: CaloriePreview?, behind: Boolean, waiting: Boolean, failed: Boolean, refused: Boolean, modifier: Modifier = Modifier) {
    val web = rememberWebPages()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.calorie_preview_title), style = MaterialTheme.typography.titleSmall)
            val c = preview?.calculation
            when {
                c != null -> Column(Modifier.alpha(if (behind) 0.6f else 1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text(
                            stringResource(R.string.calorie_preview_daily_target),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(kcal(c.dailyTarget), style = MaterialTheme.typography.headlineSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Figure(stringResource(R.string.calorie_preview_maintenance), kcal(c.maintenance))
                        Figure(stringResource(R.string.calorie_preview_at_rest), kcal(c.bmr))
                    }
                    Text(
                        when {
                            c.goalHeld -> stringResource(R.string.calorie_preview_held)
                            c.dailyDeficit == 0.0 -> stringResource(R.string.calorie_preview_at_maintenance)
                            c.dailyDeficit > 0 -> stringResource(R.string.calorie_preview_under, formatNumber(c.dailyDeficit))
                            else -> stringResource(R.string.calorie_preview_over, formatNumber(-c.dailyDeficit))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            R.string.calorie_preview_macros,
                            formatNumber(c.targetMacros.protein, 3),
                            formatNumber(c.targetMacros.fat, 3),
                            formatNumber(c.targetMacros.carbs, 3),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val notes = c.notes(risk = false)
                    if (notes.isNotEmpty()) {
                        WarningBox {
                            notes.forEach { note ->
                                Text(
                                    noteText(note),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (note.strong) FontWeight.Medium else null,
                                    color = if (note.strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    NotAdvice(onTerms = { web.open("terms") })
                }
                // There are figures to work out, and the answer is on its way: nothing is asked of the reader.
                waiting -> {
                    val label = stringResource(R.string.calorie_preview_waiting)
                    Skeleton(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = label })
                }
                else -> Text(
                    stringResource(
                        when {
                            refused -> R.string.calorie_preview_fix_fields
                            failed -> R.string.calorie_preview_failed
                            else -> R.string.calorie_preview_pending
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The target is read out when it changes; the rest is there to be read.
            if (c != null && !behind) {
                val said = stringResource(R.string.calorie_preview_said, formatNumber(c.dailyTarget))
                Text(
                    "",
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = said
                    },
                )
            }
        }
    }
}

@Composable
private fun kcal(value: Double): String = stringResource(R.string.calorie_preview_kcal, formatNumber(value))

/** That the figures are an approximation and no medical advice, with the instance's terms, which say more, as a link. */
@Composable
private fun NotAdvice(onTerms: () -> Unit) {
    val link = stringResource(R.string.calorie_preview_terms)
    val whole = stringResource(R.string.calorie_preview_not_advice, link)
    val linked = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append(whole)
        val start = whole.indexOf(link)
        if (start >= 0) {
            addLink(
                LinkAnnotation.Clickable(
                    tag = "terms",
                    styles = TextLinkStyles(SpanStyle(color = linked, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)),
                ) { onTerms() },
                start,
                start + link.length,
            )
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
