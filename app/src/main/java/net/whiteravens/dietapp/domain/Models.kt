// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/**
 * Domain models — the app's own representation, decoupled from wire DTOs.
 * Repositories map `data.remote.*Dto` into these; DTOs never reach `ui`.
 * Deliberately leaner than the wire shapes: only what screens render. All
 * nutrition values are backend-computed and rendered as-is (the rule).
 */

data class SessionUser(
    val id: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    /** Platform locale code (`en`, `pl`, …). */
    val locale: String,
    /** `light` | `dark` | `system`. */
    val theme: String,
    /** `kg` | `lb`, or null for the unit of where the phone is. */
    val weightUnit: String? = null,
    /** `none` | `admin` | `byok`: whether the account has a model to ask, and whose. */
    val aiMode: String,
)

data class Profile(
    val id: String,
    val name: String,
    val dietType: String,
    val mealCount: Int,
)

/**
 * A profile's figures and choices as its form edits them, in the API's units (cm, kg): what its
 * calorie target is worked out from, and what its plans are made for. An empty one is null.
 */
data class ProfileFigures(
    val name: String,
    val age: Int,
    /** `male` | `female`, or null when not said. */
    val sex: String?,
    val heightCm: Double,
    val weightKg: Double,
    val targetWeightKg: Double?,
    val activityLevel: String,
    val dietType: String,
    /** `0.25` | `0.5` | `0.75` | `1.0` kg a week, or null for none. */
    val weeklyLossTarget: String?,
    val manualCalorieTarget: Int?,
    val mealCount: Int,
    val weightReminderCadence: String,
    /** The split of energy on the `custom` diet, in per cent; null on another. */
    val customMacroSplit: MacroSplit?,
    val allergens: List<String>,
)

data class MacroSplit(val protein: Int, val fat: Int, val carbs: Int)

/** What a profile would rather not have, beyond its allergens, and how often a meal may come back. */
data class ProfilePreferences(
    val favoriteIngredientIds: List<String>,
    val excludedIngredientIds: List<String>,
    val dislikedIngredientIds: List<String>,
    val preferredCuisines: List<String>,
    val maxConsecutiveDaysSameMeal: Int,
    val maxTimesPerWeekSameMeal: Int,
    /** The most of one ingredient, in g or ml, any seven days of a plan hold together. */
    val maxWeeklyAmountPerIngredient: Int,
    val inventoryBiasResetEvery: Int,
) {
    val limits: PlanLimits
        get() = PlanLimits(maxConsecutiveDaysSameMeal, maxTimesPerWeekSameMeal, maxWeeklyAmountPerIngredient, inventoryBiasResetEvery)

    fun with(limits: PlanLimits) = copy(
        maxConsecutiveDaysSameMeal = limits.maxConsecutiveDaysSameMeal,
        maxTimesPerWeekSameMeal = limits.maxTimesPerWeekSameMeal,
        maxWeeklyAmountPerIngredient = limits.maxWeeklyAmountPerIngredient,
        inventoryBiasResetEvery = limits.inventoryBiasResetEvery,
    )
}

/** A change of some of a profile's preferences: what is set replaces what was there, and the rest stays. */
data class PreferencesChange(
    val allergens: List<String>? = null,
    val favoriteIngredientIds: List<String>? = null,
    val excludedIngredientIds: List<String>? = null,
    val dislikedIngredientIds: List<String>? = null,
    val preferredCuisines: List<String>? = null,
)

/**
 * How often a plan may repeat itself: the same recipe on days in a row and in any seven days, the
 * most of one ingredient in any seven days, and after how many plans that leaned on the pantry the
 * next one does not (0: never).
 */
data class PlanLimits(
    val maxConsecutiveDaysSameMeal: Int,
    val maxTimesPerWeekSameMeal: Int,
    val maxWeeklyAmountPerIngredient: Int,
    val inventoryBiasResetEvery: Int,
)

/** A profile as it is kept: its figures, its preferences, and whether it said it understands the risk of its goal. */
data class ProfileDetails(
    val id: String,
    val figures: ProfileFigures,
    val preferences: ProfilePreferences,
    val riskAcknowledged: Boolean,
)

/**
 * What a calorie target is worked out from: the parts of a profile the API's engine reads, and
 * whether somebody under 18 or underweight who asks to lose weight has said they understand the risk.
 */
