// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import io.mockk.coEvery
import io.mockk.mockk
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
