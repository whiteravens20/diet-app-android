package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Small rounded pill — meal slots, diet types, "off-diet", "from pantry"… */
@Composable
fun LabelChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(999.dp), color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Primary-tinted chip (the web's `bg-primary/10 text-primary` accent). */
@Composable
fun AccentChip(text: String, modifier: Modifier = Modifier) {
    LabelChip(
        text,
        modifier,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}