data class CalorieQuestion(
    val age: Int,
    val sex: String?,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: String,
    val dietType: String,
    val customMacroSplit: MacroSplit?,
    val weeklyLossTarget: String?,
    val manualCalorieTarget: Int?,
    val acknowledgeRisk: Boolean,
)

/** What figures come to: their calculation, and the same body at each pace of loss, slowest first. */
data class CaloriePreview(val calculation: CalorieTarget, val paces: List<CalorieTarget>)

/** An ingredient of the catalogue, as a picker offers it. */
data class IngredientChoice(val id: String, val name: String, val category: String)

data class Nutrition(
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
)

/** Grams of protein, fat and carbohydrate: what a plan or a day aims at under its diet's split. */
data class Macros(val protein: Double, val fat: Double, val carbs: Double)

/** What a model had to do with a meal: it chose the meal's recipe (and may have said why), or it wrote or changed the recipe. */
sealed interface ModelPart {
    data class Picked(val reason: String?) : ModelPart
    data object Written : ModelPart
    data object Changed : ModelPart
}

data class CalorieTarget(
    val bmr: Double,
    val maintenance: Double,
    /** kcal/day subtracted from maintenance — engine-computed, rendered as-is. */
    val dailyDeficit: Double,
    /** What the chosen pace asks for, before any limit. */
    val requestedDeficit: Double,
    val dailyTarget: Double,
    val targetMacros: Nutrition,
    /** `calculated` | `manual_override` — lets the UI explain the number. */
    val source: String,
    /** The lowest a calculated target goes for the profile. */
    val safetyFloor: Double,
    val safetyFloorApplied: Boolean,
    val deficitCapped: Boolean,
    val maintenanceUnderFloor: Boolean,
    val goalHeld: Boolean,
    /** `minor` | `underweight` | `below_floor` | `below_bmr`. */
    val warnings: List<String>,
)

/** One scheduled meal. [name] is the recipe title or the custom-meal name. */
data class Meal(
    val id: String,
    /** Meal slot: `breakfast`, `lunch`, `dinner`, `snack`, `second_breakfast`. */
    val slot: String,
    val name: String,
    val recipeId: String?,
    val servings: Double,
    val eaten: Boolean,
    /** Off the plan's diet type (swapped-in favourite) — render the chip. */
    val offDiet: Boolean,
    val nutrition: Nutrition,
    /** What the meal takes of each ingredient, as planned; empty for a meal of the user's own. */
    val lines: List<MealLine>,
    /** The allergens of the meal's recipe. */
    val allergens: List<String>,
    /** What the profile has ruled out since the meal was planned: `allergen` | `excluded_ingredient` | `avoided`. */
    val conflicts: List<String>,
    /** The names of the meal's ingredients the profile would rather not eat. */
    val disliked: List<String>,
    /** A meal of the user's own, with the macros they typed; [name] is empty where they gave it none. */
    val custom: Boolean,
    /** The multiplier a rebalance gave the meal; 1 where none did. */
    val quantityScale: Double,
    /** In a plan made for cooking ahead: `cook` on the meal cooked for the next one too, `cooked` on the one eaten from it. */
    val cookAhead: String?,
    /** What a model had to do with the meal, if anything. */
    val byModel: ModelPart?,
    /** The limits the meal went outside of when it was planned, because no recipe was left inside them. */
    val outsideLimits: List<String>,
    /** For this meal on the plan's diet the profile left one recipe to choose from. */
    val onlyRecipe: Boolean,
)

/** A line of a meal: an ingredient and its amount as the server rounds it and names its unit. */
data class MealLine(
    val ingredientId: String,
    val name: String,
    val quantity: Double,
    /** `g` | `ml` | `piece` | `slice` | `clove` | `handful`. */
    val unit: String,
)

data class PlanDay(
    val id: String,
    /** ISO date (yyyy-MM-dd). */
    val date: String,
    val meals: List<Meal>,
    val nutrition: Nutrition,
    val calorieTarget: Double,
    /** Signed; positive = over budget. */
    val calorieDelta: Double,
    val skipped: Boolean,
    /** `over` | `under` when the day is outside a tenth either side of its target; null inside it. */
    val offTarget: String?,
    /** A target somebody typed puts the day under the lowest a calculated plan goes. */
    val belowSafetyFloor: Boolean,
    /** What the day aims at in grams under the diet's split. */
    val targetMacros: Macros,
    /** The recipe each meal of the day was locked to when the plan was made, by meal. */
    val locks: Map<String, String>,
)

