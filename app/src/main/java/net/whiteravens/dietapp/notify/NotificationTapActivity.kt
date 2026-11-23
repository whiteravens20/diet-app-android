// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.notify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.MainActivity
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.di.ApplicationScope
import net.whiteravens.dietapp.ui.common.attempt

/**
 * Where a tap on one of the application's own notifications lands, without a screen of its own. It is not exported,
 * so only the application's pending intents start it: it says what the tap asked for ([Taps]), reads a reminder of
 * water through the queue without a network, and brings the application's screens to the front, which open it.
 */
class NotificationTapActivity : ComponentActivity() {
    /** What a tap takes from the application's graph. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Parts {
        fun taps(): Taps

        fun notifications(): NotificationRepository

        @ApplicationScope
        fun application(): CoroutineScope
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parts = EntryPointAccessors.fromApplication(applicationContext, Parts::class.java)
        when (intent?.action) {
            PhoneNotifier.OPEN_NOTIFICATIONS -> parts.taps().notificationsAsked()
            PhoneNotifier.OPEN_WATER -> {
                intent.getStringExtra(PhoneNotifier.EXTRA_PROFILE)?.let { profileId -> parts.taps().waterAsked(profileId) }
                intent.getStringExtra(PhoneNotifier.EXTRA_NOTIFICATION)?.let { reminder ->
                    parts.application().launch { attempt { parts.notifications().read(reminder) } }
                }
            }
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
