package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.domain.Nutrition
import androidx.compose.foundation.Canvas
import kotlin.math.roundToInt

/** Web macro-chart colours: protein → primary, fat → accent, carbs → amber. */
@Composable
fun macroColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.tertiary,
)

/**
 * Donut of the protein / fat / carb split (grams) with the calorie total in
 * the middle — the Compose take on the web dashboard's pie chart.
 */
@Composable
fun MacroRing(
    nutrition: Nutrition,
    centerLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    strokeWidth: Dp = 16.dp,
) {
    val colors = macroColors()
    val values = listOf(nutrition.protein, nutrition.fat, nutrition.carbs)
    val total = values.sum().takeIf { it > 0 } ?: 1.0
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = stroke)
            var start = -90f
            values.forEachIndexed { i, v ->
                val sweep = (v / total * 360f).toFloat()
                if (sweep > 0f) {
                    drawArc(colors[i], start, sweep, false, topLeft, arcSize, style = stroke)
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${nutrition.calories.roundToInt()}", style = MaterialTheme.typography.titleLarge)
            Text(
                centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Legend row for [MacroRing]: coloured dot, macro name, grams. */
@Composable
fun MacroLegend(labels: List<String>, grams: List<Double>, modifier: Modifier = Modifier) {
    val colors = macroColors()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        labels.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i]))
                Column(Modifier.padding(start = 6.dp)) {
                    Text("${grams[i].roundToInt()} g", style = MaterialTheme.typography.labelLarge)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
