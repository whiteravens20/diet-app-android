// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The address of the instance's web application, which serves the API under itself: the API's
 * base as [readAddress] keeps it (`…/api/`) without its last segment. Null where the base is no
 * address, as before a server is chosen.
 */
fun webAppAddress(apiBase: String): String? {
    val url = apiBase.toHttpUrlOrNull() ?: return null
    val segments = url.pathSegments.filter { it.isNotEmpty() }.let { if (it.lastOrNull() == "api") it.dropLast(1) else it }
    return url.newBuilder().encodedPath("/").apply { segments.forEach { addPathSegment(it) } }.addPathSegment("").build().toString()
}

/** A page of the web application at [webApp]: [path] under it, with [query] in the order given. */
fun webPage(webApp: String, path: String, vararg query: Pair<String, String>): String {
    val url = webApp.toHttpUrl().newBuilder().addPathSegments(path)
    query.forEach { (name, value) -> url.addQueryParameter(name, value) }
    return url.build().toString()
}
