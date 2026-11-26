// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import java.util.Locale

/**
 * Content drawn in English, then in Polish, each kept for the eye as a phone in that language keeps it: [show] draws
 * it, given the language it is shown in, and [check] reads it and keeps its picture in each language in turn.
 */
class EachLanguage(private val compose: ComposeContentTestRule) {
    /** The language the content is shown in. */
    private var language by mutableStateOf("en")

    /** Raised to draw the content anew: once the phone's language is set, what follows it comes out in that language. */
    private var round by mutableIntStateOf(0)

    /** [content] in the language shown, given that language. */
    fun show(content: @Composable (language: String) -> Unit) {
        compose.setContent {
            DietAppTheme {
                Surface {
                    // Clear of the phone's bars, as a screen of the app is.
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
                        val context = LocalContext.current.createConfigurationContext(configuration)
                        CompositionLocalProvider(
                            LocalContext provides context,
                            LocalConfiguration provides configuration,
                            LocalResources provides context.resources,
                        ) {
                            key(language, round) { content(language) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Each language in turn: what is shown in it, as the eye reads it ([check]), and its picture, [name] and the
     * language, checked as a phone in that language checks its words: Polish holds its words of one letter to the next.
     */
    fun check(name: String, check: (language: String, said: List<String>) -> Unit) {
        val phone = Locale.getDefault()
        try {
            for (each in listOf("en", "pl")) {
                // The phone in that language before the content is drawn in it: what follows the phone's language, a
                // day's date among it, comes out in that language too.
                Locale.setDefault(Locale.forLanguageTag(each))
                language = each
                round += 1
                compose.waitForIdle()
                check(each, said())
                compose.keepScreenshot("$name-$each")
                Locale.setDefault(phone)
            }
        } finally {
            Locale.setDefault(phone)
        }
    }

    /** Every text shown, as the eye reads it: a no-break space as a space. */
    private fun said(): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text.replace('\u00A0', ' ') } }
}
