// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.AiDraftRecipeRequest
import net.whiteravens.dietapp.data.remote.AlreadyYoursDto
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.IngredientDto
import net.whiteravens.dietapp.data.remote.ModelApiService
import net.whiteravens.dietapp.data.remote.OwnRecipeLineDto
import net.whiteravens.dietapp.data.remote.OwnRecipePreviewRequest
import net.whiteravens.dietapp.data.remote.OwnRecipeRequest
import net.whiteravens.dietapp.data.remote.RecipeProblemsDto
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.LineIngredient
import net.whiteravens.dietapp.domain.OwnRecipe
import net.whiteravens.dietapp.domain.OwnRecipeLine
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeDraftAsk
import net.whiteravens.dietapp.domain.RecipeFilter
import net.whiteravens.dietapp.domain.RecipePage
import net.whiteravens.dietapp.domain.RecipePreview
import net.whiteravens.dietapp.domain.RecipeProblem
import net.whiteravens.dietapp.domain.RecipeToEdit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recipe catalogue browsing, and the account's own recipes drafted by a model or written and edited
 * by hand — network-only. The catalogue is large and server-paginated, so it is not mirrored into
 * the offline cache; recipes a user actually needs offline arrive embedded in their cached meal plans.
 */
@Singleton
class RecipeRepository @Inject constructor(
    private val api: DietApiService,
    private val model: ModelApiService,
    private val json: Json,
) {
    /** A page of the library as [filter] narrows it; what a profile marked and favours is asked about with the profile alone. */
    suspend fun search(filter: RecipeFilter = RecipeFilter(), page: Int = 1): RecipePage {
        val aboutProfile = filter.profileId.takeIf { filter.marked != null || filter.favoriteIngredients }
        return apiCall(json) {
            api.recipes(
                search = filter.search.trim().ifEmpty { null },
                dietType = filter.dietType,
                mealType = filter.mealType,
                difficulty = filter.difficulty,
                maxCalories = filter.maxCalories,
                maxPrepMinutes = filter.maxPrepMinutes,
                cuisine = filter.cuisine,
                minProtein = filter.minProtein,
                maxFat = filter.maxFat,
                maxCarbs = filter.maxCarbs,
                ingredientIds = filter.ingredients.joinToString(",") { it.id }.ifEmpty { null },
                withoutAllergens = filter.withoutAllergens.joinToString(",").ifEmpty { null },
                profileId = aboutProfile,
                marked = filter.marked.takeIf { aboutProfile != null },
                favoriteIngredients = true.takeIf { aboutProfile != null && filter.favoriteIngredients },
                page = page,
            )
        }.toDomain()
    }

    /** A recipe of one's own a model writes from [ask]; the engine works out its nutrition. */
    suspend fun draft(ask: RecipeDraftAsk): Recipe = apiCall(json) {
        model.draftRecipe(
            AiDraftRecipeRequest(
                profileId = ask.profileId,
                mealType = ask.mealType,
                dietType = ask.dietType,
                cuisine = ask.cuisine,
                cookingMethod = ask.cookingMethod,
                complexity = ask.complexity,
                kcalTarget = ask.kcalTarget,
                prepTimeMaxMinutes = ask.prepMinutes,
                useFavoriteIngredients = ask.useFavoriteIngredients,
                respectExclusions = ask.respectExclusions,
                addToFavorites = ask.addToFavorites,
            ),
        )
    }.recipe.toDomain()

    /** A page of the account's own recipes. */
    suspend fun mine(page: Int = 1): RecipePage = apiCall(json) { api.myRecipes(page = page) }.toDomain()

    /** Puts a recipe of one's own before the curators; asking again changes nothing. */
    suspend fun submit(id: String) = apiCall(json) { api.submitRecipe(id) }

    /** Deletes a recipe of one's own; deleting it again changes nothing. */
    suspend fun delete(id: String) = apiCall(json) { api.deleteRecipe(id) }

    suspend fun byId(id: String): Recipe = apiCall(json) { api.recipe(id) }.toDomain()

    /** The catalogue's ingredients whose name holds [search], as a recipe written by hand takes them. */
    suspend fun ingredients(search: String): List<LineIngredient> =
        apiCall(json) { api.ingredients(search = search) }.map { it.toLine() }

    /**
     * What a serving of [servings] of [lines] would hold, with the allergens and the diets: the
     * notes say nothing of it, so they are not sent.
     */
    suspend fun preview(servings: Int, lines: List<OwnRecipeLine>): RecipePreview {
        val answer = apiCall(json) { api.previewOwnRecipe(OwnRecipePreviewRequest(servings, lines.map { it.copy(note = null).toDto() })) }
        return RecipePreview(answer.nutritionPerServing.toDomain(), answer.allergens, answer.dietTags)
    }

    /** Writes [recipe] as one of the account's own, in the language of the app's texts; answers it as it is kept. */
    suspend fun write(recipe: OwnRecipe): Recipe = apiCall(json) { api.writeRecipe(recipe.toDto()) }.toDomain()

    /** Writes [recipe] as a new version of the account's recipe [id]; answers the new version, under an id of its own. */
    suspend fun edit(id: String, recipe: OwnRecipe): Recipe = apiCall(json) { api.editRecipe(id, recipe.toDto()) }.toDomain()

    /**
     * The account's recipe [id] as its form starts on it: its lines in the amounts it keeps, each
     * with its ingredient and the units the catalogue gives it in. An ingredient the catalogue no
     * longer names keeps the unit its line is in, and saving then says it is gone.
     */
    suspend fun toEdit(id: String): RecipeToEdit {
        val recipe = apiCall(json) { api.recipe(id) }
        val ids = recipe.ingredients.map { it.ingredientId }.distinct()
        val found = apiCall(json) { api.ingredients(ids = ids.joinToString(",")) }.associateBy { it.id }
        val kept = OwnRecipe(
            title = recipe.title,
            description = recipe.description,
            steps = recipe.steps,
            servings = recipe.servings,
            mealTypes = recipe.mealTypes,
            prepMinutes = recipe.prepMinutes,
            cookMinutes = recipe.cookMinutes,
            difficulty = recipe.difficulty,
            cuisine = recipe.cuisine,
            lines = recipe.ingredients.map { OwnRecipeLine(it.ingredientId, it.quantity, it.unit, it.note) },
        )
        val ingredients = recipe.ingredients.map { line ->
            found[line.ingredientId]?.toLine() ?: LineIngredient(line.ingredientId, line.name, listOf(line.unit), PIECE)
        }
        return RecipeToEdit(kept, ingredients)
    }

    /** The rules a refused recipe would break, read from a refusal with `RECIPE_INVALID`; null for any other. */
    fun problemsOf(error: Throwable): List<RecipeProblem>? =
        detailsOf(error, "RECIPE_INVALID", RecipeProblemsDto.serializer())?.problems?.map { RecipeProblem(it.path, it.rule) }

    /** The recipe of the account's own a refused one would repeat, read from a refusal with `RECIPE_ALREADY_YOURS`; null for any other. */
    fun alreadyYours(error: Throwable): String? = detailsOf(error, "RECIPE_ALREADY_YOURS", AlreadyYoursDto.serializer())?.recipeId

    /** What a refusal with [code] says more, read as [serializer] reads it; null for another refusal, or for details it cannot read. */
    private fun <T> detailsOf(error: Throwable, code: String, serializer: KSerializer<T>): T? {
        if (error !is ApiException || error.code != code) return null
        val details = error.details ?: return null
        return try {
            json.decodeFromJsonElement(serializer, details)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** What a piece of an ingredient is called where the catalogue says nothing of it. */
private const val PIECE = "piece"

private fun IngredientDto.toLine() = LineIngredient(id, name, recipeUnits, displayUnit ?: PIECE)

private fun OwnRecipeLine.toDto() = OwnRecipeLineDto(ingredientId, quantity, unit, note)

private fun OwnRecipe.toDto() = OwnRecipeRequest(
    title = title,
    description = description,
    steps = steps,
    servings = servings,
    mealTypes = mealTypes,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    difficulty = difficulty,
    ingredients = lines.map { it.toDto() },
    cuisine = cuisine,
)
