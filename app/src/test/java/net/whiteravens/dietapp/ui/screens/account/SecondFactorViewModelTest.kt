// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.SecondFactorRepository
import net.whiteravens.dietapp.domain.SecondFactorSetup
import net.whiteravens.dietapp.domain.SecondFactorState
import net.whiteravens.dietapp.ui.screens.account.SecondFactorViewModel.Purpose
import net.whiteravens.dietapp.ui.screens.account.SecondFactorViewModel.Step
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SecondFactorViewModelTest {
    private val off = SecondFactorState(enabled = false, since = null, recoveryCodesLeft = 0)
    private val on = SecondFactorState(enabled = true, since = Instant.parse("2026-10-17T08:42:56.963Z"), recoveryCodesLeft = 10)
    private val setup = SecondFactorSetup("LBBALDN6MTVWWNMCVDDHRHV7UMMLPLR5", "otpauth://totp/x?secret=LBBALDN6MTVWWNMCVDDHRHV7UMMLPLR5", "enrolment-1")
    private val codes = listOf("PZ2KE-VY1ZF", "DKKDP-6ZEC4")
    private val secondFactor = mockk<SecondFactorRepository>()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `set up takes the password, hands out a secret, and its first code switches it on with the recovery codes shown once`() = runTest {
        coEvery { secondFactor.state() } returnsMany listOf(off, on)
        coEvery { secondFactor.propose("Str0ng-Passw0rd-1!") } returns setup
        coEvery { secondFactor.begin("enrolment-1", "123456") } returns codes
        val model = SecondFactorViewModel(secondFactor)
        assertEquals(off, model.state.value.secondFactor)

        model.setUp()
        model.continueWithPassword()
        coVerify(exactly = 0) { secondFactor.propose(any()) }
        model.typePassword("Str0ng-Passw0rd-1!")
        model.continueWithPassword()
        assertEquals(Step.Scan(setup), model.state.value.step)
        assertEquals("", model.state.value.password)

        model.typeCode("123456")
        model.switchOn()

        assertEquals(Step.Codes(codes, fresh = true), model.state.value.step)
        assertEquals(on, model.state.value.secondFactor)
        assertEquals("", model.state.value.code)
        model.saved()
        assertEquals(Step.Rest, model.state.value.step)
    }

    @Test
    fun `a wrong first code is said and can be typed again, and a set-up that ran out begins again`() = runTest {
        coEvery { secondFactor.state() } returns off
        coEvery { secondFactor.propose(any()) } returns setup
        val wrong = ApiException(401, "INVALID_SECOND_FACTOR_CODE", "That code is not right.")
        val expired = ApiException(400, "SECOND_FACTOR_SETUP_EXPIRED", "This set-up has run out.")
        coEvery { secondFactor.begin(any(), "111111") } throws wrong
        coEvery { secondFactor.begin(any(), "222222") } throws expired
        val model = SecondFactorViewModel(secondFactor)
        model.setUp()
        model.typePassword("Str0ng-Passw0rd-1!")
        model.continueWithPassword()

        model.typeCode("111111")
        model.switchOn()
        assertEquals(Step.Scan(setup), model.state.value.step)
        assertSame(wrong, model.state.value.error)
        assertFalse(model.state.value.busy)

        model.typeCode("222222")
        model.switchOn()
        assertEquals(Step.Rest, model.state.value.step)
        assertSame(expired, model.state.value.error)
    }

    @Test
    fun `a wrong password is said where it was typed, and cancelling forgets what was typed`() = runTest {
        coEvery { secondFactor.state() } returns off
        val wrong = ApiException(401, "INVALID_PASSWORD", "Current password is incorrect.")
        coEvery { secondFactor.propose(any()) } throws wrong
        val model = SecondFactorViewModel(secondFactor)

        model.setUp()
        model.typePassword("Wr0ng-Passw0rd-9?")
        model.continueWithPassword()
        assertEquals(Step.Password, model.state.value.step)
        assertSame(wrong, model.state.value.error)
        assertEquals("Wr0ng-Passw0rd-9?", model.state.value.password)

        model.cancel()
        assertEquals(Step.Rest, model.state.value.step)
        assertEquals("", model.state.value.password)
        assertNull(model.state.value.error)
    }

    @Test
    fun `new recovery codes and switching off each take the password and a code, and the state is read again`() = runTest {
        coEvery { secondFactor.state() } returnsMany listOf(on, on.copy(recoveryCodesLeft = 10), off)
        coEvery { secondFactor.newRecoveryCodes("Str0ng-Passw0rd-1!", "PZ2KE-VY1ZF") } returns codes
        coEvery { secondFactor.end("Str0ng-Passw0rd-1!", "DKKDP-6ZEC4") } returns Unit
        val model = SecondFactorViewModel(secondFactor)

        model.prove(Purpose.NEW_CODES)
        model.typePassword("Str0ng-Passw0rd-1!")
        model.submitProof()
        coVerify(exactly = 0) { secondFactor.newRecoveryCodes(any(), any()) }
        model.typeCode("PZ2KE-VY1ZF")
        model.submitProof()
        assertEquals(Step.Codes(codes, fresh = false), model.state.value.step)
        model.saved()

        model.prove(Purpose.SWITCH_OFF)
        model.typePassword("Str0ng-Passw0rd-1!")
        model.typeCode("DKKDP-6ZEC4")
        model.submitProof()

        assertEquals(Step.Rest, model.state.value.step)
        assertEquals(off, model.state.value.secondFactor)
    }
}
