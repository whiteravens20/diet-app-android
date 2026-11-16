// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.unlock

import android.content.Context
import android.os.Build
import androidx.biometric.AuthenticationRequest
import androidx.biometric.AuthenticationRequest.Biometric.Fallback
import androidx.biometric.AuthenticationRequest.Biometric.Strength
import androidx.biometric.AuthenticationResult
import androidx.biometric.AuthenticationResultLauncher
import androidx.biometric.BiometricPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.auth.UnlockDevice
import net.whiteravens.dietapp.data.auth.UnlockStandIn
import net.whiteravens.dietapp.data.config.UnlockStore
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.ChangeSender
import net.whiteravens.dietapp.di.ApplicationScope
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.inject.Inject
import javax.inject.Singleton

/** What a prompt is shown for, which its title says. */
enum class Asking(val title: Int) {
    Unlock(R.string.unlock_prompt_title),
    TurnOn(R.string.unlock_turn_on_title),
    TurnOff(R.string.unlock_turn_off_title),
}

/**
 * The unlock of the application's screens: the gate, the setting that turns it on, the key that watches the phone's
 * fingerprints, and the one prompt of the activity that asks for both. The activity hands its launcher over while it
 * lives ([attach]); a prompt asked for while none is there is not shown, and the gate stays closed.
 */
@Singleton
class Unlock @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: UnlockStore,
    private val device: UnlockDevice,
    private val auth: AuthRepository,
    private val changes: ChangeQueue,
    private val sender: ChangeSender,
    @ApplicationScope private val application: CoroutineScope,
) {
    private val gate = UnlockGate(
        now = { System.currentTimeMillis() + UnlockStandIn.clockShift() },
        canAsk = device::canAsk,
        voided = device::voided,
    )
    private var launcher: AuthenticationResultLauncher? = null
    private var waiting: ((AuthenticationResult) -> Unit)? = null

    val state: StateFlow<Gate> = gate.state
    val on: Flow<Boolean> = store.on

    init {
        // On the main thread, as the activity's starts and stops reach the gate.
        application.launch(Dispatchers.Main.immediate) { store.on.collect(gate::settingIs) }
        application.launch(Dispatchers.Main.immediate) {
            gate.state.collect { if (it == Gate.Voided) leave() }
        }
    }

    fun attach(launcher: AuthenticationResultLauncher) {
        this.launcher = launcher
    }

    fun detach(launcher: AuthenticationResultLauncher) {
        if (this.launcher === launcher) this.launcher = null
    }

    /** What the activity's prompt answered. */
    fun answered(result: AuthenticationResult) {
        val then = waiting ?: return
        waiting = null
        then(result)
    }

    fun wentAway() = gate.wentAway()

    fun cameBack() = gate.cameBack()

    /** Shows the prompt the gate asks for, once. */
    fun askForTheGate() {
        if ((gate.state.value as? Gate.Locked)?.askNow != true) return
        gate.prompted()
        val shown = ask(Asking.Unlock) { result ->
            when (result) {
                is AuthenticationResult.Success -> gate.answered(PromptAnswer.Passed)
                // Android 10's prompt has no screen lock: its own button is the account's password.
                is AuthenticationResult.CustomFallbackSelected -> {
                    gate.answered(PromptAnswer.Password)
                    leave()
                }
                else -> gate.answered(PromptAnswer.NotPassed)
            }
        }
        if (!shown) gate.answered(PromptAnswer.NotPassed)
    }

    fun askAgain() = gate.askAgain()

    /** Whether something that shows the account outside the screens may go ahead now (see [UnlockGate.lets]). */
    fun lets(): Boolean = gate.lets()

    /** Signs out from the locked screen: the changes waiting go first where they can, as at any sign-out. */
    fun leave() {
        application.launch {
            runCatching { changes.drain(sender::send) }
            auth.signOut()
        }
    }

    fun voidedSeen() = gate.voidedSeen()

    /** Whether the unlock can be turned on: the phone has a strong biometric enrolled. */
    fun canTurnOn(): Boolean = device.canTurnOn()

    /** Turns it on after a prompt that passed; [done] says whether it is on. */
    fun turnOn(done: (Boolean) -> Unit) {
        try {
            device.makeKey()
        } catch (e: GeneralSecurityException) {
            return done(false)
        } catch (e: ProviderException) {
            return done(false)
        }
        val shown = ask(Asking.TurnOn) { result ->
            application.launch {
                if (result is AuthenticationResult.Success) {
                    store.turnOn()
                    done(true)
                } else {
                    device.deleteKey()
                    done(false)
                }
            }
        }
        if (!shown) {
            device.deleteKey()
            done(false)
        }
    }

    /** Turns it off after a prompt that passed, its key with it; cancelled, it stays on. [done] says whether it is off. */
    fun turnOff(done: (Boolean) -> Unit) {
        val shown = ask(Asking.TurnOff) { result ->
            application.launch {
                if (result is AuthenticationResult.Success) {
                    store.forget()
                    device.deleteKey()
                    done(true)
                } else {
                    done(false)
                }
            }
        }
        if (!shown) done(false)
    }

    /** Asks the phone, or the stand-in of the screens' tests; false where nothing could be shown. */
    private fun ask(asking: Asking, then: (AuthenticationResult) -> Unit): Boolean {
        UnlockStandIn.answer()?.let { passes ->
            then(
                if (passes) {
                    AuthenticationResult.Success(null, BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC)
                } else {
                    AuthenticationResult.Error(BiometricPrompt.ERROR_USER_CANCELED, "")
                },
            )
            return true
        }
        val shown = launcher ?: return false
        waiting = then
        shown.launch(request(asking))
        return true
    }

    private fun request(asking: Asking): AuthenticationRequest.Biometric {
        val title = context.getString(asking.title)
        val fallback = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Fallback.DeviceCredential
            asking == Asking.Unlock -> Fallback.CustomOption(context.getString(R.string.unlock_use_password))
            else -> Fallback.CustomOption(context.getString(R.string.common_cancel))
        }
        return AuthenticationRequest.biometricRequest(title, fallback) {
            setMinStrength(Strength.Class3())
        }
    }
}
