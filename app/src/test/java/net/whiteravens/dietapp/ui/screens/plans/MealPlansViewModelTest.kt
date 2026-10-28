// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.MealPlanPageDto
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.MealPlanPage
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanCalendarDay
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.testing.fixture
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MealPlansViewModelTest {
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    /** The cache as it is on the device: the list of plans [list], and nothing else. It keeps what was dropped. */
    private class Cache(private val list: CachedPlanList?) : CacheDao {
        val dropped = mutableListOf<String>()
        override suspend fun upsertPlanList(list: CachedPlanList) = Unit
        override suspend fun planList(profileId: String) = list?.takeIf { it.profileId == profileId }
        override suspend fun deletePlanList(profileId: String) {
            dropped += profileId
        }
        override suspend fun upsertMealPlan(plan: CachedMealPlan) = Unit
        override suspend fun mealPlan(id: String): CachedMealPlan? = null
        override suspend fun deleteMealPlan(id: String) = Unit
        override suspend fun upsertShoppingList(list: CachedShoppingList) = Unit
        override suspend fun shoppingList(id: String): CachedShoppingList? = null
        override suspend fun shoppingListsForPlan(planId: String) = emptyList<CachedShoppingList>()
        override suspend fun deleteShoppingList(id: String) = Unit
    }

    /**
     * A row the classes cannot read is what the cache holds after an update of the application
     * changed them, or after a backend changed what it answers. Without a network the cache is
     * all the screen has.
     */
    @Test
    fun `a cached list of plans the classes cannot read is left out, and the screen goes on without it`() {
        val profile = json.decodeFromString<List<ProfileDto>>(fixture("profiles")).first()
        val api = mockk<DietApiService>()
        coEvery { api.profiles() } returns listOf(profile)
        coEvery { api.mealPlans(any(), any(), any()) } throws IOException("no network")
        coEvery { api.planCalendar(any(), any(), any()) } throws IOException("no network")
        val cache = Cache(CachedPlanList(profile.id, """[{"id":"plan"}]""", 1L))

        var escaped: Throwable? = null
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> escaped = e }
        val model = try {
            MealPlansViewModel(UserRepository(api, json), MealPlanRepository(api, mockk(), cache, json))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }

        assertNull(escaped)
        assertFalse(model.state.value.loading)
        assertEquals(emptyList<MealPlanSummary>(), model.state.value.plans)
        assertEquals(listOf(profile.id), cache.dropped)
    }

    @Test
    fun `the plans of a profile deleted elsewhere leave the screen and the phone, the copy shown first with them`() {
        val profile = json.decodeFromString<List<ProfileDto>>(fixture("profiles")).first()
        val api = mockk<DietApiService>()
        coEvery { api.profiles() } returns listOf(profile)
        coEvery { api.mealPlans(any(), any(), any()) } throws HttpException(
            Response.error<Any>(404, fixture("meal-plans-of-an-unknown-profile").toResponseBody("application/json".toMediaType())),
        )
        coEvery { api.planCalendar(any(), any(), any()) } throws IOException("not asked here")
        val kept = json.encodeToString(json.decodeFromString<MealPlanPageDto>(fixture("meal-plans")))
        val cache = Cache(CachedPlanList(profile.id, kept, 1L))

        val model = MealPlansViewModel(UserRepository(api, json), MealPlanRepository(api, mockk(), cache, json))

        assertEquals(emptyList<MealPlanSummary>(), model.state.value.plans)
        assertEquals("PROFILE_NOT_FOUND", (model.state.value.error as ApiException).code)
        assertEquals(listOf(profile.id), cache.dropped)
    }

    private val ada = Profile("ada", "Ada", "balanced", 3, 70.0, null)
    private val bo = Profile("bo", "Bo", "vegan", 4, 70.0, null)
    private fun summary(id: String) = MealPlanSummary(id, "2026-10-12", 7, "balanced", Nutrition(2000.0, 100.0, 70.0, 220.0), 0.4, false)
    private fun page(vararg ids: String, totalPages: Int = 1, syncedAt: Long? = null) = MealPlanPage(ids.map(::summary), 1, totalPages, syncedAt)
    private fun calendarDay(date: LocalDate, planId: String = "p1") = PlanCalendarDay(date, planId, 1, 1980.0, 2000.0, null, 0, 3, false)

    private val users = mockk<UserRepository>().also { coEvery { it.profiles() } returns listOf(ada, bo) }
    private val asked = mutableListOf<Pair<LocalDate, LocalDate>>()
    private val plans = mockk<MealPlanRepository>().also {
        coEvery { it.cachedPlans(any()) } returns null
        coEvery { it.plans(any(), any()) } returns page("p1")
        coEvery { it.calendar(any(), any(), any()) } answers {
            asked += secondArg<LocalDate>() to thirdArg<LocalDate>()
            listOf(calendarDay(secondArg<LocalDate>().plusDays(7)))
        }
    }

    @Test
    fun `a month is drawn in whole weeks from Monday, with the days of the months beside it that share them`() {
        val october = MealPlansViewModel.weeksOf(YearMonth.of(2026, 10))
        assertEquals(LocalDate.of(2026, 9, 28), october.first().first())
        assertEquals(LocalDate.of(2026, 11, 1), october.last().last())
        assertEquals(5, october.size)

        // A month that begins on a Monday and ends on a Sunday is its own four weeks.
        val february = MealPlansViewModel.weeksOf(YearMonth.of(2027, 2))
        assertEquals(LocalDate.of(2027, 2, 1), february.first().first())
        assertEquals(LocalDate.of(2027, 2, 28), february.last().last())
        assertEquals(4, february.size)

        // Six weeks at the most: the 42 days the API answers for at once.
        val august = MealPlansViewModel.weeksOf(YearMonth.of(2026, 8))
        assertEquals(6, august.size)
        assertEquals(LocalDate.of(2026, 7, 27), august.first().first())
        assertEquals(LocalDate.of(2026, 9, 6), august.last().last())

        for (weeks in listOf(october, february, august)) {
            assertTrue(weeks.all { week -> week.size == 7 && week.first().dayOfWeek == DayOfWeek.MONDAY })
            assertEquals(weeks.flatten(), weeks.flatten().sorted().distinct())
        }
    }

    @Test
    fun `the calendar asks for the weeks of the month shown, and another month when it is leafed to`() {
        val model = MealPlansViewModel(users, plans)

        val month = YearMonth.now()
        val weeks = MealPlansViewModel.weeksOf(month)
        assertEquals(weeks.first().first() to weeks.last().last(), asked.single())
        val calendar = model.state.value.calendar
        assertEquals(month, calendar.month)
        assertFalse(calendar.loading)
        assertEquals(setOf(weeks.first().first().plusDays(7)), calendar.days.keys)

        model.leaf(1)

        val next = MealPlansViewModel.weeksOf(month.plusMonths(1))
        assertEquals(next.first().first() to next.last().last(), asked.last())
        assertEquals(month.plusMonths(1), model.state.value.calendar.month)
        assertEquals(setOf(next.first().first().plusDays(7)), model.state.value.calendar.days.keys)
    }

    @Test
    fun `a month that failed says so and is asked for again, and another profile's month holds none of the first one's days`() {
        coEvery { plans.calendar("ada", any(), any()) } throws IOException("no network") andThen listOf(calendarDay(LocalDate.now()))
        coEvery { plans.calendar("bo", any(), any()) } returns emptyList()
        val model = MealPlansViewModel(users, plans)

        assertTrue(model.state.value.calendar.error is IOException)

        model.retryCalendar()

        assertNull(model.state.value.calendar.error)
        assertEquals(setOf(LocalDate.now()), model.state.value.calendar.days.keys)

        model.selectProfile("bo")

        assertEquals(emptyMap<LocalDate, PlanCalendarDay>(), model.state.value.calendar.days)
        coVerify { plans.calendar("bo", any(), any()) }
    }

    @Test
    fun `the next page is shown below the first without a plan shown already, and no more is offered after the last`() {
        coEvery { plans.plans("ada", 1) } returns page("p1", "p2", totalPages = 2)
        coEvery { plans.plans("ada", 2) } returns MealPlanPage(listOf(summary("p2"), summary("p3")), 2, 2, null)
        val model = MealPlansViewModel(users, plans)
        assertTrue(model.state.value.hasMore)

        model.showMore()

        assertEquals(listOf("p1", "p2", "p3"), model.state.value.plans.map { it.id })
        assertFalse(model.state.value.hasMore)
    }

    @Test
    fun `coming back reads the list and the month again, keeping what is shown when the network is gone`() {
        val model = MealPlansViewModel(users, plans)
        coEvery { plans.plans("ada", 1) } returns page("p1", "new")

        model.shownAgain(cameToTheFront = false)

        assertEquals(listOf("p1", "new"), model.state.value.plans.map { it.id })
        assertEquals(2, asked.size)

        coEvery { plans.plans("ada", 1) } throws IOException("no network")
        model.shownAgain(cameToTheFront = false)

        assertEquals(listOf("p1", "new"), model.state.value.plans.map { it.id })
        assertTrue(model.state.value.offline)
    }

    @Test
    fun `a pull reads the list and the month again at once, and ends with the list's answer`() {
        val model = MealPlansViewModel(users, plans)
        coEvery { plans.plans("ada", 1) } returns page("p1", "pulled")

        model.pull()

        assertEquals(listOf("p1", "pulled"), model.state.value.plans.map { it.id })
        assertEquals(2, asked.size)
        assertFalse(model.state.value.pulled)
    }

    @Test
    fun `coming back after the profile shown was deleted shows the first profile left`() {
        val model = MealPlansViewModel(users, plans)
        model.selectProfile("bo")
        coEvery { users.profiles() } returns listOf(ada)

        model.shownAgain(cameToTheFront = false)

        assertEquals("ada", model.state.value.selectedProfileId)
        assertEquals(listOf(ada), model.state.value.profiles)
    }
}
