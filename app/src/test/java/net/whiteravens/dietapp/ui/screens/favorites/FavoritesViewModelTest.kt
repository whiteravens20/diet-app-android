// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.favorites

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.FavoriteFilter
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.testing.changeQueue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** A profile's marked recipes as the screen keeps them: narrowed, in order, and each mark changed or taken off where it is shown. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private val oats = MarkedRecipe("oats", "Oats", listOf("breakfast"), "favorite", emptyList(), tags = listOf("quick", "cheap"), calories = 320.0)
    private val soup = MarkedRecipe("soup", "Soup", listOf("lunch"), "often", emptyList(), tags = listOf("warm"), calories = 410.0)

    private val users = mockk<UserRepository>().also {
        coEvery { it.profiles() } returns listOf(Profile("ada", "Ada", "balanced", 3, 70.0, null), Profile("bo", "Bo", "vegan", 4, 70.0, null))
    }

    private fun favorites(vararg kept: MarkedRecipe) = mockk<FavoriteRepository>().also {
        coEvery { it.marked(any(), any()) } returns kept.toList()
    }

    /** The changes made on the phone that wait to be sent: none, unless a test makes one without a network. */
    private val queue = changeQueue()

    private val offline: suspend () -> Nothing = { throw IOException("no network") }

    @Test
    fun `the first profile's marks are read, and where a filter leaves some out, every mark too for the tags to choose from`() {
        val favorites = favorites(oats, soup)
        coEvery { favorites.marked("ada", FavoriteFilter(sentiment = "often")) } returns listOf(soup)
        val model = FavoritesViewModel(favorites, users, queue)

        model.narrow(FavoriteFilter(sentiment = "often"))

        assertEquals("ada", model.state.value.profileId)
        assertEquals(listOf(soup), model.state.value.items)
        assertEquals(listOf("cheap", "quick", "warm"), model.state.value.tags)
        coVerify { favorites.marked("ada", FavoriteFilter()) }
    }

    @Test
    fun `another profile shows its own marks, and a tag of the first narrows nothing there`() {
        val favorites = favorites(oats, soup)
        val model = FavoritesViewModel(favorites, users, queue)
        model.narrow(FavoriteFilter(tag = "quick", sort = "title"))

        model.pickProfile("bo")

        assertEquals("bo", model.state.value.profileId)
        assertEquals(FavoriteFilter(sort = "title"), model.state.value.filter)
        coVerify { favorites.marked("bo", FavoriteFilter(sort = "title")) }
    }

    @Test
    fun `a change of a mark is shown at once and sent whole, with the tags the recipe has`() {
        val favorites = favorites(oats, soup)
        val sent = CompletableDeferred<Unit>()
        coEvery { favorites.mark("ada", "oats", "avoid", listOf("quick", "cheap")) } coAnswers {
            sent.await()
            Submitted.Sent(Unit)
        }
        val model = FavoritesViewModel(favorites, users, queue)

        model.setSentiment(oats, "avoid")

        assertEquals("avoid", model.state.value.shown(oats).sentiment)
        coEvery { favorites.marked("ada", FavoriteFilter()) } returns listOf(oats.copy(sentiment = "avoid"), soup)
        sent.complete(Unit)
        assertNull(model.state.value.sending)
        assertEquals("avoid", model.state.value.items.first().sentiment)
    }

    @Test
    fun `a change the API refuses is taken back and said, and the list is read again`() {
        val favorites = favorites(oats, soup)
        val refused = ApiException(404, "RECIPE_NOT_FOUND", "Recipe not found.")
        coEvery { favorites.mark(any(), any(), any(), any()) } throws refused
        val model = FavoritesViewModel(favorites, users, queue)

        model.setSentiment(oats, "often")

        assertEquals("favorite", model.state.value.shown(oats).sentiment)
        assertEquals(FavoritesViewModel.Failure.SAVE to refused, model.state.value.failed)
        coVerify(exactly = 2) { favorites.marked("ada", FavoriteFilter()) }
    }

    @Test
    fun `a tag is put on trimmed and once, up to the most a recipe holds, and one taken off is sent without it`() {
        val favorites = favorites(oats, soup)
        coEvery { favorites.mark(any(), any(), any(), any()) } returns Submitted.Sent(Unit)
        val full = oats.copy(tags = (1..FavoriteFilter.MOST_TAGS).map { "t$it" })
        val model = FavoritesViewModel(favorites, users, queue)

        model.addTag(oats, " quick ")
        model.addTag(oats, "   ")
        model.addTag(full, "one more")
        model.addTag(oats, " for two ")
        model.removeTag(oats, "quick")

        coVerify(exactly = 1) { favorites.mark("ada", "oats", "favorite", listOf("quick", "cheap", "for two")) }
        coVerify(exactly = 1) { favorites.mark("ada", "oats", "favorite", listOf("cheap")) }
        coVerify(exactly = 2) { favorites.mark(any(), any(), any(), any()) }
    }

    @Test
    fun `a mark taken off leaves the list, and one the API no longer has is said and the list read again`() {
        val favorites = favorites(oats, soup)
        coEvery { favorites.unmark("ada", "soup") } returns Submitted.Sent(Unit)
        val model = FavoritesViewModel(favorites, users, queue)
        coEvery { favorites.marked("ada", FavoriteFilter()) } returns listOf(oats)

        model.remove(soup)

        assertEquals(listOf(oats), model.state.value.items)
        assertNull(model.state.value.failed)

        val gone = ApiException(404, "FAVORITE_NOT_FOUND", "That recipe is not marked for this profile.")
        coEvery { favorites.unmark("ada", "oats") } throws gone
        coEvery { favorites.marked("ada", FavoriteFilter()) } returns emptyList()

        model.remove(oats)

        assertEquals(FavoritesViewModel.Failure.REMOVE to gone, model.state.value.failed)
        assertEquals(emptyList<MarkedRecipe>(), model.state.value.items)
    }

    @Test
    fun `without a network a change of a mark is shown over it, waiting, and a mark taken off is left out, until they are sent and the list read again`() {
        val favorites = favorites(oats, soup)
        coEvery { favorites.mark("ada", "oats", "avoid", listOf("quick", "cheap")) } coAnswers {
            queue.submit(QueuedChange.Marked("ada", "oats", "avoid", listOf("quick", "cheap")), offline)
        }
        coEvery { favorites.unmark("ada", "soup") } coAnswers { queue.submit(QueuedChange.Unmarked("ada", "soup"), offline) }
        val model = FavoritesViewModel(favorites, users, queue)

        model.setSentiment(oats, "avoid")
        model.remove(soup)

        assertEquals(listOf(oats), model.state.value.shownItems)
        assertEquals("avoid", model.state.value.shown(oats).sentiment)
        assertEquals(setOf("oats"), model.state.value.waitingRecipes)
        assertTrue(model.state.value.offline)
        // What was read stays as read, and is not read again without a network.
        assertEquals(listOf(oats, soup), model.state.value.items)
        coVerify(exactly = 1) { favorites.marked("ada", FavoriteFilter()) }

        coEvery { favorites.marked("ada", FavoriteFilter()) } returns listOf(oats.copy(sentiment = "avoid"))
        runBlocking { queue.drain { } }

        assertEquals(listOf(oats.copy(sentiment = "avoid")), model.state.value.shownItems)
        assertEquals(emptySet<String>(), model.state.value.waitingRecipes)
        assertFalse(model.state.value.offline)
    }
}
