// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.auth

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.SignInResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The password and the second step's code are held only until they have been taken. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val auth = mockk<AuthRepository>()

    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model(): AuthViewModel {
        val server = mockk<ServerConfigStore>()
        every { server.baseUrl } returns flowOf("https://diet.example.test/api/")
        val instance = mockk<ServerRepository>()
        every { instance.checkSiteKey } returns MutableStateFlow(null)
        return AuthViewModel(auth, server, instance)
    }

    @Test
    fun `a password a sign-in refused is kept to be corrected, and one it took is forgotten`() {
        coEvery { auth.signIn(EMAIL, "wrong", null) } throws ApiException(401, "INVALID_CREDENTIALS", "No.")
        coEvery { auth.signIn(EMAIL, "right", null) } returns SignInResult.SignedIn
        val model = model()

        model.typePassword("wrong")
        model.signIn(EMAIL, model.password.value)
        assertEquals("wrong", model.password.value)

        model.typePassword("right")
        model.signIn(EMAIL, model.password.value)
        assertEquals("", model.password.value)
    }

    @Test
    fun `a sign-in that asks for a second step forgets the password, and the code is forgotten once taken`() {
        coEvery { auth.signIn(EMAIL, "right", null) } returns SignInResult.SecondStep("challenge")
        coEvery { auth.answerSecondStep("challenge", "000000") } throws ApiException(401, "INVALID_SECOND_FACTOR_CODE", "No.")
        coEvery { auth.answerSecondStep("challenge", "123456") } returns Unit
        val model = model()

        model.typePassword("right")
        model.signIn(EMAIL, model.password.value)
        assertEquals("", model.password.value)
        assertEquals("challenge", model.state.value.secondStep)

        model.typeSecondStepCode("000000")
        model.answerSecondStep(model.secondStepCode.value)
        assertEquals("000000", model.secondStepCode.value)

        model.typeSecondStepCode("123456")
        model.answerSecondStep(model.secondStepCode.value)
        assertEquals("", model.secondStepCode.value)
    }

    private companion object {
        const val EMAIL = "someone@example.test"
    }
}
