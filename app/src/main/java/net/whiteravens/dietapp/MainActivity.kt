// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.registerForAuthenticationResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.config.AppearanceStore
import net.whiteravens.dietapp.data.notify.PhoneNotificationsSwitch
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.update.UpdateInstaller
import net.whiteravens.dietapp.data.update.Updates
import net.whiteravens.dietapp.domain.Palette
import net.whiteravens.dietapp.notify.Taps
import net.whiteravens.dietapp.ui.common.Returns
import net.whiteravens.dietapp.ui.navigation.DietAppRoot
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import net.whiteravens.dietapp.ui.unlock.Gate
import net.whiteravens.dietapp.ui.unlock.LockedScreen
import net.whiteravens.dietapp.ui.unlock.Unlock
import javax.inject.Inject

/**
 * Single-activity host; all navigation is Compose-based (ui/navigation). It is the launcher, so any application can
 * start it with an intent of its choosing: it acts on none. What the application's own notifications ask for comes
 * through [Taps], and what the system's installer asks through [UpdateInstaller.question].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var phoneNotifications: PhoneNotificationsSwitch

    @Inject
    lateinit var changes: ChangeQueue

    @Inject
    lateinit var appearance: AppearanceStore

    @Inject
    lateinit var updates: Updates

    @Inject
    lateinit var installer: UpdateInstaller

    @Inject
    lateinit var taps: Taps

    @Inject
    lateinit var unlock: Unlock

    /** The one prompt of the unlock, for its gate and its setting: registered as the activity is made, as it must be. */
    private val authentication = registerForAuthenticationResult { result -> unlock.answered(result) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The system's installer asks the user about a newer build while the application is in front.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                installer.question.filterNotNull().collect { ask ->
                    installer.asked()
                    startActivity(ask)
                }
            }
        }
        // The phone's notifications, where they are on, keep the schedule this version asks for.
        lifecycleScope.launch { phoneNotifications.keepScheduled() }
        unlock.attach(authentication)
        // While the unlock is on, the recents show nothing of the application: from Android 13 without refusing
        // screenshots, below by a secure window, which refuses them (his answer).
        lifecycleScope.launch { unlock.on.collect(::hiddenFromTheRecents) }
        setContent {
            // Drawn once the colour scheme is read, so that no frame shows another scheme first.
            val palette by appearance.palette.collectAsStateWithLifecycle<Palette?>(initialValue = null)
            palette?.let { scheme ->
                DietAppTheme(palette = scheme) {
                    Surface(Modifier.fillMaxSize()) {
                        // Nothing of the account is composed until the gate opens: its screens and what they read wait.
                        val gate by unlock.state.collectAsStateWithLifecycle()
                        val openNotifications by taps.openNotifications.collectAsStateWithLifecycle()
                        val openWaterOf by taps.openWaterOf.collectAsStateWithLifecycle()
                        LaunchedEffect(gate) { if ((gate as? Gate.Locked)?.askNow == true) unlock.askForTheGate() }
                        when (gate) {
                            Gate.Open -> DietAppRoot(
                                openNotifications = openNotifications,
                                onNotificationsOpened = taps::notificationsOpened,
                                openWaterOf = openWaterOf,
                                onWaterOpened = taps::waterOpened,
                            )
                            Gate.Reading -> Unit
                            else -> LockedScreen(gate, onUnlock = unlock::askAgain, onSignOut = unlock::leave, onVoidedSeen = unlock::voidedSeen)
                        }
                    }
                }
            }
        }
    }

    // Counted before the screens resume, so that they know the application came back to the front.
    override fun onStart() {
        super.onStart()
        unlock.cameBack()
        Returns.started()
        lifecycleScope.launch { phoneNotifications.readNow() }
        // The changes made on the phone that wait go as soon as there is a network.
        lifecycleScope.launch { changes.sendSoon() }
        // A newer build is asked for on every start and return, at most every six hours.
        lifecycleScope.launch { updates.askIfDue() }
    }

    override fun onStop() {
        Returns.stopped()
        unlock.wentAway()
        super.onStop()
    }

    override fun onDestroy() {
        unlock.detach(authentication)
        super.onDestroy()
    }

    private fun hiddenFromTheRecents(hidden: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!hidden)
        } else if (hidden) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
