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
import net.whiteravens.dietapp.data.remote.AddCustomMealRequest
import net.whiteravens.dietapp.data.remote.AiSuggestIngredientRequest
import net.whiteravens.dietapp.data.remote.AiSwapOfferRequest
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.ApplyFavoriteSetRequest
import net.whiteravens.dietapp.data.remote.ApplyMealSwapRequest
import net.whiteravens.dietapp.data.remote.DayOverrideDto
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.GeneratePlanRequest
import net.whiteravens.dietapp.data.remote.LockedSlotDto
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.MealPlanPageDto
import net.whiteravens.dietapp.data.remote.ModelApiService
import net.whiteravens.dietapp.data.remote.NutritionDto
import net.whiteravens.dietapp.data.remote.RebalanceRequest
import net.whiteravens.dietapp.data.remote.RebalanceResultDto
import net.whiteravens.dietapp.data.remote.RegenerateRequest
import net.whiteravens.dietapp.data.remote.RelaxationNeededDto
import net.whiteravens.dietapp.data.remote.RestoreScaleDto
import net.whiteravens.dietapp.data.remote.SetMealEatenRequest
import net.whiteravens.dietapp.data.remote.SwapIngredientRequest
import net.whiteravens.dietapp.data.remote.SwapOfferRequest
import net.whiteravens.dietapp.data.remote.TakeDayVariationRequest
import net.whiteravens.dietapp.data.remote.WeighRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.DayAsk
import net.whiteravens.dietapp.domain.DayWay
import net.whiteravens.dietapp.domain.DayWays
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.MealPlanPage
import net.whiteravens.dietapp.domain.OverviewDay
import net.whiteravens.dietapp.domain.PlanAsk
import net.whiteravens.dietapp.domain.PlanCalendarDay
import net.whiteravens.dietapp.domain.PlanFilter
import net.whiteravens.dietapp.domain.PlanOverview
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.Rebalanced
import net.whiteravens.dietapp.domain.RelaxationNeeded
import net.whiteravens.dietapp.domain.RelaxedMeal
import net.whiteravens.dietapp.domain.Reroll
import net.whiteravens.dietapp.domain.Share
import net.whiteravens.dietapp.domain.Substitution
import net.whiteravens.dietapp.domain.SubstitutionPreview
import net.whiteravens.dietapp.domain.SuggestedSubstitute
import net.whiteravens.dietapp.domain.SwapAsk
import net.whiteravens.dietapp.domain.SwapOffer
import net.whiteravens.dietapp.domain.UseUpFound
import net.whiteravens.dietapp.domain.UseUpSwap
import net.whiteravens.dietapp.domain.UseUpTake
import net.whiteravens.dietapp.domain.WayMeal
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
    private val model: ModelApiService,
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
    /** What the profile's plan of the moment comes to; null for a profile with no plan. */
    suspend fun overview(profileId: String): PlanOverview? = apiCall(json) { api.planOverview(profileId) }.overview?.let {
        PlanOverview(
            planId = it.plan.id,
            startDate = it.plan.startDate,
            durationDays = it.plan.durationDays,
            dietType = it.plan.dietType,
            status = it.plan.status,
            energy = it.energy.planned,
            energyTarget = it.energy.target,
            macros = it.macros.planned.toDomain(),
            macrosTarget = it.macros.target.toDomain(),
            eaten = Share(it.eaten.meals, it.eaten.of),
            onDiet = Share(it.onDiet.meals, it.onDiet.of),
            favourites = Share(it.favourites.meals, it.favourites.of),
            ingredientReuse = it.ingredientReuse,
            shoppingLists = it.shopping.lists,
            shoppingEntries = it.shopping.entries,
            shoppingTicked = it.shopping.ticked,
            days = it.days.map { day -> OverviewDay(day.date, day.calories, day.target, day.skipped) },
        )
    }

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
     * A recipe for the meal as [ask] asks for one, other than those offered for it already ([seen]),
     * those the pantry covers first where [respectInventory]. Nothing is written.
     */
    suspend fun offerSwap(
        planId: String,
        mealId: String,
        ask: SwapAsk,
        seen: List<String> = emptyList(),
        respectInventory: Boolean = true,
    ): SwapOffer {
        if (ask.strategy == AI_STRATEGY) {
            val preview = apiCall(json) {
                model.aiOfferSwap(AiSwapOfferRequest(planId = planId, plannedMealId = mealId, respectInventory = respectInventory, skip = seen))
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
                    respectInventory = respectInventory,
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

    /**
     * Makes a plan, a part of it or one of its days again, as [reroll] asks; the plan is kept for
     * offline use. Refused with `PLAN_NEEDS_RELAXATION` where a limit stands in the way,
     * `LOCKED_RECIPE_INELIGIBLE` where a lock may no longer hold, and `NOTHING_TO_REGENERATE` where
     * every day asked for has passed.
     */
    suspend fun reroll(planId: String, reroll: Reroll): MealPlan {
        val body = RegenerateRequest(
            from = reroll.from,
            to = reroll.to,
            includePast = reroll.includePast,
            replaceEaten = reroll.replaceEaten,
            replaceCustom = reroll.replaceCustom,
            dropIneligibleLocks = reroll.dropIneligibleLocks,
            allowRelaxations = reroll.allowRelaxations,
        )
        val plan = apiCall(json) {
            if (reroll.dayId != null) api.regenerateDay(planId, reroll.dayId, body) else api.regeneratePlan(planId, body)
        }
        mirror(plan)
        return plan.toDomain()
    }

    /** Other ways to fill a day of a plan, put in order by a model where [weigh]. Nothing is written. */
    suspend fun dayWays(planId: String, dayId: String, weigh: Boolean = false): DayWays {
        val answer = apiCall(json) { model.dayVariations(planId, dayId, WeighRequest(weigh)) }
        return DayWays(
            calorieTarget = answer.calorieTarget,
            now = answer.current.dayNutrition.toDomain(),
            nowWasted = answer.current.packs.wasted,
            ways = answer.variations.map { way ->
                DayWay(
                    meals = way.meals.map { WayMeal(it.mealType, it.title, it.nutrition.calories, it.stays) },
                    nutrition = way.dayNutrition.toDomain(),
                    wasted = way.packs.wasted,
                    reason = way.reason,
                    offer = way.offer,
                )
            },
            weighed = answer.aiMeta != null && answer.aiMeta.fallbackReason == null,
            fallbackReason = answer.aiMeta?.fallbackReason,
        )
    }

    /** Takes one of the ways a day was offered; the plan is kept for offline use. A plan changed since the offer is refused with `PLAN_CHANGED`. */
    suspend fun takeDayWay(planId: String, dayId: String, offer: String): MealPlan {
        val plan = apiCall(json) { api.takeDayVariation(planId, dayId, TakeDayVariationRequest(offer)) }
        mirror(plan)
        return plan.toDomain()
    }

    /**
     * Puts the recipes of the favourite set [setId] on the days [dates] (yyyy-MM-dd) of a plan, each in
     * the place of the day's meal of its kind, the rest of the day as it was; the plan is kept for
     * offline use. Refused with `MEAL_EATEN` where a meal it would take the place of was eaten, and
     * with `FAVORITE_SET_NOT_ELIGIBLE` or `FAVORITE_SET_ALLERGEN_CONFLICT` where the profile now rules
     * out one of its recipes.
     */
    suspend fun applySet(setId: String, planId: String, dates: List<String>): MealPlan {
        val plan = apiCall(json) { api.applyFavoriteSet(setId, ApplyFavoriteSetRequest(planId, dates)) }
        mirror(plan)
        return plan.toDomain()
    }

    /** Swaps that would use up what the plan opens, put in order by a model where [weigh]. Nothing is written. */
    suspend fun useUp(planId: String, weigh: Boolean = false): UseUpFound {
        val answer = apiCall(json) { model.useUp(planId, WeighRequest(weigh)) }
        return UseUpFound(
            revision = answer.revision,
            swaps = answer.suggestions.map { suggestion ->
                UseUpSwap(
                    mealId = suggestion.plannedMealId,
                    date = suggestion.date,
                    slot = suggestion.mealType,
                    fromTitle = suggestion.from.title,
                    toId = suggestion.to.id,
                    toTitle = suggestion.to.title,
                    takes = suggestion.takes.map { UseUpTake(it.name, it.unit, it.amount, it.left) },
                    before = suggestion.before.toDomain(),
                    after = suggestion.after.toDomain(),
                    reason = suggestion.reason,
                    pick = suggestion.pick,
                )
            },
            weighed = answer.aiMeta != null && answer.aiMeta.fallbackReason == null,
            fallbackReason = answer.aiMeta?.fallbackReason,
        )
    }

    /** Takes a swap that uses up what the plan opens, as any swap is taken, on the plan as it was when it was found. */
    suspend fun takeUseUp(planId: String, revision: Int, swap: UseUpSwap): Rebalanced = kept(
        apiCall(json) {
            api.swapMeal(
                ApplyMealSwapRequest(
                    planId = planId,
                    plannedMealId = swap.mealId,
                    recipeId = swap.toId,
                    revision = revision,
                    strategy = RANDOM_STRATEGY,
                    pick = swap.pick,
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

    /** The ingredients the catalogue's own rules name in the place of those of a meal, by the ingredient they would replace. */
    suspend fun substitutes(planId: String, mealId: String): Map<String, IngredientChoice> =
        apiCall(json) { api.substitutes(planId, mealId) }.associate { it.fromIngredientId to IngredientChoice(it.to.id, it.to.name, it.to.category) }

    /** What [substitution] would do to its meal. Nothing is written. */
    suspend fun previewSubstitution(substitution: Substitution): SubstitutionPreview =
        apiCall(json) { api.previewSubstitution(substitution.toRequest()) }.toDomain()

    /**
     * Makes [substitution]: the meal gets a recipe of its own with the substitute in it, and the plan,
     * balanced around it, is kept for offline use. Where a model was asked to reword the text and the
     * engine's plain replacement stands instead, the answer says why.
     */
    suspend fun substitute(substitution: Substitution): Rebalanced {
        val result = apiCall(json) { model.substitute(substitution.toRequest()) }
        mirror(result.plan)
        return Rebalanced(result.plan.toDomain(), result.rebalance?.toDomain(), result.aiMeta?.fallbackReason)
    }

    /** An ingredient a model picked for the line of [fromIngredientId], with the note of its pick, or why the engine picked. */
    suspend fun suggestSubstitute(planId: String, mealId: String, fromIngredientId: String): SuggestedSubstitute {
        val answer = apiCall(json) { model.suggestSubstitute(AiSuggestIngredientRequest(planId, mealId, fromIngredientId)) }
        return SuggestedSubstitute(
            ingredient = IngredientChoice(answer.toIngredient.id, answer.toIngredient.name, answer.toIngredient.category),
            pick = answer.pick,
            reason = answer.aiMeta.reason.takeIf { answer.pick != null },
            fallbackReason = answer.aiMeta.fallbackReason,
        )
    }

    private fun Substitution.toRequest() = SwapIngredientRequest(
        planId = planId,
        plannedMealId = mealId,
        fromIngredientId = fromIngredientId,
        toIngredientId = toIngredientId,
        rewriteWithAi = rewriteWithAi,
        acceptChange = acceptChange,
        pick = pick,
    )

    /** A plan as a change left it, kept for offline use, with what the change rebalanced. */
    private suspend fun kept(result: RebalanceResultDto): Rebalanced {
        mirror(result.plan)
        return Rebalanced(result.plan.toDomain(), result.rebalance?.toDomain(), fallbackReason = null)
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
