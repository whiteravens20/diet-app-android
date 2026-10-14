// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.remote.ClientHeaders

/** The headers every request carries, from a device whose texts are in English. */
fun clientHeaders(): ClientHeaders {
    val context = mockk<Context>()
    every { context.getString(R.string.api_language) } returns "en"
    return ClientHeaders(context)
}
