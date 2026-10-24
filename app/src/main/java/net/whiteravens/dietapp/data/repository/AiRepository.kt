// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.AiProviderConfigDto
import net.whiteravens.dietapp.data.remote.AiProviderConfigRequest
import net.whiteravens.dietapp.data.remote.AiQuotaStatusDto
import net.whiteravens.dietapp.data.remote.AiTestConnectionRequest
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.ModelApiService
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.AiProviderConfig
import net.whiteravens.dietapp.domain.AiStatus
import net.whiteravens.dietapp.domain.ConnectionTest
import net.whiteravens.dietapp.domain.ProviderSetup
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What AI the account has, and the providers it brings of its own: listed without their keys, set
 * up, tested before they are kept, and removed. Network-only, as the account's other settings are.
 */
@Singleton
class AiRepository @Inject constructor(
    private val api: DietApiService,
    private val model: ModelApiService,
    private val json: Json,
) {
    /** What AI the account has now: the mode it chose, and what is left of the instance's allowance. */
    suspend fun status(): AiStatus = apiCall(json) { api.aiQuota() }.toDomain()

    /** The providers the account set up, the one asked first first. */
    suspend fun providers(): List<AiProviderConfig> = apiCall(json) { api.aiProviders() }.map { it.toDomain() }

    /** Saves the account's configuration of the provider [setup] names: made the first time, the one it has replaced afterwards. */
    suspend fun save(setup: ProviderSetup): AiProviderConfig =
        apiCall(json) {
            api.saveAiProvider(AiProviderConfigRequest(setup.provider, setup.model, setup.apiKey, setup.baseUrl, setup.priority, enabled = true))
        }.toDomain()

    /** Removes the provider [id] and its key. Where it was the account's last, the API gives the account the instance's provider in the place of its own. */
    suspend fun remove(id: String) = apiCall(json) { api.removeAiProvider(id) }

    /** Asks the provider of [setup] whether it answers to its key or at its address, and which models it has; nothing is kept. */
    suspend fun test(setup: ProviderSetup): ConnectionTest =
        apiCall(json) { model.testAiConnection(AiTestConnectionRequest(setup.provider, setup.apiKey, setup.baseUrl, setup.model.ifEmpty { null })) }
            .let { ConnectionTest(it.ok, it.models, it.error) }
}

private fun AiQuotaStatusDto.toDomain() = AiStatus(
    mode = mode,
    limit = limit,
    used = used,
    remaining = remaining,
    resetAt = resetAt?.let(Instant::parse),
    instanceLimitReached = instanceLimitReached,
    adminProviderConfigured = adminProviderConfigured,
    adminAccess = adminAccess,
    newUntil = newUntil?.let(Instant::parse),
    newAccountsLimitReached = newAccountsLimitReached,
)

private fun AiProviderConfigDto.toDomain() = AiProviderConfig(
    id = id,
    provider = provider,
    model = model,
    priority = priority,
    enabled = enabled,
    hasKey = hasKey,
    keyUnreadable = keyUnreadable,
    baseUrl = baseUrl,
)
