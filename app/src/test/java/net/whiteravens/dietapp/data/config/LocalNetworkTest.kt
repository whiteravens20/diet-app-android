// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which servers are on the local network, as Android 17 tells them apart. */
class LocalNetworkTest {
    @Test
    fun `private, link-local and unique local addresses and local names are on the local network`() {
        for (host in listOf("192.168.1.20", "10.0.2.2", "172.16.0.5", "172.31.255.1", "169.254.10.1", "fd12:3456::1", "fe80::1", "nas.local")) {
            assertTrue(host, isLocalHost(host))
        }
    }

    @Test
    fun `addresses on the internet and the phone itself are not`() {
        for (host in listOf("8.8.8.8", "172.32.0.1", "100.64.0.1", "2001:db8::1", "127.0.0.1", "::1")) {
            assertFalse(host, isLocalHost(host))
        }
    }

    @Test
    fun `a name that resolves to nothing is not on the local network`() {
        assertFalse(isLocalHost("no-such-server.invalid"))
    }
}
