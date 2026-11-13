// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.products

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.OwnProduct
import net.whiteravens.dietapp.domain.OwnProductLimits
import net.whiteravens.dietapp.domain.OwnProductState
import net.whiteravens.dietapp.domain.Product
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.unitName
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.components.Text
import net.whiteravens.dietapp.ui.components.offProductPage
import net.whiteravens.dietapp.ui.components.rememberWebPages
import net.whiteravens.dietapp.ui.screens.diary.Note
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import java.io.IOException

private val UNITS = listOf("g", "ml")

/**
 * The window a product of one's own is written in, on the list of products and over the diary's
 * window. What cannot be right is said of its field before anything is sent. Opened [from] a product
 * of the base, it writes the reader's correction of it, and says where a correction reaches
 * everybody. It needs a network: without one the window says so, and [onOwnMeal] leads to a meal of
 * one's own, which goes through the queue.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OwnProductDialog(
    product: OwnProduct?,
    code: String,
    onSaved: (OwnProduct) -> Unit,
    onDismiss: () -> Unit,
    from: Product? = null,
    onOwnMeal: (() -> Unit)? = null,
    viewModel: OwnProductViewModel = hiltViewModel(key = "own-product-${product?.product?.id ?: from?.id ?: code}"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val web = rememberWebPages()
    LaunchedEffect(product?.product?.id, from?.id, code) { viewModel.open(product, code, from) }
    LaunchedEffect(state.saved) { viewModel.state.value.saved?.let(onSaved) }
    val draft = state.draft
    val problems = if (state.showProblems) draft.problems() else emptyMap()
    val places = remember { OwnField.entries.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(state.refusals) {
        if (state.refusals > 0) viewModel.state.value.draft.problems().keys.minOrNull()?.let { places.getValue(it).bringIntoView() }
    }

    @Composable
    fun problemOf(field: OwnField): String? = problems[field]?.let { problem ->
        stringResource(
            when (problem) {
                OwnProblem.CODE -> R.string.products_err_code
                OwnProblem.NAME -> R.string.products_err_name
                OwnProblem.AMOUNT -> R.string.products_err_amount
                OwnProblem.CALORIES -> R.string.products_err_calories
                OwnProblem.FIGURE -> R.string.products_err_figure
                OwnProblem.MACROS -> R.string.products_err_macros
                OwnProblem.SUGARS -> R.string.products_err_sugars
                OwnProblem.SATURATED -> R.string.products_err_saturated
            },
        )
    }

    @Composable
    fun figure(field: OwnField, label: Int, value: String, modifier: Modifier, change: OwnProductDraft.(String) -> OwnProductDraft) = FormField(
        value = value,
        onValueChange = { typed -> viewModel.type { it.change(typed) } },
        label = stringResource(label),
        problem = problemOf(field),
        keyboardType = KeyboardType.Decimal,
        optional = field != OwnField.CALORIES,
        modifier = modifier.bringIntoViewRequester(places.getValue(field)),
    )

    ScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    when {
                        product != null -> R.string.products_edit_title
                        from != null -> R.string.products_correct_title
                        else -> R.string.products_add_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                from?.code?.let { corrected ->
                    Note(stringResource(R.string.products_correct_intro))
                    TextButton(onClick = { web.openOutside(offProductPage(corrected)) }) {
                        Text(stringResource(R.string.products_correct_at_off))
                    }
                }
                FormField(
                    value = draft.code,
                    onValueChange = { typed -> viewModel.type { it.copy(code = typed.take(CODE_LONGEST)) } },
                    label = stringResource(R.string.products_code),
                    optional = true,
                    hint = stringResource(R.string.products_code_hint),
                    problem = problemOf(OwnField.CODE),
                    modifier = Modifier.bringIntoViewRequester(places.getValue(OwnField.CODE)),
                )
                FormField(
                    value = draft.name,
                    onValueChange = { typed -> viewModel.type { it.copy(name = typed.take(OwnProductLimits.NAME)) } },
                    label = stringResource(R.string.products_name),
                    problem = problemOf(OwnField.NAME),
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.bringIntoViewRequester(places.getValue(OwnField.NAME)),
                )
                FormField(
                    value = draft.brand,
                    onValueChange = { typed -> viewModel.type { it.copy(brand = typed.take(OwnProductLimits.BRAND)) } },
                    label = stringResource(R.string.products_brand),
                    optional = true,
                    keyboardType = KeyboardType.Text,
                )
                FormField(
                    value = draft.pack,
                    onValueChange = { typed -> viewModel.type { it.copy(pack = typed.take(OwnProductLimits.PACK)) } },
                    label = stringResource(R.string.products_pack),
                    optional = true,
                    keyboardType = KeyboardType.Text,
                )
                ChoiceField(
                    label = stringResource(R.string.products_unit),
                    options = UNITS,
                    selected = draft.unit,
                    optionLabel = { unitName(it) },
                    onSelect = { unit -> viewModel.type { it.copy(unit = unit) } },
                )
                val unit = unitName(draft.unit)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = draft.packAmount,
                        onValueChange = { typed -> viewModel.type { it.copy(packAmount = typed) } },
                        label = stringResource(R.string.products_pack_amount, unit),
                        optional = true,
                        problem = problemOf(OwnField.PACK_AMOUNT),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f).bringIntoViewRequester(places.getValue(OwnField.PACK_AMOUNT)),
                    )
                    FormField(
                        value = draft.servingAmount,
                        onValueChange = { typed -> viewModel.type { it.copy(servingAmount = typed) } },
                        label = stringResource(R.string.products_serving_amount, unit),
                        optional = true,
                        problem = problemOf(OwnField.SERVING_AMOUNT),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f).bringIntoViewRequester(places.getValue(OwnField.SERVING_AMOUNT)),
                    )
                }
                Text(
                    stringResource(R.string.products_in_100, unit),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Note(stringResource(R.string.products_figures_hint))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    figure(OwnField.CALORIES, R.string.products_calories, draft.calories, Modifier.weight(1f)) { copy(calories = it) }
                    figure(OwnField.PROTEIN, R.string.products_protein, draft.protein, Modifier.weight(1f)) { copy(protein = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    figure(OwnField.FAT, R.string.products_fat, draft.fat, Modifier.weight(1f)) { copy(fat = it) }
                    figure(OwnField.SATURATED, R.string.products_saturated, draft.saturatedFat, Modifier.weight(1f)) { copy(saturatedFat = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    figure(OwnField.CARBS, R.string.products_carbs, draft.carbs, Modifier.weight(1f)) { copy(carbs = it) }
                    figure(OwnField.SUGARS, R.string.products_sugars, draft.sugars, Modifier.weight(1f)) { copy(sugars = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    figure(OwnField.FIBER, R.string.products_fiber, draft.fiber, Modifier.weight(1f)) { copy(fiber = it) }
                    figure(OwnField.SALT, R.string.products_salt, draft.salt, Modifier.weight(1f)) { copy(salt = it) }
                }
                if (product?.state == OwnProductState.SENT) Note(stringResource(R.string.products_change_leaves_queue))
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.refused?.let { refused ->
                        ErrorBanner(if (refused is IOException) stringResource(R.string.products_offline) else errorMessage(refused))
                        if (refused is IOException && onOwnMeal != null) {
                            OutlinedButton(onClick = onOwnMeal) { Text(stringResource(R.string.diary_product_as_own)) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Button(onClick = viewModel::save, enabled = !state.saving) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}

/** The longest code the field takes, with whatever is read between its digits. */
private const val CODE_LONGEST = 20
