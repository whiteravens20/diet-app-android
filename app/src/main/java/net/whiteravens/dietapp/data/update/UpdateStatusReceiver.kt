// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Where the system's installer answers an install session of a newer build. It is not exported, so only the
 * session's own pending intent reaches it; [UpdateInstaller.received] takes the answer.
 */
class UpdateStatusReceiver : BroadcastReceiver() {
    /** What the answer takes from the application's graph: Android makes the receiver itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Parts {
        fun installer(): UpdateInstaller
    }

    override fun onReceive(context: Context, intent: Intent) {
        EntryPointAccessors.fromApplication(context.applicationContext, Parts::class.java).installer().received(intent)
    }
}