data class MealPlan(
    val id: String,
    val profileId: String,
    val startDate: String,
    val durationDays: Int,
    val dietType: String,
    val days: List<PlanDay>,
    val averageDailyNutrition: Nutrition,
    /** Epoch millis of the cache row this came from; null when fresh off the network. */
    val syncedAt: Long?,
    /** What a day of the plan aims at on average, in grams. */
    val targetMacros: Macros,
    /** 0-1: the share of the plan's ingredients that more than one of its recipes uses. */
    val ingredientReuse: Double,
    /** What the menu buys and uses of the packs that do not keep once opened. */
    val packs: PlanPacks,
    /** Parts of the plan were chosen with a model's help. */
    val byModel: Boolean,
    /** The limits the plan was let go outside of, in the order they give way. */
    val mayGiveWay: List<String>,
    /** What the pantry had to do with the choice of recipes: `counted` | `left_out` | `unused` | `empty` | `not_asked`; null on a plan from before it was kept. */
    val pantry: String?,
    /** How many times the plan has been changed; a change made from an older one is refused. */
    val revision: Int,
) {
    /** The meals that hold something the profile has ruled out since the plan was made. */
    val conflicts: Int get() = days.sumOf { day -> day.meals.count { it.conflicts.isNotEmpty() } }
}

/**
 * What a change of a plan did to the servings of its meals to keep the day, or the week, on its
 * target: each meal scaled, from what to what, what the day held before and after, whether the day
 * is still outside its window ([bestEffort]), and what the week could not take back, in kcal (over
 * where it is above nought).
 */
data class Rebalance(
    /** `day` | `week`. */
    val scope: String,
    val bestEffort: Boolean,
    val changes: List<ScaleChange>,
    val unabsorbedKcal: Double,
    val caloriesBefore: Double,
    val caloriesAfter: Double,
)

/** A meal's servings scaled by a rebalance: [before] and [after] are its scales. */
data class ScaleChange(val mealId: String, val before: Double, val after: Double)

/**
 * A plan as a change left it, what the change rebalanced, if it did, and why the engine stood in for
 * a model where one was asked to do part of the change ([fallbackReason]).
 */
data class Rebalanced(val plan: MealPlan, val rebalance: Rebalance?, val fallbackReason: String?)

/**
 * A substitution of one ingredient of a meal for another: in [mealId] of [planId], the line of
 * [fromIngredientId] takes [toIngredientId]; a model rewords the text where [rewriteWithAi], a
 * substitution that moves what a serving holds by more than a tenth is made where [acceptChange],
 * and [pick] is the note of a substitute a model suggested.
 */
data class Substitution(
    val planId: String,
    val mealId: String,
    val fromIngredientId: String,
    val toIngredientId: String,
    val rewriteWithAi: Boolean = false,
    val acceptChange: Boolean = false,
    val pick: String? = null,
)

/**
 * What a substitution would do to its meal, the portion on the plate: what the meal holds before
 * and after, what it takes of the ingredient that leaves and of the one that comes, how the amount
 * was decided (`energy` | `capped` | `mass`), whether the recipe has the substitute already, what
 * stands in its way (`off_diet` | `allergen` | `excluded_ingredient`) and what of a serving it moves
 * by more than a tenth.
 */
data class SubstitutionPreview(
    val before: Nutrition,
    val after: Nutrition,
    val from: MealLine,
    val to: MealLine,
    val sizing: String,
    val merged: Boolean,
    val violations: List<String>,
    val moved: List<String>,
    val valid: Boolean,
)

/** A substitute a model was asked for: the ingredient, and the note and sentence of the model's pick, or why the engine picked. */
data class SuggestedSubstitute(val ingredient: IngredientChoice, val pick: String?, val reason: String?, val fallbackReason: String?)

/** A meal of the user's own: its name, its time of day, what one serving holds, and how many servings it is. */
data class CustomMeal(val name: String, val slot: String, val perServing: Nutrition, val servings: Double)

