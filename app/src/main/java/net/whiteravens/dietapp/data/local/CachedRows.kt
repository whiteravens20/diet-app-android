// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.local

import kotlinx.serialization.SerializationException

/**
 * Reads a cached row with the classes of this version of the app. A row they cannot read, kept by
 * an older version or written from an answer of another shape, is dropped with [drop] and read as
 * null: the screen goes on with what can be read, and the next refresh fetches the rest again.
 */
suspend inline fun <T> readOrDrop(read: () -> T, drop: suspend () -> Unit): T? =
    try {
        read()
    } catch (e: SerializationException) {
        drop()
        null
    }
