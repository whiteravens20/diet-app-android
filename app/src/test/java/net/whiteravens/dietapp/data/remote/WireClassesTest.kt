// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a running backend answered each call of the client, read with the class the client reads it
 * into and with the application's own JSON settings. An answer the client cannot read is a screen
 * that shows an error instead of what was asked for.
 */
class WireClassesTest {
    private val json = AppModule.json()

    private inline fun <reified T> read(name: String): T = json.decodeFromString<T>(fixture(name))

    @Test
    fun `reads what registering, signing in and refreshing answer`() {
        read<AuthResponse>("auth-register")
        assertNotNull(read<LoginResponseDto>("auth-login").tokens)
        read<AuthResponse>("auth-refresh")
    }

    @Test
    fun `reads the account, its profiles and a profile's calorie target`() {
        read<SessionUserDto>("users-me")
        assertEquals(emptyList<ProfileDto>(), read<List<ProfileDto>>("profiles-none"))
        assertTrue(read<List<ProfileDto>>("profiles").isNotEmpty())
        read<ProfileDto>("profile")
        read<CalorieCalculationDto>("profile-calories")
    }

    @Test
    fun `reads shopping lists, and a row ticked and unticked`() {
        assertEquals(emptyList<ShoppingListDto>(), read<List<ShoppingListDto>>("shopping-lists-none"))
        assertTrue(read<List<ShoppingListDto>>("shopping-lists").isNotEmpty())
        read<ShoppingListDto>("shopping-list")
        read<ShoppingListDto>("shopping-list-generate")
        read<ShoppingListDto>("shopping-item-ticked")
        read<ShoppingListDto>("shopping-item-unticked")
    }

    @Test
    fun `reads a refusal by the code it names, with the fields that failed`() {
        assertEquals("INVALID_CREDENTIALS", read<ApiErrorDto>("auth-login-wrong-password").error)
        assertEquals("SESSION_JUST_REFRESHED", read<ApiErrorDto>("auth-refresh-used-token-at-once").error)
        assertEquals("UNAUTHORIZED", read<ApiErrorDto>("without-a-token").error)
        val refused = read<ApiErrorDto>("auth-register-weak-password")
        assertEquals("VALIDATION_FAILED", refused.error)
        assertTrue(refused.issues.orEmpty().isNotEmpty())
    }

    @Test
    fun `reads what the recipe library answers`() {
        assertTrue(read<RecipeSearchPageDto>("recipes").items.isNotEmpty())
        read<RecipeSearchPageDto>("recipes-search")
        read<RecipeDto>("recipe")
    }

    @Test
    fun `reads a plan, a plan just made, a swap offered and the plan after it was taken`() {
        assertTrue(read<MealPlanDto>("meal-plan").days.isNotEmpty())
        read<MealPlanDto>("meal-plan-generate")
        read<MealSwapPreviewDto>("meal-plan-swap-preview")
        read<RebalanceResultDto>("meal-plan-swap-apply")
    }

    @Test
    fun `reads the plans of a profile, a page of summaries`() {
        assertEquals(emptyList<MealPlanSummaryDto>(), read<MealPlanPageDto>("meal-plans-none").items)
        assertTrue(read<MealPlanPageDto>("meal-plans").items.isNotEmpty())
    }

    @Test
    fun `reads what signing in answers for an account with two-step sign-in, a challenge and no session`() {
        val answer = read<LoginResponseDto>("auth-login-two-step-account")
        assertNotNull(answer.secondFactor)
        assertNull(answer.tokens)
    }
}
