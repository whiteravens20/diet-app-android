// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.unlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

class UnlockReachTest {
    @Test
    fun `what runs in the background never meets the gate, only the setting`() {
        val root = File("src/main/java/net/whiteravens/dietapp")
        // The notifications' worker and the water button, the queue of changes, and the session's own refreshing.
        val background = (
            listOf(File(root, "notify")) + listOf("data/sync", "data/auth").map { File(root, it) }
            ).flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }

        assertTrue(background.any { it.name == "NotificationsWorker.kt" } && background.any { it.name == "WaterReminderReceiver.kt" })
        assertEquals(emptyList<String>(), background.filter { "ui.unlock" in it.readText() || "UnlockGate" in it.readText() }.map { it.name })
    }
}
