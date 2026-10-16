// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Recipe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** A recipe's view as it keeps the marks of the account's profiles: read for each, put on as a favourite and taken off. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModelTest {
    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    private val ada = Profile("ada", "Ada", "balanced", 3)
    private val bo = Profile("bo", "Bo", "vegan", 4)
    private val cy = Profile("cy", "Cy", "keto", 3)

    private val recipes = mockk<RecipeRepository>().also {
        coEvery { it.byId("soup") } returns Recipe(
            "soup", "Soup", "", 2, listOf("lunch"), emptyList(), emptyList(), emptyList(), 10, 10, "easy",
            Nutrition(410.0, 30.0, 20.0, 50.0), byModel = null, cuisine = null, allergens = emptyList(), source = null,
        )
    }
    private val users = mockk<UserRepository>().also { coEvery { it.profiles() } returns listOf(ada, bo, cy) }

    private fun marked(recipeId: String, sentiment: String) = MarkedRecipe(recipeId, recipeId, listOf("lunch"), sentiment, emptyList())

    private fun favorites() = mockk<FavoriteRepository>().also {
        coEvery { it.marked("ada", any()) } returns listOf(marked("oats", "favorite"))
        coEvery { it.marked("bo", any()) } returns listOf(marked("soup", "avoid"))
        coEvery { it.marked("cy", any()) } throws IOException("no answer")
    }

    private fun model(favorites: FavoriteRepository) = RecipeDetailViewModel(SavedStateHandle(mapOf("recipeId" to "soup")), recipes, users, favorites)

    @Test
    fun `each profile says how it marked the recipe, and one whose marks cannot be read is not offered`() {
        val model = model(favorites())

        assertEquals(
            listOf(RecipeDetailViewModel.ProfileMark(ada, null), RecipeDetailViewModel.ProfileMark(bo, "avoid")),
            model.state.value.marks,
        )
        assertEquals("Soup", model.state.value.recipe?.title)
    }

    @Test
    fun `a profile puts the recipe among its favourites, and takes off a mark of any kind`() {
        val favorites = favorites()
        coEvery { favorites.mark("ada", "soup", "favorite", emptyList()) } just runs
        coEvery { favorites.unmark("bo", "soup") } just runs
        val model = model(favorites)

        model.toggleMark("ada")
        model.toggleMark("bo")

        coVerify { favorites.mark("ada", "soup", "favorite", emptyList()) }
        coVerify { favorites.unmark("bo", "soup") }
        assertEquals(listOf("favorite", null), model.state.value.marks.map { it.sentiment })
        assertNull(model.state.value.marking)
    }

    @Test
    fun `a mark that was gone already is off, and a mark refused is said and left as it was`() {
        val favorites = favorites()
        coEvery { favorites.unmark("bo", "soup") } throws ApiException(404, "FAVORITE_NOT_FOUND", "That recipe is not marked for this profile.")
        val full = ApiException(409, "FAVORITE_LIMIT_REACHED", "A profile can have at most 500 marked recipes.")
        coEvery { favorites.mark("ada", "soup", "favorite", emptyList()) } throws full
        val model = model(favorites)

        model.toggleMark("bo")
        model.toggleMark("ada")

        assertEquals(listOf(null, null), model.state.value.marks.map { it.sentiment })
        assertEquals(RecipeDetailViewModel.MarkFailed(removing = false, error = full), model.state.value.markFailed)
    }
}
