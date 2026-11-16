// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionEndStoresTest {
    @Test
    fun `every store of the phone is forgotten when a session ends, or holds nothing of an account`() {
        val stores = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" && "by preferencesDataStore(" in it.readText() }
            .map { it.nameWithoutExtension }
            .toSet()
        val end = File("src/main/java/net/whiteravens/dietapp/data/auth/SessionEnd.kt").readText()
        // The phone's own: the server it talks to, and the newest version of the app it was told of.
        val thePhones = setOf("ServerConfigStore", "UpdateStore")
        // Forgotten through what switches it.
        val through = mapOf("PhoneNotificationsStore" to "PhoneNotificationsSwitch")

        assertTrue("a store named here is gone: $stores", stores.containsAll(thePhones + through.keys))
        assertEquals(emptyList<String>(), (stores - thePhones).filterNot { (through[it] ?: it) in end }.sorted())
    }
}
