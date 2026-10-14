// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.RecipeSearchPageDto
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.di.AppModule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModelTest {
    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `a search cancelled by the next one leaves no error on the screen`() {
        val api = mockk<DietApiService>()
        val first = CompletableDeferred<RecipeSearchPageDto>()
        val second = CompletableDeferred<RecipeSearchPageDto>()
        val answers = mutableListOf(first, second)
        coEvery { api.recipes(any(), any(), any(), any(), any()) } coAnswers { answers.removeAt(0).await() }

        val model = RecipesViewModel(RecipeRepository(api, AppModule.json()))
        model.retry()
        assertNull(model.state.value.error)

        second.complete(RecipeSearchPageDto(emptyList(), 1, 20, 0, 0))

        assertFalse(model.state.value.loading)
        assertNull(model.state.value.error)
    }
}
