// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * Keeps the account's time zone that of the phone, whose hours the account's reminders keep: asked
 * whenever the app comes to the front, so that a phone taken to another zone moves them with it. One
 * that does not get through is tried again the next time; one asks at a time.
 */
@HiltViewModel
class TimeZoneViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    private var keeping: Job? = null

    fun keep() {
        if (keeping?.isActive == true) return
        keeping = viewModelScope.launch { attempt { users.keepTimeZone() } }
    }
}
