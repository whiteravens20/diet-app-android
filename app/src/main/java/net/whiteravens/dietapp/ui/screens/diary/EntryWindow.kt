// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.repository.DiaryRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.domain.DiaryIngredient
import net.whiteravens.dietapp.domain.DiaryLimits
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.Product
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeFilter
import net.whiteravens.dietapp.domain.canSearchProducts
import net.whiteravens.dietapp.domain.looksLikeProductCode
import net.whiteravens.dietapp.domain.productCodeOf
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel

/** The window that adds what was eaten, as its owner keeps it: what it holds while it is open, and how its sending went. */
data class EntryWindowState(
    /** What the window holds, while it is open. */
    val form: EntryForm? = null,
    /** The camera is open over the window, to read a barcode. */
    val scanning: Boolean = false,
    /** What is in the window is on its way. */
    val sending: Boolean = false,
    /** What the window holds is said to be wrong, once it was sent. */
    val showProblems: Boolean = false,
    /** The fields the API refused of what the window sent, by their paths. */
    val refused: Map<String, FieldRefusal> = emptyMap(),
    /** Why the API refused what the window sent, other than by its fields. */
    val windowError: Throwable? = null,
)

/**
 * What the window that adds what was eaten keeps of itself for when Android ends the app in the background: what it is
 * of and where it goes, what was typed in it, and what was picked, by what reads it again.
 */
