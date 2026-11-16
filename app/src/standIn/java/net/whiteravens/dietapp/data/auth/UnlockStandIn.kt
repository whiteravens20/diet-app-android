// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

/**
 * What stands in for the phone in the screens' tests of the unlock: an emulator has no finger, and the system's prompt
 * lies outside Compose's reach. In the debug and the minified builds alone; the release's [UnlockStandIn] does nothing.
 * A journey sets [phone] before it turns the unlock on, and takes it away at its end.
 */
object UnlockStandIn {
    @Volatile
    var phone: StandInPhone? = null

    fun device(): UnlockDevice? = phone

    /** How the next prompt ends; null where the phone itself is asked. */
    fun answer(): Boolean? = phone?.passes

    /** Time the journey moved forward, for the half hour in the background. */
    fun clockShift(): Long = phone?.shift ?: 0L
}

/** A phone that can always ask, whose prompts end as [passes] says and whose key a journey voids. */
class StandInPhone(
    @Volatile var passes: Boolean = true,
    @Volatile var shift: Long = 0L,
) : UnlockDevice {
    @Volatile
    private var key = false

    @Volatile
    private var voidedNow = false

    /** A fingerprint added on the phone since the unlock was turned on. */
    fun voidTheKey() {
        voidedNow = true
    }

    override fun canAsk() = true

    override fun canTurnOn() = true

    override fun makeKey() {
        key = true
        voidedNow = false
    }

    override fun voided() = voidedNow || !key

    override fun deleteKey() {
        key = false
    }
}
