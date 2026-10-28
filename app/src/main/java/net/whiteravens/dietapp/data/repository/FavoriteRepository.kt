// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.AddFavoriteRequest
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.FavoriteSetDto
import net.whiteravens.dietapp.data.remote.MarkedRecipeDto
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.FavoriteFilter
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.MarkedRecipe
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The recipes a profile marked, and its favourite sets: a recipe for each meal a set names. A mark
 * put on or taken off goes through the queue of changes, and waits there for a network where there
 * is none.
 */
@Singleton
class FavoriteRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
    private val queue: ChangeQueue,
) {
    /** The recipes [profileId] marked, narrowed and put in order as [filter] says; the API's own order is not sent. */
    suspend fun marked(profileId: String, filter: FavoriteFilter = FavoriteFilter()): List<MarkedRecipe> =
        apiCall(json) {
            api.favorites(
                profileId,
                search = filter.search.trim().ifEmpty { null },
                mealType = filter.mealType,
                sentiment = filter.sentiment,
                tag = filter.tag,
                sort = filter.sort.takeUnless { it == FavoriteFilter.NEWEST },
            )
        }.map { it.toDomain() }

    /**
     * Marks [recipeId] for [profileId] as [sentiment], under [tags]: what was said of it before is
     * replaced. Without a network, or behind changes waiting, the mark is kept to be sent in its turn.
     */
    suspend fun mark(profileId: String, recipeId: String, sentiment: String, tags: List<String>): Submitted<Unit> {
        val queued = QueuedChange.Marked(profileId, recipeId, sentiment, tags)
        return queue.submit(queued) { send(queued) }
    }

    /** Sends a mark. */
    suspend fun send(change: QueuedChange.Marked) =
        apiCall(json) { api.markRecipe(AddFavoriteRequest(change.profileId, change.recipeId, change.tags, change.sentiment)) }

    /** Takes the mark of [profileId] off [recipeId], or keeps that to be sent in its turn. */
    suspend fun unmark(profileId: String, recipeId: String): Submitted<Unit> {
        val queued = QueuedChange.Unmarked(profileId, recipeId)
        return queue.submit(queued) { send(queued) }
    }

    /** Sends a mark's taking off. */
    suspend fun send(change: QueuedChange.Unmarked) = apiCall(json) { api.unmarkRecipe(change.profileId, change.recipeId) }

    /** The favourite sets of [profileId], the one made last first. */
    suspend fun sets(profileId: String): List<FavoriteSet> = apiCall(json) { api.favoriteSets(profileId) }.map { it.toDomain() }

    private fun FavoriteSetDto.toDomain() = FavoriteSet(id, label, slots)

    private fun MarkedRecipeDto.toDomain() =
        MarkedRecipe(recipe.id, recipe.title, recipe.mealTypes, sentiment, recipe.dietTags, tags, recipe.nutritionPerServing.calories)
}
