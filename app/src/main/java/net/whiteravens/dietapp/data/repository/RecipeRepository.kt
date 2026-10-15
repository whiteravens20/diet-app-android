// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeFilter
import net.whiteravens.dietapp.domain.RecipePage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recipe catalogue browsing — network-only. The catalogue is large and
 * server-paginated, so it is not mirrored into the offline cache; recipes a
 * user actually needs offline arrive embedded in their cached meal plans.
 */
@Singleton
class RecipeRepository @Inject constructor(
    private val api: DietApiService,
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

    /** A page of the account's own recipes. */
    suspend fun mine(page: Int = 1): RecipePage = apiCall(json) { api.myRecipes(page = page) }.toDomain()

    /** Puts a recipe of one's own before the curators; asking again changes nothing. */
    suspend fun submit(id: String) = apiCall(json) { api.submitRecipe(id) }

    /** Deletes a recipe of one's own; deleting it again changes nothing. */
    suspend fun delete(id: String) = apiCall(json) { api.deleteRecipe(id) }

    suspend fun byId(id: String): Recipe = apiCall(json) { api.recipe(id) }.toDomain()
}
