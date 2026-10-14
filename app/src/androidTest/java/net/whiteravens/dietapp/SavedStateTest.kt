// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.savedStateHolds
import net.whiteravens.dietapp.testing.text
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the sign-in screen puts into the saved state, which the system may write to disk. */
@RunWith(AndroidJUnit4::class)
class SavedStateTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun aTypedPasswordIsLeftOutOfTheSavedStateWhileTheEmailIsKept() {
        screen.onNodeWithText(text(R.string.auth_email)).performTextInput(EMAIL)
        screen.onNodeWithText(text(R.string.auth_password)).performTextInput(PASSWORD)

        // The e-mail is there, so a search that finds nothing has looked where the screen keeps what it keeps.
        assertTrue("the e-mail is not in the saved state", screen.savedStateHolds(EMAIL))
        assertFalse("the password is in the saved state", screen.savedStateHolds(PASSWORD))
    }

    private companion object {
        const val EMAIL = "kept@example.test"
        const val PASSWORD = "Not-Kept-Passw0rd"
    }
}
