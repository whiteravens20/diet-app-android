// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.res.Configuration
import androidx.compose.runtime.Composable
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
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.ui.common.Bound
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.refusalText
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Locale

/**
 * What a person reads when a request fails, in English and in Polish: the API's own words for its
 * code (contract/errors.json), the wait a held-up request names, a refused form, whose fields say
 * the rest, the meal no recipe was left for; a code this version has no words for in the words the
 * API sent; an answer that is not the API's, and no answer at all. And what a refused field says:
 * the kind of refusal and its bound, in the field's unit.
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
            issues = listOf(
                ApiErrorIssueDto("email", "Invalid email address", "invalid_format"),
                ApiErrorIssueDto("password", "Password must be at least 12 characters.", "too_small", minimum = 12.0, inclusive = true),
            ),
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

    /** What each of [texts] says, in each of [languages]. */
    private fun said(texts: List<@Composable () -> String>, vararg languages: String): Map<String, List<String>> {
        val said = languages.associateWith { arrayOfNulls<String>(texts.size) }
        compose.setContent {
            languages.forEach { language ->
                val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
                val context = LocalContext.current.createConfigurationContext(configuration)
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalConfiguration provides configuration,
                    LocalResources provides context.resources,
                ) {
                    texts.forEachIndexed { i, text -> said.getValue(language)[i] = text() }
                }
            }
        }
        compose.waitForIdle()
        return said.mapValues { (_, texts) -> texts.map { checkNotNull(it) } }
    }

    @Test
    fun aFailureReadsInTheLanguageOfTheApp() {
        val said = said(failures.map { failure -> @Composable { errorMessage(failure) } }, "en", "pl")

        assertEquals(
            listOf(
                "Meal plan not found.",
                "Too many attempts. Try again in 30 seconds.",
                "Too many attempts. Try again in 1 second.",
                "Too many attempts. Try again in 3 minutes.",
                "Too many requests. Try again in a moment.",
                "Some fields need attention.",
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
                "Niektóre pola wymagają poprawy.",
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

    @Test
    fun aRefusedFieldSaysTheKindOfRefusalAndItsBoundInTheLanguageOfTheApp() {
        val pounds = Bound.Number(decimals = 1, unit = "lb", inUnit = WeightUnit.LB::shown)
        val said = said(
            listOf<@Composable () -> String>(
                { refusalText(FieldRefusal.TooSmall(13.0, inclusive = true)) },
                { refusalText(FieldRefusal.TooBig(120.0, inclusive = true)) },
                { refusalText(FieldRefusal.TooSmall(0.0, inclusive = false)) },
                { refusalText(FieldRefusal.TooBig(10.0, inclusive = false)) },
                { refusalText(FieldRefusal.TooSmall(0.25, inclusive = true), Bound.Number(decimals = 2)) },
                // 400 kg is 881.85 lb and 30 kg 66.14 lb: the most is said rounded down, the least up, so that neither is refused.
                { refusalText(FieldRefusal.TooBig(400.0, inclusive = true), pounds) },
                { refusalText(FieldRefusal.TooSmall(30.0, inclusive = true), pounds) },
                { refusalText(FieldRefusal.TooSmall(1.0, inclusive = true), Bound.Characters) },
                { refusalText(FieldRefusal.TooBig(80.0, inclusive = true), Bound.Characters) },
                { refusalText(FieldRefusal.TooBig(2.0, inclusive = true), Bound.Characters) },
                { refusalText(FieldRefusal.Other) },
                { refusalText(FieldRefusal.Other, other = "The field's own words.") },
            ),
            "en",
            "pl",
        )

        assertEquals(
            listOf(
                "At least 13.",
                "At most 120.",
                "More than 0.",
                "Less than 10.",
                "At least 0.25.",
                "At most 881.8 lb.",
                "At least 66.2 lb.",
                "This cannot be empty.",
                "At most 80 characters.",
                "At most 2 characters.",
                "The server does not take this.",
                "The field's own words.",
            ),
            said.getValue("en"),
        )
        assertEquals(
            listOf(
                "Co najmniej 13.",
                "Najwyżej 120.",
                "Więcej niż 0.",
                "Mniej niż 10.",
                "Co najmniej 0,25.",
                "Najwyżej 881,8 lb.",
                "Co najmniej 66,2 lb.",
                "To pole nie może być puste.",
                "Najwyżej 80 znaków.",
                "Najwyżej 2 znaki.",
                "Serwer tego nie przyjmuje.",
                "The field's own words.",
            ),
            said.getValue("pl"),
        )
    }
}
