// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A build that starts with no server, as a release build does, asks for one before anything else. */
@RunWith(AndroidJUnit4::class)
class FirstStartTest {
    @get:Rule
    val screen = createEmptyComposeRule()

    /** Keeps "no server chosen" as the app keeps its server, before the app reads it. */
    private fun noServerChosen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            context.preferencesDataStoreFile("server")
        }
        store.edit { it[stringPreferencesKey("base_url")] = "" }
        job.cancelAndJoin()
    }

    @Test
    fun anAppWithNoServerAsksForOneAndThenSignsInToIt() {
        noServerChosen()
        val account = Instance.newAccount()

        ActivityScenario.launch(MainActivity::class.java).use {
            screen.waitForText(text(R.string.server_first_title))
            screen.keepScreenshot("first-start-which-server")
            screen.onNodeWithText(text(R.string.server_first_button)).performClick()
            screen.onNode(hasSetTextAction()).performTextInput(BuildConfig.API_BASE_URL)
            screen.onNodeWithText(text(R.string.common_save)).performClick()

            screen.waitForText(text(R.string.auth_welcome_back))
            screen.signIn(account)
        }
    }
}
