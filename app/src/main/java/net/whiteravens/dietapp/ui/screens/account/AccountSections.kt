// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.AiModes
import net.whiteravens.dietapp.notify.PhoneNotifier
import net.whiteravens.dietapp.ui.components.Text

/**
 * A part of the account's settings with a screen of its own, titled as its row in the list is named. A link names one
 * by its name (`AccountRoute.section`), as the way to turn AI on does.
 */
enum class AccountSection(@StringRes val title: Int) {
    UNIT(R.string.account_weight_unit),
    PALETTE(R.string.appearance_palette),
    UPDATES(R.string.updates_title),
    NOTIFICATIONS(R.string.phone_notifications_title),
    AI(R.string.account_ai),
    UNLOCK(R.string.unlock_title),
    SECOND_FACTOR(R.string.account_second_factor),
    SESSIONS(R.string.account_sessions),
    WEB(R.string.account_web_settings),
}

/** The heading of a group of the list, named as the web's tab of the same settings. */
@Composable
internal fun SettingsHeading(@StringRes name: Int) {
    Text(
        stringResource(name),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/** A row of the list: a setting's [name] and what it is set to, [value]; a press opens it. */
@Composable
internal fun SettingRow(name: String, value: String?, onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            value?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A setting that is on or off, in words. */
@Composable
internal fun onOrOff(on: Boolean?): String? = on?.let { stringResource(if (it) R.string.common_on else R.string.common_off) }

/** The AI mode [mode] in the words its choice has. */
@Composable
internal fun aiModeName(mode: String): String = stringResource(
    when (mode) {
        AiModes.NONE -> R.string.account_ai_mode_none
        AiModes.INSTANCE -> R.string.account_ai_mode_admin
        else -> R.string.account_ai_mode_byok
    },
)

/** What becomes of the phone's notices: off, shown, blocked by the system, or held back by the battery's saving. */
internal enum class PhoneNotices { OFF, ON, BLOCKED, DELAYED }

/** What becomes of the notices where the app shows them ([on]) and the system [allowed] them and left the app [unrestricted]. */
internal fun phoneNotices(on: Boolean?, allowed: Boolean, unrestricted: Boolean): PhoneNotices = when {
    on != true -> PhoneNotices.OFF
    !allowed -> PhoneNotices.BLOCKED
    !unrestricted -> PhoneNotices.DELAYED
    else -> PhoneNotices.ON
}

/** [notices] in the words of the list. */
@Composable
internal fun phoneNoticesSaid(notices: PhoneNotices): String = stringResource(
    when (notices) {
        PhoneNotices.OFF -> R.string.common_off
        PhoneNotices.ON -> R.string.common_on
        PhoneNotices.BLOCKED -> R.string.phone_notifications_blocked
        PhoneNotices.DELAYED -> R.string.phone_notifications_delayed
    },
)

/**
 * What the system allows the notices, and whether it leaves the app unrestricted by the battery, read again whenever
 * the screen comes back: the system changes both in its own settings.
 */
internal class PhoneAllowance(private val context: Context) {
    var allowed by mutableStateOf(PhoneNotifier.allowed(context))
        private set
    var unrestricted by mutableStateOf(batteryUnrestricted(context))
        private set

    fun read() {
        allowed = PhoneNotifier.allowed(context)
        unrestricted = batteryUnrestricted(context)
    }
}

/** The phone's [PhoneAllowance], read again whenever the screen comes back. */
@Composable
internal fun rememberPhoneAllowance(): PhoneAllowance {
    val context = LocalContext.current
    val allowance = remember(context) { PhoneAllowance(context) }
    LifecycleResumeEffect(allowance) {
        allowance.read()
        onPauseOrDispose {}
    }
    return allowance
}

/** Whether the phone can lock the app with what it has enrolled, read again whenever the screen comes back. */
@Composable
internal fun rememberUnlockAvailable(viewModel: UnlockViewModel): Boolean {
    var available by remember { mutableStateOf(viewModel.available()) }
    LifecycleResumeEffect(Unit) {
        available = viewModel.available()
        onPauseOrDispose {}
    }
    return available
}
