// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.readOrDrop
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.GeneratePlanRequest
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.MealPlanPageDto
import net.whiteravens.dietapp.data.remote.SwapMealRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.MealPlanPage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first meal-plan access (docs/sync-strategy.md). Reads serve the Room
 * cache immediately; [refresh] re-fetches and re-mirrors. Writes (generate,
 * swap) are backend operations — the response is mirrored into the cache so the
 * next offline read sees it. The raw response JSON is what gets cached, so the
 * cache never diverges from the wire contract.
 */
@Singleton
class MealPlanRepository @Inject constructor(
    private val api: DietApiService,
    private val cache: CacheDao,
    private val json: Json,
) {
    /** The first page of a profile's plans as it was last fetched, or null before the first [plans]. */
    suspend fun cachedPlans(profileId: String): MealPlanPage? =
        cache.planList(profileId)?.let { row ->
            readOrDrop({ json.decodeFromString<MealPlanPageDto>(row.json).toDomain(syncedAt = row.syncedAt) }) {
                cache.deletePlanList(profileId)
            }
        }

    /** One cached plan by id, or null before it was ever fetched. */
    suspend fun cachedPlan(id: String): MealPlan? = cache.mealPlan(id)?.let { row -> read(row) }

    private suspend fun read(row: CachedMealPlan): MealPlan? =
        readOrDrop({ json.decodeFromString<MealPlanDto>(row.json).toDomain(syncedAt = row.syncedAt) }) {
            cache.deleteMealPlan(row.id)
        }

    /** Fetch one plan from the API and mirror it. Throws when offline. */
    suspend fun refreshPlan(id: String): MealPlan {
        val plan = apiCall(json) { api.mealPlan(id) }
        mirror(plan)
        return plan.toDomain()
    }

    /** A page of a profile's plans from the API; the first is kept for offline use. Throws when offline. */
    suspend fun plans(profileId: String, page: Int = 1): MealPlanPage {
        val answer = apiCall(json) { api.mealPlans(profileId, page, PAGE_SIZE) }
        if (page == 1) {
            cache.upsertPlanList(CachedPlanList(profileId, json.encodeToString(answer), System.currentTimeMillis()))
        }
        return answer.toDomain()
    }

    /** Generate a new plan for a profile (deterministic engine, backend-side). */
    suspend fun generate(
        profileId: String,
        startDate: String,
        durationDays: Int,
        mealCount: Int? = null,
    ): MealPlan {
        val plan = apiCall(json) {
            api.generatePlan(
                GeneratePlanRequest(
                    profileId = profileId,
                    startDate = startDate,
                    durationDays = durationDays,
                    mealCount = mealCount,
                ),
            )
        }
        mirror(plan)
        return plan.toDomain()
    }

    /** Swap one planned meal for a random same-diet alternative. */
    suspend fun swapMeal(planId: String, plannedMealId: String): MealPlan {
        val result = apiCall(json) {
            api.swapMeal(SwapMealRequest(planId = planId, plannedMealId = plannedMealId, strategy = "random"))
        }
        mirror(result.plan)
        return result.plan.toDomain()
    }

    private companion object {
        /** The API's own page, as many plans as the web application lists at a time. */
        const val PAGE_SIZE = 20
    }

    private suspend fun mirror(plan: MealPlanDto) {
        cache.upsertMealPlan(
            CachedMealPlan(
                id = plan.id,
                profileId = plan.profileId,
                json = json.encodeToString(plan),
                syncedAt = System.currentTimeMillis(),
            ),
        )
    }
}
