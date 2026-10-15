// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import org.junit.Assert.assertEquals
import org.junit.Test

/** An address as a person types it for the server, and the base of the API the app keeps. */
class TypedAddressTest {
    private fun release(typed: String) = readAddress(typed, plainHttp = false)

    private fun debug(typed: String) = readAddress(typed, plainHttp = true)

    private fun base(url: String) = TypedAddress.Base(url)

    private fun wrong(problem: AddressProblem) = TypedAddress.Wrong(problem)

    @Test
    fun `a server's name or address becomes the base of its API`() {
        assertEquals(base("https://diet.example.org/api/"), release("diet.example.org"))
        assertEquals(base("https://diet.example.org/api/"), release("  https://diet.example.org  "))
        assertEquals(base("https://diet.example.org/api/"), release("https://diet.example.org/"))
        assertEquals(base("https://diet.example.org/api/"), release("https://diet.example.org/api"))
        assertEquals(base("https://diet.example.org/api/"), release("https://diet.example.org/api/"))
        assertEquals(base("https://example.org/diet/api/"), release("https://example.org/diet"))
        assertEquals(base("https://diet.example.org:8443/api/"), release("diet.example.org:8443"))
    }

    @Test
    fun `plain http is taken by a debug build alone`() {
        assertEquals(wrong(AddressProblem.PLAIN_HTTP), release("http://192.168.1.20:4000"))
        assertEquals(base("http://192.168.1.20:4000/api/"), debug("http://192.168.1.20:4000"))
    }

    @Test
    fun `an address with a user name or a password in it is refused`() {
        assertEquals(wrong(AddressProblem.CREDENTIALS), release("https://ala:secret@diet.example.org"))
        assertEquals(wrong(AddressProblem.CREDENTIALS), release("https://ala@diet.example.org/api/"))
    }

    @Test
    fun `an address with a query, a fragment or a path past the API is refused`() {
        assertEquals(wrong(AddressProblem.QUERY_OR_FRAGMENT), release("https://diet.example.org/?lang=pl"))
        assertEquals(wrong(AddressProblem.QUERY_OR_FRAGMENT), release("https://diet.example.org/#top"))
        assertEquals(wrong(AddressProblem.PAST_THE_API), release("https://diet.example.org/api/auth/login"))
    }

    @Test
    fun `what is no address of a server is named as such`() {
        assertEquals(wrong(AddressProblem.NOT_AN_ADDRESS), release(""))
        assertEquals(wrong(AddressProblem.NOT_AN_ADDRESS), release("   "))
        assertEquals(wrong(AddressProblem.NOT_AN_ADDRESS), release("not an address"))
        assertEquals(wrong(AddressProblem.NOT_AN_ADDRESS), release("ftp://diet.example.org"))
    }
}
