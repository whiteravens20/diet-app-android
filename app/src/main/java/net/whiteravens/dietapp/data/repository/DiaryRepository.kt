// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.local.CachedDiaryDay
import net.whiteravens.dietapp.data.local.KeptReadsDao
import net.whiteravens.dietapp.data.local.readOrDrop
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.CopyDiaryDayRequest
import net.whiteravens.dietapp.data.remote.CreateDiaryEntryRequest
import net.whiteravens.dietapp.data.remote.DiaryAmountRequest
import net.whiteravens.dietapp.data.remote.DiaryDayDto
import net.whiteravens.dietapp.data.remote.DiaryEntryDto
import net.whiteravens.dietapp.data.remote.DiaryMealNutritionDto
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.IngredientDto
import net.whiteravens.dietapp.data.remote.UpdateDiaryEntryRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.DiaryAmount
import net.whiteravens.dietapp.domain.DiaryCopied
import net.whiteravens.dietapp.domain.DiaryDay
import net.whiteravens.dietapp.domain.DiaryDayPlan
import net.whiteravens.dietapp.domain.DiaryEntry
import net.whiteravens.dietapp.domain.DiaryIngredient
import net.whiteravens.dietapp.domain.DiaryPlanMeal
import net.whiteravens.dietapp.domain.Macros
import net.whiteravens.dietapp.domain.NewDiaryEntry
import net.whiteravens.dietapp.domain.Nutrition
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A profile's diary: a day read, and kept on the phone for a while so that it is there without a
 * network; an entry written, changed or taken out, each through the queue of changes, where it waits
 * for a network where there is none. An entry is sent with a key that has the server write it once
 * however often it is sent. A day copied asks the server, as what it brings is the server's to say.
 */
@Singleton
class DiaryRepository @Inject constructor(
    private val api: DietApiService,
    private val kept: KeptReadsDao,
    private val json: Json,
    private val queue: ChangeQueue,
) {
    /** The day as it was last read on this phone; null where it was not, or not for a while. */
    suspend fun keptDay(profileId: String, date: LocalDate): DiaryDay? = kept.diaryDay(profileId, date.toString())?.let { row ->
        readOrDrop({ json.decodeFromString<DiaryDayDto>(row.json).toDomain(syncedAt = row.syncedAt) }) {
            kept.deleteDiaryDay(profileId, date.toString())
        }
    }

    /**
     * The day from the API, kept on the phone. A profile the API says is not there takes what is kept
     * of its diary with it. Throws when offline.
     */
    suspend fun day(profileId: String, date: LocalDate): DiaryDay {
        val read = try {
            apiCall(json) { api.diaryDay(profileId, date.toString()) }
        } catch (e: ApiException) {
            if (e.code == PROFILE_NOT_FOUND) kept.deleteDiaryOf(profileId)
            throw e
        }
        keep(read)
        return read.toDomain()
    }

    /** The catalogue's ingredients whose name holds [search], in the app's language, as an entry takes them. */
    suspend fun ingredients(search: String): List<DiaryIngredient> = apiCall(json) { api.ingredients(search = search) }.map { it.toDiary() }

    /** Writes [entry] for the day of [date] of [profileId]'s diary: the entry as the API keeps it, or kept to be sent in its turn. */
    suspend fun add(profileId: String, date: LocalDate, entry: NewDiaryEntry): Submitted<DiaryEntry> {
        val queued = entry.toChange(profileId, date.toString())
        return queue.submit(queued) { send(queued) }
    }

    /** Sends an entry written; answers it as the API keeps it. */
    suspend fun send(change: QueuedChange.DiaryAdded): DiaryEntry = apiCall(json) { api.addDiaryEntry(change.key, change.toRequest()) }.toDomain()

    /** Changes the entry [id] of [profileId]'s diary: the entry as the API keeps it now, or kept to be sent in its turn. */
    suspend fun change(profileId: String, id: String, slot: String?, servings: Double?, quantity: Double?): Submitted<DiaryEntry> {
        val queued = QueuedChange.DiaryChanged(profileId, id, slot, servings, quantity)
        return queue.submit(queued) { send(queued) }
    }

    /** Sends an entry's change; answers it as the API keeps it now. */
    suspend fun send(change: QueuedChange.DiaryChanged): DiaryEntry =
        apiCall(json) { api.changeDiaryEntry(change.id, UpdateDiaryEntryRequest(change.slot, change.servings, change.quantity)) }.toDomain()

    /** Takes the entry [id] out of [profileId]'s diary, or keeps that to be sent in its turn. */
    suspend fun remove(profileId: String, id: String): Submitted<Unit> {
        val queued = QueuedChange.DiaryRemoved(profileId, id)
        return queue.submit(queued) { send(queued) }
    }

    /** Sends an entry's taking out. */
    suspend fun send(change: QueuedChange.DiaryRemoved) = apiCall(json) { api.removeDiaryEntry(change.id) }

    /**
     * Copies what was eaten on the day [from] into the day [to] of [profileId]'s diary: its entries,
     * and the meals its plan marked as eaten. A key goes with it, so that an answer lost on the way
     * does not have it copied twice.
     */
    suspend fun copy(profileId: String, from: LocalDate, to: LocalDate): DiaryCopied {
        val answer = apiCall(json) { api.copyDiaryDay(UUID.randomUUID().toString(), CopyDiaryDayRequest(profileId, from.toString(), to.toString())) }
        keep(answer.day)
        return DiaryCopied(answer.copied, answer.day.toDomain())
    }

    /** Keeps the day read, and forgets those read long enough ago. */
    private suspend fun keep(day: DiaryDayDto) {
        val now = System.currentTimeMillis()
        kept.upsertDiaryDay(CachedDiaryDay(day.profileId, day.date, json.encodeToString(day), now))
        kept.deleteDiaryReadBefore(now - KEPT_FOR.toMillis())
    }

    private companion object {
        /** How long a day read stays on the phone. */
        val KEPT_FOR: Duration = Duration.ofDays(14)

        /** The profile asked for is not there, or is not the account's. */
        const val PROFILE_NOT_FOUND = "PROFILE_NOT_FOUND"

        /** What a piece of an ingredient is called where the catalogue says nothing of it. */
        const val PIECE = "piece"
    }

    private fun IngredientDto.toDiary() = DiaryIngredient(id, name, recipeUnits, displayUnit ?: PIECE, canonicalUnit, caloriesPer100)
}

