// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.remote.NotificationDto
import net.whiteravens.dietapp.data.remote.SessionUserDto
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.WeightRepository
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.Macros
import net.whiteravens.dietapp.domain.OverviewDay
import net.whiteravens.dietapp.domain.PlanOverview
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Share
import net.whiteravens.dietapp.domain.WeightEntry
import net.whiteravens.dietapp.domain.WeightPoint
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.testing.fixture
import net.whiteravens.dietapp.testing.refusal
import net.whiteravens.dietapp.ui.screens.dashboard.DashboardViewModel.WeightLog
import net.whiteravens.dietapp.ui.screens.dashboard.DashboardViewModel.WeightProblem
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    /** A profile with no plan, unless a test says otherwise. */
    private val plans = mockk<MealPlanRepository>().also { coEvery { it.overview(any()) } returns null }

    /** No profile has logged a weight, unless a test says otherwise. */
    private val weights = mockk<WeightRepository>().also {
        coEvery { it.entries(any(), any()) } returns emptyList()
        coEvery { it.days(any(), any(), any()) } returns emptyList()
    }

    /** Nothing is unread, unless a test says otherwise. */
    private val notifications = mockk<NotificationRepository>().also { coEvery { it.unread() } returns emptyList() }

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `a calorie target that cannot be had leaves its profile shown with the reason, and the others whole`() {
        val users = mockk<UserRepository>()
        val refusal = ApiException(429, "RATE_LIMITED", "Too many requests.", retryAfterSeconds = 30)
        val target = json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
        val (held, whole) = listOf(Profile("a", "Ala", "balanced", 4, 70.0, null), Profile("b", "Olek", "vegan", 3, 70.0, null))
        coEvery { users.profiles() } returns listOf(held, whole)
        coEvery { users.calorieTarget(held.id) } throws refusal
        coEvery { users.calorieTarget(whole.id) } returns target

        val state = DashboardViewModel(users, plans, weights, notifications).state.value

        assertNull(state.error)
        assertEquals(listOf(held, whole), state.profiles.map { it.profile })
        assertNull(state.profiles[0].target)
        assertSame(refusal, state.profiles[0].targetError)
        assertEquals(target, state.profiles[1].target)
        assertNull(state.profiles[1].targetError)
    }

    @Test
    fun `the dashboard read again on coming back shows a profile made in the meantime, and a failed reading leaves what was shown`() {
        val users = mockk<UserRepository>()
        val target = json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
        val made = Profile("a", "Ala", "balanced", 4, 70.0, null)
        coEvery { users.profiles() } returns emptyList()
        val model = DashboardViewModel(users, plans, weights, notifications)
        assertEquals(emptyList<DashboardViewModel.ProfileStats>(), model.state.value.profiles)

        coEvery { users.profiles() } returns listOf(made)
        coEvery { users.calorieTarget(made.id) } returns target
        model.shownAgain(cameToTheFront = false)
        assertEquals(listOf(made), model.state.value.profiles.map { it.profile })

        val readAt = model.state.value.readAt
        assertNotNull(readAt)
        assertFalse(model.state.value.offline)
        coEvery { users.profiles() } throws IOException("unreachable")
        model.shownAgain(cameToTheFront = false)
        assertEquals(listOf(made), model.state.value.profiles.map { it.profile })
        assertNull(model.state.value.error)
        assertTrue(model.state.value.offline)
        assertEquals(readAt, model.state.value.readAt)
    }

    @Test
    fun `coming back to the front right after the last read reads nothing, while a pull reads at once and ends with the answer`() {
        val users = mockk<UserRepository>()
        coEvery { users.profiles() } returns emptyList()
        val model = DashboardViewModel(users, plans, weights, notifications)

        model.shownAgain(cameToTheFront = true)
        coVerify(exactly = 1) { users.profiles() }

        model.pull()
        coVerify(exactly = 2) { users.profiles() }
        assertFalse(model.state.value.pulled)
    }

    @Test
    fun `the plan of the moment of each profile is read beside its target, and one that cannot be read is said on its own card`() {
        val users = mockk<UserRepository>()
        val target = json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
        val (planned, unread) = listOf(Profile("a", "Ala", "balanced", 4, 70.0, null), Profile("b", "Olek", "vegan", 3, 70.0, null))
        val overview = PlanOverview(
            planId = "plan", startDate = "2026-10-13", durationDays = 7, dietType = "balanced", status = "active",
            energy = 1980.0, energyTarget = 2000.0, macros = Macros(100.0, 70.0, 220.0), macrosTarget = Macros(110.0, 65.0, 230.0),
            eaten = Share(4, 8), onDiet = Share(26, 28), favourites = Share(3, 28), ingredientReuse = 0.4,
            shoppingLists = 1, shoppingEntries = 40, shoppingTicked = 10,
            days = listOf(OverviewDay("2026-10-13", 1980.0, 2000.0, skipped = false)),
        )
        val refusal = ApiException(429, "RATE_LIMITED", "Too many requests.", retryAfterSeconds = 30)
        coEvery { users.profiles() } returns listOf(planned, unread)
        coEvery { users.calorieTarget(any()) } returns target
        coEvery { plans.overview(planned.id) } returns overview
        coEvery { plans.overview(unread.id) } throws refusal

        val state = DashboardViewModel(users, plans, weights, notifications).state.value

        assertEquals(overview, state.profiles[0].overview)
        assertNull(state.profiles[0].overviewError)
        assertNull(state.profiles[1].overview)
        assertSame(refusal, state.profiles[1].overviewError)
        assertEquals(target, state.profiles[1].target)
    }

    private val ala = Profile("a", "Ala", "balanced", 4, 70.0, null)

    private fun users() = mockk<UserRepository>().also {
        coEvery { it.profiles() } returns listOf(ala)
        coEvery { it.calorieTarget(any()) } returns json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
    }

    /** The account as the API answers it, with the unit it chose for weights. */
    private fun account(unit: String) = json.decodeFromString<SessionUserDto>(fixture("users-me")).toDomain().copy(weightUnit = unit)

    private val logged = WeightEntry("logged", 79.85, Instant.parse("2026-10-16T21:51:18.696Z"))
    private val earlier = WeightEntry("earlier", 80.6, Instant.parse("2026-10-13T07:30:00Z"))

    /** The days of the chart as the API reads them from [earlier] and [logged]. */
    private val earlierDay = WeightPoint(LocalDate.parse("2026-10-13"), 80.6, 80.6)
    private val loggedDay = WeightPoint(LocalDate.parse("2026-10-16"), 79.85, 80.23)

    @Test
    fun `each profile's weights of the last ninety days are read with it in the account's unit, and ones that cannot be read are said on its card`() {
        val users = users().also { coEvery { it.me() } returns account("lb") }
        val since = slot<Instant>()
        val daysSince = slot<Instant>()
        val zone = slot<ZoneId>()
        coEvery { weights.entries(ala.id, capture(since)) } returns listOf(logged, earlier)
        coEvery { weights.days(ala.id, capture(daysSince), capture(zone)) } returns listOf(earlierDay, loggedDay)

        val state = DashboardViewModel(users, plans, weights, notifications).state.value

        assertEquals(WeightUnit.LB, state.unit)
        assertEquals(listOf(logged, earlier), state.profiles.single().weights)
        assertEquals(listOf(earlierDay, loggedDay), state.profiles.single().days)
        assertEquals(90L, Duration.between(since.captured, Instant.now()).toDays())
        // The days of the same span, where the phone is.
        assertEquals(since.captured, daysSince.captured)
        assertEquals(ZoneId.systemDefault(), zone.captured)

        val refusal = ApiException(429, "RATE_LIMITED", "Too many requests.", retryAfterSeconds = 30)
        coEvery { weights.entries(ala.id, any()) } throws refusal
        val unread = DashboardViewModel(users, plans, weights, notifications).state.value.profiles.single()

        assertNull(unread.weights)
        assertNull(unread.days)
        assertSame(refusal, unread.weightsError)
        assertNotNull(unread.target)

        coEvery { weights.entries(ala.id, any()) } returns listOf(logged, earlier)
        coEvery { weights.days(ala.id, any(), any()) } throws refusal
        val noDays = DashboardViewModel(users, plans, weights, notifications).state.value.profiles.single()

        assertNull(noDays.weights)
        assertNull(noDays.days)
        assertSame(refusal, noDays.weightsError)
    }

    @Test
    fun `a weight typed in the account's unit is logged in kilograms, empties the field, is the newest and the chart's days are read again, what is no number is said and not sent, and a refusal says its bound`() {
        val users = users().also { coEvery { it.me() } returns account("lb") }
        coEvery { weights.entries(ala.id, any()) } returns listOf(earlier)
        val todayDay = WeightPoint(LocalDate.parse("2026-10-16"), 81.65, 81.13)
        coEvery { weights.days(ala.id, any(), any()) } returnsMany listOf(listOf(earlierDay), listOf(earlierDay, todayDay))
        val today = logged.copy(kg = 81.65)
        coEvery { weights.log(ala.id, 81.65) } returns today
        // A thousand pounds is more than the 400 kg the API takes.
        coEvery { weights.log(ala.id, 453.59) } throws refusal("weight-refused")
        val model = DashboardViewModel(users, plans, weights, notifications)

        model.typeWeight(ala.id, "eighty")
        model.logWeight(ala.id)

        assertEquals(WeightLog(typed = "eighty", problem = WeightProblem.NotANumber), model.state.value.weightLogs[ala.id])
        coVerify(exactly = 0) { weights.log(any(), any()) }

        model.typeWeight(ala.id, "1000")
        model.logWeight(ala.id)

        assertEquals(
            WeightLog(typed = "1000", problem = WeightProblem.Refused(FieldRefusal.TooBig(400.0, inclusive = true))),
            model.state.value.weightLogs[ala.id],
        )
        coVerify(exactly = 1) { weights.days(ala.id, any(), any()) }

        model.typeWeight(ala.id, "180,0")
        model.logWeight(ala.id)

        coVerify { weights.log(ala.id, 81.65) }
        assertEquals(WeightLog(), model.state.value.weightLogs[ala.id])
        assertEquals(listOf(today, earlier), model.state.value.profiles.single().weights)
        assertEquals(listOf(earlierDay, todayDay), model.state.value.profiles.single().days)
        coVerify(exactly = 2) { weights.days(ala.id, any(), any()) }
    }

    @Test
    fun `a weight the API refuses keeps what was typed, with the reason`() {
        val users = users().also { coEvery { it.me() } returns account("kg") }
        val refusal = ApiException(404, "PROFILE_NOT_FOUND", "Profile not found.")
        coEvery { weights.log(ala.id, 80.2) } throws refusal
        val model = DashboardViewModel(users, plans, weights, notifications)

        model.typeWeight(ala.id, "80.2")
        model.logWeight(ala.id)

        assertEquals(WeightLog(typed = "80.2", problem = WeightProblem.LogFailed(refusal)), model.state.value.weightLogs[ala.id])
        assertEquals(emptyList<WeightEntry>(), model.state.value.profiles.single().weights)
        coVerify(exactly = 1) { weights.days(ala.id, any(), any()) }
    }

    @Test
    fun `the newest weight is taken back, the chart's days read again, then the one before it, and a refusal is said on the card`() {
        coEvery { weights.entries(ala.id, any()) } returns listOf(logged, earlier)
        coEvery { weights.days(ala.id, any(), any()) } returnsMany listOf(listOf(earlierDay, loggedDay), listOf(earlierDay))
        coEvery { weights.delete(logged.id) } just runs
        val gone = ApiException(404, "WEIGHT_ENTRY_NOT_FOUND", "Weight entry not found.")
        coEvery { weights.delete(earlier.id) } throws gone
        val model = DashboardViewModel(users(), plans, weights, notifications)

        model.removeLatestWeight(ala.id)

        coVerify { weights.delete(logged.id) }
        assertEquals(listOf(earlier), model.state.value.profiles.single().weights)
        assertEquals(listOf(earlierDay), model.state.value.profiles.single().days)

        model.removeLatestWeight(ala.id)

        coVerify { weights.delete(earlier.id) }
        assertEquals(WeightLog(problem = WeightProblem.RemoveFailed(gone)), model.state.value.weightLogs[ala.id])
        assertEquals(listOf(earlier), model.state.value.profiles.single().weights)
        coVerify(exactly = 2) { weights.days(ala.id, any(), any()) }
    }

    @Test
    fun `days that cannot be read again after a change are said on the card, which keeps its weights, until a later reading has them`() {
        val users = users().also { coEvery { it.me() } returns account("kg") }
        coEvery { weights.entries(ala.id, any()) } returns listOf(earlier)
        val refusal = ApiException(429, "RATE_LIMITED", "Too many requests.", retryAfterSeconds = 30)
        val today = logged.copy(kg = 80.2)
        val todayDay = WeightPoint(LocalDate.parse("2026-10-16"), 80.2, 80.4)
        coEvery { weights.days(ala.id, any(), any()) } returns listOf(earlierDay) andThenThrows refusal andThen listOf(earlierDay, todayDay)
        coEvery { weights.log(ala.id, 80.2) } returns today
        val model = DashboardViewModel(users, plans, weights, notifications)

        model.typeWeight(ala.id, "80.2")
        model.logWeight(ala.id)

        val unread = model.state.value.profiles.single()
        assertSame(refusal, unread.weightsError)
        assertEquals(listOf(today, earlier), unread.weights)

        model.typeWeight(ala.id, "80.2")
        model.logWeight(ala.id)

        val read = model.state.value.profiles.single()
        assertNull(read.weightsError)
        assertEquals(listOf(earlierDay, todayDay), read.days)
    }

    @Test
    fun `days read again after a change and answered after a later reading's are left out`() {
        val users = users().also { coEvery { it.me() } returns account("kg") }
        coEvery { weights.entries(ala.id, any()) } returns listOf(earlier)
        val today = logged.copy(kg = 80.2)
        coEvery { weights.log(ala.id, 80.2) } returns today
        coEvery { weights.delete(today.id) } just runs
        val afterTheLog = CompletableDeferred<List<WeightPoint>>()
        val afterTheRemoval = CompletableDeferred<List<WeightPoint>>()
        val answers = ArrayDeque(listOf(CompletableDeferred(listOf(earlierDay)), afterTheLog, afterTheRemoval))
        coEvery { weights.days(ala.id, any(), any()) } coAnswers { answers.removeFirst().await() }
        val model = DashboardViewModel(users, plans, weights, notifications)

        model.typeWeight(ala.id, "80.2")
        model.logWeight(ala.id)
        model.removeLatestWeight(ala.id)
        afterTheRemoval.complete(listOf(earlierDay))
        afterTheLog.complete(listOf(earlierDay, WeightPoint(LocalDate.parse("2026-10-16"), 80.2, 80.4)))

        assertEquals(listOf(earlierDay), model.state.value.profiles.single().days)
        assertEquals(listOf(earlier), model.state.value.profiles.single().weights)
    }

    @Test
    fun `the bell counts the notifications not read yet, read again on coming back, and keeps its count while they cannot be read`() {
        coEvery { notifications.unread() } returns json.decodeFromString<List<NotificationDto>>(fixture("notifications-unread")).map { it.toDomain() }
        val model = DashboardViewModel(users(), plans, weights, notifications)
        assertEquals(3, model.state.value.unread)

        coEvery { notifications.unread() } throws IOException("unreachable")
        model.shownAgain(cameToTheFront = false)
        assertEquals(3, model.state.value.unread)

        coEvery { notifications.unread() } returns emptyList()
        model.shownAgain(cameToTheFront = false)
        assertEquals(0, model.state.value.unread)
    }
}
