// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.ui.navigation.Tab
import net.whiteravens.dietapp.ui.navigation.TabBar
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bar of the tabs at the bottom of the screen, with its five Polish labels: whole at every size of the font,
 * one size for all of them, as large as the bar allows up to the size the setting asks for.
 */
@RunWith(AndroidJUnit4::class)
class TabBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val labels = listOf("Pulpit", "Plany", "Przepisy", "Zakupy", "Więcej")

    /** The size of the bar's labels at the phone's own setting, read from the theme. */
    private var asked: TextUnit = TextUnit.Unspecified

    private fun shown(width: Dp, fontScale: Float) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                DietAppTheme {
                    asked = MaterialTheme.typography.labelMedium.fontSize
                    Box(Modifier.width(width)) {
                        TabBar(labels.map { Tab(Icons.Default.Home, it) }, selected = 4, onPick = {})
                    }
                }
            }
        }
    }

    private fun layoutOf(label: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.single()
    }

    @Test
    fun theLabelsKeepTheSizeTheSettingAsksForWhereTheyFit() {
        shown(360.dp, 1f)

        labels.forEach { label ->
            val layout = layoutOf(label)
            assertFalse("$label is cut short", layout.isLineEllipsized(0))
            assertEquals(label, asked, layout.layoutInput.style.fontSize)
        }
    }

    @Test
    fun atALargeFontOnANarrowPhoneEveryLabelIsWholeAtOneSizeLargerThanTheDefault() {
        shown(360.dp, 2f)

        val sizes = labels.map { label ->
            val layout = layoutOf(label)
            assertFalse("$label is cut short", layout.isLineEllipsized(0))
            layout.layoutInput.style.fontSize
        }
        assertEquals("one size: $sizes", 1, sizes.distinct().size)
        // Smaller than the setting asks for, which would cut "Przepisy", yet larger than at the phone's default font.
        assertTrue("$sizes against $asked", sizes.first().value < asked.value && sizes.first().value * 2f > asked.value)
    }
}
