// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeFilter
import net.whiteravens.dietapp.domain.RecipePage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** The recipe library as the screen keeps it: narrowed, a page at a time. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModelTest {
    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    private fun recipe(id: String) = Recipe(id, id, "", 2, listOf("lunch"), emptyList(), emptyList(), emptyList(), 20, "easy", Nutrition(500.0, 30.0, 20.0, 50.0), byModel = null)

    private val users = mockk<UserRepository>().also {
        coEvery { it.profiles() } returns listOf(Profile("ada", "Ada", "balanced", 3))
        coEvery { it.me() } throws IOException("not asked here")
    }
    private val profiles = mockk<ProfileRepository>()

    @Test
    fun `a search cancelled by the next one leaves no error on the screen`() {
        val recipes = mockk<RecipeRepository>()
        val first = CompletableDeferred<RecipePage>()
        val second = CompletableDeferred<RecipePage>()
        val answers = mutableListOf(first, second)
        coEvery { recipes.search(any(), any()) } coAnswers { answers.removeAt(0).await() }

        val model = RecipesViewModel(recipes, users, profiles)
        model.retry()
        assertNull(model.state.value.error)

        second.complete(RecipePage(emptyList(), 1, 0))

        assertFalse(model.state.value.loading)
        assertNull(model.state.value.error)
    }

    @Test
    fun `the library narrowed is asked for from its first page with the profile's marks, and cleared it keeps the words and the profile`() {
        val recipes = mockk<RecipeRepository>()
        val asked = mutableListOf<RecipeFilter>()
        coEvery { recipes.search(capture(asked), 1) } returns RecipePage(listOf(recipe("soup")), 1, 1)
        val model = RecipesViewModel(recipes, users, profiles)

        model.narrow(RecipeFilter(dietType = "vegan", marked = "often", profileId = "ada"))
        model.clearFilters()

        assertEquals(RecipeFilter(dietType = "vegan", marked = "often", profileId = "ada"), asked[1])
        assertEquals(RecipeFilter(profileId = "ada"), asked.last())
        assertEquals(listOf("soup"), model.state.value.items.map { it.id })
    }
}
