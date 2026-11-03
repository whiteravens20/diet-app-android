// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.domain.DiaryLimits
import net.whiteravens.dietapp.domain.Product
import net.whiteravens.dietapp.domain.ProductSource
import net.whiteravens.dietapp.domain.canSearchProducts
import net.whiteravens.dietapp.domain.looksLikeProductCode
import net.whiteravens.dietapp.domain.productCodeOf
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.unitName
import net.whiteravens.dietapp.ui.components.rememberWebPages
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import java.io.IOException
import kotlin.math.roundToInt

private const val OPEN_FOOD_FACTS = "https://world.openfoodfacts.org"
private const val ODBL = "https://opendatacommons.org/licenses/odbl/1-0/"

/** Where the figures of a product come from, under the licence they come with. */
@Composable
internal fun ProductSourceNote(source: ProductSource) {
    if (source == ProductSource.OWN) return Note(stringResource(R.string.diary_product_source_own))
    val web = rememberWebPages()
    FlowRow(verticalArrangement = Arrangement.spacedBy(0.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Note(stringResource(if (source == ProductSource.INSTANCE) R.string.diary_product_source_instance else R.string.diary_product_source))
        TextButton(onClick = { web.openOutside(OPEN_FOOD_FACTS) }) { Text(stringResource(R.string.diary_product_source_name)) }
        TextButton(onClick = { web.openOutside(ODBL) }) { Text(stringResource(R.string.diary_product_source_licence)) }
    }
}

/**
 * The field a product is looked for in, and the way to the camera: words look among what the instance
 * holds as they are typed, and a code is asked about only when somebody says so.
 */
@Composable
internal fun ProductSearch(form: EntryForm, lookups: Boolean, viewModel: DiaryViewModel) {
    FormField(
        value = form.search,
        onValueChange = viewModel::search,
        label = stringResource(R.string.diary_product_field),
        problem = null,
        keyboardType = KeyboardType.Text,
    )
    Note(stringResource(R.string.diary_product_hint))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::openScanner) { Text(stringResource(R.string.diary_product_scan)) }
        if (looksLikeProductCode(form.search)) {
            OutlinedButton(onClick = viewModel::checkCode, enabled = !form.checkingCode) { Text(stringResource(R.string.diary_product_check)) }
        }
    }
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (form.codeBad) Text(stringResource(R.string.diary_product_code_bad), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        form.codeFailed?.let { failure ->
            val text = when {
                failure is IOException -> stringResource(R.string.diary_product_offline)
                failure is ApiException && failure.code == "PRODUCT_NOT_FOUND" ->
                    stringResource(if (lookups) R.string.diary_product_not_found else R.string.diary_product_not_found_here)
                else -> errorMessage(failure)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (failure is ApiException && failure.code == "PRODUCT_NOT_FOUND") {
                OutlinedButton(onClick = { viewModel.writeOwnProduct(productCodeOf(form.search).orEmpty()) }) {
                    Text(stringResource(R.string.diary_product_add_own))
                }
            }
            TextButton(onClick = { viewModel.asOwnMeal(null) }) { Text(stringResource(R.string.diary_product_as_own)) }
        }
        val searching = !looksLikeProductCode(form.search) && canSearchProducts(form.search)
        when {
            !searching || !form.searched -> Unit
            form.searchFailed is IOException -> {
                Note(stringResource(R.string.diary_product_offline))
                TextButton(onClick = { viewModel.asOwnMeal(null) }) { Text(stringResource(R.string.diary_product_as_own)) }
            }
            form.searchFailed != null -> Text(stringResource(R.string.diary_search_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            form.products.isEmpty() -> {
                Note(stringResource(R.string.diary_product_nothing_here))
                AddOwnProduct(viewModel)
            }
            else -> Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    form.products.forEach { found ->
                        Found(
                            found.fullName,
                            listOfNotNull(
                                found.calories?.let { stringResource(R.string.diary_per_100, formatNumber(it.roundToInt()), unitName(found.unit)) },
                                stringResource(R.string.diary_product_own_mark).takeIf { found.source == ProductSource.OWN },
                            ).joinToString(" · ").ifEmpty { null },
                        ) { viewModel.pickProduct(found) }
                    }
                }
            }
        }
        if (searching && form.searched && form.searchFailed == null && form.products.isNotEmpty()) AddOwnProduct(viewModel)
    }
}

