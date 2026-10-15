// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.readOrDrop
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.AddCustomMealRequest
import net.whiteravens.dietapp.data.remote.AiSwapOfferRequest
import net.whiteravens.dietapp.data.remote.ApplyMealSwapRequest
import net.whiteravens.dietapp.data.remote.DayOverrideDto
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.GeneratePlanRequest
import net.whiteravens.dietapp.data.remote.LockedSlotDto
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.MealPlanPageDto
import net.whiteravens.dietapp.data.remote.NutritionDto
import net.whiteravens.dietapp.data.remote.RebalanceRequest
import net.whiteravens.dietapp.data.remote.RebalanceResultDto
import net.whiteravens.dietapp.data.remote.RelaxationNeededDto
import net.whiteravens.dietapp.data.remote.RestoreScaleDto
import net.whiteravens.dietapp.data.remote.SetMealEatenRequest
import net.whiteravens.dietapp.data.remote.SwapOfferRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.DayAsk
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.MealPlanPage
import net.whiteravens.dietapp.domain.PlanAsk
import net.whiteravens.dietapp.domain.PlanCalendarDay
import net.whiteravens.dietapp.domain.PlanFilter
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.Rebalanced
import net.whiteravens.dietapp.domain.RelaxationNeeded
import net.whiteravens.dietapp.domain.RelaxedMeal
import net.whiteravens.dietapp.domain.SwapAsk
import net.whiteravens.dietapp.domain.SwapOffer
import java.time.LocalDate
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

    /**
     * A page of a profile's plans from the API, narrowed by [filter]; the first page of the whole
     * list is kept for offline use. Throws when offline.
     */
    suspend fun plans(profileId: String, page: Int = 1, filter: PlanFilter = PlanFilter()): MealPlanPage {
        val answer = apiCall(json) {
            api.mealPlans(profileId, page, PAGE_SIZE, filter.status, filter.from?.toString(), filter.to?.toString())
        }
        if (page == 1 && filter.isEmpty) {
            cache.upsertPlanList(CachedPlanList(profileId, json.encodeToString(answer), System.currentTimeMillis()))
        }
        return answer.toDomain()
    }

    /** The days a profile's plans hold from [from] to [to]: what a calendar of them shows. */
    suspend fun calendar(profileId: String, from: LocalDate, to: LocalDate): List<PlanCalendarDay> =
        apiCall(json) { api.planCalendar(profileId, from.toString(), to.toString()) }.days.map {
            PlanCalendarDay(
                date = LocalDate.parse(it.date),
                planId = it.planId,
                plans = it.plans,
                calories = it.calories,
                target = it.target,
                offTarget = it.offTarget,
                mealsEaten = it.eaten.meals,
                meals = it.eaten.of,
                skipped = it.skipped,
            )
        }

    /**
     * Makes a plan as [ask] asks for it (the engine fills it on the server). A plan that cannot be
     * filled inside its limits is refused with `PLAN_NEEDS_RELAXATION`: [relaxationNeeded] reads it.
     */
    suspend fun generate(ask: PlanAsk): MealPlan {
        val plan = apiCall(json) {
            api.generatePlan(
                GeneratePlanRequest(
                    profileId = ask.profileId,
                    startDate = ask.startDate.toString(),
                    durationDays = ask.durationDays,
                    dietType = ask.dietType,
                    mealCount = ask.mealCount,
                    calorieTargetOverride = ask.calorieTarget,
                    mealPrepFriendly = ask.mealPrepFriendly,
                    respectExclusions = ask.respectExclusions,
                    respectFavorites = ask.respectFavorites,
                    respectInventory = ask.respectInventory,
                    maxRepeatsPerRecipe = ask.maxRepeatsPerRecipe,
                    allowRelaxations = ask.allowRelaxations.takeIf { it.isNotEmpty() },
                    dayOverrides = ask.days.map { it.toDto() }.takeIf { it.isNotEmpty() },
                ),
            )
        }
        mirror(plan)
        return plan.toDomain()
    }

    /** What stands in the way of a plan, read from a refusal with `PLAN_NEEDS_RELAXATION`; null for any other. */
    fun relaxationNeeded(error: Throwable): RelaxationNeeded? {
        if (error !is ApiException || error.code != "PLAN_NEEDS_RELAXATION") return null
        val details = error.details ?: return null
        val read = try {
            json.decodeFromJsonElement(RelaxationNeededDto.serializer(), details)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        return RelaxationNeeded(read.needs, read.meals.map { RelaxedMeal(LocalDate.parse(it.date), it.slot, it.gaveWay) })
    }

    private fun DayAsk.toDto() = DayOverrideDto(
        date = date.toString(),
        mealCount = mealCount,
        skip = skip.takeIf { it },
        calorieTarget = calorieTarget,
        dayType = dayType,
        cookTimeBudgetMinutes = cookTimeMinutes,
        useUpBy = useUpBy.takeIf { it },
        lockedSlots = locks.map { (meal, recipe) -> LockedSlotDto(meal, recipe) }.takeIf { it.isNotEmpty() },
    )

    /**
     * A recipe for the meal as [ask] asks for one, other than those offered for it already ([seen]).
     * Nothing is written.
     */
    suspend fun offerSwap(planId: String, mealId: String, ask: SwapAsk, seen: List<String> = emptyList()): SwapOffer {
        if (ask.strategy == AI_STRATEGY) {
            val preview = apiCall(json) {
                api.aiOfferSwap(AiSwapOfferRequest(planId = planId, plannedMealId = mealId, skip = seen))
            }
            return preview.toDomain(planId, mealId, ask, seen + preview.recipe.id)
        }
        val preview = apiCall(json) {
            api.offerSwap(
                SwapOfferRequest(
                    planId = planId,
                    plannedMealId = mealId,
                    strategy = ask.strategy,
                    favoriteRecipeId = ask.favoriteRecipeId,
                    allowOffDiet = ask.allowOffDiet,
                    skip = seen,
                ),
            )
        }
        return preview.toDomain(planId, mealId, ask, seen + preview.recipe.id)
    }

    /**
     * Takes [offer]: the meal gets its recipe, and the plan, balanced around it, is kept for offline
     * use. A plan changed since the offer is refused with `PLAN_CHANGED`.
     */
    suspend fun takeSwap(offer: SwapOffer): Rebalanced = kept(
        apiCall(json) {
            api.swapMeal(
                ApplyMealSwapRequest(
                    planId = offer.planId,
                    plannedMealId = offer.mealId,
                    recipeId = offer.recipeId,
                    revision = offer.revision,
                    // What a model picked is held to what the engine's own pick is.
                    strategy = if (offer.ask.strategy == AI_STRATEGY) RANDOM_STRATEGY else offer.ask.strategy,
                    allowOffDiet = offer.ask.allowOffDiet,
                    pick = offer.pick,
                ),
            )
        },
    )

    /** Marks a meal eaten or not; its day is balanced again around it. */
    suspend fun setEaten(planId: String, mealId: String, eaten: Boolean): Rebalanced =
        kept(apiCall(json) { api.setMealEaten(planId, mealId, SetMealEatenRequest(eaten)) })

    /** Adds a meal of the user's own to the day of [date] (yyyy-MM-dd); the day is balanced around it. */
    suspend fun addCustomMeal(planId: String, date: String, meal: CustomMeal): Rebalanced = kept(
        apiCall(json) {
            api.addCustomMeal(
                planId,
                date,
                AddCustomMealRequest(
                    name = meal.name.trim(),
                    mealType = meal.slot,
                    nutrition = NutritionDto(meal.perServing.calories, meal.perServing.protein, meal.perServing.fat, meal.perServing.carbs),
                    servings = meal.servings,
                ),
            )
        },
    )

    /** Balances the days from today to the end of their week of the plan, each around its own meals. */
    suspend fun rebalanceWeek(planId: String): Rebalanced =
        kept(apiCall(json) { api.rebalancePlan(planId, RebalanceRequest(scope = "week")) })

    /** Puts the meals [rebalance] scaled back as they were. */
    suspend fun undo(planId: String, rebalance: Rebalance): Rebalanced = kept(
        apiCall(json) {
            api.rebalancePlan(planId, RebalanceRequest(scope = "day", restore = rebalance.changes.map { RestoreScaleDto(it.mealId, it.before) }))
        },
    )

    /** Deletes a plan; the copies on the phone of it, of its shopping lists and of its profile's list go with it. */
    suspend fun delete(plan: MealPlan) {
        apiCall(json) { api.deletePlan(plan.id) }
        cache.shoppingListsForPlan(plan.id).forEach { cache.deleteShoppingList(it.id) }
        cache.deleteMealPlan(plan.id)
        cache.deletePlanList(plan.profileId)
    }

    /** A plan as a change left it, kept for offline use, with what the change rebalanced. */
    private suspend fun kept(result: RebalanceResultDto): Rebalanced {
        mirror(result.plan)
        return Rebalanced(result.plan.toDomain(), result.rebalance?.toDomain())
    }

    private companion object {
        /** The API's own page, as many plans as the web application lists at a time. */
        const val PAGE_SIZE = 20

        /** A model asked which of the recipes the engine puts first to take. */
        const val AI_STRATEGY = "ai"

        /** Any recipe of the plan's diet that fits the meal. */
        const val RANDOM_STRATEGY = "random"
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
