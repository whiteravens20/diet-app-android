package net.whiteravens.dietapp.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.BuildConfig
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.components.AccentChip
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import java.util.Locale

/** Account overview + sign-out. Settings editing stays on the web for now. */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.nav_profile))

        state.error?.let {
            ErrorBanner(errorMessage(it))
            Button(onClick = viewModel::load) { Text(stringResource(R.string.common_retry)) }
        }

        when {
            state.loading -> {
                Skeleton(Modifier.fillMaxWidth().height(96.dp))
                Skeleton(Modifier.fillMaxWidth().height(120.dp))
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
                                    AccentChip(stringResource(R.string.profile_verified))
                                } else {
                                    LabelChip(stringResource(R.string.profile_unverified))
                                }
                            }
                        }
                    }
                }

                AppCard {
                    SettingRow(
                        stringResource(R.string.profile_language),
                        Locale.forLanguageTag(user.locale).let { locale ->
                            locale.getDisplayLanguage(locale)
                                .replaceFirstChar { it.titlecase(locale) }
                                .ifBlank { user.locale }
                        },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    SettingRow(
                        stringResource(R.string.profile_theme),
                        when (user.theme) {
                            "light" -> stringResource(R.string.profile_theme_light)
                            "dark" -> stringResource(R.string.profile_theme_dark)
                            else -> stringResource(R.string.profile_theme_system)
                        },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    SettingRow(stringResource(R.string.profile_app_version), BuildConfig.VERSION_NAME)
                    Text(
                        stringResource(R.string.profile_settings_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

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
                Text(stringResource(R.string.profile_sign_out))
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
