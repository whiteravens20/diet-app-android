// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.config.serverAddress
import net.whiteravens.dietapp.ui.common.WhenShownAgain
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.components.AccentChip
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineAsOf
import net.whiteravens.dietapp.ui.components.PullToRead
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.ServerUrlDialog
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.Text
import net.whiteravens.dietapp.ui.components.UpdateViewModel
import net.whiteravens.dietapp.ui.components.rememberWebPages

/**
 * Account overview, the unit weights are shown in, what AI it has and the providers it brings of its
 * own, two-step sign-in, where it is signed in, and sign-out. The password, the address, the name and
 * the language of the mails, the account's data in a file and its deletion are the web application's,
 * whose settings open in the browser.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    /** The section a link asked for, opened at once; going back from it leaves the settings. */
    asked: AccountSection? = null,
    viewModel: AccountViewModel = hiltViewModel(),
    secondFactor: SecondFactorViewModel = hiltViewModel(),
    sessions: SessionsViewModel = hiltViewModel(),
    ai: AiViewModel = hiltViewModel(),
    phoneNotifications: PhoneNotificationsViewModel = hiltViewModel(),
    appearance: AppearanceViewModel = hiltViewModel(),
    update: UpdateViewModel = hiltViewModel(),
    unlock: UnlockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val twoStep by secondFactor.state.collectAsStateWithLifecycle()
    val signedIn by sessions.state.collectAsStateWithLifecycle()
    val aiState by ai.state.collectAsStateWithLifecycle()
    val colours by appearance.state.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val mailsSent by viewModel.mailsSent.collectAsStateWithLifecycle()
    val operatorContact by viewModel.operatorContact.collectAsStateWithLifecycle()
    var showServerDialog by rememberSaveable { mutableStateOf(false) }
    val web = rememberWebPages()
    // The section open on its own screen, or the list of them where none is; the list keeps its place under it.
    var section by rememberSaveable { mutableStateOf(asked) }
    val listScroll = rememberScrollState()
    val backToList = section != null && asked == null
    BackHandler(enabled = backToList) { section = null }
    // The account's cards are read again together: on being shown again, and on a pull.
    val readTheOthers = {
        ai.load()
        secondFactor.load()
        sessions.load()
    }
    WhenShownAgain { cameToTheFront -> if (viewModel.shownAgain(cameToTheFront)) readTheOthers() }

    if (showServerDialog) {
        ServerUrlDialog(
            current = serverUrl,
            onSave = viewModel::changeServer,
            onDismiss = { showServerDialog = false },
            warning = stringResource(R.string.server_change_signs_out),
        )
    }
    state.leaving?.let { asked ->
        LeaveWithChangesDialog(asked, onSend = viewModel::sendThenLeave, onDrop = viewModel::dropThenLeave, onStay = viewModel::stay)
    }

    PullToRead(state.pulled, onPull = {
        viewModel.pull()
        readTheOthers()
    }) {
        val open = section
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(if (open == null) listScroll else rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenHeader(
                title = stringResource(open?.title ?: R.string.account_title),
                onBack = if (backToList) ({ section = null }) else onBack,
            )
            state.readAt?.takeIf { state.offline }?.let { readAt -> OfflineAsOf(readAt) }

            state.error?.let {
                ErrorBanner(errorMessage(it))
                Button(onClick = viewModel::load) { Text(stringResource(R.string.common_retry)) }
            }

            when {
                state.loading -> {
                    Skeleton(Modifier.fillMaxWidth().height(96.dp))
                    Skeleton(Modifier.fillMaxWidth().height(120.dp))
                }

                open != null -> when (open) {
                    AccountSection.UNIT -> AppCard {
                        WeightUnitChoice(
                            chosen = state.weightUnit,
                            saving = state.choosing != null,
                            failed = state.unitError,
                            onChoose = viewModel::chooseWeightUnit,
                        )
                    }

                    AccountSection.PALETTE -> PaletteCard(colours, onChoose = appearance::choose)
                    AccountSection.UPDATES -> UpdatesCard(update)
                    AccountSection.NOTIFICATIONS -> PhoneNotificationsCard(phoneNotifications)
                    // The mode and the providers it may use stand together, as on the web's tab: the mode points to them.
                    AccountSection.AI -> {
                        AiModeCard(aiState, operatorContact, onChoose = ai::chooseMode, onRetry = ai::load)
                        AiProvidersCard(
                            aiState,
                            AiProviderActions(
                                retry = ai::load,
                                open = ai::open,
                                change = ai::change,
                                cancel = ai::cancel,
                                edit = ai::edit,
                                chooseModel = ai::chooseModel,
                                choosePolicy = ai::choosePolicy,
                                test = ai::test,
                                save = ai::save,
                                remove = ai::remove,
                            ),
                        )
                    }

                    AccountSection.UNLOCK -> UnlockCard(unlock)
                    AccountSection.SECOND_FACTOR -> SecondFactorCard(
                        twoStep,
                        SecondFactorActions(
                            load = secondFactor::load,
                            setUp = secondFactor::setUp,
                            prove = secondFactor::prove,
                            cancel = secondFactor::cancel,
                            saved = secondFactor::saved,
                            typePassword = secondFactor::typePassword,
                            typeCode = secondFactor::typeCode,
                            continueWithPassword = secondFactor::continueWithPassword,
                            switchOn = secondFactor::switchOn,
                            submitProof = secondFactor::submitProof,
                        ),
                        onOthersSignedOut = sessions::load,
                    )

                    AccountSection.SESSIONS -> SessionsCard(signedIn, onEnd = sessions::end, onEndAll = sessions::endAll, onRetry = sessions::load)
                    AccountSection.WEB -> AppCard {
                        Text(
                            stringResource(R.string.account_settings_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { web.open("settings", "tab" to "account") },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.web_open))
                        }
                    }
                }

                state.user != null -> {
                    val user = state.user!!
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    user.displayName.trim()
                                        .split(Regex("\\s+"))
                                        .take(2)
                                        .mapNotNull { it.firstOrNull()?.uppercase() }
                                        .joinToString("")
                                        .ifEmpty { "?" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.padding(top = 6.dp)) {
                                    if (user.emailVerified) {
                                        AccentChip(stringResource(R.string.account_verified))
                                    } else {
                                        LabelChip(stringResource(R.string.account_unverified))
                                    }
                                }
                            }
                        }
                        // Sending the confirmation again means something only for an address not confirmed, on a server that sends mail.
                        if (!user.emailVerified && mailsSent == true) {
                            if (state.resent) {
                                Text(
                                    stringResource(R.string.account_resend_sent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            } else {
                                OutlinedButton(
                                    onClick = viewModel::resendVerification,
                                    enabled = !state.resending,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.padding(top = 12.dp),
                                ) {
                                    Text(stringResource(if (state.resending) R.string.account_resend_sending else R.string.account_resend_verification))
                                }
                            }
                            state.resendError?.let { ErrorBanner(errorMessage(it), Modifier.padding(top = 8.dp)) }
                        }
                    }

                    // The settings by the web's tabs: a row a setting, its value beside its name; a press opens its screen.
                    val unitValue = stringResource(
                        when (state.weightUnit) {
                            null -> R.string.account_weight_unit_by_region
                            "kg" -> R.string.account_weight_unit_kg
                            else -> R.string.account_weight_unit_lb
                        },
                    )
                    val paletteValue = colours.chosen?.let { paletteName(it) }
                        ?: stringResource(R.string.appearance_palette_account, paletteName(colours.account))
                    val updatesOn by update.on.collectAsStateWithLifecycle()
                    val noticesOn by phoneNotifications.on.collectAsStateWithLifecycle()
                    val allowance = rememberPhoneAllowance()
                    val unlockOn by unlock.on.collectAsStateWithLifecycle()
                    val unlockShown = rememberUnlockAvailable(unlock) || unlockOn == true
                    val open = { chosen: AccountSection -> section = chosen }

                    SettingsHeading(R.string.account_group_general)
                    AppCard {
                        SettingRow(stringResource(R.string.account_weight_unit), unitValue) { open(AccountSection.UNIT) }
                        RowLine()
                        SettingRow(stringResource(R.string.appearance_palette), paletteValue) { open(AccountSection.PALETTE) }
                        RowLine()
                        SettingRow(stringResource(R.string.server_title), serverAddress(serverUrl)) { showServerDialog = true }
                        if (update.hasChannel) {
                            RowLine()
                            SettingRow(stringResource(R.string.updates_title), onOrOff(updatesOn)) { open(AccountSection.UPDATES) }
                        }
                        RowLine()
                        SettingRow(
                            stringResource(R.string.phone_notifications_title),
                            noticesOn?.let { phoneNoticesSaid(phoneNotices(it, allowance.allowed, allowance.unrestricted)) },
                        ) { open(AccountSection.NOTIFICATIONS) }
                    }

                    SettingsHeading(R.string.account_group_ai)
                    AppCard {
                        SettingRow(stringResource(R.string.account_ai), aiState.mode?.let { aiModeName(it) }) { open(AccountSection.AI) }
                        RowLine()
                        SettingRow(
                            stringResource(R.string.account_ai_providers),
                            aiState.providers?.let { providers ->
                                if (providers.isEmpty()) {
                                    stringResource(R.string.account_ai_providers_none_short)
                                } else {
                                    pluralStringResource(R.plurals.account_ai_providers_count, providers.size, providers.size)
                                }
                            },
                        ) { open(AccountSection.AI) }
                    }

                    SettingsHeading(R.string.account_group_security)
                    AppCard {
                        if (unlockShown) {
                            SettingRow(stringResource(R.string.unlock_title), onOrOff(unlockOn)) { open(AccountSection.UNLOCK) }
                            RowLine()
                        }
                        SettingRow(stringResource(R.string.account_second_factor), onOrOff(twoStep.secondFactor?.enabled)) {
                            open(AccountSection.SECOND_FACTOR)
                        }
                        RowLine()
                        SettingRow(
                            stringResource(R.string.account_sessions),
                            signedIn.sessions?.let { pluralStringResource(R.plurals.account_sessions_count, it.size, it.size) },
                        ) { open(AccountSection.SESSIONS) }
                    }

                    SettingsHeading(R.string.account_group_account)
                    AppCard {
                        SettingRow(stringResource(R.string.account_web_settings), stringResource(R.string.account_web_settings_where)) {
                            open(AccountSection.WEB)
                        }
                    }
                }
            }

            if (open == null) {
                Button(
                    onClick = viewModel::signOut,
                    enabled = !state.signingOut,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.signingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        Text(stringResource(R.string.account_sign_out))
                    }
                }
            }
        }
    }
}

