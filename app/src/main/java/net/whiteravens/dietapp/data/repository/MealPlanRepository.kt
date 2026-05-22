package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.MealPlanDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first meal-plan access. Reads serve the Room cache immediately;
 * a network refresh updates the cache in the background. When offline, the
 * cached copy is returned so plans remain viewable (Phase 3 requirement).
 */
@Singleton
class MealPlanRepository @Inject constructor(
    private val api: DietApiService,
    private val cache: CacheDao,
    private val json: Json,
) {
    /** Cached plans first; callers can trigger [refresh] for fresh data. */
    suspend fun cachedPlans(profileId: String): List<MealPlanDto> =
        cache.mealPlans(profileId).map { json.decodeFromString(it.json) }

    /** Fetch from the API and update the cache. Throws when offline. */
    suspend fun refresh(profileId: String): List<MealPlanDto> {
        val plans = api.mealPlans(profileId)
        plans.forEach { plan ->
            cache.upsertMealPlan(
                CachedMealPlan(
                    id = plan.id,
                    profileId = profileId,
                    json = json.encodeToString(plan),
                    syncedAt = System.currentTimeMillis(),
                ),
            )
        }
        return plans
    }
}
