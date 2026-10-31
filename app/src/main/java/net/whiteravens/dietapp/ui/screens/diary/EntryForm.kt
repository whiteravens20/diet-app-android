// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import net.whiteravens.dietapp.domain.DiaryLimits
import net.whiteravens.dietapp.domain.DiaryEntry
import net.whiteravens.dietapp.domain.DiaryIngredient
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.NewDiaryEntry
import net.whiteravens.dietapp.domain.Recipe
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** What an entry is of. */
enum class EntryKind { RECIPE, INGREDIENT, MEAL }

/** A field of an entry, by the path the API names it by when it refuses what it holds. */
enum class EntryField(val path: String) {
    NAME("name"),
    SERVINGS("servings"),
    QUANTITY("amount.quantity"),
    CALORIES("nutrition.calories"),
    PROTEIN("nutrition.protein"),
    FAT("nutrition.fat"),
    CARBS("nutrition.carbs"),
}

/** What is wrong with what a field of an entry holds: no number where one is asked for, or one the API would refuse. */
sealed interface EntryProblem {
    data object NotANumber : EntryProblem

    data class OutOfBounds(val refusal: FieldRefusal) : EntryProblem
}

/**
 * An entry as the window that adds it is filled in: what it is of, the meal it goes under, what was
 * looked for and found, and how much of it there was. What is typed is held to the bounds the API
 * has, so that an entry written without a network is not refused only once it is sent.
 */
data class EntryForm(
    val kind: EntryKind = EntryKind.RECIPE,
    val slot: String,
    val search: String = "",
    val recipes: List<Recipe> = emptyList(),
    val ingredients: List<DiaryIngredient> = emptyList(),
    /** Whether the search has answered the words as they are typed now. */
    val searched: Boolean = false,
    /** Why the search did not answer: that says nothing of what there is. */
    val searchFailed: Throwable? = null,
    val recipe: Recipe? = null,
    val ingredient: DiaryIngredient? = null,
    val servings: String = "1",
    val quantity: String = "",
    val unit: String = "g",
    val name: String = "",
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
) {
    /** Nothing was picked or typed yet: the window may still open on another kind. */
    val untouched: Boolean
        get() = search.isEmpty() && recipe == null && ingredient == null && name.isEmpty() && calories.isEmpty() && protein.isEmpty() && fat.isEmpty() && carbs.isEmpty()

    /** What was not picked yet, of a kind that is picked. */
    val nothingPicked: Boolean get() = (kind == EntryKind.RECIPE && recipe == null) || (kind == EntryKind.INGREDIENT && ingredient == null)

    /** What the fields of the kind hold that the API would refuse, by field. */
    fun problems(): Map<EntryField, EntryProblem> = buildMap {
        when (kind) {
            EntryKind.RECIPE -> if (recipe != null) putAll(servingsProblem(servings))
            EntryKind.INGREDIENT -> if (ingredient != null) {
                when (val typed = quantity.number()) {
                    null -> put(EntryField.QUANTITY, EntryProblem.NotANumber)
                    else -> quantityProblem(typed, unit)?.let { put(EntryField.QUANTITY, it) }
                }
            }
            EntryKind.MEAL -> {
                if (name.isBlank()) put(EntryField.NAME, EntryProblem.OutOfBounds(FieldRefusal.TooSmall(1.0, inclusive = true)))
                if (name.trim().length > DiaryLimits.name) put(EntryField.NAME, EntryProblem.OutOfBounds(FieldRefusal.TooBig(DiaryLimits.name.toDouble(), inclusive = true)))
                perServing(EntryField.CALORIES, calories, DiaryLimits.calories, required = true)
                perServing(EntryField.PROTEIN, protein, DiaryLimits.protein, required = false)
                perServing(EntryField.FAT, fat, DiaryLimits.fat, required = false)
                perServing(EntryField.CARBS, carbs, DiaryLimits.carbs, required = false)
                putAll(servingsProblem(servings))
            }
        }
    }

    private fun MutableMap<EntryField, EntryProblem>.perServing(field: EntryField, typed: String, most: Double, required: Boolean) {
        if (typed.isBlank() && !required) return
        val number = typed.number() ?: return run { put(field, EntryProblem.NotANumber) }
        if (number < 0) put(field, EntryProblem.OutOfBounds(FieldRefusal.TooSmall(0.0, inclusive = true)))
        if (number > most) put(field, EntryProblem.OutOfBounds(FieldRefusal.TooBig(most, inclusive = true)))
    }

    /** The kcal the entry adds to the day, rounded as the API rounds it, where the window knows it: an ingredient's figures go through the API's units. */
    fun adds(): Int? {
        val times = servings.number()?.takeIf { it >= DiaryLimits.leastServings && it <= DiaryLimits.mostServings } ?: return null
        return when (kind) {
            EntryKind.RECIPE -> recipe?.let { (it.nutritionPerServing.calories * times).roundToInt() }
            EntryKind.MEAL -> calories.number()?.takeIf { it >= 0 && it <= DiaryLimits.calories }?.let { (it * times).roundToInt() }
            EntryKind.INGREDIENT -> null
        }
    }

    /** The entry to write; null while something is not picked, or a field holds what the API would refuse. */
    fun entry(): NewDiaryEntry? {
        if (nothingPicked || problems().isNotEmpty()) return null
        return when (kind) {
            EntryKind.RECIPE -> recipe?.let { NewDiaryEntry.OfRecipe(slot, it.id, it.title, it.nutritionPerServing.calories, servings.number()!!) }
            EntryKind.INGREDIENT -> ingredient?.let { NewDiaryEntry.OfIngredient(slot, it.id, it.name, it.pieceName, quantity.number()!!, unit) }
            EntryKind.MEAL -> NewDiaryEntry.OwnMeal(slot, name.trim(), servings.number()!!, calories.number()!!, protein.number(), fat.number(), carbs.number())
        }
    }
}

