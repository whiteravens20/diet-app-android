// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import net.whiteravens.dietapp.data.remote.ApiErrorDto
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.di.AppModule

/** What a running backend answered, as `src/test/resources/fixtures/<name>.json` holds it. */
fun fixture(name: String): String =
    checkNotNull(Fixtures::class.java.getResource("/fixtures/$name.json")) { "There is no fixture named $name" }.readText()

/** The refusal a running backend answered, as the fixture [name] holds it, in the form a repository throws it. */
fun refusal(name: String): ApiException {
    val answer = AppModule.json().decodeFromString<ApiErrorDto>(fixture(name))
    return ApiException(answer.statusCode, answer.error, answer.message, answer.issues.orEmpty(), details = answer.details)
}

private object Fixtures