@Serializable
data class KeptEntryForm(
    val kind: EntryKind,
    val slot: String,
    val search: String = "",
    val recipeId: String? = null,
    val ingredientId: String? = null,
    val productId: String? = null,
    val productCode: String? = null,
    val productName: String? = null,
    val servings: String = "1",
    val quantity: String = "",
    val unit: String = "g",
    val name: String = "",
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
) {
    /** The window as it opens again: what was typed in it, what was picked to be read again. */
    fun toForm() = EntryForm(
        kind = kind,
        slot = slot,
        search = search,
        servings = servings,
        quantity = quantity,
        unit = unit,
        name = name,
        calories = calories,
        protein = protein,
        fat = fat,
        carbs = carbs,
    )

    companion object {
        fun of(form: EntryForm) = KeptEntryForm(
            kind = form.kind,
            slot = form.slot,
            search = form.search,
            recipeId = form.recipe?.id,
            ingredientId = form.ingredient?.id,
            productId = form.product?.id,
            productCode = form.product?.code,
            productName = form.product?.name,
            servings = form.servings,
            quantity = form.quantity,
            unit = form.unit,
            name = form.name,
            calories = form.calories,
            protein = form.protein,
            fat = form.fat,
            carbs = form.carbs,
        )

        /** How it is written into a screen's saved state, and read back. */
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The window that adds what was eaten, to a day of the diary or to a plan's day: it looks for what is typed, takes
 * what is picked, reads a barcode and writes a product of one's own, on the state its owner keeps ([read], [write]).
 * Where the window opens, what adding sends and what closing puts away with it are its owner's ([open], [add],
 * [close]).
 */
class EntryWindow(
    private val scope: CoroutineScope,
    private val diary: DiaryRepository,
    private val recipes: RecipeRepository,
    /** What the instance says of its products; null until it was read. */
    val rules: StateFlow<InstanceRules?>,
    private val read: () -> EntryWindowState,
    private val write: ((EntryWindowState) -> EntryWindowState) -> Unit,
    /** Opens the window where the camera is asked for while it is closed. */
    private val open: () -> Unit,
    /** Sends what the window holds. */
    val add: () -> Unit,
    /** Puts the window away. */
    val close: () -> Unit,
) {
    /** A search, or a question about a code, on its way to the window. */
    private var searching: Job? = null

    /** Lets an answer on its way to the window go unheard: the window closes, or opens anew. */
    fun stop() {
        searching?.cancel()
    }

    /**
     * Opens the window as it was [kept] when Android ended the app: what was typed comes back at once, and what was
     * picked is read again and taken, the amount typed for it kept. Where it cannot be read, it is looked for again.
     */
    fun restore(kept: KeptEntryForm) {
        searching?.cancel()
        write { EntryWindowState(form = kept.toForm()) }
        val picked = kept.recipeId ?: kept.ingredientId ?: kept.productCode ?: kept.productName
        if (picked == null) {
            if (kept.search.isNotBlank()) search(kept.search)
            return
        }
        searching = scope.launch {
            when {
                kept.recipeId != null -> attempt { recipes.byId(kept.recipeId) }
                    .onSuccess { recipe -> changeForm { it.copy(recipe = recipe) } }
                kept.ingredientId != null -> attempt { diary.ingredient(kept.ingredientId) }
                    .onSuccess { found -> changeForm { it.copy(ingredient = found, unit = it.unit.takeIf { unit -> unit in found.units } ?: found.units.first()) } }
                kept.productCode != null -> attempt { diary.product(kept.productCode) }
                    .onSuccess { found -> changeForm { it.copy(product = found) } }
                kept.productName != null -> attempt { diary.products(kept.productName) }
                    .onSuccess { found -> found.firstOrNull { it.id == kept.productId }?.let { product -> changeForm { it.copy(product = product) } } }
            }
        }
    }

    /** Opens the window, and the camera over it. */
    fun openScanner() {
        if (read().form == null) open()
        chooseKind(EntryKind.PRODUCT)
        write { it.copy(scanning = true) }
    }

    /** Puts the camera away, back to the window. */
    fun closeScanner() = write { it.copy(scanning = false) }

    /** Says what the window holds that cannot be sent: its fields say it from now on. */
    fun sayProblems() = write { it.copy(showProblems = true) }

    /** The code the camera read: asked about at once, as somebody meant to. */
    fun scanned(code: String) {
        write { it.copy(scanning = false) }
        chooseKind(EntryKind.PRODUCT)
        unpick()
        search(code)
        checkCode()
    }

    /**
     * Asks about the code typed. It is asked about only on somebody's word: eight digits on the way to
     * thirteen can be a code as well, and a code the instance does not hold counts against what the
     * account may ask.
     */
    fun checkCode() {
        val form = read().form ?: return
        val code = productCodeOf(form.search)
        searching?.cancel()
        if (code == null) return changeForm { it.copy(codeBad = true, codeFailed = null) }
        changeForm { it.copy(codeBad = false, codeFailed = null, checkingCode = true) }
        searching = scope.launch {
            attempt { diary.product(code) }
                .onSuccess(::pickProduct)
                .onFailure { failure -> changeForm { it.copy(checkingCode = false, codeFailed = failure) } }
        }
    }

    fun pickProduct(product: Product) {
        searching?.cancel()
        changeForm {
            it.copy(
                product = product,
                quantity = firstAmount(product),
                search = "",
                products = emptyList(),
                searched = false,
                searchFailed = null,
                codeBad = false,
                checkingCode = false,
                codeFailed = null,
            )
        }
    }

    /** Writes a product of one's own, over the window: the search stays as it was behind it. */
    fun writeOwnProduct(code: String) = changeForm { it.copy(writingProduct = code) }

    /** Writes the reader's correction of [product], a product of the base whose label says otherwise, over the window. */
    fun correctProduct(product: Product) = changeForm { it.copy(writingProduct = product.code.orEmpty(), correcting = product) }

    fun closeOwnProduct() = changeForm { it.copy(writingProduct = null, correcting = null) }

    /** What was written is what the entry is made of; a correction takes the place of what it corrects, with the amount typed for it. */
    fun takeWrittenProduct(product: Product) {
        val corrected = read().form?.correcting != null
        closeOwnProduct()
        if (corrected) changeForm { it.copy(product = product) } else pickProduct(product)
    }

    /** What a product could not give is written as a meal of one's own, under its name where it has one. */
    fun asOwnMeal(name: String?) {
        chooseKind(EntryKind.MEAL)
        if (name != null) changeForm { it.copy(name = name.take(DiaryLimits.name)) }
    }

    private fun changeForm(change: (EntryForm) -> EntryForm) = write { state ->
        state.copy(form = state.form?.let(change), windowError = null)
    }

    fun chooseKind(kind: EntryKind) {
        searching?.cancel()
        changeForm { form ->
            form.copy(
                kind = kind,
                search = "",
                recipes = emptyList(),
                ingredients = emptyList(),
                products = emptyList(),
                searched = false,
                searchFailed = null,
                codeBad = false,
                checkingCode = false,
                codeFailed = null,
                // An amount is of what it was typed for: a product it comes back to starts as it did.
                quantity = if (kind == EntryKind.PRODUCT) form.product?.let(::firstAmount).orEmpty() else "",
            )
        }
        write { it.copy(showProblems = false, refused = emptyMap()) }
    }

    fun chooseSlot(slot: String) = changeForm { it.copy(slot = slot) }

    /** Looks for what is typed once two letters are typed and typing pauses, as the pantry's search does. */
    fun search(text: String) {
        changeForm { it.copy(search = text, recipes = emptyList(), ingredients = emptyList(), products = emptyList(), searched = false, searchFailed = null, codeBad = false, checkingCode = false, codeFailed = null) }
        searching?.cancel()
        val kind = read().form?.kind ?: return
        if (kind == EntryKind.PRODUCT) {
            if (looksLikeProductCode(text) || !canSearchProducts(text)) return
        } else if (text.trim().length < ProfilesViewModel.SEARCH_FROM) {
            return
        }
        searching = scope.launch {
            delay(ProfilesViewModel.SEARCH_DELAY_MS)
            if (kind == EntryKind.PRODUCT) {
                attempt { diary.products(text.trim()) }.let { found ->
                    answered(text, found.exceptionOrNull()) { it.copy(products = found.getOrDefault(emptyList())) }
                }
            } else if (kind == EntryKind.RECIPE) {
                attempt { recipes.search(RecipeFilter(search = text.trim())).items.take(OFFERED) }.let { found ->
                    answered(text, found.exceptionOrNull()) { it.copy(recipes = found.getOrDefault(emptyList())) }
                }
            } else {
                attempt { diary.ingredients(text.trim()).take(OFFERED) }.let { found ->
                    answered(text, found.exceptionOrNull()) { it.copy(ingredients = found.getOrDefault(emptyList())) }
                }
            }
        }
    }

    private fun answered(text: String, failed: Throwable?, found: (EntryForm) -> EntryForm) = changeForm { form ->
        if (form.search != text) form else found(form).copy(searched = true, searchFailed = failed)
    }

    fun pickRecipe(recipe: Recipe) {
        searching?.cancel()
        changeForm { it.copy(recipe = recipe, search = "", recipes = emptyList(), searched = false, searchFailed = null) }
    }

    /** Takes [ingredient], in the unit the API offers first: the one it is weighed or measured in. */
    fun pickIngredient(ingredient: DiaryIngredient) {
        searching?.cancel()
        changeForm { it.copy(ingredient = ingredient, unit = ingredient.units.first(), search = "", ingredients = emptyList(), searched = false, searchFailed = null) }
    }

    /** Lets another be looked for in the place of the one picked. */
    fun unpick() = changeForm { it.copy(recipe = null, ingredient = null, product = null, servings = "1", quantity = "") }

    /** What is typed into a field of the window: [change] says which. */
    fun typeEntry(field: EntryField, change: (EntryForm) -> EntryForm) {
        changeForm(change)
        write { it.copy(refused = it.refused - field.path) }
    }

    companion object {
        /** How many found recipes or ingredients the window offers. */
        const val OFFERED = 8
    }
}
