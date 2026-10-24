// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import net.whiteravens.dietapp.BuildConfig
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.HealthDto
import net.whiteravens.dietapp.data.remote.ServerAddress
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.ServerProbe
import net.whiteravens.dietapp.domain.ServerVersion
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the server the app talks to tells anybody, asked with `GET /config`: its version, and
 * the anti-abuse check it asks of a sign-up and a sign-in. A version this build was not checked
 * against (BuildConfig.API_VERSION, from contract/API_VERSION) is named on the screen, and an
 * answer the app cannot read, or a server without that route, is a version it does not support.
 * No answer at all says nothing about the version.
 */
@Singleton
class ServerRepository @Inject constructor(private val api: AuthApiService, private val json: Json) {
    private val _version = MutableStateFlow<ServerVersion>(ServerVersion.Unknown)
    val version: StateFlow<ServerVersion> = _version.asStateFlow()

    private val _checkSiteKey = MutableStateFlow<String?>(null)

    /** The site key of the anti-abuse check the server asks for, or null when it asks for none. */
    val checkSiteKey: StateFlow<String?> = _checkSiteKey.asStateFlow()

    private val _rules = MutableStateFlow<InstanceRules?>(null)

    /** What the server allows an account and what its catalogue holds; null until it has said. */
    val rules: StateFlow<InstanceRules?> = _rules.asStateFlow()

    suspend fun refresh() {
        _rules.value = null
        _version.value = ask()
    }

    /**
     * What answers at [baseUrl], an address typed for the server, with `GET /health`: asked before
     * the address is kept, so that a wrong one is named at once. Its version is asked with
     * `GET /config` once it is kept, as for every server.
     */
    suspend fun probe(baseUrl: String): ServerProbe =
        try {
            api.health(ServerAddress(baseUrl))
            ServerProbe.Answers
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            // The API answers 503 with its health when its database is down; anything else is not the API's.
            val health = e.response()?.errorBody()?.string()?.let { body ->
                try {
                    json.decodeFromString<HealthDto>(body)
                } catch (e: SerializationException) {
                    null
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            if (e.code() == 503 && health?.db == "down") ServerProbe.DatabaseDown else ServerProbe.NotTheApi
        } catch (e: SerializationException) {
            ServerProbe.NotTheApi
        } catch (e: UnknownHostException) {
            ServerProbe.UnknownHost
        } catch (e: SSLException) {
            ServerProbe.Untrusted
        } catch (e: IOException) {
            ServerProbe.NoAnswer
        }

    private suspend fun ask(): ServerVersion =
        try {
            val config = api.config()
            _checkSiteKey.value = config.turnstile.siteKey.takeIf { config.turnstile.enabled }
            _rules.value = InstanceRules(
                maxProfiles = config.accounts.maxProfiles,
                mailsSent = config.email.enabled,
                maxRepeatsPerRecipe = config.plans.maxRepeatsPerRecipe,
                operatorContact = config.instance.operatorContact,
                catalogueDepth = config.catalogue.depth.mapValues { (_, byCount) ->
                    byCount.mapNotNull { (count, recipes) -> count.toIntOrNull()?.let { it to recipes } }.toMap()
                },
            )
            val version = config.instance.version
            if (version == BuildConfig.API_VERSION) {
                ServerVersion.Checked
            } else {
                ServerVersion.Other(server = version, checkedAgainst = BuildConfig.API_VERSION)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            ServerVersion.Unsupported
        } catch (e: HttpException) {
            if (e.code() == 404) ServerVersion.Unsupported else ServerVersion.Unknown
        } catch (e: Exception) {
            ServerVersion.Unknown
        }
}
