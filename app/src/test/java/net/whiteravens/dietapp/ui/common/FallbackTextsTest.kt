// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Every reason the API gives for the engine standing in where a model was asked has its text in the app, and no other one has. */
class FallbackTextsTest {
    @Test
    fun `every reason of the contract has a text, and only those`() {
        val contract = File(checkNotNull(System.getProperty("contract.dir")) { "The build sets contract.dir" }, "openapi.json").readText()
        val reasons = Json.parseToJsonElement(contract).jsonObject
            .getValue("components").jsonObject
            .getValue("schemas").jsonObject
            .getValue("AiFallbackReason").jsonObject
            .getValue("enum").jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()

        assertEquals(reasons, FALLBACK_TEXTS.keys)
    }
}
