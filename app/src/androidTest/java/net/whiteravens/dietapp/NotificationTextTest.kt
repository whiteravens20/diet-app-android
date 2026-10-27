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
        return said.mapValues { (_, texts) -> texts.map { checkNotNull(it) } }
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
