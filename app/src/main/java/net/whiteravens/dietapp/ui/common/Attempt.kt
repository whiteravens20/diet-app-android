// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for what a coroutine of a screen asks for: a failure is the result, but the
 * coroutine's cancellation is not one — it goes on up, so that a screen that is gone stops what
 * it started instead of showing its cancellation as an error.
 */
suspend inline fun <T> attempt(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
