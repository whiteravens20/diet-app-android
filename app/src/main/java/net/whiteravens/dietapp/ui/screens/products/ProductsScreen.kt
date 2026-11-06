// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.products

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.OwnProduct
import net.whiteravens.dietapp.domain.OwnProductLimits
import net.whiteravens.dietapp.domain.OwnProductState
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.unitName
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OPEN_FOOD_FACTS
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.offProductPage
import net.whiteravens.dietapp.ui.components.rememberWebPages
import net.whiteravens.dietapp.ui.screens.diary.Note
import net.whiteravens.dietapp.ui.screens.diary.plainNumber
import net.whiteravens.dietapp.ui.components.DOT
import java.io.IOException
import kotlin.math.roundToInt

private const val ODBL = "https://opendatacommons.org/licenses/odbl/1-0/"

/**
 * The account's products with a barcode, new to the base or its corrections of the base's: their state,
 * a new one written, one changed, sent to the instance's reviewers, taken back or removed, and the file of
 * what people of the instance added. As the web's page has them; the base itself is searched in the
 * diary's window.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductsScreen(onBack: () -> Unit, viewModel: ProductsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val web = rememberWebPages()
    val context = LocalContext.current
    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { place ->
        place?.let {
            viewModel.saveAdditions { text ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(place)?.use { file -> file.write(text.toByteArray()) }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.products_title),
                subtitle = stringResource(R.string.products_subhead),
                onBack = onBack,
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::openNew) { Text(stringResource(R.string.products_add)) }
                OutlinedButton(onClick = { saveTo.launch("products-added.json") }) { Text(stringResource(R.string.products_file)) }
            }
            Note(
                stringResource(
                    R.string.products_limits,
                    formatNumber(OwnProductLimits.MOST_HELD),
                    formatNumber(OwnProductLimits.WRITTEN_A_DAY),
                    formatNumber(OwnProductLimits.SENT_A_DAY),
                ),
            )
            Note(stringResource(R.string.products_source))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { web.openOutside(OPEN_FOOD_FACTS) }) { Text(stringResource(R.string.diary_product_source_name)) }
                TextButton(onClick = { web.openOutside(ODBL) }) { Text(stringResource(R.string.diary_product_source_licence)) }
            }
            if (state.fileSaved) Note(stringResource(R.string.products_file_saved))
        }
        state.refused?.takeIf { state.refusedOn == null }?.let { refused ->
            item { ErrorBanner(if (refused is IOException) stringResource(R.string.products_offline) else errorMessage(refused)) }
        }
        when {
            state.loading -> item { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }
            state.failed != null -> item {
                Column {
                    val failed = state.failed!!
                    ErrorBanner(if (failed is IOException) stringResource(R.string.products_offline) else errorMessage(failed))
                    Button(onClick = viewModel::read, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
            state.items.isEmpty() -> item {
                EmptyState(
                    icon = AppIcons.Inventory,
                    title = stringResource(R.string.products_empty_title),
                    description = stringResource(R.string.products_empty_body),
                )
            }
            else -> items(state.items, key = { it.product.id }) { own ->
                OwnProductCard(
                    own,
                    state.asking?.takeIf { it.first == own.product.id }?.second,
                    state.busy,
                    state.refused?.takeIf { state.refusedOn == own.product.id },
                    viewModel,
                )
            }
        }
    }

    if (state.writing) {
        OwnProductDialog(
            product = state.writingOn,
            code = "",
            onSaved = { viewModel.saved() },
            onDismiss = viewModel::closeWindow,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnProductCard(own: OwnProduct, asking: ProductsViewModel.Ask?, busy: Boolean, refused: Throwable?, viewModel: ProductsViewModel) {
    val product = own.product
    val web = rememberWebPages()
    AppCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(product.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                LabelChip(
                    stringResource(
                        when {
                            product.correction -> R.string.products_state_correction
                            own.state == OwnProductState.SENT -> R.string.products_state_sent
                            own.state == OwnProductState.REJECTED -> R.string.products_state_rejected
                            else -> R.string.products_state_own
                        },
                    ),
                )
            }
            listOfNotNull(product.brand, product.pack, product.code).joinToString(DOT).takeIf { it.isNotEmpty() }?.let { Note(it) }
            val grams = { value: Double? -> value?.let(::plainNumber) ?: "—" }
            Note(
                stringResource(R.string.diary_per_100, formatNumber((product.calories ?: 0.0).roundToInt()), unitName(product.unit)) + DOT +
                    stringResource(R.string.products_macros, grams(product.protein), grams(product.fat), grams(product.carbs)),
            )
            own.reason?.let { reason ->
                Text(stringResource(R.string.products_reason, reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (product.doubtful) Text(stringResource(R.string.products_doubtful), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            // A correction stays its author's: the base has the product it corrects.
            product.code?.takeIf { product.correction }?.let { code ->
                Note(stringResource(R.string.products_correction_note))
                TextButton(onClick = { web.openOutside(offProductPage(code)) }) { Text(stringResource(R.string.products_correct_at_off)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { viewModel.openOn(own) }, enabled = !busy) { Text(stringResource(R.string.products_edit)) }
                if (own.state == OwnProductState.SENT) {
                    TextButton(onClick = { viewModel.withdraw(product.id) }, enabled = !busy) { Text(stringResource(R.string.products_withdraw)) }
                } else if (!product.correction) {
                    TextButton(
                        onClick = { viewModel.ask(product.id, ProductsViewModel.Ask.SEND) },
                        enabled = !busy && own.sendable,
                    ) { Text(stringResource(R.string.products_send)) }
                }
                TextButton(onClick = { viewModel.ask(product.id, ProductsViewModel.Ask.REMOVE) }, enabled = !busy) {
                    Text(stringResource(R.string.products_remove))
                }
            }
            refused?.let { ErrorBanner(if (it is IOException) stringResource(R.string.products_offline) else errorMessage(it)) }
            if (!own.sendable && own.state != OwnProductState.SENT && !product.correction) Note(stringResource(R.string.products_send_needs))
            asking?.let { asked ->
                Note(stringResource(if (asked == ProductsViewModel.Ask.SEND) R.string.products_send_note else R.string.products_remove_note))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (asked == ProductsViewModel.Ask.SEND) viewModel.send(product.id) else viewModel.remove(product.id)
                        },
                        enabled = !busy,
                    ) {
                        Text(stringResource(if (asked == ProductsViewModel.Ask.SEND) R.string.products_send_confirm else R.string.products_remove_confirm))
                    }
                    TextButton(onClick = viewModel::notNow) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}
