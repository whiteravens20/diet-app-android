// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.config.AppearanceStore
import net.whiteravens.dietapp.data.notify.PhoneNotificationsSwitch
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.update.UpdateInstaller
import net.whiteravens.dietapp.data.update.Updates
import net.whiteravens.dietapp.domain.Palette
import net.whiteravens.dietapp.notify.PhoneNotifier
import net.whiteravens.dietapp.ui.common.Returns
import net.whiteravens.dietapp.ui.navigation.DietAppRoot
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import javax.inject.Inject

/** Single-activity host; all navigation is Compose-based (ui/navigation). */
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

    /** A tap on a phone's notification asked for the list of notifications, not opened yet. */
    private var openNotifications by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a start of its own: an activity made again, the phone turned, has been asked already.
        if (savedInstanceState == null) {
            openNotifications = intent?.action == PhoneNotifier.OPEN_NOTIFICATIONS
            intent?.takeIf { it.action == UpdateInstaller.STATUS }?.let { status -> installer.answered(status, ::startActivity) }
        }
        // The phone's notifications, where they are on, keep the schedule this version asks for.
        lifecycleScope.launch { phoneNotifications.keepScheduled() }
        setContent {
            // Drawn once the colour scheme is read, so that no frame shows another scheme first.
            val palette by appearance.palette.collectAsStateWithLifecycle<Palette?>(initialValue = null)
            palette?.let { scheme ->
                DietAppTheme(palette = scheme) {
                    Surface(Modifier.fillMaxSize()) {
                        DietAppRoot(openNotifications = openNotifications, onNotificationsOpened = { openNotifications = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == PhoneNotifier.OPEN_NOTIFICATIONS) openNotifications = true
        // What the system's installer answered of a newer build: the question it asks the user, or how it ended.
        if (intent.action == UpdateInstaller.STATUS) installer.answered(intent, ::startActivity)
    }

    // Counted before the screens resume, so that they know the application came back to the front.
    override fun onStart() {
        super.onStart()
        Returns.started()
        lifecycleScope.launch { phoneNotifications.readNow() }
        // The changes made on the phone that wait go as soon as there is a network.
        lifecycleScope.launch { changes.sendSoon() }
        // A newer build is asked for on every start and return, at most every six hours.
        lifecycleScope.launch { updates.askIfDue() }
    }

    override fun onStop() {
        Returns.stopped()
        super.onStop()
    }
}
