// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import net.whiteravens.dietapp.testing.knownDefect
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import java.io.File
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation

/**
 * Every call of the client held to the contract this build was checked against
 * (`contract/openapi.json`): the route is one the API has, the query and the body are ones it
 * takes, and the class an answer is read into can read every answer the API describes. A field
 * the API sends that the client leaves out is no fault; what is only a risk (a field the API may
 * leave out, a number the client reads as a whole one) is printed, not failed.
 */
@OptIn(ExperimentalSerializationApi::class)
@RunWith(Parameterized::class)
class ContractTest(private val call: Call) {

    /** One call of the client, read off its Retrofit interface. */
    class Call(
        val method: String,
        val route: String,
        val name: String,
        val queries: List<String>,
        val body: Type?,
        val answer: Type?,
    ) {
        override fun toString() = "$method /api/$route ($name)"
    }

    private class Problem(val level: String, val where: String, val what: String) {
        override fun toString() = "$level $where: $what"
    }

    @Test
    fun `fits the contract`() {
        val defect = KNOWN_DEFECTS["${call.method} ${call.route}"]
        if (defect == null) assertFits() else knownDefect(defect) { assertFits() }
    }

    private fun assertFits() {
        val problems = compare()
        problems.filter { it.level !in FAULTS }.forEach { println("$call: $it") }
        val faults = problems.filter { it.level in FAULTS }
        if (faults.isNotEmpty()) fail("$call does not fit the contract:\n  " + faults.joinToString("\n  "))
    }

    private fun compare(): List<Problem> {
        val found = mutableListOf<Problem>()
        val operation = PATHS["/api/${call.route}"]?.jsonObject?.get(call.method.lowercase())?.jsonObject
            ?: return listOf(Problem("NO ROUTE", "/api/${call.route}", "the contract has no ${call.method} here"))

        val parameters = operation["parameters"]?.jsonArray.orEmpty().map { it.jsonObject }
            .filter { it.string("in") == "query" }
        val named = parameters.mapNotNull { it.string("name") }.toSet()
        call.queries.filterNot { it in named }
            .forEach { found += Problem("REFUSED", "query $it", "the API names no such parameter") }
        parameters.filter { it["required"] == JsonPrimitive(true) }.mapNotNull { it.string("name") }
            .filterNot { it in call.queries }
            .forEach { found += Problem("REFUSED", "query $it", "the API requires it, the client does not send it") }

        val requestBody = operation["requestBody"]?.jsonObject
        val bodySchema = requestBody?.jsonSchemaOf()
        when {
            call.body != null && bodySchema != null -> checkBody(descriptorOf(call.body), bodySchema, found)
            call.body != null -> found += Problem("note", "body", "the client sends one, the route names none")
            bodySchema != null && requestBody["required"] == JsonPrimitive(true) ->
                found += Problem("REFUSED", "body", "the route requires one, the client sends none")
        }

        val responses = operation["responses"]?.jsonObject.orEmpty()
        val answerSchema = responses.keys.firstOrNull { it.startsWith("2") }
            ?.let { responses[it]?.jsonObject?.jsonSchemaOf() }
        if (call.answer != null) {
            val descriptor = descriptorOf(call.answer)
            if (answerSchema == null) {
                found += Problem("FAILS", descriptor.shortName, "the client reads a body, the route answers none")
            } else {
                checkAnswer(descriptor, answerSchema, descriptor.shortName, found, mutableSetOf())
            }
        }
        return found
    }