/**
 * Other ways to fill a day: the day as it is (what it holds and how many packs the plan leaves more
 * than a fifth of with it), and up to three ways that differ from it, in a model's order where one
 * was asked and answered, or the engine's with why ([fallbackReason]); [weighed] where a model put them in order.
 */
data class DayWays(
    val calorieTarget: Double,
    val now: Nutrition,
    val nowWasted: Int,
    val ways: List<DayWay>,
    val weighed: Boolean,
    val fallbackReason: String?,
)

/** One other way to fill a day: its meals, what it holds, the packs the plan would then leave, a model's sentence on it, and the note that takes it. */
data class DayWay(val meals: List<WayMeal>, val nutrition: Nutrition, val wasted: Int, val reason: String?, val offer: String)

/** A meal of another way to fill a day; [stays] where it is on the day already: eaten, or the user's own. */
data class WayMeal(val slot: String, val title: String, val calories: Double, val stays: Boolean)

/**
 * Swaps that would use up what a plan opens, for the plan at [revision]: one for a meal, the one
 * that leaves the least first, in a model's order where one was asked and answered ([weighed]), or
 * the engine's with why ([fallbackReason]).
 */
data class UseUpFound(val revision: Int, val swaps: List<UseUpSwap>, val weighed: Boolean, val fallbackReason: String?)

/**
 * A recipe in the place of a meal that would use up what the plan opens: the meal, the recipe it
 * holds and the one that would come, what it takes more of each pack and what would be left of it,
 * what the meal holds before and after, a model's sentence on it, and the note of a model's pick.
 */
data class UseUpSwap(
    val mealId: String,
    val date: String,
    val slot: String,
    val fromTitle: String,
    val toId: String,
    val toTitle: String,
    val takes: List<UseUpTake>,
    val before: Nutrition,
    val after: Nutrition,
    val reason: String?,
    val pick: String?,
)

/** What a swap takes more of a pack, and what would be left of the pack. */
data class UseUpTake(val name: String, val unit: String, val amount: Int, val left: Int)

/** What a plan's menu buys and uses of the packs that do not keep once opened, and the packs it does not finish. */
data class PlanPacks(val bought: Double, val used: Double, val open: List<OpenPack>)

/** A pack the menu opens and does not finish: what is left of the last one, and whether that is more than a fifth of it. */
data class OpenPack(
    val ingredientId: String,
    val name: String,
    /** `g` | `ml`. */
    val unit: String,
    val packSize: Double,
    val packs: Int,
    val left: Int,
    val wasted: Boolean,
)

/**
 * How a recipe is asked for in the place of a meal: [strategy] is `random` (any recipe of the
 * plan's diet for the meal), `favorite` (the favourite [favoriteRecipeId] picked by hand),
 * `favorite_ingredients` (one that uses most of the profile's favourite ingredients) or `ai` (a
 * model picks among those the engine puts first). [allowOffDiet] is "show all my favourites":
 * the plan's diet is no bar, nor, for a favourite picked by hand, the meal's time of day.
 */
data class SwapAsk(val strategy: String, val favoriteRecipeId: String? = null, val allowOffDiet: Boolean = false)

/**
 * A recipe offered in the place of a meal, and what taking it would do. Nothing is written until
 * it is taken; [revision] is the plan's at the time of the offer, and [seen] the recipes offered
 * for the meal so far, which the next offer leaves out. An offer a model was asked for carries the
 * note of the model's [pick] with its [reason], or why the engine picked instead ([fallbackReason]).
 */
data class SwapOffer(
    val planId: String,
    val mealId: String,
    val ask: SwapAsk,
    val recipeId: String,
    val recipeTitle: String,
    val servings: Double,
    val before: Nutrition,
    val after: Nutrition,
    val dayBefore: Nutrition,
    val dayAfter: Nutrition,
    val calorieTarget: Double,
    val revision: Int,
    val seen: List<String>,
    val pick: String?,
    val reason: String?,
    val fallbackReason: String?,
)

/** One plan in a profile's list: what the list shows, without the days. */
data class MealPlanSummary(
    val id: String,
    val startDate: String,
    val durationDays: Int,
    val dietType: String,
    val averageDailyNutrition: Nutrition,
    /** 0-1: the share of the plan's ingredients that more than one of its recipes uses. */
    val ingredientReuse: Double,
    /** Parts of the plan were chosen with a model's help. */
    val byModel: Boolean,
)

