// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.di.AppModule
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
    fun `a refused form names each field it refused once, in the order the refusal names them`() {
        val refusal = ApiException.from(
            answered(
                400,
                """{"statusCode":400,"error":"VALIDATION_FAILED","message":"Request payload failed validation.",
                    "issues":[{"path":"email","message":"Invalid email"},{"path":"password","message":"Too short"},
                              {"path":"email","message":"Too long"},{"path":"","message":"Unrecognized key"}]}""",
            ),
            json,
        )

        assertEquals("VALIDATION_FAILED", refusal.code)
        assertEquals(listOf("email", "password"), refusal.refusedFields)
    }

    @Test
    fun `issues of a refusal that is not about fields name no field`() {
        val refusal = ApiException.from(
            answered(
                400,
                """{"statusCode":400,"error":"BAD_REQUEST","message":"No.","issues":[{"path":"email","message":"Invalid email"}]}""",
            ),
            json,
        )

        assertEquals(emptyList<String>(), refusal.refusedFields)
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
