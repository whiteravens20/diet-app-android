// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.remote.RecipeDto
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDetails
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeDraftAsk
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.testing.fixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A recipe of one's own drafted by a model: the profiles it can be for, and the recipe written or why there is none. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDraftViewModelTest {
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private val profile = json.decodeFromString<List<ProfileDto>>(fixture("profiles")).first().toDetails()
    private val recipe = json.decodeFromString<RecipeDto>(fixture("recipe")).toDomain()
    private val profiles = mockk<ProfileRepository>().also { coEvery { it.profiles() } returns listOf(profile) }
    private val users = mockk<UserRepository>().also { coEvery { it.me() } returns account("byok") }

    private fun account(aiMode: String) = SessionUser("u", "ala@example.test", "Ala", true, "en", aiMode = aiMode)

    @Test
    fun `the recipe a model wrote is what the window opens, and a refusal is said with nothing drafted`() {
        val recipes = mockk<RecipeRepository>()
        val refusal = ApiException(503, "AI_UNAVAILABLE", "No model answered.")
        coEvery { recipes.draft(any()) } throws refusal andThen recipe
        val model = RecipeDraftViewModel(recipes, profiles, users)
        val ask = RecipeDraftAsk(profileId = profile.id, dietType = "vegan")

        assertEquals(listOf(profile.id), model.state.value.profiles.map { it.id })

        model.draft(ask)

        assertEquals(refusal, model.state.value.error)
        assertNull(model.state.value.drafted)

        model.draft(ask)

        assertEquals(recipe.id, model.state.value.drafted?.id)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.drafting)
    }

    @Test
    fun `a window opened again starts afresh, the recipe drafted in the one before opening nothing`() {
        val recipes = mockk<RecipeRepository>()
        coEvery { recipes.draft(any()) } returns recipe
        val model = RecipeDraftViewModel(recipes, profiles, users)
        model.draft(RecipeDraftAsk(profileId = profile.id, dietType = "vegan"))
        assertEquals(recipe.id, model.state.value.drafted?.id)

        model.closed()

        assertNull(model.state.value.drafted)
        assertFalse(model.state.value.drafting)
        assertNull(model.state.value.error)
    }

    @Test
    fun `what a window closed while the model wrote asked for opens nothing and holds up nothing in the next`() {
        val recipes = mockk<RecipeRepository>()
        val written = CompletableDeferred<Recipe>()
        coEvery { recipes.draft(any()) } coAnswers { written.await() } andThen recipe
        val model = RecipeDraftViewModel(recipes, profiles, users)
        val ask = RecipeDraftAsk(profileId = profile.id, dietType = "vegan")
        model.draft(ask)
        assertTrue(model.state.value.drafting)

        model.closed()
        written.complete(recipe)

        assertNull(model.state.value.drafted)
        assertFalse(model.state.value.drafting)
        // The next window asks, and its own answer opens its recipe.
        model.draft(ask)
        assertEquals(recipe.id, model.state.value.drafted?.id)
    }

    @Test
    fun `a refusal that comes after its window closed is said in no window`() {
        val recipes = mockk<RecipeRepository>()
        val written = CompletableDeferred<Recipe>()
        coEvery { recipes.draft(any()) } coAnswers { written.await() }
        val model = RecipeDraftViewModel(recipes, profiles, users)
        model.draft(RecipeDraftAsk(profileId = profile.id, dietType = "vegan"))

        model.closed()
        written.completeExceptionally(ApiException(503, "AI_UNAVAILABLE", "No model answered."))

        assertNull(model.state.value.error)
        assertFalse(model.state.value.drafting)
    }

    @Test
    fun `the window says the recipe goes before the curators only where the instance's model writes it`() {
        val recipes = mockk<RecipeRepository>()

        coEvery { users.me() } returns account("admin")
        assertTrue(RecipeDraftViewModel(recipes, profiles, users).state.value.toCurators)

        coEvery { users.me() } returns account("byok")
        assertFalse(RecipeDraftViewModel(recipes, profiles, users).state.value.toCurators)

        // An account that could not be read says nothing it does not know.
        coEvery { users.me() } throws ApiException(503, "UNAVAILABLE", "No answer.")
        assertFalse(RecipeDraftViewModel(recipes, profiles, users).state.value.toCurators)
    }
}
