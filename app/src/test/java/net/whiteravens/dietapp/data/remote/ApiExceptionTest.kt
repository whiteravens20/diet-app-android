// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.FieldRefusal.Other
import net.whiteravens.dietapp.domain.FieldRefusal.TooBig
import net.whiteravens.dietapp.domain.FieldRefusal.TooSmall
import net.whiteravens.dietapp.testing.fixture
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** A refusal as the API answers it, read whole: what a screen says about it is made from this. */
class ApiExceptionTest {
    private val json = AppModule.json()

    private fun answered(status: Int, body: String, type: String = "application/json") =
        HttpException(Response.error<Any>(status, body.toResponseBody(type.toMediaType())))

    @Test
    fun `a refused form names each field it refused once, in the order the refusal names them, with the kind of refusal and its bound`() {
        val profile = ApiException.from(answered(400, fixture("profile-refused")), json)

        assertEquals("VALIDATION_FAILED", profile.code)
        assertEquals(
            mapOf(
                "name" to TooSmall(1.0, inclusive = true),
                "age" to TooBig(120.0, inclusive = true),
                "targetWeightKg" to TooBig(400.0, inclusive = true),
                "preferences.maxConsecutiveDaysSameMeal" to TooBig(7.0, inclusive = true),
                "preferences.maxTimesPerWeekSameMeal" to TooSmall(1.0, inclusive = true),
                "preferences.kindLimits.0.perDay" to TooBig(5.0, inclusive = true),
                "preferences.kindLimits.0.perWeek" to TooBig(35.0, inclusive = true),
                "preferences.inventoryBiasResetEvery" to TooBig(20.0, inclusive = true),
            ),
            profile.fieldRefusals,
        )
        assertEquals(profile.fieldRefusals.keys.toList(), profile.refusedFields)
    }

    @Test
    fun `a field refused for several things is named by the first, and one refused for no kind the app words, or for nothing in a field, is told apart`() {
        // A password too short, without a capital letter and without a digit.
        val password = ApiException.from(answered(400, fixture("auth-register-weak-password")), json)
        // A number that is not whole, beside a key the route does not know, which is no field.
        val preferences = ApiException.from(answered(400, fixture("preferences-not-whole-and-unknown")), json)
        // Three shares that do not add up to a hundred: the split as a whole is refused.
        val split = ApiException.from(answered(400, fixture("calorie-preview-split-sum-refused")), json)

        assertEquals(mapOf("password" to TooSmall(12.0, inclusive = true)), password.fieldRefusals)
        assertEquals(mapOf("inventoryBiasResetEvery" to Other), preferences.fieldRefusals)
        assertEquals(mapOf("customMacroSplit" to Other), split.fieldRefusals)
    }

    @Test
    fun `a refusal of anything but a form's fields names no field`() {
        val refusal = ApiException.from(answered(400, fixture("pantry-unit-not-convertible")), json)

        assertEquals("UNIT_NOT_CONVERTIBLE", refusal.code)
        assertEquals(emptyMap<String, Any>(), refusal.fieldRefusals)
    }

    @Test
    fun `what a refusal has more to say is kept`() {
        val refusal = ApiException.from(
            answered(
                400,
                """{"statusCode":400,"error":"NO_ELIGIBLE_RECIPE","message":"There is no recipe for lunch on the vegan diet.",
                    "details":{"slot":"lunch","dietType":"vegan","ruledOut":0}}""",
            ),
            json,
        )

        assertEquals("lunch", refusal.details?.get("slot")?.jsonPrimitive?.content)
        assertEquals("0", refusal.details?.get("ruledOut")?.jsonPrimitive?.content)
    }

    @Test
    fun `an answer that is not the API's own is named by its status and has nothing more to say`() {
        val refusal = ApiException.from(
            answered(502, "<html><body><h1>502 Bad Gateway</h1></body></html>", "text/html"),
            json,
        )

        assertEquals(502, refusal.statusCode)
        assertEquals("${ApiException.NOT_THE_API}502", refusal.code)
        assertNull(refusal.details)
        assertEquals(emptyList<String>(), refusal.refusedFields)
    }
}