/**
 * What a profile's plan of the moment comes to: the plan under way, else the one that begins next,
 * else the one that ended last ([status]). Every figure is the API's: what a day holds and aims at
 * on average, the meals eaten of those due, those that fit the diet and those of favourites, the
 * ingredients used again, how far the shopping is, and the days one by one.
 */
data class PlanOverview(
    val planId: String,
    val startDate: String,
    val durationDays: Int,
    val dietType: String,
    /** `active` | `upcoming` | `past`. */
    val status: String,
    val energy: Double,
    val energyTarget: Double,
    val macros: Macros,
    val macrosTarget: Macros,
    val eaten: Share,
    val onDiet: Share,
    val favourites: Share,
    /** 0-1: the share of the plan's ingredients that more than one of its recipes uses. */
    val ingredientReuse: Double,
    val shoppingLists: Int,
    val shoppingEntries: Int,
    val shoppingTicked: Int,
    val days: List<OverviewDay>,
)

/** [meals] out of [of]. */
data class Share(val meals: Int, val of: Int) {
    /** The share as a part of one; none where there is nothing to count. */
    val part: Double get() = if (of == 0) 0.0 else meals.toDouble() / of
}

/** A day of a plan of the moment: what it holds and aims at, in kilocalories, or that it is skipped. */
data class OverviewDay(val date: String, val calories: Double, val target: Double, val skipped: Boolean)

/** A page of a profile's plans, the newest first; [syncedAt] is set when it came from the cache. */
data class MealPlanPage(
    val plans: List<MealPlanSummary>,
    val page: Int,
    val totalPages: Int,
    val syncedAt: Long?,
)

/** A line of a recipe in the amount the API shows it: rounded, in the unit its kind of food is counted in. */
data class RecipeIngredient(
    val name: String,
    val quantity: Double,
    /** `g` | `ml` | `piece` | `slice` | `clove` | `handful`. */
    val unit: String,
    val note: String?,
)

data class Recipe(
    val id: String,
    val title: String,
    val description: String,
    val servings: Int,
    /** Meal slots this recipe fits (`breakfast`, `lunch`, …). */
    val mealTypes: List<String>,
    val dietTags: List<String>,
    val ingredients: List<RecipeIngredient>,
    val steps: List<String>,
    val prepMinutes: Int,
    val cookMinutes: Int,
    /** `easy` | `medium` | `hard`. */
    val difficulty: String,
    val nutritionPerServing: Nutrition,
    /** What a model had to do with the recipe: wrote it, or changed it; null where nothing. */
    val byModel: ModelPart?,
    /** The kitchen the recipe belongs to, where it belongs to one. */
    val cuisine: String?,
    val allergens: List<String>,
    /** Where a recipe taken in from somewhere else is from; null for one written here. */
    val source: RecipeSource?,
) {
    val totalMinutes: Int get() = prepMinutes + cookMinutes
}

/** Where a recipe taken in from somewhere else is from ([url], where there is one) and the licence it may be passed on under. */
data class RecipeSource(val url: String?, val license: String)

/**
 * How the recipe library is narrowed; every part left empty leaves it open. What a profile marked
 * and its favourite ingredients are asked about with [profileId].
 */
data class RecipeFilter(
    val search: String = "",
    val dietType: String? = null,
    val mealType: String? = null,
    val difficulty: String? = null,
    val maxCalories: Int? = null,
    val maxPrepMinutes: Int? = null,
    val cuisine: String? = null,
    /** What a serving holds, in grams: at least this much protein, at most this much fat and carbohydrate. */
    val minProtein: Double? = null,
    val maxFat: Double? = null,
    val maxCarbs: Double? = null,
    /** Ingredients every recipe of the result holds, with their names to show. */
    val ingredients: List<IngredientChoice> = emptyList(),
    /** Allergens no recipe of the result holds. */
    val withoutAllergens: List<String> = emptyList(),
    val profileId: String? = null,
    /** `marked` | `often` | `avoid`. */
    val marked: String? = null,
    val favoriteIngredients: Boolean = false,
) {
    /** How many ways of narrowing are at work, the words aside. */
    val atWork: Int get() = listOfNotNull(dietType, mealType, difficulty, maxCalories, maxPrepMinutes, marked).size + more + (if (favoriteIngredients) 1 else 0)

    /** How many of the narrower ways are at work. */
    val more: Int get() = listOfNotNull(cuisine, minProtein, maxFat, maxCarbs).size + (if (ingredients.isNotEmpty()) 1 else 0) + (if (withoutAllergens.isNotEmpty()) 1 else 0)

    /** Whether anything but the words narrows the library. */
    val narrowed: Boolean get() = dietType != null || mealType != null || difficulty != null || maxCalories != null || maxPrepMinutes != null || more > 0 || marked != null || favoriteIngredients

    /** The filter with only the words left. */
    fun cleared(): RecipeFilter = RecipeFilter(search = search, profileId = profileId)

    companion object {
        /** The most ingredients a search may ask every recipe to hold. */
        const val MAX_INGREDIENTS = 10
    }
}

