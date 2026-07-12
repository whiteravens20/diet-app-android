package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The web design system's card: `bg-card` surface, 1dp `border`, large radius,
 * whisper of elevation. All screen content sits on these.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surfaceContainer
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            shadowElevation = 1.dp,
        ) { inner() }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            shadowElevation = 1.dp,
        ) { inner() }
    }
}
