// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import net.whiteravens.dietapp.R

/**
 * A field typed into, as every screen of the app has it: its label stands above the frame, where it takes as many
 * lines as it needs at any size of text, and never in the frame's top edge, where a label of two lines was cut
 * (Material's `TextFieldLabelPosition.Above`). A field that may stay empty ([optional]) says so at the end of its
 * label's line. Every screen types through it, and a test of the sources sees that none uses Material's own field.
 *
 * It holds a string as the screens keep theirs: what is typed goes to [onValueChange], and a value the screen sets
 * comes into the field. [accept] says what of a change the field takes: the text as typed, a part of it, or null
 * for nothing, the field then keeping what it had.
 */
@Composable
fun OutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    optional: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    accept: (String) -> String? = { it },
) {
    val state = typedState(value, onValueChange)
    val filter by rememberUpdatedState(accept)
    // A field that may stay empty says so to whoever hears the screen read, as it shows it at the end of its label.
    val optionalWord = stringResource(R.string.common_optional)
    OutlinedTextField(
        state = state,
        modifier = modifier.then(if (optional) Modifier.semantics { stateDescription = optionalWord } else Modifier),
        enabled = enabled,
        readOnly = readOnly,
        labelPosition = TextFieldLabelPosition.Above(),
        label = labelOf(label, optional),
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        inputTransformation = InputTransformation {
            val typed = asCharSequence().toString()
            when (val taken = filter(typed)) {
                null -> revertAllChanges()
                typed -> Unit
                else -> replace(0, length, taken)
            }
        },
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = minLines),
        shape = shape,
        colors = colors,
    )
}

/** What a field of a secret holds, which the words of its eye name. */
enum class Secret { Password, Key }

/**
 * A field for a password or another secret, as [OutlinedField] is for the rest: its label above the frame, and what is
 * typed shown as dots, every character. The dots are what the field holds for anyone but the one typing: what a screen
 * reader is given, with the mark of a password. Material's secure field hides the characters only as they are drawn and
 * gives a screen reader the secret itself.
 *
 * Its eye shows what was typed, the mark of a password gone with the dots, and a second press hides it again. Shown or
 * not, the keyboard is told it types a password: it offers no words, corrects nothing and learns nothing of it. The
 * field is dots again whenever the screen sends what it holds ([sending]) and when the app leaves the screen, and
 * whether it was shown is kept nowhere: a screen opened again, or brought back from the background, shows dots.
 */
@Composable
fun OutlinedSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    secret: Secret = Secret.Password,
    sending: Boolean = false,
    onKeyboardAction: KeyboardActionHandler? = null,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    var shown by remember { mutableStateOf(false) }
    // Nothing is said of the eye until it is pressed: the field's own mark already says that it is hidden.
    var told by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { shown = false }
    LaunchedEffect(sending) { if (sending) shown = false }
    val words = wordsOf(secret)
    OutlinedTextField(
        state = typedState(value, onValueChange),
        modifier = if (shown) modifier else modifier.semantics { password() },
        enabled = enabled,
        labelPosition = TextFieldLabelPosition.Above(),
        label = labelOf(label, optional = false),
        trailingIcon = {
            val said = stringResource(if (shown) words.shown else words.hidden)
            // A press leaves the focus in the field, and the keyboard up.
            IconButton(
                onClick = {
                    shown = !shown
                    told = true
                },
                enabled = enabled,
                modifier = Modifier.semantics { if (told) stateDescription = said },
            ) {
                Icon(
                    if (shown) AppIcons.VisibilityOff else AppIcons.Visibility,
                    contentDescription = stringResource(if (shown) words.hide else words.show),
                )
            }
        },
        supportingText = supportingText,
        isError = isError,
        outputTransformation = if (shown) null else Dots,
        keyboardOptions = SecretKeyboard,
        onKeyboardAction = onKeyboardAction,
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = shape,
        // The eye is no part of a refusal: only the label and the frame take the colour of an error.
        colors = OutlinedTextFieldDefaults.colors(errorTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant),
    )
}

/** What the eye of a field says: what a press does, and what has been done. */
private class EyeWords(val show: Int, val hide: Int, val shown: Int, val hidden: Int)

private fun wordsOf(secret: Secret) = when (secret) {
    Secret.Password -> EyeWords(
        R.string.common_show_password,
        R.string.common_hide_password,
        R.string.common_password_shown,
        R.string.common_password_hidden,
    )
    Secret.Key -> EyeWords(R.string.common_show_key, R.string.common_hide_key, R.string.common_key_shown, R.string.common_key_hidden)
}

/** A password's keyboard, with nothing of a word's: no capitals, no corrections, nothing it offers or learns. */
private val SecretKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Password,
)

/** Every character a dot, one for one, so that the cursor stands where it stands in what was typed. */
private val Dots = OutputTransformation {
    for (index in 0 until length) replace(index, index + 1, "\u2022")
}

/**
 * The text of a field as the screen keeps it: what is typed goes to [onValueChange], and a value the screen sets comes
 * into the field. A value the field sent itself and the screen gives back, perhaps a frame late while more was typed,
 * is no value of the screen's: it never takes the place of what was typed after it. The field keeps nothing of its own
 * in the saved state: the screen decides what is kept there, and a password never is.
 */
@Composable
private fun typedState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val state = remember { TextFieldState(value) }
    val typed by rememberUpdatedState(onValueChange)
    val kept by rememberUpdatedState(value)
    // What the field sent and the screen has not given back yet, the oldest first.
    val sent = remember { ArrayDeque<String>() }
    LaunchedEffect(value) {
        val own = sent.indexOf(value)
        if (own >= 0) {
            repeat(own + 1) { sent.removeFirst() }
            return@LaunchedEffect
        }
        sent.clear()
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (text != kept) {
                sent.addLast(text)
                typed(text)
            }
        }
    }
    return state
}

/** A field's label, with the word that says the field may stay empty at the end of its line where it may. */
private fun labelOf(label: (@Composable () -> Unit)?, optional: Boolean): (@Composable TextFieldLabelScope.() -> Unit)? =
    label?.let { content ->
        {
            if (optional) {
                // The word goes under the label where the label needs the line to itself, as with a large font.
                NameAndTrail(Modifier.fillMaxWidth(), gap = 0.dp, alignment = Alignment.Bottom, name = content) {
                    // Heard once, as the state of the field.
                    Text(
                        stringResource(R.string.common_optional),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            } else {
                content()
            }
        }
    }
