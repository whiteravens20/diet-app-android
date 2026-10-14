// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.remote.MealPlanDayDto
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.MealPlanPageDto
import net.whiteravens.dietapp.data.remote.MealPlanSummaryDto
import net.whiteravens.dietapp.data.remote.MealSwapPreviewDto
import net.whiteravens.dietapp.data.remote.NutritionDto
import net.whiteravens.dietapp.data.remote.PlannedMealDto
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.remote.RecipeDto
import net.whiteravens.dietapp.data.remote.RecipeIngredientDto
import net.whiteravens.dietapp.data.remote.RecipeSearchPageDto
import net.whiteravens.dietapp.data.remote.SessionUserDto
import net.whiteravens.dietapp.data.remote.ShoppingListDto
import net.whiteravens.dietapp.data.remote.ShoppingListGroupDto
import net.whiteravens.dietapp.data.remote.ShoppingListItemDto
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.MealPlanPage
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeIngredient
import net.whiteravens.dietapp.domain.RecipePage
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.domain.ShoppingGroup
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.domain.ShoppingList
import net.whiteravens.dietapp.domain.SwapOffer

/** DTO → domain mappers. The only place wire shapes and domain shapes meet. */

fun SessionUserDto.toDomain() = SessionUser(
    id = id,
    email = email,
    displayName = displayName,
    emailVerified = emailVerified,
    locale = locale,
    theme = theme,
)

fun ProfileDto.toDomain() = Profile(
    id = id,
    name = name,
    dietType = dietType,
    mealCount = mealCount,
)

fun NutritionDto.toDomain() = Nutrition(
    calories = calories,
    protein = protein,
    fat = fat,
    carbs = carbs,
)

fun CalorieCalculationDto.toDomain() = CalorieTarget(
    bmr = bmr,
    maintenance = maintenance,
    dailyDeficit = dailyDeficit,
    requestedDeficit = requestedDeficit,
    dailyTarget = dailyTarget,
    targetMacros = Nutrition(
        calories = dailyTarget,
        protein = targetMacros.protein,
        fat = targetMacros.fat,
        carbs = targetMacros.carbs,
    ),
    source = source,
    safetyFloor = safetyFloor,
    safetyFloorApplied = safetyFloorApplied,
    deficitCapped = deficitCapped,
    maintenanceUnderFloor = maintenanceUnderFloor,
    goalHeld = goalHeld,
    warnings = warnings,
)

fun RecipeIngredientDto.toDomain() = RecipeIngredient(
    name = name,
    quantity = quantity,
    unit = unit,
    note = note,
)

fun RecipeDto.toDomain() = Recipe(
    id = id,
    title = title,
    description = description,
    servings = servings,
    mealTypes = mealTypes,
    dietTags = dietTags,
    ingredients = ingredients.map(RecipeIngredientDto::toDomain),
    steps = steps,
    totalMinutes = prepMinutes + cookMinutes,
    difficulty = difficulty,
    nutritionPerServing = nutritionPerServing.toDomain(),
)

fun RecipeSearchPageDto.toDomain() = RecipePage(
    items = items.map(RecipeDto::toDomain),
    page = page,
    totalPages = totalPages,
)

fun PlannedMealDto.toDomain() = Meal(
    id = id,
    slot = mealType,
    name = recipe?.title ?: customName.orEmpty(),
    recipeId = recipe?.id,
    servings = servings * quantityScale,
    eaten = eatenAt != null,
    offDiet = dietOverride,
    nutrition = nutrition.toDomain(),
    lines = ingredients.map { MealLine(name = it.name, quantity = it.display.quantity, unit = it.display.unit) },
    allergens = recipe?.allergens.orEmpty(),
    conflicts = restrictionConflicts,
    disliked = ingredients.filter { it.ingredientId in dislikedIngredientIds }.map { it.name }.distinct(),
)

fun MealPlanDayDto.toDomain() = PlanDay(
    id = id,
    date = date,
    meals = meals.map(PlannedMealDto::toDomain),
    nutrition = dayNutrition.toDomain(),
    calorieTarget = calorieTarget,
    calorieDelta = calorieDelta,
    skipped = skipped,
    offTarget = offTarget,
    belowSafetyFloor = belowSafetyFloor,
)

fun MealPlanDto.toDomain(syncedAt: Long? = null) = MealPlan(
    id = id,
    profileId = profileId,
    startDate = startDate,
    durationDays = durationDays,
    dietType = dietType,
    days = days.map(MealPlanDayDto::toDomain),
    averageDailyNutrition = averageDailyNutrition.toDomain(),
    syncedAt = syncedAt,
)

fun MealPlanSummaryDto.toDomain() = MealPlanSummary(
    id = id,
    startDate = startDate,
    durationDays = durationDays,
    dietType = dietType,
    averageDailyNutrition = averageDailyNutrition.toDomain(),
)

fun MealPlanPageDto.toDomain(syncedAt: Long? = null) = MealPlanPage(
    plans = items.map(MealPlanSummaryDto::toDomain),
    page = page,
    totalPages = totalPages,
    syncedAt = syncedAt,
)

fun MealSwapPreviewDto.toDomain(planId: String, mealId: String, seen: List<String>) = SwapOffer(
    planId = planId,
    mealId = mealId,
    recipeId = recipe.id,
    recipeTitle = recipe.title,
    servings = servings,
    before = before.toDomain(),
    after = after.toDomain(),
    dayBefore = dayBefore.toDomain(),
    dayAfter = dayAfter.toDomain(),
    calorieTarget = calorieTarget,
    revision = revision,
    seen = seen,
)

/** What the list of plans shows of a plan that was just made. */
fun MealPlan.toSummary() = MealPlanSummary(
    id = id,
    startDate = startDate,
    durationDays = durationDays,
    dietType = dietType,
    averageDailyNutrition = averageDailyNutrition,
)

fun ShoppingListItemDto.toDomain() = ShoppingItem(
    id = id,
    name = name,
    toBuyQuantity = toBuyQuantity,
    unit = unit,
    fromPantryQuantity = alreadyHaveQuantity,
    purchasedQuantity = purchasedQuantity,
    checked = checked,
)

fun ShoppingListGroupDto.toDomain() = ShoppingGroup(
    category = category,
    items = items.map(ShoppingListItemDto::toDomain),
)

fun ShoppingListDto.toDomain(syncedAt: Long? = null) = ShoppingList(
    id = id,
    planId = planId,
    fromDate = fromDate,
    toDate = toDate,
    groups = groups.map(ShoppingListGroupDto::toDomain),
    stale = stale,
    syncedAt = syncedAt,
)
