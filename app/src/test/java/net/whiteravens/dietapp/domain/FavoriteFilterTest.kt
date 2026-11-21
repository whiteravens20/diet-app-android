// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The filters of the favourites' window: how many are at work, and what clearing them leaves. */
class FavoriteFilterTest {
    @Test
    fun theMealTheMarkAndTheTagAreCountedTheWordsAndTheOrderNot() {
        assertEquals(0, FavoriteFilter(search = "soup", sort = "title").atWork)
        assertEquals(1, FavoriteFilter(mealType = "lunch").atWork)
        assertEquals(3, FavoriteFilter(search = "soup", mealType = "lunch", sentiment = "often", tag = "quick", sort = "calories").atWork)
    }

    @Test
    fun clearingTakesTheFiltersOfTheWindowAwayAndKeepsTheWordsAndTheOrder() {
        val cleared = FavoriteFilter(search = "soup", mealType = "lunch", sentiment = "avoid", tag = "quick", sort = "title").cleared()
        assertEquals(FavoriteFilter(search = "soup", sort = "title"), cleared)
        assertEquals(0, cleared.atWork)
    }
}
