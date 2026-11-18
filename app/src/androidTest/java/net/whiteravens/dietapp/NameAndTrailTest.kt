// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.ui.components.NameAndTrail
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A row of a name and what follows it, such as a row of the pantry or of a shopping list: on one line while the name
 * keeps its longest word whole, the rest under the name where it would not, as with a large font on a phone.
 */
@RunWith(AndroidJUnit4::class)
class NameAndTrailTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shown(width: Dp, fontScale: Float) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                DietAppTheme {
                    Box(Modifier.width(width)) {
                        NameAndTrail(name = { Text("Natka pietruszki", Modifier.testTag("name")) }) {
                            Text("1 sztuka (110 g)", Modifier.testTag("trail"))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aNameAndWhatFollowsItShareALineWhereTheNameKeepsItsWordsWhole() {
        shown(360.dp, 1f)

        val name = compose.onNodeWithTag("name").getUnclippedBoundsInRoot()
        val trail = compose.onNodeWithTag("trail").getUnclippedBoundsInRoot()
        assertTrue("beside the name: $name, $trail", trail.left >= name.right && trail.top < name.bottom)
    }

    @Test
    fun whatFollowsGoesUnderTheNameWhereTheNameWouldBreakAWord() {
        shown(240.dp, 2f)

        val name = compose.onNodeWithTag("name").getUnclippedBoundsInRoot()
        val trail = compose.onNodeWithTag("trail").getUnclippedBoundsInRoot()
        assertTrue("under the name: $name, $trail", trail.top >= name.bottom)
        // The name has the line to itself, and holds a whole word on a line: not a letter or two, as it did squeezed.
        assertTrue("words whole: $name", name.right - name.left > 100.dp)
    }
}
