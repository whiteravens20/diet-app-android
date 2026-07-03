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
)

data class Profile(
    val id: String,
    val name: String,
    val dietType: String,
    val mealCount: Int,
)

data class Nutrition(
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
)

data class CalorieTarget(
    val maintenance: Double,
    val dailyTarget: Double,
    val targetMacros: Nutrition,
    /** `calculated` | `manual_override` — lets the UI explain the number. */
    val source: String,
    val safetyFloorApplied: Boolean,
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
    /** Epoch millis of the cache row this came from; null when fresh off the network. */
    val syncedAt: Long?,
)
