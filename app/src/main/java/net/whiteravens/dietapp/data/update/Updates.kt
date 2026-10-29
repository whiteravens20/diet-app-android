// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks the build's channel for its newest version: on every start and on coming back to the front,
 * at most every [EVERY], and whenever the user asks. The request is a plain one for a file, with
 * nothing of the account in it, on a client of its own that carries none of the API's headers.
 */
@Singleton
class Updates internal constructor(
    private val channel: () -> UpdateChannel?,
    private val store: UpdateStore,
    private val json: Json,
    private val client: OkHttpClient,
    private val clock: Clock,
    private val versionCode: Int,
) {
    @Inject
    constructor(@ApplicationContext context: Context, store: UpdateStore, json: Json) : this(
        channel = { UpdateChannel.of(context) },
        store = store,
        json = json,
        client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
        clock = Clock.systemUTC(),
        versionCode = BuildConfig.VERSION_CODE,
    )

    /** Whether this build has anywhere to ask. */
    fun hasChannel(): Boolean = channel() != null

    /** A newer build than this one, unless asking is off or the user put that build off. */
    val available: Flow<VersionFile?> = combine(store.on, store.latest, store.putOff) { on, latest, putOff ->
        latest?.takeIf { on && it.versionCode > versionCode && it.versionCode != putOff }
    }

    /** The newest build the channel named, newer than this one or not, whether asking is on or not. */
    val latest: Flow<VersionFile?> = store.latest

    val asked = store.asked

    val on = store.on

    /** Asks, unless asking is off or the last ask was less than [EVERY] ago. */
    suspend fun askIfDue() {
        if (!store.isOn()) return
        val last = store.askedAt()
        if (last != null && Duration.between(last, clock.instant()) < EVERY) return
        ask()
    }

    /** Asks now, and says whether an answer came. */
    suspend fun ask(): Boolean {
        val at = channel() ?: return false
        val answer = withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(at.versionFile).build()).execute().use { response ->
                    if (!response.isSuccessful) null else json.decodeFromString<VersionFile>(response.body.string())
                }
            }.getOrNull()
        }
        store.keep(clock.instant(), answer)
        return answer != null
    }

    suspend fun turn(on: Boolean) = store.turn(on)

    /** Hides the card of [versionCode] until a newer one is out. */
    suspend fun putOff(versionCode: Int) = store.putOff(versionCode)

    companion object {
        val EVERY: Duration = Duration.ofHours(6)
    }
}
