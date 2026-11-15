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
    fun `a field keeps nothing in the saved state, and a secret is dots for a screen reader too until its eye is pressed`() {
        val own = File("src/main/java/net/whiteravens/dietapp/ui/components/OutlinedField.kt").readText()

        // What a field holds goes to the saved state only where the screen keeps it there itself, and so does
        // whether a secret was shown.
        assertFalse(own.contains("rememberTextFieldState") || own.contains("rememberSaveable"))
        // Material's secure field gives a screen reader the secret, hidden only as it is drawn.
        assertFalse(own.contains("OutlinedSecureTextField(") || own.contains("BasicSecureTextField("))
        // Hidden until the eye is pressed, and hidden is dots and the mark of a password.
        assertTrue(own.contains("var shown by remember { mutableStateOf(false) }"))
        assertTrue(own.contains("outputTransformation = if (shown) null else Dots"))
        assertTrue(own.contains("modifier = if (shown) modifier else modifier.semantics { password() }"))
        // Dots again when the app leaves the screen, and when the screen sends what the field holds.
        assertTrue(own.contains("LifecycleEventEffect(Lifecycle.Event.ON_STOP) { shown = false }"))
        assertTrue(own.contains("LaunchedEffect(sending) { if (sending) shown = false }"))
    }

    @Test
    fun `a secret is typed on a password's keyboard, shown or not, which corrects nothing`() {
        val own = File("src/main/java/net/whiteravens/dietapp/ui/components/OutlinedField.kt").readText()
        val secretField = own.substringAfter("fun OutlinedSecretField(").substringBefore("\n}\n")

        assertTrue(secretField.contains("keyboardOptions = SecretKeyboard,"))
        val keyboard = own.substringAfter("private val SecretKeyboard = KeyboardOptions(").substringBefore(")")
        assertTrue(keyboard.contains("keyboardType = KeyboardType.Password"))
        assertTrue(keyboard.contains("autoCorrectEnabled = false"))
        assertTrue(keyboard.contains("capitalization = KeyboardCapitalization.None"))
    }

    @Test
    fun `every field of a secret is told when its screen sends what it holds`() {
        val calls = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                Regex("""(?<!fun )\b(OutlinedSecretField|PasswordField)\(""").findAll(text).map { file.name to argumentsAt(text, it.range.last) }
            }
            .toList()

        // Sign-in and sign-up, the account's own field, the second step twice, and the key of a provider.
        assertTrue("only ${calls.size} fields of secrets found", calls.size >= 6)
        assertEquals(emptyList<String>(), calls.filterNot { (_, arguments) -> "sending =" in arguments }.map { it.first })
    }

    /** What stands between the bracket at [open] and the one that closes it. */
    private fun argumentsAt(text: String, open: Int): String {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return text.substring(open + 1, index)
            }
        }
        return text.substring(open + 1)
    }
}
