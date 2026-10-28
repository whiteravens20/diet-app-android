// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.local.PendingChangeDao
import net.whiteravens.dietapp.data.local.PendingChangeRow
import net.whiteravens.dietapp.data.remote.ApiException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A change of the queue that waits to be sent, made at [madeAt] (the phone's milliseconds). */
data class Waiting(val id: Long, val change: QueuedChange, val madeAt: Long)

/**
 * A change of the queue the server refused, kept until the user has seen why: the code of the
 * refusal and the server's own words. [change] is null for one this version cannot read.
 */
data class Refused(val id: Long, val change: QueuedChange?, val code: String, val message: String?)

/** What came of a change made: the server's answer, or the change kept to be sent in its turn. */
sealed interface Submitted<out T> {
    data class Sent<T>(val answer: T) : Submitted<T>

    /** Kept behind another change that waits, or, where [offline], because sending it met no network or lost the answer. */
    data class Kept(val offline: Boolean) : Submitted<Nothing>
}

/** How sending the changes waiting stopped: none is left, there is no network, or the server asks to try later. */
enum class Drained { EMPTY, OFFLINE, LATER }

/**
 * The changes made on the phone that the server does not have yet, in the order they were made,
 * kept in the cache's database. A change is sent at once where nothing waits before it and the
 * network is there; where either is not, it is kept and sent in its turn, by [ChangesWork] as soon
 * as there is a network and whenever the application comes to the front. They are sent one at a
 * time, the oldest first, and a change another one overlapped on the server goes again. A deletion
 * of something the server no longer has was made. A change the server refuses otherwise is kept as
 * refused, with why, until the user has seen it, and the ones after it go on.
 */
@Singleton
class ChangeQueue @Inject constructor(
    private val dao: PendingChangeDao,
    private val json: Json,
    private val work: ChangesWork,
) {
    /** The changes waiting, the oldest first. */
    val waiting: Flow<List<Waiting>> = dao.waiting().map { rows ->
        rows.mapNotNull { row -> read(row)?.let { Waiting(row.id, it, row.madeAt) } }
    }

    private val sending = Mutex()

    /** The changes the server refused, the oldest first. */
    val refused: Flow<List<Refused>> = dao.refused().map { rows ->
        rows.map { row -> Refused(row.id, read(row), checkNotNull(row.refusedCode), row.refusedMessage) }
    }

    /**
     * Makes [change]: sent at once with [now] where nothing waits before it, and kept to be sent in
     * its turn where something does, or where [now] meets no network. A refusal of the server is the
     * caller's, as any answer is.
     */
    suspend fun <T> submit(change: QueuedChange, now: suspend () -> T): Submitted<T> {
        var offline = false
        if (!dao.anyWaiting()) {
            try {
                return Submitted.Sent(now())
            } catch (e: IOException) {
                // No network, or the answer was lost on the way: kept, to be sent again as it is.
                offline = true
            }
        }
        keep(change)
        return Submitted.Kept(offline)
    }

    /**
     * Sends the changes waiting, the oldest first, one at a time, each with [send], until none is
     * left, the network is gone, or the server asks to try later. One sending goes on at a time:
     * a change sent twice at once could reach the server after the one made after it.
     */
    suspend fun drain(send: suspend (QueuedChange) -> Unit): Drained = sending.withLock { sendAll(send) }

    private suspend fun sendAll(send: suspend (QueuedChange) -> Unit): Drained {
        var overlapped = 0
        while (true) {
            val row = dao.next() ?: return Drained.EMPTY
            val change = read(row)
            if (change == null) {
                dao.refuse(row.id, UNREADABLE, null)
                continue
            }
            try {
                send(change)
                dao.remove(row.id)
                overlapped = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                return Drained.OFFLINE
            } catch (e: ApiException) {
                when {
                    // What the change takes away is gone already: it was made.
                    change is QueuedChange.DoneWhenGone && e.statusCode == 404 && !e.code.startsWith(ApiException.NOT_THE_API) -> {
                        dao.remove(row.id)
                        overlapped = 0
                    }
                    // Another change was written while this one was: nothing of this one was, so it goes again.
                    e.code in OVERLAPPED && overlapped < MOST_OVERLAPS -> overlapped++
                    e.code in OVERLAPPED || e.statusCode == 401 || e.statusCode == 429 || e.statusCode >= 500 ||
                        e.code.startsWith(ApiException.NOT_THE_API) -> return Drained.LATER
                    else -> dao.refuse(row.id, e.code, e.message)
                }
            } catch (e: Exception) {
                // An answer this version cannot read, or anything else that would come again with every try.
                dao.refuse(row.id, UNSENDABLE, e.message)
            }
        }
    }

    /** Whether a change waits to be sent. */
    suspend fun anyWaiting(): Boolean = dao.anyWaiting()

    /** Asks for the changes waiting to be sent, where any does: on coming to the front. */
    suspend fun sendSoon() {
        if (dao.anyWaiting()) work.sendSoon()
    }

    /** Forgets a refused change, once the user has seen why. */
    suspend fun dismiss(id: Long) = dao.remove(id)

    /**
     * Takes back the change [id] while it waits, as if it had not been made: false where it was sent
     * or refused meanwhile. A sending under way is let finish first, so that a change is never taken
     * back while it is on its way.
     */
    suspend fun withdraw(id: Long): Boolean = sending.withLock { dao.removeWaiting(id) > 0 }

    /** Drops every change, waiting or refused: when the user signs out without sending them. */
    suspend fun dropAll() {
        work.cancel()
        dao.removeAll()
    }

    private suspend fun keep(change: QueuedChange) {
        val encoded = json.encodeToJsonElement(QueuedChange.serializer(), change)
        val kind = encoded.jsonObject.getValue(json.configuration.classDiscriminator).jsonPrimitive.content
        dao.add(PendingChangeRow(kind = kind, change = encoded.toString(), madeAt = System.currentTimeMillis()))
        work.sendSoon()
    }

    /** The change a row keeps; null for one this version cannot read. */
    private fun read(row: PendingChangeRow): QueuedChange? = try {
        json.decodeFromString(QueuedChange.serializer(), row.change)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        /** The code a change is refused with when this version cannot read it. */
        const val UNREADABLE = "CHANGE_UNREADABLE"

        /** The code a change is refused with when sending it fails otherwise than for the network or the server. */
        const val UNSENDABLE = "CHANGE_UNSENDABLE"

        /** The codes of a change that met another one written at the same moment, and wrote nothing. */
        private val OVERLAPPED = setOf("PLAN_CHANGED", "WRITE_CONFLICT")

        /** How many times in a row a change is sent again when another change overlapped it. */
        private const val MOST_OVERLAPS = 3
    }
}
