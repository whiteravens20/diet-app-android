// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import net.whiteravens.dietapp.data.remote.AiMealSwapPreviewDto
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.remote.DaySettingsDto
import net.whiteravens.dietapp.data.remote.DaySettingsPreviewDto
import net.whiteravens.dietapp.data.remote.GenerationReportDto
import net.whiteravens.dietapp.data.remote.MacrosDto
import net.whiteravens.dietapp.data.remote.MealPlanDayDto
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.MealPlanPageDto
import net.whiteravens.dietapp.data.remote.MealPlanSummaryDto
import net.whiteravens.dietapp.data.remote.MealSwapPreviewDto
import net.whiteravens.dietapp.data.remote.NutritionDto
import net.whiteravens.dietapp.data.remote.PlannedIngredientDto
import net.whiteravens.dietapp.data.remote.PlannedMealDto
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.remote.RecipeDto
import net.whiteravens.dietapp.data.remote.RecipeIngredientDto
import net.whiteravens.dietapp.data.remote.RebalanceSummaryDto
import net.whiteravens.dietapp.data.remote.RecipeSearchPageDto
import net.whiteravens.dietapp.data.remote.SessionUserDto
import net.whiteravens.dietapp.data.remote.ShoppingListDto
import net.whiteravens.dietapp.data.remote.ShoppingListGroupDto
import net.whiteravens.dietapp.data.remote.ShoppingListItemDto
import net.whiteravens.dietapp.data.remote.SwapPreviewDto
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.DaySettings
import net.whiteravens.dietapp.domain.DaySettingsPreview
import net.whiteravens.dietapp.domain.Macros
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealFate
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.MealPlanPage
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.ModelPart
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.OpenPack
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.PlanPacks
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeIngredient
import net.whiteravens.dietapp.domain.RecipePage
import net.whiteravens.dietapp.domain.RecipeSource
import net.whiteravens.dietapp.domain.RowPacks
import net.whiteravens.dietapp.domain.ScaleChange
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.domain.SubstitutionPreview
import net.whiteravens.dietapp.domain.ShoppingGroup
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.domain.ShoppingList
import net.whiteravens.dietapp.domain.SwapAsk
import net.whiteravens.dietapp.domain.SwapOffer

/** DTO → domain mappers. The only place wire shapes and domain shapes meet. */

fun SessionUserDto.toDomain() = SessionUser(
    id = id,
    email = email,
    displayName = displayName,
    emailVerified = emailVerified,
    locale = locale,
    weightUnit = weightUnit,
    aiMode = aiMode,
)

fun ProfileDto.toDomain() = Profile(
    id = id,
    name = name,
    dietType = dietType,
    mealCount = mealCount,
    weightKg = weightKg,
    targetWeightKg = targetWeightKg,
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
    ingredientId = ingredientId,
    name = name,
    quantity = display.quantity,
    unit = display.unit,
    note = note,
)

/** Where the recipes of the library everyone shares come from: nobody's to edit. */
private val LIBRARY_ORIGINS = setOf("seed", "curated")

fun RecipeDto.toDomain() = Recipe(
    id = id,
    title = title,
    description = description,
    servings = servings,
    mealTypes = mealTypes,
    dietTags = dietTags,
    ingredients = ingredients.map(RecipeIngredientDto::toDomain),
    steps = steps,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    difficulty = difficulty,
    nutritionPerServing = nutritionPerServing.toDomain(),
    byModel = when (byModel) {
        "written" -> ModelPart.Written
        "changed" -> ModelPart.Changed
        else -> null
    },
    cuisine = cuisine,
    allergens = allergens,
    source = source?.let { RecipeSource(it.url, it.license) },
    own = origin !in LIBRARY_ORIGINS,
    fromSwap = origin == "swap",
    submitted = submitted,
)

fun RecipeSearchPageDto.toDomain() = RecipePage(
    items = items.map(RecipeDto::toDomain),
    page = page,
    totalPages = totalPages,
)

