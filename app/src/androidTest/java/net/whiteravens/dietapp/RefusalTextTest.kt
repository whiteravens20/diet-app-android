// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.whiteravens.dietapp.data.remote.ApiErrorIssueDto
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.ui.common.errorMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Locale

/**
 * What a person reads when a request fails, in English and in Polish: the API's own words for its
 * code (contract/errors.json), the wait a held-up request names, the fields a refused form names,
 * the meal no recipe was left for; a code this version has no words for in the words the API sent;
 * an answer that is not the API's, and no answer at all.
 */
@RunWith(AndroidJUnit4::class)
class RefusalTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun refusal(status: Int, code: String, wait: Int? = null) =
        ApiException(status, code, "The API's sentence for a log.", retryAfterSeconds = wait)

    private val failures = listOf(
        refusal(404, "PLAN_NOT_FOUND"),
        refusal(429, "RATE_LIMITED", wait = 30),
        refusal(429, "TOO_MANY_ATTEMPTS", wait = 1),
        refusal(429, "RATE_LIMITED", wait = 150),
        refusal(429, "RATE_LIMITED"),
        ApiException(
            400,
            "VALIDATION_FAILED",
            "Request payload failed validation.",
            issues = listOf(ApiErrorIssueDto("email", "Invalid email"), ApiErrorIssueDto("password", "Too short")),
        ),
        refusal(400, "VALIDATION_FAILED"),
        ApiException(
            400,
            "NO_ELIGIBLE_RECIPE",
            "Every recipe for lunch on the vegan diet (3) is ruled out.",
            details = buildJsonObject {
                put("slot", JsonPrimitive("lunch"))
                put("dietType", JsonPrimitive("vegan"))
                put("ruledOut", JsonPrimitive(3))
            },
        ),
        ApiException(409, "A_CODE_OF_A_NEWER_SERVER", "Something a newer server says."),
        ApiException(502, "${ApiException.NOT_THE_API}502", "Bad Gateway"),
        IOException("unreachable"),
    )

    /** What each of [failures] says, in each of [languages]. */
    private fun said(vararg languages: String): Map<String, List<String>> {
        val said = languages.associateWith { arrayOfNulls<String>(failures.size) }
        compose.setContent {
            languages.forEach { language ->
                val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
                val context = LocalContext.current.createConfigurationContext(configuration)
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalConfiguration provides configuration,
                    LocalResources provides context.resources,
                ) {
                    failures.forEachIndexed { i, failure -> said.getValue(language)[i] = errorMessage(failure) }
                }
            }
        }
        compose.waitForIdle()
        return said.mapValues { (_, texts) -> texts.map { checkNotNull(it) } }
    }

    @Test
    fun aFailureReadsInTheLanguageOfTheApp() {
        val said = said("en", "pl")

        assertEquals(
            listOf(
                "Meal plan not found.",
                "Too many attempts. Try again in 30 seconds.",
                "Too many attempts. Try again in 1 second.",
                "Too many attempts. Try again in 3 minutes.",
                "Too many requests. Try again in a moment.",
                "Some fields need attention: email, password.",
                "Some fields need attention.",
                "The profile's allergens, skipped ingredients and avoided recipes rule out all 3 recipes for " +
                    "\"Lunch\" on the \"Vegan\" diet, so the plan could not be made.",
                "Something a newer server says.",
                "The server answered 502, but not with the API's answer: something in front of it, such as a proxy, " +
                    "may have answered in its place.",
                "Could not reach the server. Check your connection.",
            ),
            said.getValue("en"),
        )
        assertEquals(
            listOf(
                "Nie znaleziono planu posiłków.",
                "Zbyt wiele prób. Spróbuj ponownie za 30 sekund.",
                "Zbyt wiele prób. Spróbuj ponownie za 1 sekundę.",
                "Zbyt wiele prób. Spróbuj ponownie za 3 minuty.",
                "Zbyt wiele żądań. Spróbuj ponownie za chwilę.",
                "Niektóre pola wymagają poprawy: email, password.",
                "Niektóre pola wymagają poprawy.",
                "Alergeny, pomijane składniki i przepisy oznaczone do unikania wykluczają w tym profilu wszystkie 3 " +
                    "przepisy na posiłek „Obiad” w diecie „Wegańska”, więc planu nie udało się ułożyć.",
                "Something a newer server says.",
                "Serwer odpowiedział kodem 502, ale nie odpowiedzią API: mogło odpowiedzieć coś, co stoi przed nim, " +
                    "na przykład serwer pośredniczący.",
                "Nie można połączyć się z serwerem. Sprawdź połączenie.",
            ),
            said.getValue("pl"),
        )
    }
}
