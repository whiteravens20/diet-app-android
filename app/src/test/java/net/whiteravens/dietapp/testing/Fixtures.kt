// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

/** What a running backend answered, as `src/test/resources/fixtures/<name>.json` holds it. */
fun fixture(name: String): String =
    checkNotNull(Fixtures::class.java.getResource("/fixtures/$name.json")) { "There is no fixture named $name" }.readText()

private object Fixtures
