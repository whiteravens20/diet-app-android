// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import java.net.URI

/** An address of the web, and nothing else: only such a one is made a link. */
internal fun webAddress(address: String?): String? = address?.takeIf { WEB.containsMatchIn(it) }

/** The site an address is on, for a link that is read: `recipes.example`. */
internal fun siteOf(address: String): String = runCatching { URI(address).host }.getOrNull() ?: address

private val WEB = Regex("^https?://", RegexOption.IGNORE_CASE)
