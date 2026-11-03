// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.Recipe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The window the words of a recipe a substitution made are corrected in. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeTextViewModelTest {
    private val swapped = Recipe(
        "bowl", "Miska (zamiennik: ciecierzyca)", "Miska.", 1, listOf("lunch"), emptyList(), emptyList(),
        listOf("Ugotuj ciecierzyca.", "Podaj."), 5, 10, "easy", Nutrition(410.0, 30.0, 20.0, 50.0),
        byModel = null, cuisine = null, allergens = emptyList(), source = null, own = true, fromSwap = true, submitted = false,
    )
    private val recipes = mockk<RecipeRepository>().also { coEvery { it.byId("bowl") } returns swapped }

    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `starts from what the recipe says, and writes the words alone, a step left empty left out`() {
        coEvery { recipes.reword(any(), any(), any(), any()) } returns swapped
        val model = RecipeTextViewModel(recipes)
        model.open("bowl")
        assertEquals("Miska (zamiennik: ciecierzyca)", model.state.value.title)
        assertEquals(listOf("Ugotuj ciecierzyca.", "Podaj."), model.state.value.steps)

        model.typeTitle("Miska z ciecierzycą")
        model.typeStep(0, "Ugotuj ciecierzycę.")
        model.addStep()
        model.moveStep(1, -1)
        model.save()

        assertTrue(model.state.value.done)
        coVerify { recipes.reword("bowl", "Miska z ciecierzycą", "Miska.", listOf("Podaj.", "Ugotuj ciecierzycę.")) }
    }

    @Test
    fun `keeps one step at least, and says a refusal while keeping what was typed`() {
        val refused = ApiException(400, "RECIPE_INVALID", "Breaks a rule.")
        coEvery { recipes.reword(any(), any(), any(), any()) } throws refused
        val model = RecipeTextViewModel(recipes)
        model.open("bowl")

        model.removeStep(1)
        model.removeStep(0)
        assertEquals(listOf("Ugotuj ciecierzyca."), model.state.value.steps)

        model.save()
        assertEquals(refused, model.state.value.refused)
        assertFalse(model.state.value.done)
        assertEquals("Miska (zamiennik: ciecierzyca)", model.state.value.title)
    }
}
