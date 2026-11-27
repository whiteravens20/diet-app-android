// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.whiteravens.dietapp.data.repository.PantryRepository
import net.whiteravens.dietapp.domain.EatenFood
import net.whiteravens.dietapp.domain.EatenOf
import net.whiteravens.dietapp.domain.EatenPantry
import net.whiteravens.dietapp.domain.ShownAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * What eating takes off the pantry, asked once the server has a mark or an entry: the window opens only where the API
 * offers something to take or to give back, takes or gives back only when told to, each person's portion of a meal
 * both ate from that person's pantry, and asks before an entry that took food goes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PantryQuestionsTest {
    private val scope = CoroutineScope(UnconfinedTestDispatcher())
    private val pantry = mockk<PantryRepository>()
    private val mine = EatenOf.Meal("plan-1", "meal-1")
    private val hers = EatenOf.Meal("plan-2", "meal-9")
    private val entry = EatenOf.Entry("entry-1")
    private val oats = EatenFood("oats", "Rolled oats", ShownAmount(60.0, "g"), ShownAmount(500.0, "g"), ShownAmount(60.0, "g"))
    private fun offered(name: String = "Ala") = EatenPantry(name, offer = listOf(oats), taken = emptyList())
    private fun took(name: String = "Ala") = EatenPantry(name, offer = emptyList(), taken = listOf(oats))
    private val nothing = EatenPantry("Ala", emptyList(), emptyList())

    @Test
    fun `asks of each person's portion of a meal both ate, and takes both on one press, one after the other`() {
        coEvery { pantry.eaten(mine) } returns offered("Ala")
        coEvery { pantry.eaten(hers) } returns offered("Ewa")
        coEvery { pantry.takeEaten(any()) } just Runs
        var changed = 0
        val questions = PantryQuestions(scope, pantry) { changed++ }

        questions.ask(listOf(mine, hers), PantryQuestion.TAKE)

        val window = questions.window.value!!
        assertEquals(PantryQuestion.TAKE, window.question)
        assertEquals(listOf("Ala", "Ewa"), window.portions.map { it.pantry.profileName })
        coVerify(exactly = 0) { pantry.takeEaten(any()) }
        questions.confirm()
        coVerifyOrder {
            pantry.takeEaten(mine)
            pantry.takeEaten(hers)
        }
        assertNull(questions.window.value)
        assertEquals(1, changed)
    }

    @Test
    fun `asks nothing where nothing is offered or the pantry cannot be asked, and asks of the portion it can read`() {
        val questions = PantryQuestions(scope, pantry)
        coEvery { pantry.eaten(mine) } returns nothing

        questions.ask(listOf(mine), PantryQuestion.TAKE)
        assertNull(questions.window.value)

        coEvery { pantry.eaten(mine) } throws IOException("down")
        coEvery { pantry.eaten(hers) } returns offered("Ewa")
        questions.ask(listOf(mine, hers), PantryQuestion.TAKE)
        assertEquals(listOf("Ewa"), questions.window.value!!.portions.map { it.pantry.profileName })
    }

    @Test
    fun `a mark taken back offers to give back what was taken, and nothing where nothing was`() {
        val questions = PantryQuestions(scope, pantry)
        coEvery { pantry.eaten(mine) } returns took()
        coEvery { pantry.giveBackEaten(mine) } just Runs

        // What was taken is no offer to take again.
        questions.ask(listOf(mine), PantryQuestion.TAKE)
        assertNull(questions.window.value)
        questions.ask(listOf(mine), PantryQuestion.GIVE_BACK)
        assertEquals(listOf(oats), questions.window.value!!.portions.single().foods(PantryQuestion.GIVE_BACK))
        questions.confirm()
        coVerify(exactly = 1) { pantry.giveBackEaten(mine) }
        assertNull(questions.window.value)

        coEvery { pantry.eaten(mine) } returns offered()
        questions.ask(listOf(mine), PantryQuestion.GIVE_BACK)
        assertNull(questions.window.value)
    }

    @Test
    fun `before an entry goes it asks where it took food, gives that back first, and otherwise lets it go as told`() {
        val questions = PantryQuestions(scope, pantry)
        val done = mutableListOf<String>()
        coEvery { pantry.giveBackEaten(entry) } answers { done += "given back" }

        // Nothing taken: it goes at once, unasked.
        coEvery { pantry.eaten(entry) } returns nothing
        questions.ask(listOf(entry), PantryQuestion.REMOVE) { done += "removed" }
        assertNull(questions.window.value)
        assertEquals(listOf("removed"), done)

        // Food taken: nothing goes before it is answered; given back, then removed.
        coEvery { pantry.eaten(entry) } returns took()
        done.clear()
        questions.ask(listOf(entry), PantryQuestion.REMOVE) { done += "removed" }
        assertEquals(PantryQuestion.REMOVE, questions.window.value!!.question)
        assertTrue(done.isEmpty())
        questions.confirm()
        assertEquals(listOf("given back", "removed"), done)

        // Removed without giving back.
        done.clear()
        questions.ask(listOf(entry), PantryQuestion.REMOVE) { done += "removed" }
        questions.removeOnly()
        assertEquals(listOf("removed"), done)

        // Put away: the entry stays.
        done.clear()
        questions.ask(listOf(entry), PantryQuestion.REMOVE) { done += "removed" }
        questions.close()
        assertTrue(done.isEmpty())
        assertNull(questions.window.value)
    }

    @Test
    fun `a refused take keeps the window open, says why, and a press again takes`() {
        val questions = PantryQuestions(scope, pantry)
        coEvery { pantry.eaten(mine) } returns offered()
        coEvery { pantry.takeEaten(mine) } throws IOException("down") andThen Unit

        questions.ask(listOf(mine), PantryQuestion.TAKE)
        questions.confirm()

        val refused = questions.window.value!!
        assertTrue(refused.error is IOException)
        assertFalse(refused.sending)
        questions.confirm()
        assertNull(questions.window.value)
        coVerify(exactly = 2) { pantry.takeEaten(mine) }
    }
}
