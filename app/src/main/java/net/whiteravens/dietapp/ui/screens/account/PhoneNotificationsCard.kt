// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.notify.PhoneNotifier
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.CheckRow

/**
 * The account's notifications on the phone: off until turned on here, which on Android 13 and later
 * asks for the permission first. Where Android does not let the application show them, or holds its
 * checks back to save battery, the card says so and opens the system's page where that is changed.
 */
@Composable
internal fun PhoneNotificationsCard(viewModel: PhoneNotificationsViewModel) {
    val on by viewModel.on.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // What the system allows changes in its own settings: read again whenever the screen comes back.
    var allowed by remember { mutableStateOf(PhoneNotifier.allowed(context)) }
    var unrestricted by remember { mutableStateOf(batteryUnrestricted(context)) }
    // Android said no to the permission when it was asked for here.
    var refused by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        allowed = PhoneNotifier.allowed(context)
        unrestricted = batteryUnrestricted(context)
        onPauseOrDispose {}
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = PhoneNotifier.allowed(context)
        refused = !granted
        if (granted) viewModel.turnOn()
    }

    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.phone_notifications_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.phone_notifications_about),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        CheckRow(
            label = stringResource(R.string.phone_notifications_show),
            checked = on == true,
            enabled = on != null,
            onChange = { wanted ->
                when {
                    !wanted -> viewModel.turnOff()
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PhoneNotifier.permitted(context) ->
                        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> viewModel.turnOn()
                }
            },
        )
        if ((on == true && !allowed) || (on != true && refused)) {
            Note(stringResource(R.string.phone_notifications_not_allowed))
            TextButton(onClick = { context.open(appNotificationSettings(context)) }) {
                Text(stringResource(R.string.phone_notifications_open_settings))
            }
        }
        if (on == true && allowed && !unrestricted) {
            Note(stringResource(R.string.phone_notifications_battery))
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) Note(stringResource(R.string.phone_notifications_battery_samsung))
            TextButton(onClick = { context.open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                Text(stringResource(R.string.phone_notifications_open_settings))
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Whether Android lets the application's work run on time, outside its saving of battery. */
private fun batteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** The system's page of the application's notifications. */
private fun appNotificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Opens a page of the system's settings; one the phone does not have opens nothing. */
private fun Context.open(intent: Intent) {
    runCatching { startActivity(intent) }
}
