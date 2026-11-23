// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a tap on one of the application's own notifications asked to open, kept until the screens open it. Only
 * [NotificationTapActivity] says it, which nothing outside the application can start: an intent another application
 * sends the main activity opens nothing.
 */
@Singleton
class Taps @Inject constructor() {
    private val notifications = MutableStateFlow(false)
    private val water = MutableStateFlow<String?>(null)

    /** The list of notifications, asked for and not opened yet. */
    val openNotifications: StateFlow<Boolean> = notifications.asStateFlow()

    /** The profile whose water a reminder asked for, not opened yet. */
    val openWaterOf: StateFlow<String?> = water.asStateFlow()

    fun notificationsAsked() {
        notifications.value = true
    }

    fun notificationsOpened() {
        notifications.value = false
    }

    fun waterAsked(profileId: String) {
        water.value = profileId
    }

    fun waterOpened() {
        water.value = null
    }
}