/**
 * An entry as the window that changes it is filled in: the meal it is under, and how much of it there
 * was, in servings or in the amount of its ingredient as it was given, in the words of the phone's
 * language. An amount left alone stays exactly what it was: what is shown of it may be rounded.
 */
data class EntryEdit(
    val entry: DiaryEntry,
    val slot: String = entry.slot,
    val servings: String = shownNumber(entry.servings),
    val quantity: String = shownNumber(entry.amount?.quantity),
) {
    private val servingsAtFirst = shownNumber(entry.servings)
    private val quantityAtFirst = shownNumber(entry.amount?.quantity)

    /** The meal asked for, where it is another. */
    val newSlot: String? get() = slot.takeIf { it != entry.slot }

    /** The servings typed, where they are another number than those shown. */
    val newServings: Double? get() = if (entry.amount == null && servings != servingsAtFirst) servings.number() else null

    /** The amount typed, where it is another number than the one shown. */
    val newQuantity: Double? get() = if (entry.amount != null && quantity != quantityAtFirst) quantity.number() else null

    /** Whether anything is changed at all. */
    val changed: Boolean get() = newSlot != null || newServings != null || newQuantity != null

    /** What the field holds that the API would refuse, by field; a number left alone is not asked about. */
    fun problems(): Map<EntryField, EntryProblem> = when {
        entry.amount != null -> if (quantity == quantityAtFirst) {
            emptyMap()
        } else {
            when (val typed = quantity.number()) {
                null -> mapOf(EntryField.QUANTITY to EntryProblem.NotANumber)
                else -> quantityProblem(typed, entry.amount.unit)?.let { mapOf(EntryField.QUANTITY to it) }.orEmpty()
            }
        }
        servings == servingsAtFirst -> emptyMap()
        else -> servingsProblem(servings)
    }
}

/** What is wrong with the servings typed, if anything. */
private fun servingsProblem(typed: String): Map<EntryField, EntryProblem> {
    val number = typed.number() ?: return mapOf(EntryField.SERVINGS to EntryProblem.NotANumber)
    return when {
        number < DiaryLimits.leastServings -> mapOf(EntryField.SERVINGS to EntryProblem.OutOfBounds(FieldRefusal.TooSmall(DiaryLimits.leastServings, inclusive = true)))
        number > DiaryLimits.mostServings -> mapOf(EntryField.SERVINGS to EntryProblem.OutOfBounds(FieldRefusal.TooBig(DiaryLimits.mostServings, inclusive = true)))
        else -> emptyMap()
    }
}

/** What is wrong with an amount typed in [unit], if anything. */
private fun quantityProblem(typed: Double, unit: String): EntryProblem? {
    val most = if (unit == "piece") DiaryLimits.mostPieces else DiaryLimits.mostWeighed
    return when {
        typed <= 0 -> EntryProblem.OutOfBounds(FieldRefusal.TooSmall(0.0, inclusive = false))
        typed > most -> EntryProblem.OutOfBounds(FieldRefusal.TooBig(most, inclusive = true))
        else -> null
    }
}

/** A number as a person types it, with a point or a comma. */
private fun String.number(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** A number as a field holds it: in the language's own way, without grouping, to the hundredth; nothing for none. */
private fun shownNumber(value: Double?): String =
    value?.let { NumberFormat.getNumberInstance(Locale.getDefault()).apply { isGroupingUsed = false; maximumFractionDigits = 2 }.format(it) }.orEmpty()
