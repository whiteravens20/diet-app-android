// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.repository.IncompleteExportException
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.components.AppCard
import java.io.IOException
import java.io.OutputStream

/**
 * Everything the account holds, as one JSON file kept where the user chooses, in the platform's own
 * window: the web's "Your data". The file is written as it arrives, and taken away again when it did
 * not arrive whole.
 */
@Composable
internal fun DataExportCard(state: DataExportViewModel.UiState, onExport: (open: () -> OutputStream, discard: () -> Unit) -> Unit) {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val where = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            onExport(
                { resolver.openOutputStream(uri, "wt") ?: throw IOException("No stream for $uri") },
                { DocumentsContract.deleteDocument(resolver, uri) },
            )
        }
    }
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_data), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.account_data_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedButton(
            onClick = { where.launch(DataExportViewModel.fileName()) },
            enabled = !state.preparing,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(stringResource(if (state.preparing) R.string.account_data_preparing else R.string.account_data_button))
        }
        if (state.saved) {
            Text(
                stringResource(R.string.account_data_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.error?.let {
            Text(
                if (it is IncompleteExportException) stringResource(R.string.account_data_not_whole) else errorMessage(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
