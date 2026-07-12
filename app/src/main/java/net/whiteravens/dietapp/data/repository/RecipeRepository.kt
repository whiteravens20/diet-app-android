package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.Recipe
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
    suspend fun search(query: String? = null, page: Int = 1): RecipePage =
        apiCall(json) { api.recipes(search = query?.takeIf { it.isNotBlank() }, page = page) }.toDomain()

    suspend fun byId(id: String): Recipe = apiCall(json) { api.recipe(id) }.toDomain()
}
