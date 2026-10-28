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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.data.repository.toDetails
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PreferencesChange
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.testing.fixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * A recipe's view as it keeps what the account's profiles say of the recipe: each profile's mark,
 * read, put on as a favourite and taken off; and the ingredients of its lines marked for a profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModelTest {
    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    /** A captured profile, as [id] called [name], favouring and avoiding the ingredients named. */
    private fun profile(id: String, name: String, favourite: List<String> = emptyList(), avoided: List<String> = emptyList()): ProfileDetails =
        AppModule.json().decodeFromString<ProfileDto>(fixture("profile")).toDetails().let {
            it.copy(
                id = id,
                figures = it.figures.copy(name = name),
                preferences = it.preferences.copy(favoriteIngredientIds = favourite, excludedIngredientIds = avoided),
            )
        }

    private val ada = profile("ada", "Ada", favourite = listOf("oats"), avoided = listOf("leek"))
    private val bo = profile("bo", "Bo")
    private val cy = profile("cy", "Cy")

    private val recipes = mockk<RecipeRepository>().also {
        coEvery { it.byId("soup") } returns Recipe(
            "soup", "Soup", "", 2, listOf("lunch"), emptyList(), emptyList(), emptyList(), 10, 10, "easy",
            Nutrition(410.0, 30.0, 20.0, 50.0), byModel = null, cuisine = null, allergens = emptyList(), source = null, own = false, fromSwap = false, submitted = false,
        )
    }
    private val profiles = mockk<ProfileRepository>().also { coEvery { it.profiles() } returns listOf(ada, bo, cy) }

    private fun marked(recipeId: String, sentiment: String) = MarkedRecipe(recipeId, recipeId, listOf("lunch"), sentiment, emptyList())

    private fun favorites() = mockk<FavoriteRepository>().also {
        coEvery { it.marked("ada", any()) } returns listOf(marked("oats", "favorite"))
        coEvery { it.marked("bo", any()) } returns listOf(marked("soup", "avoid"))
        coEvery { it.marked("cy", any()) } throws IOException("no answer")
    }

    private fun model(favorites: FavoriteRepository = favorites()) =
        RecipeDetailViewModel(SavedStateHandle(mapOf("recipeId" to "soup")), recipes, profiles, favorites)

    @Test
    fun `each profile says how it marked the recipe, and one whose marks cannot be read is not offered`() {
        val model = model()

        assertEquals(
            listOf(RecipeDetailViewModel.ProfileMark("ada", "Ada", null), RecipeDetailViewModel.ProfileMark("bo", "Bo", "avoid")),
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

    @Test
    fun `an ingredient marked as a favourite leaves the avoided ones, one marked again loses its mark, and the profile answered is shown`() {
        val sent = mutableListOf<PreferencesChange>()
        coEvery { profiles.updatePreferences("ada", capture(sent)) } answers {
            ada.copy(preferences = ada.preferences.copy(favoriteIngredientIds = sent.last().favoriteIngredientIds!!, excludedIngredientIds = sent.last().excludedIngredientIds!!))
        }
        val model = model()

        model.toggleIngredient("leek", avoid = false)
        model.toggleIngredient("oats", avoid = false)

        assertEquals(PreferencesChange(favoriteIngredientIds = listOf("oats", "leek"), excludedIngredientIds = emptyList()), sent[0])
        assertEquals(PreferencesChange(favoriteIngredientIds = listOf("leek"), excludedIngredientIds = emptyList()), sent[1])
        val shown = model.state.value.prefsProfile?.preferences
        assertEquals(listOf("leek"), shown?.favoriteIngredientIds)
        assertEquals(emptyList<String>(), shown?.excludedIngredientIds)
    }

    @Test
    fun `the lines are marked for the profile chosen, and a change refused is said`() {
        val refused = ApiException(400, "VALIDATION_FAILED", "Refused.")
        coEvery { profiles.updatePreferences("bo", any()) } throws refused
        val model = model()

        model.pickPrefsProfile("bo")
        model.toggleIngredient("oats", avoid = true)

        coVerify { profiles.updatePreferences("bo", PreferencesChange(favoriteIngredientIds = emptyList(), excludedIngredientIds = listOf("oats"))) }
        assertEquals(refused, model.state.value.prefsFailed)
        assertEquals("bo", model.state.value.prefsProfile?.id)
    }
}
