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

/** A plan as a change left it, and what the change rebalanced, if it did. */
data class Rebalanced(val plan: MealPlan, val rebalance: Rebalance?)

/** A meal of the user's own: its name, its time of day, what one serving holds, and how many servings it is. */
data class CustomMeal(val name: String, val slot: String, val perServing: Nutrition, val servings: Double)

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

/** A page of a profile's plans, the newest first; [syncedAt] is set when it came from the cache. */
data class MealPlanPage(
    val plans: List<MealPlanSummary>,
    val page: Int,
    val totalPages: Int,
    val syncedAt: Long?,
)

data class RecipeIngredient(
    val name: String,
    val quantity: Double,
    /** `g` | `ml` | `piece`. */
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
    val totalMinutes: Int,
    /** `easy` | `medium` | `hard`. */
    val difficulty: String,
    val nutritionPerServing: Nutrition,
)

/** One page of recipe search results. */
data class RecipePage(
    val items: List<Recipe>,
    val page: Int,
    val totalPages: Int,
)

data class ShoppingItem(
    val id: String,
    val name: String,
    val toBuyQuantity: Double,
    /** `g` | `ml` | `piece`. */
    val unit: String,
    /** Pantry-covered portion — the "from pantry" chip. */
    val fromPantryQuantity: Double,
    val purchasedQuantity: Double?,
    val checked: Boolean,
)

data class ShoppingGroup(
    /** Product category key, doubles as the section header i18n key. */
    val category: String,
    val items: List<ShoppingItem>,
)

data class ShoppingList(
    val id: String,
    val planId: String,
    val fromDate: String,
    val toDate: String,
    val groups: List<ShoppingGroup>,
    /** The plan's menu changed after the list was made, so it no longer matches what the plan needs. */
    val stale: Boolean,
    /** Epoch millis of the cache row this came from; null when fresh off the network. */
    val syncedAt: Long?,
)

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
