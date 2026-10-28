// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.LineIngredient
import net.whiteravens.dietapp.domain.OwnRecipe
import net.whiteravens.dietapp.domain.OwnRecipeLine
import net.whiteravens.dietapp.domain.RecipeProblem
import net.whiteravens.dietapp.domain.RecipeToEdit

/** A field of a recipe's form, by the place the API names it by when it refuses what it holds. */
enum class RecipeField(val path: String) {
    TITLE("title"),
    DESCRIPTION("description"),
    MEALS("mealTypes"),
    SERVINGS("servings"),
    PREP("prepMinutes"),
    COOK("cookMinutes"),
    LINES("ingredients"),
    STEPS("steps"),
}

/** A part of an ingredient line the API may refuse, by the key it names it by. */
enum class LinePart(val key: String) {
    QUANTITY("quantity"),
    NOTE("note"),
}

/** A line of the form: its ingredient, its amount as it is typed, the unit chosen and a note; kept by [key] as lines come and go. */
data class FormLine(
    val key: Int,
    val ingredient: LineIngredient,
    val quantity: String = "",
    val unit: String = ingredient.units.first(),
    val note: String = "",
)

/** A step of the form, kept by [key], so that what is said of it follows it when it is moved. */
data class FormStep(val key: Int, val text: String = "")

/** What a serving is asked about: [servings] of [lines], their notes left out. */
data class PreviewAsk(val servings: Int, val lines: List<OwnRecipeLine>)

/**
 * A recipe of one's own as its form is filled in, the numbers as they are typed. What the API takes
 * of them is the API's to say: the form knows a number from what is not one, and nothing more.
 */