/**
 * How a profile's marked recipes are narrowed and put in order: by words of the title, a meal, how
 * they are marked (`favorite` | `often` | `avoid`) and a tag of the profile's; the mark made last
 * first (`newest`), by title (`title`), or the lightest serving first (`calories`).
 */
data class FavoriteFilter(
    val search: String = "",
    val mealType: String? = null,
    val sentiment: String? = null,
    val tag: String? = null,
    val sort: String = NEWEST,
) {
    /** Whether anything narrows the list; the order does not. */
    val narrowed: Boolean get() = search.isNotBlank() || mealType != null || sentiment != null || tag != null

    companion object {
        /** The order the API lists marks in where none is asked for. */
        const val NEWEST = "newest"

        /** The orders the API takes, as the web lists them. */
        val SORTS = listOf(NEWEST, "title", "calories")

        /** The ways a profile marks a recipe, as the web lists them. */
        val SENTIMENTS = listOf("favorite", "often", "avoid")

        /** The longest the words searched for may be. */
        const val SEARCH_LENGTH = 200

        /** The most tags a marked recipe holds, and the longest a tag is: the API's own bounds. */
        const val MOST_TAGS = 20
        const val TAG_LENGTH = 40
    }
}

/**
 * The choices a model writes a recipe of one's own from, for [profileId]: each part left null is the
 * model's to choose. The profile's allergens are always left out; its excluded ingredients where
 * [respectExclusions], and its favourite ingredients are put first where [useFavoriteIngredients].
 */
data class RecipeDraftAsk(
    val profileId: String,
    val dietType: String,
    val mealType: String? = null,
    val cuisine: String? = null,
    val cookingMethod: String? = null,
    val complexity: String? = null,
    val kcalTarget: Int? = null,
    val prepMinutes: Int? = null,
    val useFavoriteIngredients: Boolean = false,
    val respectExclusions: Boolean = true,
    val addToFavorites: Boolean = true,
)

/** One page of recipe search results. */
data class RecipePage(
    val items: List<Recipe>,
    val page: Int,
    val totalPages: Int,
)

/**
 * A row of a shopping list: one ingredient summed across the meals of the list's days, every
 * quantity in [unit]. What is at home ([atHome]) starts at what the pantry had when the list was
 * made and is the user's to correct; [purchasedQuantity] is everything the user has for the row,
 * the part at home included, and null until the row is first changed.
 */
data class ShoppingItem(
    val id: String,
    val name: String,
    /** How much the plan needs, rounded up to what one buys. */
    val totalQuantity: Double,
    val toBuyQuantity: Double,
    /** `g` | `ml` | `piece`. */
    val unit: String,
    /** What to call [unit] when showing the row: `slice` for bread, `clove` for garlic. */
    val displayUnit: String,
    val atHome: Double,
    val purchasedQuantity: Double?,
    /** The earliest best-before of the pantry's rows the part at home came from. */
    val pantryBestBefore: String?,
    val estimatedCalories: Double,
    /** What is left to buy in the packs the ingredient is sold in; null where it is not sold in packs, or nothing is left to buy. */
    val packs: RowPacks?,
    val checked: Boolean,
) {
    /** Everything the user has for the row. */
    val have: Double get() = purchasedQuantity ?: atHome

    /** What is still to be obtained for the row: its need less everything the user has for it. */
    val stillToBuy: Double get() = (totalQuantity - have).coerceAtLeast(0.0)

    /**
     * Whether [packs] are what is still to be bought of the row: they are counted from the need less
     * what is at home, so they say nothing once a part of the rest was bought.
     */
    val packsStand: Boolean get() = packs != null && !checked && have == atHome
}

