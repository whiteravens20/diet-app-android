// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.whiteravens.dietapp.data.local.PendingChangeDao
import net.whiteravens.dietapp.data.local.PendingChangeRow
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.ChangesWork
import net.whiteravens.dietapp.di.AppModule

/** The changes made on the phone, kept in memory as the cache's table keeps them: in the order they were made. */
class MemoryChanges : PendingChangeDao {
    private val rows = MutableStateFlow<List<PendingChangeRow>>(emptyList())
    private var nextId = 1L

    /** Every row kept, waiting and refused. */
    val all: List<PendingChangeRow> get() = rows.value

    override suspend fun add(row: PendingChangeRow): Long {
        val id = nextId++
        rows.update { it + row.copy(id = id) }
        return id
    }

    override fun waiting(): Flow<List<PendingChangeRow>> = rows.map { list -> list.filter { it.refusedCode == null } }

    override fun refused(): Flow<List<PendingChangeRow>> = rows.map { list -> list.filter { it.refusedCode != null } }

    override suspend fun next(): PendingChangeRow? = rows.value.firstOrNull { it.refusedCode == null }

    override suspend fun anyWaiting(): Boolean = rows.value.any { it.refusedCode == null }

    override suspend fun refuse(id: Long, code: String, message: String?) =
        rows.update { list -> list.map { if (it.id == id) it.copy(refusedCode = code, refusedMessage = message) else it } }

    override suspend fun remove(id: Long) = rows.update { list -> list.filterNot { it.id == id } }

    override suspend fun removeAll() = rows.update { emptyList() }
}

/** A queue of changes kept in [changes], its sending in the background a mock that does nothing. */
fun changeQueue(changes: PendingChangeDao = MemoryChanges(), work: ChangesWork = mockk(relaxed = true)): ChangeQueue =
    ChangeQueue(changes, AppModule.json(), work)
