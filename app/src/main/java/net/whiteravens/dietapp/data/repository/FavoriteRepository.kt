// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.MarkedRecipe
import javax.inject.Inject
import javax.inject.Singleton

/** The recipes a profile marked, and its favourite sets. */
@Singleton
class FavoriteRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
) {
    suspend fun marked(profileId: String): List<MarkedRecipe> =
        apiCall(json) { api.favorites(profileId) }.map { MarkedRecipe(it.recipe.id, it.recipe.title, it.recipe.mealTypes, it.sentiment, it.recipe.dietTags) }

    suspend fun sets(profileId: String): List<FavoriteSet> =
        apiCall(json) { api.favoriteSets(profileId) }.map { FavoriteSet(it.id, it.label, it.slots) }
}
