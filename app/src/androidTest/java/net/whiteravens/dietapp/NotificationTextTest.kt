// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.domain.NotificationAbout
import net.whiteravens.dietapp.ui.common.notificationText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * What a notification says, in English and in Polish, as the web application says it: a reminder of
 * the weight counting the days since the last one, in each plural form, one where nothing is logged
 * yet, the review of preferences and a kind this version does not know; and how the bell counts
 * what is not read yet.
 */
@RunWith(AndroidJUnit4::class)
class NotificationTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val notifications = listOf(
        NotificationAbout.WeightReminder(1),
        NotificationAbout.WeightReminder(3),
        NotificationAbout.WeightReminder(5),
        NotificationAbout.WeightReminder(22),
        NotificationAbout.WeightReminder(null),
        NotificationAbout.PreferencesReview,
        NotificationAbout.ProductAccepted("Chleb"),
        NotificationAbout.ProductRejected("Chleb", "Złe dane."),
        NotificationAbout.ProductWithdrawn("Chleb", "Złe dane."),
        NotificationAbout.ProductReplaced("Chleb", fromOpenFoodFacts = true),
        NotificationAbout.ProductReplaced("Chleb", fromOpenFoodFacts = false),
        NotificationAbout.Other("plan_ready"),
    )

    private val counts = listOf(1, 4, 5, 22)

    /** What each of [notifications], then the bell for each of [counts], says in each of [languages]. */
    private fun said(vararg languages: String): Map<String, List<String>> {
        val size = notifications.size + counts.size
        val said = languages.associateWith { arrayOfNulls<String>(size) }
        compose.setContent {
            languages.forEach { language ->
                val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
                val context = LocalContext.current.createConfigurationContext(configuration)
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalConfiguration provides configuration,
                    LocalResources provides context.resources,
                ) {
                    notifications.forEachIndexed { i, about -> said.getValue(language)[i] = notificationText(about) }
                    counts.forEachIndexed { i, count ->
                        said.getValue(language)[notifications.size + i] = pluralStringResource(R.plurals.notifications_unread, count, count)
                    }
                }
            }
        }
        compose.waitForIdle()
        // As the eye reads them: a number keeps its word with a no-break space, which reads as a plain one.
        return said.mapValues { (_, texts) -> texts.map { checkNotNull(it).replace('\u00A0', ' ') } }
    }

    @Test
    fun aNotificationReadsInTheLanguageOfTheApp() {
        val said = said("en", "pl")

        assertEquals(
            listOf(
                "Time to log your weight — it’s been 1 day.",
                "Time to log your weight — it’s been 3 days.",
                "Time to log your weight — it’s been 5 days.",
                "Time to log your weight — it’s been 22 days.",
                "Log your first weight to start your trend chart.",
                "Some ingredients or recipes you had marked are no longer in the catalogue. Check your avoided " +
                    "and favourite ingredients and your favourite sets.",
                "“Chleb” is now in the base of this instance: everyone here can find it.",
                "“Chleb” was not accepted: Złe dane.",
                "“Chleb” was withdrawn from the base and is back among your products: Złe dane.",
                "Open Food Facts now knows “Chleb” by its code, so your copy gave way to it. Diary entries keep their figures.",
                "The base of this instance now has “Chleb” under its code, so your copy gave way to it. Diary entries keep their figures.",
                "A notification this version of the application cannot show.",
                "New notification",
                "4 new notifications",
                "5 new notifications",
                "22 new notifications",
            ),
            said.getValue("en"),
        )
        assertEquals(
            listOf(
                "Czas się zważyć — od ostatniego wpisu minął 1 dzień.",
                "Czas się zważyć — od ostatniego wpisu minęły 3 dni.",
                "Czas się zważyć — od ostatniego wpisu minęło 5 dni.",
                "Czas się zważyć — od ostatniego wpisu minęły 22 dni.",
                "Dodaj pierwszy wpis wagi, aby uruchomić wykres trendu.",
                "Części składników lub przepisów, które były u Ciebie oznaczone, nie ma już w katalogu. Sprawdź " +
                    "unikane i ulubione składniki oraz zestawy ulubionych.",
                "„Chleb” jest już w bazie tej instancji: każdy tutaj może go znaleźć.",
                "Nie przyjęto „Chleb”: Złe dane.",
                "„Chleb” wycofano z bazy i wrócił do Twoich produktów: Złe dane.",
                "Open Food Facts zna już „Chleb” po kodzie, więc Twoja kopia ustąpiła. Wpisy w dzienniku zachowują swoje wartości.",
                "Baza tej instancji ma już „Chleb” pod tym kodem, więc Twoja kopia ustąpiła. Wpisy w dzienniku zachowują swoje wartości.",
                "Powiadomienie, którego ta wersja aplikacji nie umie pokazać.",
                "Nowe powiadomienie",
                "4 nowe powiadomienia",
                "5 nowych powiadomień",
                "22 nowe powiadomienia",
            ),
            said.getValue("pl"),
        )
    }
}
