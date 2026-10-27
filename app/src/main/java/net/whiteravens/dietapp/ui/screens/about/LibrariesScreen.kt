// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.components.ScreenHeader

/**
 * The libraries the application ships and the licence of each, as the build read them from its own
 * dependency graph: a library opened shows its licence's text.
 */
@Composable
fun LibrariesScreen(onBack: () -> Unit) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.about_libraries),
            subtitle = stringResource(R.string.about_libraries_explain),
            onBack = onBack,
            modifier = Modifier.padding(16.dp),
        )
        LibrariesContainer(libraries, Modifier.fillMaxSize())
    }
}
