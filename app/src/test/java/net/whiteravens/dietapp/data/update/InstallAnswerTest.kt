// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallAnswerTest {
    @Test
    fun aQuestionIsShownOnlyForASessionTheApplicationIsWaitingOn() {
        assertEquals(InstallAnswer.ASK, installAnswer(PackageInstaller.STATUS_PENDING_USER_ACTION, 7, setOf(3, 7)))
        // What another application sends as the installer's question names a session that is not the application's.
        assertEquals(InstallAnswer.NOT_OURS, installAnswer(PackageInstaller.STATUS_PENDING_USER_ACTION, 9, setOf(3, 7)))
        assertEquals(InstallAnswer.NOT_OURS, installAnswer(PackageInstaller.STATUS_PENDING_USER_ACTION, -1, emptySet()))
    }

    @Test
    fun theEndOfASessionIsTakenAsTheSystemSaysIt() {
        assertEquals(InstallAnswer.DONE, installAnswer(PackageInstaller.STATUS_SUCCESS, 7, emptySet()))
        assertEquals(InstallAnswer.CANCELLED, installAnswer(PackageInstaller.STATUS_FAILURE_ABORTED, 7, emptySet()))
        assertEquals(InstallAnswer.FAILED, installAnswer(PackageInstaller.STATUS_FAILURE_INVALID, 7, setOf(7)))
    }
}
