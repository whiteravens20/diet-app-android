// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDetails
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.testing.fixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val main = StandardTestDispatcher()
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(main)

    @After
    fun reset() = Dispatchers.resetMain()

    private val kept: ProfileDetails = json.decodeFromString<ProfileDto>(fixture("profile")).toDetails()
    private val profiles = mockk<ProfileRepository>().also {
        coEvery { it.profiles() } returns listOf(kept)
    }
    private val users = mockk<UserRepository>().also {
        coEvery { it.me() } returns SessionUser("u", "ala@example.test", "Ala", true, "en", "system", weightUnit = "lb")
    }
    private val server = mockk<ServerRepository>().also {
        every { it.rules } returns MutableStateFlow<InstanceRules?>(InstanceRules(2, emptyMap()))
        coEvery { it.refresh() } just runs
    }

    private fun loaded(): ProfilesViewModel = ProfilesViewModel(profiles, users, server).also { it.load() }

    @Test
    fun `the profiles are read in the account's unit`() = runTest(main) {
        val model = loaded()
        runCurrent()

        assertEquals(listOf(kept), model.state.value.list)
        assertEquals(WeightUnit.LB, model.state.value.unit)
    }

    @Test
    fun `a profile deleted leaves the list, and the first profile's steps say what comes next`() = runTest(main) {
        coEvery { profiles.delete(kept.id) } just runs
        val model = loaded()
        runCurrent()

        coEvery { profiles.profiles() } returns emptyList()
        model.delete(kept)
        runCurrent()
        assertEquals(emptyList<ProfileDetails>(), model.state.value.list)

        coEvery { profiles.profiles() } returns listOf(kept)
        val made = Profile(kept.id, kept.figures.name, "balanced", 5)
        model.made(made)
        runCurrent()
        assertEquals(made, model.state.value.made)
        assertEquals(listOf(kept), model.state.value.list)
    }
}
