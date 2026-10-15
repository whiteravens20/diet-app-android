// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Every code the API refuses a request with has its text in the app, and no other code has one. */
class ErrorTextsTest {
    @Test
    fun `every code of the API's list has a text, and only those`() {
        val list = File(checkNotNull(System.getProperty("contract.dir")) { "The build sets contract.dir" }, "errors.json").readText()
        val codes = Json.parseToJsonElement(list).jsonObject.keys

        assertEquals(codes, ERROR_TEXTS.keys)
    }
}