private fun NewDiaryEntry.toChange(profileId: String, date: String): QueuedChange.DiaryAdded {
    val key = UUID.randomUUID().toString()
    return when (this) {
        is NewDiaryEntry.OfRecipe -> QueuedChange.DiaryAdded(profileId, date, slot, name.trim(), recipeId = recipeId, servings = servings, calories = calories, key = key)
        is NewDiaryEntry.OfIngredient -> QueuedChange.DiaryAdded(profileId, date, slot, name.trim(), ingredientId = ingredientId, quantity = quantity, unit = unit, pieceName = pieceName, key = key)
        is NewDiaryEntry.OwnMeal -> QueuedChange.DiaryAdded(profileId, date, slot, name.trim(), servings = servings, calories = calories, protein = protein, fat = fat, carbs = carbs, key = key)
    }
}

private fun QueuedChange.DiaryAdded.toRequest(): CreateDiaryEntryRequest = when {
    recipeId != null -> CreateDiaryEntryRequest(profileId, date, slot, "recipe", recipeId = recipeId, servings = servings)
    ingredientId != null -> CreateDiaryEntryRequest(profileId, date, slot, "ingredient", ingredientId = ingredientId, amount = DiaryAmountRequest(checkNotNull(quantity), checkNotNull(unit)))
    else -> CreateDiaryEntryRequest(profileId, date, slot, "meal", name = name, servings = servings, nutrition = DiaryMealNutritionDto(checkNotNull(calories), protein, fat, carbs))
}

private fun DiaryDayDto.toDomain(syncedAt: Long? = null) = DiaryDay(
    profileId = profileId,
    date = date,
    target = Nutrition(target.calories, target.protein, target.fat, target.carbs),
    targetFromPlan = target.from == "plan",
    plan = plan?.let { plan ->
        DiaryDayPlan(
            id = plan.id,
            skipped = plan.skipped,
            meals = plan.meals.map { DiaryPlanMeal(it.id, it.mealType, it.name, it.recipeId, it.eaten, it.nutrition.calories) },
            calorieDelta = plan.calorieDelta,
            offTarget = plan.offTarget,
        )
    },
    entries = entries.map { it.toDomain() },
    eaten = eaten.toDomain(),
    estimated = estimated,
    syncedAt = syncedAt,
)

private fun DiaryEntryDto.toDomain() = DiaryEntry(
    id = id,
    slot = mealType,
    kind = kind,
    name = name,
    servings = servings,
    amount = amount?.let { DiaryAmount(it.quantity, it.unit, it.shown.quantity, it.shown.unit) },
    calories = nutrition.calories,
    macros = Macros(nutrition.protein, nutrition.fat, nutrition.carbs),
    estimated = estimated,
)