/**
 * What changes of a shopping row, null for a part that stays: its tick, what is at home, and
 * everything the user has for it, the part at home included, 0 where that is cleared.
 */
data class RowChange(val checked: Boolean? = null, val atHome: Double? = null, val have: Double? = null) {
    val isEmpty: Boolean get() = checked == null && atHome == null && have == null
}

/**
 * What a shopping list could do without: the proposals that save the most first, or in a model's
 * order where one was asked and answered ([weighed]), or the engine's with why ([fallbackReason]).
 */
data class ListProposals(val proposals: List<ListProposal>, val weighed: Boolean, val fallbackReason: String?)

/** Something a shopping list could do without, with a model's sentence on it where one was asked. */
sealed interface ListProposal {
    val reason: String?

    /** [to] in the place of [from] in [meals] of the list's days: [packsSaved] fewer packs to buy. Made in the plan, meal by meal. */
    data class Merge(val from: String, val to: String, val meals: List<ProposalMeal>, val packsSaved: Int, override val reason: String?) : ListProposal

    /** [left] of a pack of [packSize] of [name] will be left, in [unit], and an opened pack of it does not keep. */
    data class Leftover(val name: String, val unit: String, val left: Int, val packSize: Double, override val reason: String?) : ListProposal
}

/** A meal of a list's days that a proposal names. */
data class ProposalMeal(val date: String, val slot: String, val title: String)

/** [count] packs of [size] [sizeUnit] each, which give the row [quantity] in its own unit. */
data class RowPacks(val count: Int, val size: Double, val sizeUnit: String, val quantity: Double)

data class ShoppingGroup(
    /** Product category key, doubles as the section header i18n key. */
    val category: String,
    val items: List<ShoppingItem>,
    /** What the entries of the aisle come to in kilocalories, an estimate as each of theirs is. */
    val estimatedCalories: Double,
)

data class ShoppingList(
    val id: String,
    val planId: String,
    val fromDate: String,
    val toDate: String,
    val groups: List<ShoppingGroup>,
    val totalEstimatedCalories: Double,
    /** The plan's menu changed after the list was made, so it no longer matches what the plan needs. */
    val stale: Boolean,
    val createdAt: String,
    /** Epoch millis of the cache row this came from; null when fresh off the network. */
    val syncedAt: Long?,
) {
    val rows: Int get() = groups.sumOf { it.items.size }
    val ticked: Int get() = groups.sumOf { group -> group.items.count { it.checked } }

    /** Whether the list is for a day of the days from [from] to [to] (yyyy-MM-dd). */
    fun meets(from: String, to: String): Boolean = fromDate <= to && from <= toDate
}

/** What the app knows of the version of the server it talks to. */
sealed interface ServerVersion {
    /** Not asked yet, or no answer: nothing to say. */
    data object Unknown : ServerVersion

    /** The version this build was checked against. */
    data object Checked : ServerVersion

    /** A version this build was not checked against; the app talks to it all the same. */
    data class Other(val server: String, val checkedAgainst: String) : ServerVersion

    /** An answer the app cannot read: a version it does not support. */
    data object Unsupported : ServerVersion
}

/** What answers at an address typed for the server, asked before the address is kept. */
sealed interface ServerProbe {
    /** A Diet App API answers there. */
    data object Answers : ServerProbe

    /** Its API answers there, but its database is down. */
    data object DatabaseDown : ServerProbe

    /** Something answers there that is not a Diet App API: a website, a proxy's page, another service. */
    data object NotTheApi : ServerProbe

    /** No server has that name. */
    data object UnknownHost : ServerProbe

    /** Nothing answers there, or not in time. */
    data object NoAnswer : ServerProbe

    /** The server's certificate is not one the phone trusts. */
    data object Untrusted : ServerProbe
}

/** What a sign-in came to: a session, or the second step an account with two-step sign-in asks for. */
sealed interface SignInResult {
    data object SignedIn : SignInResult

    /** [challenge] is answered with a code from the authenticator app or a recovery code. */
    data class SecondStep(val challenge: String) : SignInResult
}
