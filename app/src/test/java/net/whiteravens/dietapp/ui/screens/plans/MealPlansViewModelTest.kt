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
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.MealPlanPage
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanFilter
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.testing.fixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MealPlansViewModelTest {
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    /** The cache as it is on the device: the list of plans [list], and nothing else. It keeps what was dropped. */
    private class Cache(private val list: CachedPlanList?) : CacheDao {
        val dropped = mutableListOf<String>()
        override suspend fun upsertPlanList(list: CachedPlanList) = Unit
        override suspend fun planList(profileId: String) = list?.takeIf { it.profileId == profileId }
        override suspend fun deletePlanList(profileId: String) {
            dropped += profileId
        }
        override suspend fun upsertMealPlan(plan: CachedMealPlan) = Unit
        override suspend fun mealPlan(id: String): CachedMealPlan? = null
        override suspend fun deleteMealPlan(id: String) = Unit
        override suspend fun upsertShoppingList(list: CachedShoppingList) = Unit
        override suspend fun shoppingList(id: String): CachedShoppingList? = null
        override suspend fun shoppingListsForPlan(planId: String) = emptyList<CachedShoppingList>()
        override suspend fun deleteShoppingList(id: String) = Unit
    }

    /**
     * A row the classes cannot read is what the cache holds after an update of the application
     * changed them, or after a backend changed what it answers. Without a network the cache is
     * all the screen has.
     */
    @Test
    fun `a cached list of plans the classes cannot read is left out, and the screen goes on without it`() {
        val profile = json.decodeFromString<List<ProfileDto>>(fixture("profiles")).first()
        val api = mockk<DietApiService>()
        coEvery { api.profiles() } returns listOf(profile)
        coEvery { api.mealPlans(any(), any(), any(), any(), any(), any()) } throws IOException("no network")
        val cache = Cache(CachedPlanList(profile.id, """[{"id":"plan"}]""", 1L))

        var escaped: Throwable? = null
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> escaped = e }
        val model = try {
            MealPlansViewModel(UserRepository(api, json), MealPlanRepository(api, cache, json))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }

        assertNull(escaped)
        assertFalse(model.state.value.loading)
        assertEquals(emptyList<MealPlanSummary>(), model.state.value.plans)
        assertEquals(listOf(profile.id), cache.dropped)
    }

    private val ada = Profile("ada", "Ada", "balanced", 3)
    private fun summary(id: String) = MealPlanSummary(id, "2026-10-12", 7, "balanced", Nutrition(2000.0, 100.0, 70.0, 220.0))
    private fun page(vararg ids: String, totalPages: Int = 1, syncedAt: Long? = null) = MealPlanPage(ids.map(::summary), 1, totalPages, syncedAt)

    private val users = mockk<UserRepository>().also { coEvery { it.profiles() } returns listOf(ada) }
    private val plans = mockk<MealPlanRepository>().also {
        coEvery { it.cachedPlans(any()) } returns null
        coEvery { it.plans(any(), any(), any()) } returns page("p1")
    }

    @Test
    fun `a narrowed list is asked for alone, from its first page, and the whole list comes back when the filter is cleared`() {
        coEvery { plans.cachedPlans("ada") } returns page("cached", syncedAt = 1L)
        coEvery { plans.plans("ada", 1, PlanFilter()) } returns page("p1", "p2", totalPages = 2)
        val filter = PlanFilter(status = "past", from = LocalDate.of(2026, 9, 1), to = LocalDate.of(2026, 9, 30))
        coEvery { plans.plans("ada", 1, filter) } returns page("old")
        val model = MealPlansViewModel(users, plans)
        assertEquals(listOf("p1", "p2"), model.state.value.plans.map { it.id })

        model.narrow(filter)

        assertEquals(filter, model.state.value.filter)
        assertEquals(listOf("old"), model.state.value.plans.map { it.id })
        assertFalse(model.state.value.hasMore)

        model.narrow(PlanFilter())

        assertEquals(listOf("p1", "p2"), model.state.value.plans.map { it.id })
        assertTrue(model.state.value.hasMore)
    }

    @Test
    fun `the next page of a narrowed list is of the same filter`() {
        val filter = PlanFilter(status = "active")
        coEvery { plans.plans("ada", 1, filter) } returns page("a1", totalPages = 2)
        coEvery { plans.plans("ada", 2, filter) } returns MealPlanPage(listOf(summary("a2")), 2, 2, null)
        val model = MealPlansViewModel(users, plans)

        model.narrow(filter)
        model.showMore()

        assertEquals(listOf("a1", "a2"), model.state.value.plans.map { it.id })
        assertFalse(model.state.value.hasMore)
    }


}
