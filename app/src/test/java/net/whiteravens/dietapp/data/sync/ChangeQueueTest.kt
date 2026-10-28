// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.sync

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import net.whiteravens.dietapp.data.local.PendingChangeRow
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.testing.MemoryChanges
import net.whiteravens.dietapp.testing.changeQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The changes made on the phone: sent at once where they can be, kept where they cannot, and sent in their turn. */
class ChangeQueueTest {
    private val changes = MemoryChanges()
    private val work = mockk<ChangesWork>(relaxed = true)
    private val queue = changeQueue(changes, work)

    private fun row(item: String, checked: Boolean = true) = QueuedChange.ShoppingRow("list", item, checked = checked)

    private fun removal(id: String) = QueuedChange.WeightDeleted("profile", id)

    private val offline: suspend () -> Nothing = { throw IOException("no network") }

    @Test
    fun `a change is sent at once where nothing waits, and the server's answer given back`() = runBlocking {
        val sent = queue.submit(row("bread")) { "answered" }

        assertEquals(Submitted.Sent("answered"), sent)
        assertTrue(changes.all.isEmpty())
        verify(exactly = 0) { work.sendSoon() }
    }

    @Test
    fun `a change made without a network is kept, as it was made, said to be kept for want of one, and its sending asked for`() = runBlocking {
        val kept = queue.submit(row("bread"), offline)

        assertEquals(Submitted.Kept(offline = true), kept)
        assertEquals(listOf("shopping-row"), changes.all.map { it.kind })
        assertEquals(listOf(row("bread")), queue.waiting.first().map { it.change })
        verify { work.sendSoon() }
    }

    @Test
    fun `a change made while another waits is kept behind it, not sent before it`() = runBlocking {
        queue.submit(row("bread"), offline)
        var asked = false

        val kept = queue.submit(row("milk")) { asked = true }

        assertEquals(Submitted.Kept(offline = false), kept)
        assertEquals(false, asked)
        assertEquals(listOf(row("bread"), row("milk")), queue.waiting.first().map { it.change })
    }

    @Test
    fun `the server's refusal of a change sent at once is the caller's, and nothing is kept`() {
        val refused = ApiException(404, "ITEM_NOT_FOUND", "Shopping list item not found.")

        val thrown = runCatching { runBlocking { queue.submit(row("bread")) { throw refused } } }.exceptionOrNull()

        assertEquals(refused, thrown)
        assertTrue(changes.all.isEmpty())
    }

    @Test
    fun `the changes waiting are sent in the order they were made, each leaving once it is sent`() = runBlocking {
        listOf("bread", "milk", "eggs").forEach { queue.submit(row(it), offline) }
        val sent = mutableListOf<QueuedChange>()

        val drained = queue.drain { sent += it }

        assertEquals(Drained.EMPTY, drained)
        assertEquals(listOf(row("bread"), row("milk"), row("eggs")), sent)
        assertTrue(changes.all.isEmpty())
    }

    @Test
    fun `sending stops where the network is gone, and the change waits on with those after it`() = runBlocking {
        listOf("bread", "milk").forEach { queue.submit(row(it), offline) }

        val drained = queue.drain { throw IOException("no network") }

        assertEquals(Drained.OFFLINE, drained)
        assertEquals(listOf(row("bread"), row("milk")), queue.waiting.first().map { it.change })
    }

    @Test
    fun `a change the server refuses is kept as refused with why, and the ones after it are sent`() = runBlocking {
        listOf("bread", "milk").forEach { queue.submit(row(it), offline) }
        val sent = mutableListOf<QueuedChange>()

        val drained = queue.drain { change ->
            if (change == row("bread")) throw ApiException(404, "ITEM_NOT_FOUND", "Shopping list item not found.")
            sent += change
        }

        assertEquals(Drained.EMPTY, drained)
        assertEquals(listOf(row("milk")), sent)
        val refused = queue.refused.first().single()
        assertEquals(row("bread"), refused.change)
        assertEquals("ITEM_NOT_FOUND", refused.code)
        assertEquals("Shopping list item not found.", refused.message)
        assertTrue(queue.waiting.first().isEmpty())
    }

    @Test
    fun `a change another write overlapped goes again, and after three times in a row waits for later`() = runBlocking {
        queue.submit(row("bread"), offline)
        var tries = 0

        val drained = queue.drain {
            tries++
            throw ApiException(409, "WRITE_CONFLICT", "The data changed while the request was running. Try again.")
        }

        assertEquals(Drained.LATER, drained)
        assertEquals(4, tries)
        assertEquals(listOf(row("bread")), queue.waiting.first().map { it.change })
    }

    @Test
    fun `a change goes when another write overlapped it once`() = runBlocking {
        queue.submit(row("bread"), offline)
        var tries = 0

        val drained = queue.drain { if (tries++ == 0) throw ApiException(409, "WRITE_CONFLICT", "Try again.") }

        assertEquals(Drained.EMPTY, drained)
        assertEquals(2, tries)
    }

