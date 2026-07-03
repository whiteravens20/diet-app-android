package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.GeneratePlanRequest
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.SwapMealRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.MealPlan
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
    /** Cached plans, newest sync first; empty until the first [refresh]. */
    suspend fun cachedPlans(profileId: String): List<MealPlan> =
        cache.mealPlans(profileId).map { row ->
            json.decodeFromString<MealPlanDto>(row.json).toDomain(syncedAt = row.syncedAt)
        }

    /** Fetch from the API and mirror into the cache. Throws when offline. */
    suspend fun refresh(profileId: String): List<MealPlan> {
        val plans = apiCall(json) { api.mealPlans(profileId) }
        plans.forEach(::mirror)
        return plans.map { it.toDomain() }
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
