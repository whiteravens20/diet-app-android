// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Every code the API refuses a request with has its text in the app, and no other code has one. */
class ErrorTextsTest {
    @Test
    fun `every code of the API's list has a text, and only those`() {
        val list = File(checkNotNull(System.getProperty("contract.dir")) { "The build sets contract.dir" }, "errors.md").readText()
        val codes = Regex("""^\| `([A-Z0-9_]+)` \|""", RegexOption.MULTILINE).findAll(list).map { it.groupValues[1] }.toSet()
        val announced = Regex("""one of the (\d+) codes below""").find(list)?.groupValues?.get(1)?.toInt()

        assertEquals(announced, codes.size)
        assertEquals(codes, ERROR_TEXTS.keys)
    }
}
