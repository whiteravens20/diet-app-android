// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.fixture
import net.whiteravens.dietapp.testing.knownDefect
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MealPlansViewModelTest {
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    /** The cache as it is on the device: the plans of [plans], and no lists. */
    private class Cache(private val plans: List<CachedMealPlan>) : CacheDao {
        override suspend fun upsertMealPlan(plan: CachedMealPlan) = Unit
        override suspend fun mealPlans(profileId: String) = plans
        override suspend fun mealPlan(id: String) = plans.firstOrNull { it.id == id }
        override suspend fun upsertShoppingList(list: CachedShoppingList) = Unit
        override suspend fun shoppingList(id: String): CachedShoppingList? = null
        override suspend fun shoppingListsForPlan(planId: String) = emptyList<CachedShoppingList>()
    }

    /**
     * A row the classes cannot read is what the cache holds after an update of the application
     * changed them, or after a backend changed what it answers. Without a network the cache is
     * all the screen has.
     */
    @Test
    fun `a cached plan the classes cannot read is left out, and the screen goes on without it`() = knownDefect(
        "a cached row is decoded outside the error handling, and what it throws stops the application",
    ) {
        val profile = json.decodeFromString<List<ProfileDto>>(fixture("profiles")).first()
        val api = mockk<DietApiService>()
        coEvery { api.profiles() } returns listOf(profile)
        coEvery { api.mealPlans(any()) } throws IOException("no network")
        val unreadable = CachedMealPlan("plan", profile.id, """{"id":"plan"}""", 1L)

        var escaped: Throwable? = null
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> escaped = e }
        val model = try {
            MealPlansViewModel(UserRepository(api, json), MealPlanRepository(api, Cache(listOf(unreadable)), json))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }

        assertNull(escaped)
        assertFalse(model.state.value.loading)
    }
}