/**
 * A model's part in a planned meal: that it chose the meal's recipe comes first, since that is
 * about this meal; what it had to do with the recipe itself after.
 */
private fun PlannedMealDto.modelPart(): ModelPart? = when {
    modelPick != null -> ModelPart.Picked(modelPick.reason)
    recipe?.byModel == "written" -> ModelPart.Written
    recipe?.byModel == "changed" -> ModelPart.Changed
    else -> null
}

/** A meal of the day of [date], with what the generation [report] said of it. */
fun PlannedMealDto.toDomain(date: String, report: GenerationReportDto?): Meal {
    val custom = source == "USER_CUSTOM" || recipe == null
    // The report speaks of the recipes the generation placed: a meal of one's own was none of them.
    fun said(day: String, slot: String) = !custom && day == date && slot == mealType
    return Meal(
        id = id,
        slot = mealType,
        name = recipe?.title ?: customName.orEmpty(),
        recipeId = recipe?.id,
        servings = servings * quantityScale,
        eaten = eatenAt != null,
        offDiet = dietOverride,
        nutrition = nutrition.toDomain(),
        lines = ingredients.map(PlannedIngredientDto::toLine),
        allergens = recipe?.allergens.orEmpty(),
        conflicts = restrictionConflicts,
        disliked = ingredients.filter { it.ingredientId in dislikedIngredientIds }.map { it.name }.distinct(),
        custom = custom,
        quantityScale = quantityScale,
        cookAhead = cookAhead,
        byModel = modelPart(),
        outsideLimits = report?.relaxations?.firstOrNull { said(it.date, it.slot) }?.gaveWay.orEmpty(),
        onlyRecipe = report?.singleCandidate?.any { said(it.date, it.slot) } == true,
    )
}

fun MacrosDto.toDomain() = Macros(protein = protein, fat = fat, carbs = carbs)

/** A planned amount as a meal shows it: rounded, in the unit its kind of food is counted in. */
fun PlannedIngredientDto.toLine() = MealLine(ingredientId = ingredientId, name = name, quantity = display.quantity, unit = display.unit)

fun SwapPreviewDto.toDomain() = SubstitutionPreview(
    before = before.toDomain(),
    after = after.toDomain(),
    from = from.toLine(),
    to = to.toLine(),
    sizing = sizing,
    merged = merged,
    violations = violations,
    moved = moved,
    valid = valid,
)

fun MealPlanDayDto.toDomain(report: GenerationReportDto?) = PlanDay(
    id = id,
    date = date,
    meals = meals.map { it.toDomain(date, report) },
    nutrition = dayNutrition.toDomain(),
    calorieTarget = calorieTarget,
    calorieDelta = calorieDelta,
    skipped = skipped,
    offTarget = offTarget,
    belowSafetyFloor = belowSafetyFloor,
    targetMacros = targetMacros.toDomain(),
    locks = overrides?.lockedSlots.orEmpty().associate { it.mealType to it.recipeId },
    settings = mealCount?.let { count ->
        DaySettings(
            dayType = overrides?.dayType ?: "normal",
            skip = overrides?.skip == true,
            mealCount = count,
            calorieTarget = overrides?.calorieTarget,
            cookTimeMinutes = overrides?.cookTimeBudgetMinutes,
            useUpBy = overrides?.useUpBy == true,
        )
    },
)

fun DaySettingsPreviewDto.toDomain() = DaySettingsPreview(
    revision = revision,
    targetNow = calorieTarget.now,
    targetThen = calorieTarget.then,
    macrosThen = targetMacros.then.toDomain(),
    belowSafetyFloor = belowSafetyFloor,
    meals = meals.map { MealFate(it.mealId, it.mealType, it.fate, it.because) },
    added = added,
    dayCalories = dayCalories,
    fits = fits,
    listOutOfDate = listOutOfDate,
    unchanged = unchanged,
)

