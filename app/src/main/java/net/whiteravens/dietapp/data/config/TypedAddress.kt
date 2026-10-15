// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What is wrong with an address typed for the server. */
enum class AddressProblem { NOT_AN_ADDRESS, PLAIN_HTTP, CREDENTIALS, QUERY_OR_FRAGMENT, PAST_THE_API }

/** An address typed for the server: the base of its API, or what is wrong with it. */
sealed interface TypedAddress {
    data class Base(val url: String) : TypedAddress

    data class Wrong(val problem: AddressProblem) : TypedAddress
}

/**
 * Reads an address typed for the server into the base of its API: `https://` where no scheme is
 * typed, `api` added where the path does not end in it, and a trailing slash, under which the
 * API's paths go. Refused: plain `http` where [plainHttp] is not allowed (a release build talks
 * HTTPS only), a user name or a password, which would be kept in plain storage and shown on the
 * screen, a query or a fragment, and a path that goes on past `api`.
 */
fun readAddress(typed: String, plainHttp: Boolean): TypedAddress {
    val trimmed = typed.trim()
    val url = (if ("://" in trimmed) trimmed else "https://$trimmed").takeIf { trimmed.isNotEmpty() }?.toHttpUrlOrNull()
        ?: return TypedAddress.Wrong(AddressProblem.NOT_AN_ADDRESS)
    val problem = when {
        url.scheme == "http" && !plainHttp -> AddressProblem.PLAIN_HTTP
        url.username.isNotEmpty() || url.password.isNotEmpty() -> AddressProblem.CREDENTIALS
        url.query != null || url.fragment != null -> AddressProblem.QUERY_OR_FRAGMENT
        else -> null
    }
    if (problem != null) return TypedAddress.Wrong(problem)

    val segments = url.pathSegments.filter { it.isNotEmpty() }
    val path = when (segments.indexOf("api")) {
        -1 -> segments + "api"
        segments.lastIndex -> segments
        else -> return TypedAddress.Wrong(AddressProblem.PAST_THE_API)
    }
    val base = url.newBuilder().encodedPath("/").apply { path.forEach { addPathSegment(it) } }.addPathSegment("").build()
    return TypedAddress.Base(base.toString())
}
