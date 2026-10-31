// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiErrorIssueDto
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.DiaryRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.data.repository.UserRepository
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
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.NewDiaryEntry
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.Rebalanced
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipePage
import net.whiteravens.dietapp.domain.ScaleChange
import net.whiteravens.dietapp.domain.mealAt
import net.whiteravens.dietapp.testing.changeQueue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelTest {
    private val ala = Profile("a", "Ala", "balanced", 4, 70.0, null)
    private val olek = Profile("b", "Olek", "vegan", 3, 70.0, null)
    private val today: LocalDate = LocalDate.now()

    private val users = mockk<UserRepository>().also { coEvery { it.profilesOrKept() } returns listOf(ala, olek) }
    private val diary = mockk<DiaryRepository>().also {
        coEvery { it.keptDay(any(), any()) } returns null
        coEvery { it.day(any(), any()) } coAnswers { day(firstArg(), secondArg()) }
    }
    private val plans = mockk<MealPlanRepository>()
    private val recipes = mockk<RecipeRepository>()
    private val queue: ChangeQueue = changeQueue()

    private val offline = IOException("no network")

    private val main = UnconfinedTestDispatcher()

    @Before
    fun mainThread() = Dispatchers.setMain(main)

    @After
    fun reset() = Dispatchers.resetMain()

    private fun entry(id: String, slot: String = "lunch", name: String = "Stew") = DiaryEntry(id, slot, "recipe", name, 1.0, null, 300.0, null, false)

    private fun meal(id: String, slot: String = "dinner", eaten: Boolean = false, recipeId: String? = "r") = DiaryPlanMeal(id, slot, "Meal $id", recipeId, eaten, 500.0)

    private fun day(
        profileId: String = "a",
        date: LocalDate = today,
        entries: List<DiaryEntry> = emptyList(),
        plan: DiaryDayPlan? = null,
        eaten: Double = 0.0,
        syncedAt: Long? = null,
    ) = DiaryDay(profileId, date.toString(), Nutrition(2000.0, 100.0, 60.0, 250.0), plan != null, plan, entries, Nutrition(eaten, 0.0, 0.0, 0.0), false, syncedAt)

    private fun plan(vararg meals: DiaryPlanMeal, offTarget: String? = null) = DiaryDayPlan("p", false, meals.toList(), 0.0, offTarget)

    private fun model(profileId: String? = null, date: String? = null, adding: Boolean = false) = DiaryViewModel(
        SavedStateHandle(buildMap {
            profileId?.let { put("profileId", it) }
            date?.let { put("date", it) }
            put("adding", adding)
        }),
        diary,
        users,
        plans,
        recipes,
        queue,
    )

    private fun DiaryViewModel.typeOwnMeal(name: String = "Soup", calories: String = "300") {
        chooseKind(EntryKind.MEAL)
        typeEntry(EntryField.NAME) { it.copy(name = name) }
        typeEntry(EntryField.CALORIES) { it.copy(calories = calories) }
    }

    private fun rebalanced(vararg changes: ScaleChange, left: Double = 0.0, scope: String = "day"): Rebalanced =
        Rebalanced(mockk<MealPlan>(), Rebalance(scope, false, changes.toList(), left, 2500.0, 2000.0), null)

    // ── What opens ──────────────────────────────────────────────────────────

    @Test
    fun `it opens on the first profile and today, and reads that day`() {
        val state = model().state.value

        assertEquals("a", state.profileId)
        assertEquals(today, state.date)
        assertEquals(today.toString(), state.day?.date)
        assertFalse(state.loading)
    }

    @Test
    fun `a way here names the profile and the day`() {
        val state = model(profileId = "b", date = "2026-10-20").state.value

        assertEquals("b", state.profileId)
        assertEquals(LocalDate.parse("2026-10-20"), state.date)
        coVerify { diary.day("b", LocalDate.parse("2026-10-20")) }
    }

    @Test
    fun `a day no request may name goes to today, and a profile that is not there to the first`() {
        assertEquals(today, model(date = "1999-12-31").state.value.date)
        assertEquals(today, model(date = "2101-01-01").state.value.date)
        assertEquals(today, model(date = "not a day").state.value.date)
        assertEquals("a", model(profileId = "gone").state.value.profileId)
    }

    @Test
    fun `an account without profiles has no day to read`() {
        coEvery { users.profilesOrKept() } returns emptyList()

        val state = model().state.value

        assertNull(state.profileId)
        assertNull(state.day)
        coVerify(exactly = 0) { diary.day(any(), any()) }
    }

    @Test
    fun `profiles that cannot be read say why, and asked again they are read`() {
        coEvery { users.profilesOrKept() } throws offline
        val model = model()

        assertSame(offline, model.state.value.profilesError)

        coEvery { users.profilesOrKept() } returns listOf(ala)
        model.pull()

        assertNull(model.state.value.profilesError)
        assertEquals("a", model.state.value.profileId)
    }

    @Test
    fun `asked for by today's card, the window opens at the meal of the hour, once`() {
        val handle = SavedStateHandle(mapOf("profileId" to "a", "adding" to true))
        val before = mealAt(LocalTime.now())

        val model = DiaryViewModel(handle, diary, users, plans, recipes, queue)

        assertTrue(model.state.value.form?.slot == before || model.state.value.form?.slot == mealAt(LocalTime.now()))
        assertEquals(false, handle.get<Boolean>("adding"))
    }

    @Test
    fun `a day's own button opens at its meal, and the general one on another day at the first meal the day has`() {
        coEvery { diary.day(any(), LocalDate.parse("2026-10-20")) } returns day(date = LocalDate.parse("2026-10-20"), entries = listOf(entry("e", slot = "snack")))
        val model = model(date = "2026-10-20")

        model.openWindow("dinner")
        assertEquals("dinner", model.state.value.form?.slot)
        model.closeWindow()

        model.openWindowNow()
        assertEquals("snack", model.state.value.form?.slot)
    }

    // ── Reading, with a network and without ─────────────────────────────────

    @Test
    fun `what was kept of a day is shown, with when, where it cannot be read for want of a network`() {
        coEvery { diary.keptDay("a", today) } returns day(eaten = 640.0, syncedAt = 1234L)
        coEvery { diary.day("a", today) } throws offline

        val state = model().state.value

        assertTrue(state.offline)
        assertEquals(640.0, state.day?.eaten?.calories)
        assertEquals(1234L, state.readAt)
        assertNull(state.error)
    }

    @Test
    fun `a day neither read nor kept, for want of a network, is no failure, it takes what is written all the same`() {
        coEvery { diary.day("a", today) } throws offline

        val state = model().state.value

        assertTrue(state.offline)
        assertNull(state.day)
        assertNull(state.error)
        assertTrue(state.canAdd)
    }

    @Test
    fun `a refusal of the server says why, what was read is not kept up, and asked again it is read`() {
        val refusal = ApiException(404, "PROFILE_NOT_FOUND", "Profile not found.")
        coEvery { diary.day("a", today) } throws refusal
        val model = model()

        assertSame(refusal, model.state.value.error)
        assertFalse(model.state.value.offline)

        coEvery { diary.day("a", today) } returns day()
        model.retry()

        assertNull(model.state.value.error)
    }

    @Test
    fun `a window opened without a network opens on what can be written without one, one opened before finds out, unless something was done in it`() {
        coEvery { diary.day("a", today) } throws offline
        val model = model()

        model.openWindow("lunch")
        assertEquals(EntryKind.MEAL, model.state.value.form?.kind)

        val told = model(profileId = "a", adding = true)
        assertEquals(EntryKind.MEAL, told.state.value.form?.kind)
    }

    @Test
    fun `read anew, what is shown stays until the answer, and stays where none comes`() {
        val model = model()
        coEvery { diary.day("a", today) } throws offline

        model.pull()

        assertEquals(today.toString(), model.state.value.day?.date)
        assertTrue(model.state.value.offline)
        assertFalse(model.state.value.pulled)
    }

    @Test
    fun `read anew with an answer, the screen is no longer offline`() {
        coEvery { diary.day("a", today) } throws offline
        val model = model()
        coEvery { diary.day("a", today) } returns day(eaten = 100.0)

        model.pull()

        assertFalse(model.state.value.offline)
        assertEquals(100.0, model.state.value.day?.eaten?.calories)
    }

    @Test
    fun `shown again after another screen it reads at once, back from another app only as often as it may`() {
        val model = model()

        model.shownAgain(cameToTheFront = true)
        coVerify(exactly = 1) { diary.day("a", today) }

        model.shownAgain(cameToTheFront = false)
        coVerify(exactly = 2) { diary.day("a", today) }
    }

    @Test
    fun `another day or another profile is read, and what was said of the one before goes`() {
        val model = model()
        coEvery { diary.remove("a", "e") } throws ApiException(500, "SERVER", "No.")
        model.remove(entry("e"))
        assertEquals(DiaryViewModel.Failure.REMOVE, model.state.value.failed?.first)

        val yesterday = today.minusDays(1)
        model.pickDay(yesterday)

        assertNull(model.state.value.failed)
        assertEquals(yesterday, model.state.value.date)
        assertEquals(yesterday.minusDays(1), model.state.value.copyFrom)
        coVerify { diary.day("a", yesterday) }

        model.pickProfile("b")

        assertEquals("b", model.state.value.profileId)
        coVerify { diary.day("b", yesterday) }
    }

    @Test
    fun `no day a request may not name is gone to`() {
        val model = model(date = "2000-01-01")

        model.pickDay(LocalDate.parse("1999-12-31"))

        assertEquals(LocalDate.parse("2000-01-01"), model.state.value.date)
    }

    @Test
    fun `a day to come and a day over a year back take no entry, and one with the most entries neither`() {
        assertTrue(model(date = today.plusDays(1).toString()).state.value.ahead)
        assertFalse(model(date = today.plusDays(1).toString()).state.value.canAdd)
        assertTrue(model(date = today.minusDays(367).toString()).state.value.tooOld)
        assertFalse(model(date = today.minusDays(366).toString()).state.value.tooOld)
        assertTrue(model(date = today.minusDays(366).toString()).state.value.canAdd)

        coEvery { diary.day("a", today) } returns day(entries = (1..40).map { entry("e$it") })
        assertTrue(model().state.value.full)
        assertFalse(model().state.value.canAdd)
    }

    @Test
    fun `the meals a day has are those of its plan and those written for, in the order they are eaten`() {
        coEvery { diary.day("a", today) } returns day(entries = listOf(entry("e", slot = "snack")), plan = plan(meal("m", slot = "dinner"), meal("n", slot = "breakfast")))

        assertEquals(listOf("breakfast", "snack", "dinner"), model().state.value.slots)
    }

    // ── An entry written ────────────────────────────────────────────────────

    @Test
    fun `an entry written with a network closes the window, and the day is read anew`() {
        coEvery { diary.add(any(), any(), any()) } returns Submitted.Sent(entry("new"))
        val model = model()
        model.openWindow("dinner")
        model.typeOwnMeal()
        coEvery { diary.day("a", today) } returns day(entries = listOf(entry("new")), eaten = 300.0)

        model.addEntry()

        coVerify { diary.add("a", today, NewDiaryEntry.OwnMeal("dinner", "Soup", 1.0, 300.0, null, null, null)) }
        assertNull(model.state.value.form)
        assertEquals(300.0, model.state.value.day?.eaten?.calories)
    }

    @Test
    fun `an entry written without a network closes the window and is shown waiting, the sum as the server had it, said to leave it out`() {
        val diaryQueue = changeQueue()
        coEvery { diary.add(any(), any(), any()) } coAnswers {
            val change = QueuedChange.DiaryAdded("a", today.toString(), "dinner", "Soup", servings = 2.0, calories = 300.0, key = "k")
            diaryQueue.submit(change) { throw offline }
        }
        coEvery { diary.day("a", today) } returns day(eaten = 100.0)
        val model = DiaryViewModel(SavedStateHandle(), diary, users, plans, recipes, diaryQueue)
        model.openWindow("dinner")
        model.typeOwnMeal()

        model.addEntry()

        val state = model.state.value
        assertNull(state.form)
        assertTrue(state.offline)
        assertEquals(100.0, state.day?.eaten?.calories)
        assertEquals(1, state.leftOut)
        val shown = state.entries.single()
        assertEquals("k", shown.id)
        assertEquals("Soup", shown.name)
        assertEquals(600.0, shown.calories)
        assertTrue("k" in state.waitingEntries)
        assertEquals(listOf("dinner"), state.slots)
    }

    @Test
    fun `once what waits is sent, the screen is no longer offline, and the day is read as the server has it`() {
        val diaryQueue = changeQueue()
        coEvery { diary.day("a", today) } throws offline
        val model = DiaryViewModel(SavedStateHandle(), diary, users, plans, recipes, diaryQueue)
        runBlocking { diaryQueue.submit(QueuedChange.DiaryAdded("a", today.toString(), "dinner", "Soup", calories = 300.0, key = "k")) { throw offline } }
        assertTrue(model.state.value.offline)
        coEvery { diary.day("a", today) } returns day(entries = listOf(entry("kept", slot = "dinner", name = "Soup")), eaten = 300.0)

        runBlocking { diaryQueue.drain { } }

        assertFalse(model.state.value.offline)
        assertEquals(0, model.state.value.leftOut)
        assertEquals(listOf("kept"), model.state.value.entries.map { it.id })
    }

    @Test
    fun `nothing is sent while the window holds what the API would refuse, and it says so`() {
        val model = model()
        model.openWindow("dinner")
        model.typeOwnMeal(name = " ", calories = "much")

        model.addEntry()

        assertTrue(model.state.value.showProblems)
        assertTrue(model.state.value.form != null)
        coVerify(exactly = 0) { diary.add(any(), any(), any()) }
    }

    @Test
    fun `a refusal of the API by its fields stays in the window, by the field, until it is typed over`() {
        val refused = ApiException(
            400,
            "VALIDATION_FAILED",
            "Request payload failed validation.",
            issues = listOf(ApiErrorIssueDto(path = "servings", message = "m", code = "too_big", maximum = 20.0, inclusive = true)),
        )
        coEvery { diary.add(any(), any(), any()) } throws refused
        val model = model()
        model.openWindow("dinner")
        model.typeOwnMeal()

        model.addEntry()

        assertTrue(model.state.value.form != null)
        assertTrue(model.state.value.refused["servings"] is FieldRefusal.TooBig)
        assertNull(model.state.value.windowError)

        model.typeEntry(EntryField.SERVINGS) { it.copy(servings = "2") }
        assertNull(model.state.value.refused["servings"])
    }

    @Test
    fun `a refusal of the API otherwise says why in the window, which stays as it is`() {
        val refused = ApiException(400, "DIARY_MACROS_OVER_ENERGY", "No.")
        coEvery { diary.add(any(), any(), any()) } throws refused
        val model = model()
        model.openWindow("dinner")
        model.typeOwnMeal()

        model.addEntry()

        assertSame(refused, model.state.value.windowError)
        assertEquals("Soup", model.state.value.form?.name)
    }

    // ── Looking for what to write ───────────────────────────────────────────

    @Test
    fun `a search goes out from two letters, once typing pauses, and what it found is offered`() {
        val found = mockk<Recipe>()
        coEvery { recipes.search(any(), any()) } returns RecipePage(List(10) { found }, 1, 1)
        val model = model()
        model.openWindow("lunch")

        model.search("o")
        main.scheduler.advanceUntilIdle()
        assertFalse(model.state.value.form!!.searched)
        coVerify(exactly = 0) { recipes.search(any(), any()) }

        model.search("oat")
        model.search("oats")
        main.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { recipes.search(match { it.search == "oats" }, any()) }
        assertTrue(model.state.value.form!!.searched)
        assertEquals(DiaryViewModel.OFFERED, model.state.value.form!!.recipes.size)
    }

    @Test
    fun `an ingredient is looked for in the catalogue, and picked in the unit the API offers first`() {
        val egg = DiaryIngredient("i", "Egg", listOf("g", "piece"), "piece", "g", 143.0)
        coEvery { diary.ingredients("egg") } returns listOf(egg)
        val model = model()
        model.openWindow("lunch")
        model.chooseKind(EntryKind.INGREDIENT)

        model.search("egg")
        main.scheduler.advanceUntilIdle()
        assertEquals(listOf(egg), model.state.value.form!!.ingredients)

        model.pickIngredient(egg)

        assertSame(egg, model.state.value.form!!.ingredient)
        assertEquals("g", model.state.value.form!!.unit)
        assertEquals("", model.state.value.form!!.search)
    }

    @Test
    fun `a search with no answer says so, and says nothing of what there is`() {
        coEvery { recipes.search(any(), any()) } throws offline
        val model = model()
        model.openWindow("lunch")

        model.search("oat")
        main.scheduler.advanceUntilIdle()

        assertTrue(model.state.value.form!!.searched)
        assertSame(offline, model.state.value.form!!.searchFailed)
        assertEquals(emptyList<Recipe>(), model.state.value.form!!.recipes)

        // Typed further, what was said of the last words goes.
        model.search("oats")
        assertNull(model.state.value.form!!.searchFailed)
    }

    @Test
    fun `a recipe picked empties the search, and picked again the servings start over`() {
        val found = mockk<Recipe>()
        val model = model()
        model.openWindow("lunch")

        model.pickRecipe(found)
        assertSame(found, model.state.value.form?.recipe)
        model.typeEntry(EntryField.SERVINGS) { it.copy(servings = "3") }

        model.unpick()

        assertNull(model.state.value.form?.recipe)
        assertEquals("1", model.state.value.form?.servings)
    }

    @Test
    fun `another kind leaves what was looked for, and what was said of it`() {
        val model = model()
        model.openWindow("lunch")
        model.search("oat")

        model.chooseKind(EntryKind.INGREDIENT)

        assertEquals("", model.state.value.form?.search)
        assertEquals(EntryKind.INGREDIENT, model.state.value.form?.kind)
        assertEquals("lunch", model.state.value.form?.slot)
    }

    // ── An entry changed, taken out ─────────────────────────────────────────

    @Test
    fun `an entry changed goes by what changes, and the day is read anew`() {
        coEvery { diary.change(any(), any(), any(), any(), any()) } returns Submitted.Sent(entry("e"))
        val model = model()
        val e = entry("e")

        model.openEdit(e)
        model.typeEdit { it.copy(servings = "2") }
        model.saveEdit()

        coVerify { diary.change("a", "e", null, 2.0, null) }
        assertNull(model.state.value.edit)
    }

    @Test
    fun `a change of an amount refused names it by the word alone, and the window says it at the field`() {
        val refused = ApiException(
            400,
            "VALIDATION_FAILED",
            "Request payload failed validation.",
            issues = listOf(ApiErrorIssueDto(path = "quantity", message = "m", code = "too_big", maximum = 5000.0, inclusive = true)),
        )
        coEvery { diary.change(any(), any(), any(), any(), any()) } throws refused
        val model = model()
        val milk = DiaryEntry("e", "snack", "ingredient", "Milk", null, DiaryAmount(250.0, "g", 250.0, "g"), 48.0, null, false)

        model.openEdit(milk)
        model.typeEdit { it.copy(quantity = "300") }
        model.saveEdit()

        assertTrue(model.state.value.refused[EntryField.QUANTITY.path] is FieldRefusal.TooBig)
        assertNull(model.state.value.windowError)
        assertTrue(model.state.value.edit != null)
    }

    @Test
    fun `a refusal by a field the window does not have is said in words, not swallowed`() {
        val refused = ApiException(
            400,
            "VALIDATION_FAILED",
            "Request payload failed validation.",
            issues = listOf(ApiErrorIssueDto(path = "date", message = "m", code = "custom")),
        )
        coEvery { diary.add(any(), any(), any()) } throws refused
        val model = model()
        model.openWindow("dinner")
        model.typeOwnMeal()

        model.addEntry()

        assertEquals(emptyMap<String, FieldRefusal>(), model.state.value.refused)
        assertSame(refused, model.state.value.windowError)
    }

    @Test
    fun `nothing changed, nothing is sent, and the window goes`() {
        val model = model()

        model.openEdit(entry("e"))
        model.saveEdit()

        assertNull(model.state.value.edit)
        coVerify(exactly = 0) { diary.change(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an entry changed without a network is shown as changed, waiting, its figures unknown`() {
        val diaryQueue = changeQueue()
        coEvery { diary.day("a", today) } returns day(entries = listOf(entry("e")))
        coEvery { diary.change("a", "e", null, 2.0, null) } coAnswers { diaryQueue.submit(QueuedChange.DiaryChanged("a", "e", null, 2.0, null)) { throw offline } }
        val model = DiaryViewModel(SavedStateHandle(), diary, users, plans, recipes, diaryQueue)

        model.openEdit(entry("e"))
        model.typeEdit { it.copy(servings = "2") }
        model.saveEdit()

        val shown = model.state.value.entries.single()
        assertEquals(2.0, shown.servings)
        assertNull(shown.calories)
        assertTrue("e" in model.state.value.waitingEntries)
        assertEquals(1, model.state.value.leftOut)
    }

    @Test
    fun `an entry that waits to be written is changed once the server has it`() {
        val diaryQueue = changeQueue()
        val model = DiaryViewModel(SavedStateHandle(), diary, users, plans, recipes, diaryQueue)
        runBlocking { diaryQueue.submit(QueuedChange.DiaryAdded("a", today.toString(), "dinner", "Soup", calories = 300.0, key = "k")) { throw offline } }

        model.openEdit(model.state.value.entries.single())

        assertNull(model.state.value.edit)
    }

    @Test
    fun `an entry that waits to be written is taken out of the queue, unsent, and nothing is read for it`() {
        val diaryQueue = changeQueue()
        coEvery { diary.day("a", today) } throws offline
        val model = DiaryViewModel(SavedStateHandle(), diary, users, plans, recipes, diaryQueue)
        runBlocking { diaryQueue.submit(QueuedChange.DiaryAdded("a", today.toString(), "dinner", "Soup", calories = 300.0, key = "k")) { throw offline } }

        model.remove(model.state.value.entries.single())

        assertEquals(emptyList<DiaryEntry>(), model.state.value.entries)
        assertTrue(model.state.value.offline)
        coVerify(exactly = 0) { diary.remove(any(), any()) }
    }

    @Test
    fun `an entry taken out without a network is left out, waiting`() {
        val diaryQueue = changeQueue()
        coEvery { diary.day("a", today) } returns day(entries = listOf(entry("e")))
        coEvery { diary.remove("a", "e") } coAnswers { diaryQueue.submit(QueuedChange.DiaryRemoved("a", "e")) { throw offline } }
        val model = DiaryViewModel(SavedStateHandle(), diary, users, plans, recipes, diaryQueue)

        model.remove(entry("e"))

        assertEquals(emptyList<DiaryEntry>(), model.state.value.entries)
        assertEquals(1, model.state.value.leftOut)
        assertTrue(model.state.value.offline)
    }

    // ── The plan's meals ────────────────────────────────────────────────────

    @Test
    fun `a meal marked eaten rebalances its day, says how with the way back, and the day is read anew`() {
        val change = ScaleChange("n", 1.0, 0.8)
        val answer = rebalanced(change)
        coEvery { diary.day("a", today) } returns day(plan = plan(meal("m"), meal("n", slot = "lunch")))
        coEvery { plans.setEaten("p", "m", true) } returns Submitted.Sent(answer)
        coEvery { plans.undo("p", answer.rebalance!!) } returns rebalanced()
        val model = model()

        model.setEaten(meal("m"))

        assertEquals(answer.rebalance, model.state.value.rebalance)
        assertEquals("p", model.state.value.rebalancedPlan)

        model.undoRebalance()

        coVerify { plans.undo("p", answer.rebalance!!) }
        // The way back says nothing of itself.
        assertNull(model.state.value.rebalance)
    }

    @Test
    fun `a meal marked eaten that moves nothing says nothing of it`() {
        coEvery { diary.day("a", today) } returns day(plan = plan(meal("m")))
        coEvery { plans.setEaten("p", "m", true) } returns Submitted.Sent(rebalanced())
        val model = model()

        model.setEaten(meal("m"))

        assertNull(model.state.value.rebalance)
        assertNull(model.state.value.resizeLeft)
    }

    @Test
    fun `a meal marked eaten without a network shows so, waiting, the sum as the server had it`() {
        val diaryQueue = changeQueue()
        coEvery { diary.day("a", today) } returns day(plan = plan(meal("m")), eaten = 0.0)
        coEvery { plans.setEaten("p", "m", true) } coAnswers { diaryQueue.submit(QueuedChange.Eaten("p", "m", true)) { throw offline } }
        val model = DiaryViewModel(SavedStateHandle(), diary, users, plans, recipes, diaryQueue)

        model.setEaten(meal("m"))

        assertTrue(model.state.value.planMeals.single().eaten)
        assertTrue("m" in model.state.value.waitingMeals)
        assertEquals(0.0, model.state.value.day?.eaten?.calories)
        assertEquals(1, model.state.value.leftOut)
    }

    @Test
    fun `a meal of a day to come cannot have been eaten`() {
        val ahead = today.plusDays(1)
        coEvery { diary.day("a", ahead) } returns day(date = ahead, plan = plan(meal("m")))
        val model = model(date = ahead.toString())

        model.setEaten(meal("m"))

        coVerify(exactly = 0) { plans.setEaten(any(), any(), any()) }
    }

    @Test
    fun `a press to resize that moves something says how, one that moves nothing says what is left`() {
        coEvery { diary.day("a", today) } returns day(plan = plan(meal("m"), offTarget = "over"))
        val moved = rebalanced(ScaleChange("m", 1.0, 0.9))
        coEvery { plans.resizeDay("p", today.toString()) } returns moved
        val model = model()

        model.resize()

        assertEquals(moved.rebalance, model.state.value.rebalance)
        assertNull(model.state.value.resizeLeft)

        coEvery { plans.resizeDay("p", today.toString()) } returns rebalanced(left = 120.0)
        model.resize()

        assertNull(model.state.value.rebalance)
        assertEquals(120.0, model.state.value.resizeLeft)
    }

    @Test
    fun `a change of the plan that fails says why`() {
        coEvery { diary.day("a", today) } returns day(plan = plan(meal("m"), offTarget = "over"))
        coEvery { plans.resizeDay(any(), any()) } throws ApiException(409, "PLAN_CHANGED", "No.")
        val model = model()

        model.resize()

        assertEquals(DiaryViewModel.Failure.REBALANCE, model.state.value.failed?.first)
        assertFalse(model.state.value.changing)
    }

    // ── Copying a day ───────────────────────────────────────────────────────

    @Test
    fun `a day is copied from the one before by default, and says how many came, and the day shown is the one it left`() {
        val after = day(entries = listOf(entry("c")), eaten = 300.0)
        coEvery { diary.copy("a", today.minusDays(1), today) } returns DiaryCopied(1, after)
        val model = model()

        model.copy()

        assertEquals(DiaryViewModel.Copied(today.minusDays(1), 1), model.state.value.copied)
        assertEquals(listOf("c"), model.state.value.entries.map { it.id })
        assertFalse(model.state.value.copying)
    }

    @Test
    fun `what came here is not copied again by a second press, and another day may be`() {
        coEvery { diary.copy(any(), any(), any()) } returns DiaryCopied(1, day())
        val model = model()

        model.copy()
        model.copy()
        coVerify(exactly = 1) { diary.copy(any(), any(), any()) }

        model.chooseCopyFrom(today.minusDays(2))
        model.copy()
        coVerify(exactly = 1) { diary.copy("a", today.minusDays(2), today) }
    }

    @Test
    fun `a day that held nothing may be asked again, and a day is not copied into itself`() {
        coEvery { diary.copy(any(), any(), any()) } returns DiaryCopied(0, day())
        val model = model()

        model.copy()
        model.copy()
        coVerify(exactly = 2) { diary.copy(any(), any(), any()) }

        model.chooseCopyFrom(today)
        model.copy()
        coVerify(exactly = 2) { diary.copy(any(), any(), any()) }
    }

    @Test
    fun `a copy that fails says why`() {
        coEvery { diary.copy(any(), any(), any()) } throws ApiException(409, "DIARY_DAY_FULL", "No.")
        val model = model()

        model.copy()

        assertEquals(DiaryViewModel.Failure.COPY, model.state.value.failed?.first)
        assertNull(model.state.value.copied)
    }

    @Test
    fun `a profile's changes that wait are shown over its own diary alone`() {
        val diaryQueue = changeQueue()
        val model = DiaryViewModel(SavedStateHandle(mapOf("profileId" to "b")), diary, users, plans, recipes, diaryQueue)

        runBlocking { diaryQueue.submit(QueuedChange.DiaryAdded("a", today.toString(), "dinner", "Ala's", calories = 1.0, key = "k")) { throw offline } }

        assertEquals(emptyList<DiaryEntry>(), model.state.value.entries)
        assertEquals(0, model.state.value.leftOut)
    }
}
