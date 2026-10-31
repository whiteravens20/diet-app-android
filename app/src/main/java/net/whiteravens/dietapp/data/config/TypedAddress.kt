// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What is wrong with an address typed for the server. */
enum class AddressProblem { NOT_AN_ADDRESS, PLAIN_HTTP, CREDENTIALS }

/** An address typed for the server: the base of its API, or what is wrong with it. */
sealed interface TypedAddress {
    data class Base(val url: String) : TypedAddress

    data class Wrong(val problem: AddressProblem) : TypedAddress
}

/** A scheme at the start of what is typed. */
private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/**
 * Reads an address typed for the server into the base of its API. A person types the server's name,
 * with its port where it has one: `https://` goes before it where no scheme is typed, and the base
 * is `/api/` under it whatever follows the name, so that an address pasted from the browser, of any
 * page of the web application, is the server's too. Refused: plain `http` where [plainHttp] is not
 * allowed (a release build talks HTTPS only), and a user name or a password, which would be kept in
 * plain storage and shown on the screen.
 */
fun readAddress(typed: String, plainHttp: Boolean): TypedAddress {
    val trimmed = typed.trim()
    val url = (if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed").takeIf { trimmed.isNotEmpty() }?.toHttpUrlOrNull()
        ?: return TypedAddress.Wrong(AddressProblem.NOT_AN_ADDRESS)
    val problem = when {
        url.scheme == "http" && !plainHttp -> AddressProblem.PLAIN_HTTP
        url.username.isNotEmpty() || url.password.isNotEmpty() -> AddressProblem.CREDENTIALS
        else -> null
    }
    if (problem != null) return TypedAddress.Wrong(problem)
    return TypedAddress.Base(url.newBuilder().encodedPath("/api/").query(null).fragment(null).build().toString())
}

/**
 * The server's address as a person reads and types it, from the base of its API: its name, with the
 * port where it is not its scheme's own, and the scheme only where it is not `https`. Nothing where
 * [base] is no address, as before a server is chosen.
 */
fun serverAddress(base: String): String {
    val url = base.toHttpUrlOrNull() ?: return ""
    val host = if (':' in url.host) "[${url.host}]" else url.host
    val name = if (url.port == HttpUrl.defaultPort(url.scheme)) host else "$host:${url.port}"
    return if (url.scheme == "https") name else "${url.scheme}://$name"
}
