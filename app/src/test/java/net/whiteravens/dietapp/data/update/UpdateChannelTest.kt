// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A build is downloaded only from the channel's own addresses, whatever its version file names. */
class UpdateChannelTest {
    private val edge = UpdateChannel(
        "https://whiteravens20.github.io/diet-app-android/version.json",
        "https://whiteravens20.github.io/diet-app-android/",
    )
    private val releases = UpdateChannel(
        "https://github.com/whiteravens20/diet-app-android/releases/latest/download/version.json",
        "https://github.com/whiteravens20/diet-app-android/releases/",
    )

    @Test
    fun `a file named beside the version file is downloaded from beside it`() {
        assertEquals("https://whiteravens20.github.io/diet-app-android/diet-app-edge.apk", edge.addressOf("diet-app-edge.apk"))
        assertEquals(
            "https://github.com/whiteravens20/diet-app-android/releases/latest/download/diet-app-1.0.0.apk",
            releases.addressOf("diet-app-1.0.0.apk"),
        )
    }

    @Test
    fun `a file anywhere else is not`() {
        assertNull(edge.addressOf("https://example.com/diet-app-edge.apk"))
        assertNull(edge.addressOf("../other-project/app.apk"))
        assertNull(releases.addressOf("../../../../other-project/releases/download/1.0.0/app.apk"))
        assertNull(UpdateChannel("https://whiteravens20.github.io/diet-app-android/version.json", "").addressOf("diet-app-edge.apk"))
    }
}
