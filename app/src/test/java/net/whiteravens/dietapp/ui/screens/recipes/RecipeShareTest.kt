// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A recipe sent to somebody, as a text and as a page to print, written as the web writes them. */
class RecipeShareTest {
    private val soup = SharedRecipe(
        title = "Tomato soup",
        ingredientsHeading = "Ingredients for 2 servings",
        ingredients = listOf(
            SharedLine("Tomatoes", "400\u00A0g", "diced"),
            SharedLine("Eggs", "2 eggs (110\u00A0g)", null),
        ),
        stepsHeading = "Method",
        steps = listOf("Simmer the tomatoes.", "Blend."),
        notes = emptyList(),
    )

    @Test
    fun `a text gives the title, the ingredients with their amounts and notes under what they are for, and the steps by number`() {
        assertEquals(
            listOf(
                "Tomato soup",
                "",
                "Ingredients for 2 servings:",
                "- Tomatoes: 400 g — diced",
                "- Eggs: 2 eggs (110 g)",
                "",
                "Method:",
                "1. Simmer the tomatoes.",
                "2. Blend.",
            ),
            recipeText(soup).lines(),
        )
    }

    @Test
    fun `a text ends with what is said under the recipe, after a line of its own`() {
        val text = recipeText(soup.copy(notes = listOf("From https://recipes.example/soup\u00A0· Licence: CC BY 4.0", "Made with a model.")))

        assertEquals(
            listOf("", "From https://recipes.example/soup · Licence: CC BY 4.0", "Made with a model."),
            text.lines().takeLast(3),
        )
    }

    @Test
    fun `a text has only plain spaces, the narrow no-break ones of a number too`() {
        val text = recipeText(soup.copy(ingredients = listOf(SharedLine("Flour", "1\u202F000\u00A0g", null))))

        assertFalse(text.contains('\u00A0') || text.contains('\u202F'))
        assertTrue(text.contains("- Flour: 1 000 g"))
    }

    @Test
    fun `a page to print holds the recipe in its order, with the mark and the name of the application`() {
        val page = recipePage(soup.copy(notes = listOf("Licence: CC BY 4.0")), "Diet App", "iVBORw0KGgo=")

        val order = listOf(
            "data:image/png;base64,iVBORw0KGgo=",
            "Diet App",
            "<h1>Tomato soup</h1>",
            "Ingredients for 2 servings",
            "Tomatoes<small>diced</small>",
            "400\u00A0g",
            "Method",
            "<li>Simmer the tomatoes.</li>",
            "<li>Blend.</li>",
            "<p class=\"note\">Licence: CC BY 4.0</p>",
        ).map { page.indexOf(it) }
        assertTrue("every part is there: $order", order.all { it >= 0 })
        assertEquals(order.sorted(), order)
    }

    @Test
    fun `a page to print writes out the marks of markup in what a user wrote, so that nothing of it runs`() {
        val page = recipePage(
            soup.copy(title = "<script>alert(1)</script>", steps = listOf("Add \"salt\" & <b>pepper</b>")),
            "Diet App",
            null,
        )

        assertFalse(page.contains("<script>"))
        assertFalse(page.contains("<b>"))
        assertTrue(page.contains("<h1>&lt;script&gt;alert(1)&lt;/script&gt;</h1>"))
        assertTrue(page.contains("<li>Add &quot;salt&quot; &amp; &lt;b&gt;pepper&lt;/b&gt;</li>"))
    }

    @Test
    fun `a page to print without a mark has no picture`() {
        assertFalse(recipePage(soup, "Diet App", null).contains("<img"))
    }
}
