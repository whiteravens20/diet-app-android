// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.whiteravens.dietapp.R
import java.io.IOException

/**
 * Saving a text where the user chooses, in the platform's own window: the function given back asks
 * where to keep a file of [mime] under the name it is given, and writes the text it is given there.
 * The text waits for the place through a turn of the screen. A file that cannot be written is said
 * in a short note.
 */
@Composable
fun rememberTextSaver(mime: String): (name: String, text: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.export_failed)
    var waiting by rememberSaveable { mutableStateOf<String?>(null) }
    val where = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri: Uri? ->
        val text = waiting
        waiting = null
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                attempt { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IOException("No stream for $uri") }
            }
            if (written.isFailure) Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
        }
    }
    return { name, text ->
        waiting = text
        where.launch(name)
    }
}
