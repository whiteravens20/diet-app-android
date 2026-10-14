// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import net.whiteravens.dietapp.BuildConfig
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.domain.ServerVersion
import retrofit2.HttpException
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
class ServerRepository @Inject constructor(private val api: AuthApiService) {
    private val _version = MutableStateFlow<ServerVersion>(ServerVersion.Unknown)
    val version: StateFlow<ServerVersion> = _version.asStateFlow()

    private val _checkSiteKey = MutableStateFlow<String?>(null)

    /** The site key of the anti-abuse check the server asks for, or null when it asks for none. */
    val checkSiteKey: StateFlow<String?> = _checkSiteKey.asStateFlow()

    suspend fun refresh() {
        _version.value = ask()
    }

    private suspend fun ask(): ServerVersion =
        try {
            val config = api.config()
            _checkSiteKey.value = config.turnstile.siteKey.takeIf { config.turnstile.enabled }
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
