// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/** How hard a recipe is, the easiest first: what a list of difficulties offers to choose from. */
val DIFFICULTIES = listOf("easy", "medium", "hard")

/**
 * An ingredient of the catalogue as a recipe written by hand takes it: the [units] an amount of it
 * can be given in, the one to offer first first, and what a piece of it is called ([pieceName]).
 */
data class LineIngredient(val id: String, val name: String, val units: List<String>, val pieceName: String)

/** A line of a recipe written by hand: how much of an ingredient, in which unit, and a note where there is one. */
data class OwnRecipeLine(val ingredientId: String, val quantity: Double, val unit: String, val note: String? = null)

/**
 * A recipe of one's own as it is written by hand or edited: what it is, its method, how many
 * servings it makes and for which meals, how long it takes, how hard it is, the kitchen it belongs
 * to where it belongs to one, and its lines. What a serving holds, its allergens and its diets are
 * the API's to work out.
 */
data class OwnRecipe(
    val title: String,
    val description: String,
    val steps: List<String>,
    val servings: Int,
    val mealTypes: List<String>,
    val prepMinutes: Int,
    val cookMinutes: Int,
    val difficulty: String,
    val cuisine: String?,
    val lines: List<OwnRecipeLine>,
)

/** A recipe of one's own as its form starts on it: the recipe as it is kept, and the ingredient of each of its lines, in their order. */
data class RecipeToEdit(val recipe: OwnRecipe, val ingredients: List<LineIngredient>)

/** What a serving of a recipe being written would hold, its allergens and the diets it fits. */
data class RecipePreview(val perServing: Nutrition, val allergens: List<String>, val dietTags: List<String>)

/** A rule of every recipe that a recipe would break, where it would (`titles.en`, `steps.en[3]`, `ingredients[0].quantity`). */
data class RecipeProblem(val path: String, val rule: String)