/** The way to a product of one's own from what a search found, or did not. */
@Composable
private fun AddOwnProduct(viewModel: DiaryViewModel) {
    TextButton(onClick = { viewModel.writeOwnProduct("") }) { Text(stringResource(R.string.diary_product_add_own)) }
}

/** What was picked: what its label says in 100 of its unit, with what is unknown said to be so. */
@Composable
internal fun PickedProduct(product: Product, viewModel: DiaryViewModel) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.bodyLarge)
                    listOfNotNull(product.brand, product.pack).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Note(it) }
                }
                TextButton(onClick = viewModel::unpick) { Text(stringResource(R.string.diary_change_pick)) }
            }
            val energy = product.calories
            if (energy == null) {
                Text(stringResource(R.string.diary_product_no_figures), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { viewModel.asOwnMeal(product.name) }) { Text(stringResource(R.string.diary_product_as_own)) }
            } else {
                Text(stringResource(R.string.diary_label_in_100, unitName(product.unit)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                LabelRow(stringResource(R.string.diary_label_energy), (if (product.energyWorkedOut) "≈ " else "") + stringResource(R.string.diary_meal_kcal, formatNumber(energy.roundToInt())))
                LabelRow(stringResource(R.string.diary_label_protein), grams(product.protein))
                LabelRow(stringResource(R.string.diary_label_fat), grams(product.fat))
                LabelRow(stringResource(R.string.diary_label_saturated), grams(product.saturatedFat), inner = true)
                LabelRow(stringResource(R.string.diary_label_carbs), grams(product.carbs))
                LabelRow(stringResource(R.string.diary_label_sugars), grams(product.sugars), inner = true)
                LabelRow(stringResource(R.string.diary_label_fiber), grams(product.fiber))
                LabelRow(stringResource(R.string.diary_label_salt), grams(product.salt))
            }
            if (product.doubtful && energy != null) {
                Text(stringResource(R.string.diary_product_doubtful), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (product.lacksMacros) {
                Note(stringResource(if (product.source == ProductSource.OWN) R.string.diary_product_lacking_own else R.string.diary_product_lacking))
            }
            if (product.energyWorkedOut) Note(stringResource(R.string.diary_product_energy_worked_out))
            ProductSourceNote(product.source)
        }
    }
}

@Composable
private fun grams(value: Double?): String =
    value?.let { stringResource(R.string.diary_label_grams, plainNumber(it)) } ?: stringResource(R.string.diary_label_unknown)

@Composable
private fun LabelRow(label: String, value: String, inner: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(start = if (inner) 12.dp else 0.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (inner) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** What the amount of a picked product is typed in, with a serving and a pack a press away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProductAmount(product: Product, form: EntryForm, problem: String?, viewModel: DiaryViewModel) {
    val unit = unitName(product.unit)
    FormField(
        value = form.quantity,
        onValueChange = { typed -> viewModel.typeEntry(EntryField.PRODUCT_QUANTITY) { it.copy(quantity = typed) } },
        label = "${stringResource(R.string.diary_quantity)} ($unit)",
        problem = problem,
        keyboardType = KeyboardType.Decimal,
    )
    val serving = product.servingAmount?.takeIf { it <= DiaryLimits.mostWeighed }
    val pack = product.packAmount?.takeIf { it <= DiaryLimits.mostWeighed }
    if (serving != null || pack != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            serving?.let { amount ->
                OutlinedButton(onClick = { viewModel.typeEntry(EntryField.PRODUCT_QUANTITY) { it.copy(quantity = plainNumber(amount)) } }) {
                    Text(stringResource(R.string.diary_product_serving, "${plainNumber(amount)} $unit"))
                }
            }
            pack?.let { amount ->
                OutlinedButton(onClick = { viewModel.typeEntry(EntryField.PRODUCT_QUANTITY) { it.copy(quantity = plainNumber(amount)) } }) {
                    Text(stringResource(R.string.diary_product_pack, "${plainNumber(amount)} $unit"))
                }
            }
        }
    }
}
