// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.ModelApiService
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.changeQueue
import net.whiteravens.dietapp.testing.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the offline cache gives back. A row the classes of this version cannot read is what an
 * update of the application, or a change of what the backend answers, leaves on the device: it is
 * dropped, and the rows that can be read are still shown.
 */
class CachedRowsTest {
    private val json = AppModule.json()
    private val api = mockk<DietApiService>()
    private val asking = mockk<ModelApiService>()

    /** The rows on the device, kept in memory; [dropped] names what was deleted. */
    private class Cache(
        private val planLists: List<CachedPlanList> = emptyList(),
        private val plans: List<CachedMealPlan> = emptyList(),
        private val lists: List<CachedShoppingList> = emptyList(),
    ) : CacheDao {
        val dropped = mutableListOf<String>()
        override suspend fun upsertPlanList(list: CachedPlanList) = Unit
        override suspend fun planList(profileId: String) = planLists.firstOrNull { it.profileId == profileId }
        override suspend fun deletePlanList(profileId: String) {
            dropped += profileId
        }
        override suspend fun upsertMealPlan(plan: CachedMealPlan) = Unit
        override suspend fun mealPlan(id: String) = plans.firstOrNull { it.id == id }
        override suspend fun deleteMealPlan(id: String) {
            dropped += id
        }
        override suspend fun upsertShoppingList(list: CachedShoppingList) = Unit
        override suspend fun shoppingList(id: String) = lists.firstOrNull { it.id == id }
        override suspend fun shoppingListsForPlan(planId: String) = lists.filter { it.planId == planId }
        override suspend fun deleteShoppingList(id: String) {
            dropped += id
        }
    }

    private val unreadable = """{"id":"x"}"""

    @Test
    fun `a cached list of plans the classes cannot read is dropped, and a readable one is given back`() {
        val cache = Cache(
            planLists = listOf(
                CachedPlanList("readable", fixture("meal-plans"), 2L),
                CachedPlanList("unreadable", unreadable, 1L),
            ),
        )
        val plans = MealPlanRepository(api, asking, cache, json)

        assertNull(runBlocking { plans.cachedPlans("unreadable") })
        val readable = runBlocking { plans.cachedPlans("readable") }

        assertTrue(readable!!.plans.isNotEmpty())
        assertEquals(2L, readable.syncedAt)
        assertEquals(listOf("unreadable"), cache.dropped)
    }

    @Test
    fun `a cached plan the classes cannot read is dropped, and a readable one is given back`() {
        val cache = Cache(
            plans = listOf(
                CachedMealPlan("readable", "profile", fixture("meal-plan"), 2L),
                CachedMealPlan("unreadable", "profile", unreadable, 1L),
            ),
        )
        val plans = MealPlanRepository(api, asking, cache, json)

        assertNull(runBlocking { plans.cachedPlan("unreadable") })
        assertNotNull(runBlocking { plans.cachedPlan("readable") })
        assertEquals(listOf("unreadable"), cache.dropped)
    }

    @Test
    fun `a cached shopping list the classes cannot read is dropped, and the readable ones are given back`() {
        val cache = Cache(
            lists = listOf(
                CachedShoppingList("readable", "plan", fixture("shopping-list"), 2L),
                CachedShoppingList("unreadable", "plan", unreadable, 1L),
            ),
        )
        val lists = ShoppingListRepository(api, asking, cache, json, changeQueue())

        val cached = runBlocking { lists.cachedForPlan("plan") }

        assertEquals(1, cached.size)
        assertNull(runBlocking { lists.cached("unreadable") })
        assertNotNull(runBlocking { lists.cached("readable") })
        assertEquals(listOf("unreadable", "unreadable"), cache.dropped)
    }
}