    /** Whether the client's class can read every answer [schema] allows. */
    private fun checkAnswer(
        descriptor: SerialDescriptor,
        schema: JsonElement,
        where: String,
        found: MutableList<Problem>,
        seen: MutableSet<Pair<String, Int>>,
    ) {
        val alternatives = variants(schema).filter { typeOf(it) != "null" }
        if (alternatives.isEmpty()) return
        when (val kind = descriptor.kind) {
            StructureKind.LIST -> for (alternative in alternatives) {
                if (typeOf(alternative) != "array") {
                    found += Problem("FAILS", where, "the client reads a list, the API answers ${describe(schema)}")
                } else {
                    val items = alternative["items"] ?: JsonObject(emptyMap())
                    checkAnswer(descriptor.getElementDescriptor(0), items, "$where[]", found, seen)
                }
            }
            is PrimitiveKind, SerialKind.ENUM -> {
                val whole = kind == PrimitiveKind.INT || kind == PrimitiveKind.LONG || kind == PrimitiveKind.SHORT || kind == PrimitiveKind.BYTE
                val takes = when {
                    kind == PrimitiveKind.STRING || kind == SerialKind.ENUM -> setOf("string")
                    kind == PrimitiveKind.BOOLEAN -> setOf("boolean")
                    whole || kind == PrimitiveKind.DOUBLE || kind == PrimitiveKind.FLOAT -> setOf("integer", "number")
                    else -> return
                }
                for (alternative in alternatives) {
                    val type = typeOf(alternative) ?: continue
                    if (type !in takes) {
                        found += Problem("FAILS", where, "the client reads $kind, the API answers ${describe(alternative)}")
                    } else if (whole && type == "number") {
                        found += Problem("may fail", where, "the client reads a whole number, the API answers any number")
                    }
                }
            }
            StructureKind.CLASS, StructureKind.OBJECT -> {
                val objects = alternatives.filter { typeOf(it) == "object" || "properties" in it }
                alternatives.filterNot { it in objects }.forEach {
                    found += Problem("FAILS", where, "the client reads ${descriptor.shortName}, the API may answer ${describe(it)}")
                }
                for (shape in objects) {
                    if (!seen.add(descriptor.serialName to System.identityHashCode(shape))) continue
                    val properties = shape["properties"]?.jsonObject.orEmpty()
                    val required = shape["required"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
                    for (index in 0 until descriptor.elementsCount) {
                        val name = descriptor.getElementName(index)
                        val element = descriptor.getElementDescriptor(index)
                        val must = !descriptor.isElementOptional(index) && !element.isNullable
                        val here = "$where.$name"
                        val property = properties[name]
                        if (property == null) {
                            found += if (must) Problem("FAILS", here, "the client requires it, the API does not send it")
                            else Problem("unused", here, "the client knows it, the API does not send it")
                            continue
                        }
                        if (must && name !in required) found += Problem("may fail", here, "the client requires it, the API may leave it out")
                        if (!element.isNullable && variants(property).any { typeOf(it) == "null" }) {
                            found += Problem("may fail", here, "the API may answer null, the client takes none")
                        }
                        checkAnswer(element, property, here, found, seen)
                    }
                    val known = (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet()
                    val dropped = properties.keys - known
                    if (dropped.isNotEmpty()) found += Problem("dropped", where, "the client leaves out ${dropped.sorted().joinToString()}")
                }
            }
            else -> Unit // maps and JSON elements are taken as they come
        }
    }

    /** Whether the API takes the body the client's class sends. */
    private fun checkBody(descriptor: SerialDescriptor, schema: JsonElement, found: MutableList<Problem>) {
        val shape = deref(schema)
        val properties = shape["properties"]?.jsonObject.orEmpty()
        val required = shape["required"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        val strict = shape["additionalProperties"] == JsonPrimitive(false)
        val sends = (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet()
        required.filterNot { it in sends }.forEach {
            found += Problem("REFUSED", descriptor.shortName, "the API requires \"$it\", the client has no such field")
        }
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            if (name in properties) continue
            val always = !descriptor.isElementOptional(index) && !descriptor.getElementDescriptor(index).isNullable
            found += when {
                strict && always -> Problem("REFUSED", descriptor.shortName, "the client always sends \"$name\", and the API takes no field of that name")
                strict -> Problem("refused when set", descriptor.shortName, "\"$name\" is no field of the API")
                else -> Problem("ignored", descriptor.shortName, "\"$name\" is no field of the API")
            }
        }
    }

    private companion object {
        val FAULTS = setOf("NO ROUTE", "REFUSED", "FAILS")

        /** Calls that do not fit the contract yet, each with why; the change that fixes one removes it here. */
        val KNOWN_DEFECTS = mapOf(
            "POST auth/login" to "an account with two-step sign-in is answered with a challenge, which the client's class cannot read",
        )

        val CONTRACT: JsonObject = Json.parseToJsonElement(
            File(checkNotNull(System.getProperty("contract.dir")) { "The build sets contract.dir" }, "openapi.json").readText(),
        ).jsonObject
        val PATHS: JsonObject = CONTRACT.getValue("paths").jsonObject
        val SCHEMAS: JsonObject = CONTRACT.getValue("components").jsonObject.getValue("schemas").jsonObject

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun calls(): List<Array<Any>> =
            listOf(AuthApiService::class.java, RefreshApiService::class.java, DietApiService::class.java)
                .flatMap(::callsOf)
                .map { arrayOf(it) }

        fun callsOf(service: Class<*>): List<Call> = service.declaredMethods
            .filterNot { it.isSynthetic }
            .mapNotNull { method ->
                val (verb, route) = method.annotations.firstNotNullOfOrNull { annotation ->
                    when (annotation) {
                        is GET -> "GET" to annotation.value
                        is POST -> "POST" to annotation.value
                        is PUT -> "PUT" to annotation.value
                        is PATCH -> "PATCH" to annotation.value
                        is DELETE -> "DELETE" to annotation.value
                        is HTTP -> annotation.method to annotation.path
                        else -> null
                    }
                } ?: return@mapNotNull null
                val types = method.genericParameterTypes
                var body: Type? = null
                val queries = mutableListOf<String>()
                method.parameterAnnotations.forEachIndexed { index, annotations ->
                    annotations.forEach { annotation ->
                        when (annotation) {
                            is Body -> body = types[index]
                            is Query -> queries += annotation.value
                        }
                    }
                }
                // A suspending function's answer is the type its continuation is resumed with, and a
                // call made synchronously answers the type of its `Call`.
                val continuation = types.lastOrNull() as? ParameterizedType
                val returned = method.genericReturnType as? ParameterizedType
                val answer = when {
                    continuation?.rawType == Continuation::class.java ->
                        continuation.actualTypeArguments.single().let { (it as? WildcardType)?.lowerBounds?.firstOrNull() ?: it }
                    returned?.rawType == retrofit2.Call::class.java -> returned.actualTypeArguments.single()
                    else -> method.genericReturnType
                }
                Call(verb, route, method.name, queries, body, answer.takeUnless { it == Unit::class.java || it == Void.TYPE })
            }
            .sortedBy { "${it.route} ${it.method}" }

        fun descriptorOf(type: Type): SerialDescriptor = serializer(type).descriptor

        val SerialDescriptor.shortName: String
            get() = if (kind == StructureKind.LIST) "List<${getElementDescriptor(0).shortName}>"
            else serialName.substringAfterLast('.').removeSuffix("?")

        fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull

        fun JsonObject.jsonSchemaOf(): JsonElement? =
            this["content"]?.jsonObject?.get("application/json")?.jsonObject?.get("schema")

        fun deref(schema: JsonElement): JsonObject {
            var current = schema.jsonObject
            while (true) {
                val ref = current.string("\$ref") ?: return current
                current = SCHEMAS.getValue(ref.substringAfterLast('/')).jsonObject
            }
        }

        /** A schema as the alternatives it may be, null among them. */
        fun variants(schema: JsonElement): List<JsonObject> {
            val shape = deref(schema)
            for (key in listOf("anyOf", "oneOf")) {
                shape[key]?.let { parts -> return parts.jsonArray.flatMap { variants(it) } }
            }
            val type = shape["type"]
            if (type is JsonArray) return type.map { JsonObject(shape + ("type" to it)) }
            return listOf(shape)
        }

        fun typeOf(shape: JsonObject): String? = shape.string("type")
            ?: if ("enum" in shape || "const" in shape) "string" else if ("properties" in shape) "object" else null

        fun describe(schema: JsonElement): String = variants(schema)
            .map { typeOf(it) ?: "any" }
            .distinct()
            .joinToString(" | ")
            .let { kinds -> (schema as? JsonObject)?.string("\$ref")?.substringAfterLast('/')?.let { "$it ($kinds)" } ?: kinds }
    }
}
