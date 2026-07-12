package net.whiteravens.dietapp.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ServerUrlDialog

/**
 * Login / registration — the mobile twin of the web `AuthForm`: centered
 * brand mark + card. Switching between the two modes stays inside this
 * composable; a successful submit flips the root auth state instead.
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var showServerDialog by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()

    if (showServerDialog) {
        ServerUrlDialog(
            current = serverUrl,
            onSave = viewModel::saveServerUrl,
            onDismiss = { showServerDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
        ) {
            Icon(
                AppIcons.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )

        AppCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (registerMode) R.string.auth_create_account else R.string.auth_welcome_back),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            if (registerMode) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.auth_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            if (registerMode) {
                Text(
                    stringResource(R.string.auth_password_rules),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            state.error?.let { ErrorBanner(errorMessage(it), Modifier.padding(top = 12.dp)) }
            Button(
                onClick = {
                    if (registerMode) viewModel.register(email, password, displayName)
                    else viewModel.signIn(email, password)
                },
                enabled = !state.busy && email.isNotBlank() && password.isNotBlank() &&
                    (!registerMode || displayName.isNotBlank()),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        stringResource(
                            if (registerMode) R.string.auth_create_account_button
                            else R.string.auth_sign_in_button,
                        ),
                    )
                }
            }
        }

        TextButton(onClick = { registerMode = !registerMode }, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                stringResource(
                    if (registerMode) R.string.auth_has_account else R.string.auth_no_account,
                ),
            )
        }
        TextButton(onClick = { showServerDialog = true }) {
            Text(
                stringResource(R.string.server_label, serverUrl),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
