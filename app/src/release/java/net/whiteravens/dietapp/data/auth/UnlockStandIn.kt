// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

/** The release has no stand-in for the phone: the unlock always asks the phone itself. */
object UnlockStandIn {
    fun device(): UnlockDevice? = null

    fun answer(): Boolean? = null

    fun clockShift(): Long = 0L
}