/** The line between two rows of a group. */
@Composable
private fun RowLine() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/**
 * The unit body weights are shown and typed in, as the web's settings choose it: by where the phone
 * is, which is what an account that never chose goes by, kilograms or pounds. [chosen] is `kg`, `lb`,
 * or null for where the phone is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeightUnitChoice(chosen: String?, saving: Boolean, failed: Throwable?, onChoose: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                null to R.string.account_weight_unit_by_region,
                "kg" to R.string.account_weight_unit_kg,
                "lb" to R.string.account_weight_unit_lb,
            ).forEach { (unit, label) ->
                FilterChip(
                    selected = chosen == unit,
                    onClick = { onChoose(unit) },
                    label = { Text(stringResource(label)) },
                    enabled = !saving,
                )
            }
        }
        Text(
            stringResource(R.string.account_weight_unit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        failed?.let {
            Text(
                "${stringResource(R.string.account_weight_unit_failed)} ${errorMessage(it)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Leaving with changes made on the phone that wait to be sent: they are sent first, the session
 * ending once they are; or dropped; or the session is kept. Where they could not be sent now, it
 * says so.
 */
@Composable
private fun LeaveWithChangesDialog(asked: AccountViewModel.Asked, onSend: () -> Unit, onDrop: () -> Unit, onStay: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.account_leave_waiting, asked.count, asked.count))
                if (asked.notSent) Text(stringResource(R.string.account_leave_not_sent), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !asked.sending) {
                Text(stringResource(if (asked.sending) R.string.account_leave_sending else R.string.account_leave_send))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onStay, enabled = !asked.sending) { Text(stringResource(R.string.common_cancel)) }
                TextButton(onClick = onDrop, enabled = !asked.sending) {
                    Text(stringResource(R.string.account_leave_drop), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}
