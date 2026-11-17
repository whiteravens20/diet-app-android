// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.about

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Every group of accounts and every choice of endpoints the instance may say of its own AI has its text in the app, and no other has. */
class OperatorAiTextsTest {
    private val schemas: JsonObject = Json.parseToJsonElement(
        File(checkNotNull(System.getProperty("contract.dir")) { "The build sets contract.dir" }, "openapi.json").readText(),
    ).jsonObject.getValue("components").jsonObject.getValue("schemas").jsonObject

    private fun enumOf(schema: String): Set<String> =
        schemas.getValue(schema).jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }.toSet()

    @Test
    fun `every group of accounts of the contract has a text, and only those`() {
        assertEquals(enumOf("OperatorAiAccess"), AI_ACCESS_TEXTS.keys)
    }

    @Test
    fun `every choice of endpoints of the contract has a text, and only those`() {
        assertEquals(enumOf("AiDataPolicy"), AI_POLICY_TEXTS.keys)
    }
}
