// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

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
fun webPage(webApp: String, path: String, vararg query: Pair<String, String>): String = page(webApp, path, query).toString()

/**
 * The page of the web application at [webApp] that signs the browser in with [code] and then goes
 * on to [path] with [query]. Both are in the fragment, which a browser sends to no server.
 */
fun signedInWebPage(webApp: String, code: String, path: String, vararg query: Pair<String, String>): String {
    // The page to go on to, as the web application names its own pages: from its root.
    val next = page("http://web.invalid/", path, query).let { url -> url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "") }
    return webApp.toHttpUrl().newBuilder()
        .addPathSegments("from-the-app")
        .encodedFragment("code=${URLEncoder.encode(code, "UTF-8")}&next=${URLEncoder.encode(next, "UTF-8")}")
        .build()
        .toString()
}

private fun page(webApp: String, path: String, query: Array<out Pair<String, String>>): HttpUrl {
    val url = webApp.toHttpUrl().newBuilder().addPathSegments(path)
    query.forEach { (name, value) -> url.addQueryParameter(name, value) }
    return url.build()
}
