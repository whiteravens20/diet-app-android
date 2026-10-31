// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.whiteravens.dietapp.data.local.CachedDiaryDay
import net.whiteravens.dietapp.data.local.CachedProfile
import net.whiteravens.dietapp.data.local.KeptReadsDao
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

    override suspend fun removeWaiting(id: Long): Int {
        val waiting = rows.value.any { it.id == id && it.refusedCode == null }
        if (waiting) remove(id)
        return if (waiting) 1 else 0
    }

    override suspend fun removeAll() = rows.update { emptyList() }
}

/** What is read for a start without a network, kept in memory as the cache's tables keep it. */
class MemoryKeptReads : KeptReadsDao() {
    private var rows = emptyList<CachedProfile>()

    /** The days kept, by their profile and date. */
    val days = mutableMapOf<Pair<String, String>, CachedDiaryDay>()

    override suspend fun profiles(): List<CachedProfile> = rows.sortedBy { it.position }

    override suspend fun addProfiles(profiles: List<CachedProfile>) {
        rows = rows + profiles
    }

    override suspend fun removeProfiles() {
        rows = emptyList()
    }

    override suspend fun diaryDay(profileId: String, date: String): CachedDiaryDay? = days[profileId to date]

    override suspend fun upsertDiaryDay(day: CachedDiaryDay) {
        days[day.profileId to day.date] = day
    }

    override suspend fun deleteDiaryDay(profileId: String, date: String) {
        days.remove(profileId to date)
    }

    override suspend fun deleteDiaryOf(profileId: String) {
        days.keys.removeAll { it.first == profileId }
    }

    override suspend fun deleteDiaryReadBefore(moment: Long) {
        days.values.removeAll { it.syncedAt < moment }
    }
}

/** A queue of changes kept in [changes], its sending in the background a mock that does nothing. */
fun changeQueue(changes: PendingChangeDao = MemoryChanges(), work: ChangesWork = mockk(relaxed = true)): ChangeQueue =
    ChangeQueue(changes, AppModule.json(), work)
