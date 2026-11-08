// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.whiteravens.dietapp.data.auth.TokenStore
import net.whiteravens.dietapp.data.config.WateredStore
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.WaterRepository
import net.whiteravens.dietapp.di.ApplicationScope
import net.whiteravens.dietapp.notify.WaterShortcuts
import net.whiteravens.dietapp.ui.common.attempt
import java.time.LocalDate

/**
 * A shortcut of the application's icon, without a screen of its own: its amount goes to today of
 * the profile watered last on this phone, or of the account's first profile, among those that track
 * water, through the queue of changes without a network, and a short message says so ("Added 330 ml
 * · Adam"). Signed out, before any profile was read on this phone, or where no profile tracks water,
 * the application opens instead.
 */
class WaterShortcutActivity : ComponentActivity() {
    /** What a shortcut takes from the application's graph. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Parts {
        fun tokens(): TokenStore

        fun users(): UserRepository

        fun watered(): WateredStore

        fun water(): WaterRepository

        fun shortcuts(): WaterShortcuts

        @ApplicationScope
        fun application(): CoroutineScope
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ml = intent.getIntExtra(ML, 0)
        val app = applicationContext
        val parts = EntryPointAccessors.fromApplication(app, Parts::class.java)
        // Read at once, from what the phone keeps: the application can be opened only while this is in front.
        val profile = runBlocking(Dispatchers.IO) {
            if (ml <= 0 || parts.tokens().current() == null) {
                null
            } else {
                parts.users().keptProfiles()?.let { kept -> parts.watered().profileOf(kept.profiles.filter { it.waterTracking }) }
            }
        }
        if (profile == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        parts.shortcuts().used(ml)
        parts.application().launch {
            val added = attempt { parts.water().add(profile.id, LocalDate.now(), ml) }.isSuccess
            val said = if (added) {
                app.getString(R.string.notifications_water_added, "$ml\u00A0${app.getString(R.string.unit_ml)}", profile.name)
            } else {
                app.getString(R.string.water_add_failed)
            }
            withContext(Dispatchers.Main) { Toast.makeText(app, said, Toast.LENGTH_SHORT).show() }
        }
        // Nothing to show: the message comes once the water is added or kept.
        finish()
    }

    companion object {
        private const val ADD_WATER = "net.whiteravens.dietapp.action.ADD_WATER_FROM_SHORTCUT"
        private const val ML = "ml"

        /** What a shortcut of [ml] starts. */
        fun adding(context: Context, ml: Int): Intent = Intent(context, WaterShortcutActivity::class.java)
            .setAction(ADD_WATER)
            .putExtra(ML, ml)
    }
}
