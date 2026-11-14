// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OutlinedFieldTest {
    @Test
    fun `every field typed into is the app's own, whose label stands above the frame`() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        val own = File("src/main/java/net/whiteravens/dietapp/ui/components/OutlinedField.kt")
        val fields = Regex(
            """^import androidx\.compose\.(material3\.(OutlinedTextField|TextField|OutlinedSecureTextField|SecureTextField|\*)|foundation\.text\.(BasicTextField|BasicSecureTextField))$""",
            RegexOption.MULTILINE,
        )

        val others = sources.filter { it != own && fields.containsMatchIn(it.readText()) }.map { it.name }

        assertEquals(emptyList<String>(), others)
        // A label in the frame's top edge holds one line, and the second of a long label was cut there.
        val ownText = own.readText()
        assertEquals(2, Regex("""labelPosition = TextFieldLabelPosition\.Above\(\)""").findAll(ownText).count())
        assertTrue(ownText.contains("OutlinedTextField("))
    }

    @Test
    fun `a field keeps nothing in the saved state, and a secret is dots for a screen reader too`() {
        val own = File("src/main/java/net/whiteravens/dietapp/ui/components/OutlinedField.kt").readText()

        // What a field holds goes to the saved state only where the screen keeps it there itself.
        assertFalse(own.contains("rememberTextFieldState") || own.contains("rememberSaveable"))
        // Material's secure field gives a screen reader the secret, hidden only as it is drawn.
        assertFalse(own.contains("OutlinedSecureTextField(") || own.contains("BasicSecureTextField("))
        assertTrue(own.contains("outputTransformation = Dots") && own.contains("password()"))
    }
}