/** The settings as a request carries them; the revision and the limits that may give way go with a change only. */
fun DaySettings.toDto(revision: Int? = null, allowRelaxations: List<String>? = null) = DaySettingsDto(
    dayType = dayType,
    skip = skip,
    mealCount = mealCount,
    calorieTarget = calorieTarget,
    cookTimeBudgetMinutes = cookTimeMinutes,
    useUpBy = useUpBy,
    revision = revision,
    allowRelaxations = allowRelaxations,
)

fun MealPlanDto.toDomain(syncedAt: Long? = null) = MealPlan(
    id = id,
    profileId = profileId,
    startDate = startDate,
    durationDays = durationDays,
    dietType = dietType,
    days = days.map { it.toDomain(report) },
    averageDailyNutrition = averageDailyNutrition.toDomain(),
    syncedAt = syncedAt,
    targetMacros = targetMacros.toDomain(),
    ingredientReuse = ingredientReuseScore,
    packs = PlanPacks(
        bought = packs.bought,
        used = packs.used,
        open = packs.open.map { OpenPack(it.ingredientId, it.name, it.unit, it.packSize, it.packs, it.left, it.wasted) },
    ),
    byModel = generationMode == "ai_assisted",
    mayGiveWay = options.allowRelaxations,
    pantry = report?.pantry,
    revision = revision,
)

fun RebalanceSummaryDto.toDomain() = Rebalance(
    scope = scope,
    bestEffort = feasibility == "best-effort",
    changes = changes.map { ScaleChange(it.mealId, it.before, it.after) },
    unabsorbedKcal = unabsorbedKcal,
    caloriesBefore = macrosBefore.calories,
    caloriesAfter = macrosAfter.calories,
)

fun MealPlanSummaryDto.toDomain() = MealPlanSummary(
    id = id,
    startDate = startDate,
    durationDays = durationDays,
    dietType = dietType,
    averageDailyNutrition = averageDailyNutrition.toDomain(),
    ingredientReuse = ingredientReuseScore,
    byModel = generationMode == "ai_assisted",
)

fun MealPlanPageDto.toDomain(syncedAt: Long? = null) = MealPlanPage(
    plans = items.map(MealPlanSummaryDto::toDomain),
    page = page,
    totalPages = totalPages,
    syncedAt = syncedAt,
)

fun MealSwapPreviewDto.toDomain(planId: String, mealId: String, ask: SwapAsk, seen: List<String>) = SwapOffer(
    planId = planId,
    mealId = mealId,
    ask = ask,
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
    pick = null,
    reason = null,
    fallbackReason = null,
)

/** An offer a model was asked for: the note and the sentence of the model's pick, or why the engine picked instead. */
fun AiMealSwapPreviewDto.toDomain(planId: String, mealId: String, ask: SwapAsk, seen: List<String>) = SwapOffer(
    planId = planId,
    mealId = mealId,
    ask = ask,
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
    pick = pick,
    reason = aiMeta.reason.takeIf { pick != null },
    fallbackReason = aiMeta.fallbackReason,
)

fun ShoppingListItemDto.toDomain() = ShoppingItem(
    id = id,
    name = name,
    totalQuantity = totalQuantity,
    toBuyQuantity = toBuyQuantity,
    unit = unit,
    displayUnit = displayUnit,
    atHome = alreadyHaveQuantity,
    purchasedQuantity = purchasedQuantity,
    pantryBestBefore = pantryBestBefore,
    estimatedCalories = estimatedCalories,
    packs = packs?.let { RowPacks(it.count, it.size, it.sizeUnit, it.quantity) },
    checked = checked,
)

fun ShoppingListGroupDto.toDomain() = ShoppingGroup(
    category = category,
    items = items.map(ShoppingListItemDto::toDomain),
    estimatedCalories = estimatedCalories,
)

fun ShoppingListDto.toDomain(syncedAt: Long? = null) = ShoppingList(
    id = id,
    planId = planId,
    fromDate = fromDate,
    toDate = toDate,
    groups = groups.map(ShoppingListGroupDto::toDomain),
    totalEstimatedCalories = totalEstimatedCalories,
    stale = stale,
    createdAt = createdAt,
    syncedAt = syncedAt,
)