data class RecipeForm(
    val title: String = "",
    val description: String = "",
    val mealTypes: List<String> = emptyList(),
    val servings: String = "1",
    val prepMinutes: String = "10",
    val cookMinutes: String = "0",
    val difficulty: String = "easy",
    val cuisine: String? = null,
    val lines: List<FormLine> = emptyList(),
    val steps: List<FormStep> = listOf(FormStep(0)),
    /** The key the next line or step is given. */
    val nextKey: Int = 1,
) {
    /** The fields that hold no whole number where one is asked for. */
    fun notWhole(): Set<RecipeField> = buildSet {
        if (servings.whole() == null) add(RecipeField.SERVINGS)
        if (prepMinutes.whole() == null) add(RecipeField.PREP)
        if (cookMinutes.whole() == null) add(RecipeField.COOK)
    }

    /** The lines, by their keys, whose amount is no number. */
    fun notNumbers(): Set<Int> = lines.filter { it.quantity.number() == null }.mapTo(mutableSetOf()) { it.key }

    /**
     * What a serving is asked about, once there is a line, the servings are a whole number and every
     * amount is a number; null before.
     */
    fun previewAsk(): PreviewAsk? {
        if (lines.isEmpty()) return null
        val portions = servings.whole() ?: return null
        val sent = sentLines() ?: return null
        return PreviewAsk(portions, sent.map { it.copy(note = null) })
    }

    /** The recipe to send, every step as it is typed, or null while a field holds no number where one is asked for. */
    fun recipe(): OwnRecipe? = OwnRecipe(
        title = title,
        description = description,
        steps = steps.map { it.text },
        servings = servings.whole() ?: return null,
        mealTypes = mealTypes,
        prepMinutes = prepMinutes.whole() ?: return null,
        cookMinutes = cookMinutes.whole() ?: return null,
        difficulty = difficulty,
        cuisine = cuisine,
        lines = sentLines() ?: return null,
    )

    /** The form with [ingredient] on a line of its own after the others, in the unit it is offered in first. */
    fun withLine(ingredient: LineIngredient): RecipeForm = copy(lines = lines + FormLine(nextKey, ingredient), nextKey = nextKey + 1)

    /** The form with an empty step after the others. */
    fun withStep(): RecipeForm = copy(steps = steps + FormStep(nextKey), nextKey = nextKey + 1)

    /** The form with the step [key] moved [by] places, where there is a place to move it to. */
    fun withStepMoved(key: Int, by: Int): RecipeForm {
        val from = steps.indexOfFirst { it.key == key }
        val to = from + by
        if (from == -1 || to !in steps.indices) return this
        return copy(steps = steps.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** What [field] holds, to tell whether it changed. */
    fun valueOf(field: RecipeField): Any? = when (field) {
        RecipeField.TITLE -> title
        RecipeField.DESCRIPTION -> description
        RecipeField.MEALS -> mealTypes
        RecipeField.SERVINGS -> servings
        RecipeField.PREP -> prepMinutes
        RecipeField.COOK -> cookMinutes
        RecipeField.LINES -> lines.map { it.key }
        RecipeField.STEPS -> steps.map { it.text }
    }

    /**
     * What [part] of the line [key] holds, to tell whether it changed; an amount with the unit and the
     * servings, which say with it how much a serving holds. Null for a line that is gone.
     */
    fun valueOf(key: Int, part: LinePart): Any? {
        val line = lines.find { it.key == key } ?: return null
        return when (part) {
            LinePart.QUANTITY -> listOf(line.quantity, line.unit, servings)
            LinePart.NOTE -> line.note
        }
    }

    /** The lines as they are sent, in their order, a note left empty sent as none; null while an amount is no number. */
    private fun sentLines(): List<OwnRecipeLine>? = lines.map { line ->
        OwnRecipeLine(line.ingredient.id, line.quantity.number() ?: return null, line.unit, line.note.takeIf { it.isNotBlank() })
    }

    companion object {
        /** The form of a recipe of one's own to edit: what [kept] holds, each line with its ingredient. */
        fun of(kept: RecipeToEdit): RecipeForm {
            val recipe = kept.recipe
            val lines = recipe.lines.mapIndexed { index, line ->
                FormLine(index, kept.ingredients[index], line.quantity.plain(), line.unit, line.note.orEmpty())
            }
            val steps = recipe.steps.ifEmpty { listOf("") }.mapIndexed { index, text -> FormStep(lines.size + index, text) }
            return RecipeForm(
                title = recipe.title,
                description = recipe.description,
                mealTypes = recipe.mealTypes,
                servings = recipe.servings.toString(),
                prepMinutes = recipe.prepMinutes.toString(),
                cookMinutes = recipe.cookMinutes.toString(),
                difficulty = recipe.difficulty,
                cuisine = recipe.cuisine,
                lines = lines,
                steps = steps,
                nextKey = lines.size + steps.size,
            )
        }
    }
}

/** Why the API refused a field: past one of its bounds, or against a rule every recipe keeps, by the rule's name. */
sealed interface RecipeRefusal {
    data class OutOfBounds(val refusal: FieldRefusal) : RecipeRefusal

    data class AgainstRule(val rule: String) : RecipeRefusal
}

/**
 * The fields of a recipe's form the API refused, by its answer: the form's own, a line's parts by
 * the line and a step by the step, from their places among those the form sent. A place the form
 * has no field for is in [elsewhere].
 */
data class RecipeRefusals(
    val fields: Map<RecipeField, RecipeRefusal> = emptyMap(),
    val lines: Map<Int, Map<LinePart, RecipeRefusal>> = emptyMap(),
    val steps: Map<Int, RecipeRefusal> = emptyMap(),
    val elsewhere: Set<String> = emptySet(),
) {
    /** Whether a field of the form is refused. */
    val marked: Boolean get() = fields.isNotEmpty() || lines.isNotEmpty() || steps.isNotEmpty()

    /** What is left of them once the form is [now] as it was [before]: a field changed since is no longer refused. */
    fun after(before: RecipeForm, now: RecipeForm): RecipeRefusals = copy(
        fields = fields.filterKeys { field -> before.valueOf(field) == now.valueOf(field) },
        lines = lines.mapValues { (key, parts) ->
            parts.filterKeys { part -> now.valueOf(key, part).let { it != null && it == before.valueOf(key, part) } }
        }.filterValues { it.isNotEmpty() },
        steps = steps.filterKeys { key -> now.steps.find { it.key == key }?.text.let { it != null && it == before.steps.find { step -> step.key == key }?.text } },
    )

    /** These, and where [under] says something of a field these say nothing of, that too. */
    fun over(under: RecipeRefusals): RecipeRefusals = RecipeRefusals(
        fields = under.fields + fields,
        lines = (under.lines.keys + lines.keys).associateWith { key -> under.lines[key].orEmpty() + lines[key].orEmpty() },
        steps = under.steps + steps,
        elsewhere = under.elsewhere + elsewhere,
    )

    companion object {
        private val LINE = Regex("""ingredients\.(\d+)\.(\w+)""")
        private val STEP = Regex("""steps\.(\d+)""")
        private val MEAL = Regex("""mealTypes\.\d+""")

        /** The fields the bounds [refused] names were refused for, of [form] as it was sent. */
        fun ofBounds(refused: Map<String, FieldRefusal>, form: RecipeForm): RecipeRefusals =
            of(refused.mapValues { (_, refusal) -> RecipeRefusal.OutOfBounds(refusal) }, form)

        /** The fields [problems] names the rules of, of [form] as it was sent; the first rule of a field that breaks several. */
        fun ofRules(problems: List<RecipeProblem>, form: RecipeForm): RecipeRefusals =
            of(problems.reversed().associate { dotted(it.path) to RecipeRefusal.AgainstRule(it.rule) }, form)

        private fun of(refused: Map<String, RecipeRefusal>, form: RecipeForm): RecipeRefusals {
            val fields = mutableMapOf<RecipeField, RecipeRefusal>()
            val lines = mutableMapOf<Int, MutableMap<LinePart, RecipeRefusal>>()
            val steps = mutableMapOf<Int, RecipeRefusal>()
            val elsewhere = mutableSetOf<String>()
            for ((path, refusal) in refused) {
                val field = RecipeField.entries.firstOrNull { it.path == path }
                    ?: RecipeField.MEALS.takeIf { MEAL.matches(path) }
                if (field != null) {
                    fields[field] = refusal
                    continue
                }
                val line = LINE.matchEntire(path)
                val lineKey = line?.groupValues?.get(1)?.toInt()?.let { form.lines.getOrNull(it)?.key }
                val part = line?.groupValues?.get(2)?.let { key -> LinePart.entries.firstOrNull { it.key == key } }
                if (lineKey != null && part != null) {
                    lines.getOrPut(lineKey) { mutableMapOf() }[part] = refusal
                    continue
                }
                val stepKey = STEP.matchEntire(path)?.groupValues?.get(1)?.toInt()?.let { form.steps.getOrNull(it)?.key }
                if (stepKey != null) steps[stepKey] = refusal else elsewhere += path
            }
            return RecipeRefusals(fields, lines, steps, elsewhere)
        }

        /**
         * A place as the rules of every recipe name it, said as the API's bounds say it: a place in a
         * list by a dot, and the text of the recipe's language as the field it is typed in
         * (`titles.en` → `title`, `steps.en[3]` → `steps.3`, `ingredients[0].quantity` → `ingredients.0.quantity`).
         */
        private fun dotted(path: String): String = path
            .replace(Regex("""\[(\d+)]"""), ".$1")
            .replace(Regex("""^titles\.[^.]+"""), "title")
            .replace(Regex("""^descriptions\.[^.]+"""), "description")
            .replace(Regex("""^steps\.[a-zA-Z][^.]*"""), "steps")
    }
}

/**
 * What the fields of a recipe's form say is wrong with them: what holds no whole number
 * ([notWhole]) or, on a line, no number ([notNumbers], by the line's key) where one is needed; and
 * what the API refused ([refusals]).
 */
data class RecipeMarks(
    val notWhole: Set<RecipeField> = emptySet(),
    val notNumbers: Set<Int> = emptySet(),
    val refusals: RecipeRefusals = RecipeRefusals(),
)

/** A number as a person types it, with a point or a comma. */
private fun String.number(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** A whole number as a person types it. */
private fun String.whole(): Int? = trim().toIntOrNull()

/** An amount kept, as a field shows it: without a point where it is whole. */
private fun Double.plain(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