    @Test
    fun `a server that asks to wait, is down, has no session for it or is not the API keeps the change for later`() = runBlocking {
        val refusals = listOf(
            ApiException(429, "RATE_LIMITED", "Too many requests."),
            ApiException(503, "SERVICE_UNAVAILABLE", "Down."),
            ApiException(401, "UNAUTHORIZED", "Signed out."),
            ApiException(403, "${ApiException.NOT_THE_API}403", "Forbidden"),
        )
        queue.submit(row("bread"), offline)

        refusals.forEach { refusal ->
            assertEquals(Drained.LATER, queue.drain { throw refusal })
            assertEquals(listOf(row("bread")), queue.waiting.first().map { it.change })
        }
    }

    @Test
    fun `a removal of what the server no longer has was made, unless the answer is not the API's`() = runBlocking {
        listOf("gone", "behind-a-proxy").forEach { queue.submit(removal(it), offline) }

        val drained = queue.drain { change ->
            if (change == removal("gone")) throw ApiException(404, "WEIGHT_ENTRY_NOT_FOUND", "Weight entry not found.")
            throw ApiException(404, "${ApiException.NOT_THE_API}404", "Not Found")
        }

        assertEquals(Drained.LATER, drained)
        assertEquals(listOf(removal("behind-a-proxy")), queue.waiting.first().map { it.change })
        assertTrue(queue.refused.first().isEmpty())
    }

    @Test
    fun `a change taken back while it waits leaves unsent, and one sent meanwhile is not taken back`() = runBlocking {
        listOf("bread", "milk").forEach { queue.submit(row(it), offline) }
        val (bread, milk) = queue.waiting.first()
        val sent = mutableListOf<QueuedChange>()

        assertTrue(queue.withdraw(bread.id))
        queue.drain { sent += it }

        assertEquals(listOf(row("milk")), sent)
        assertFalse(queue.withdraw(milk.id))
    }

    @Test
    fun `a change is taken back only once the sending under way has ended`() = runTest {
        queue.submit(row("bread"), offline)
        val bread = queue.waiting.first().single()
        val held = CompletableDeferred<Unit>()
        val sent = mutableListOf<QueuedChange>()

        val sending = async { queue.drain { change -> held.await(); sent += change } }
        testScheduler.runCurrent()
        val taking = async { queue.withdraw(bread.id) }
        testScheduler.runCurrent()
        assertFalse(taking.isCompleted)
        held.complete(Unit)

        assertEquals(Drained.EMPTY, sending.await())
        assertFalse(taking.await())
        assertEquals(listOf(row("bread")), sent)
    }

    @Test
    fun `a change this version cannot read is kept as refused, and the ones after it are sent`() = runBlocking {
        changes.add(PendingChangeRow(kind = "from-a-newer-version", change = """{"type":"from-a-newer-version"}""", madeAt = 1))
        queue.submit(row("bread"), offline)
        val sent = mutableListOf<QueuedChange>()

        assertEquals(Drained.EMPTY, queue.drain { sent += it })

        assertEquals(listOf(row("bread")), sent)
        val refused = queue.refused.first().single()
        assertEquals(null, refused.change)
        assertEquals(ChangeQueue.UNREADABLE, refused.code)
    }

    @Test
    fun `a change whose answer cannot be read is kept as refused, not sent again and again`() = runBlocking {
        queue.submit(row("bread"), offline)

        assertEquals(Drained.EMPTY, queue.drain { throw SerializationException("unreadable answer") })

        assertEquals(ChangeQueue.UNSENDABLE, queue.refused.first().single().code)
    }

    @Test
    fun `a refused change is forgotten once seen, and dropping forgets every change and the sending asked for`() = runBlocking {
        listOf("bread", "milk").forEach { queue.submit(row(it), offline) }
        queue.drain { change -> if (change == row("bread")) throw ApiException(404, "ITEM_NOT_FOUND", "Gone.") else throw IOException("no network") }

        queue.dismiss(queue.refused.first().single().id)
        assertTrue(queue.refused.first().isEmpty())
        queue.dropAll()

        assertTrue(changes.all.isEmpty())
        verify { work.cancel() }
    }

    @Test
    fun `two sendings at once send each change once, in its turn`() = runTest {
        listOf("bread", "milk").forEach { queue.submit(row(it), offline) }
        val sent = mutableListOf<QueuedChange>()
        val held = CompletableDeferred<Unit>()

        val first = async { queue.drain { change -> held.await(); sent += change } }
        val second = async { queue.drain { change -> sent += change } }
        testScheduler.runCurrent()
        held.complete(Unit)

        assertEquals(Drained.EMPTY, first.await())
        assertEquals(Drained.EMPTY, second.await())
        assertEquals(listOf(row("bread"), row("milk")), sent)
    }
}
