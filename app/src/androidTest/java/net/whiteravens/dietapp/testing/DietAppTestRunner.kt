// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.os.Build
import androidx.test.runner.AndroidJUnitRunner
import net.whiteravens.dietapp.data.config.LOCAL_NETWORK_PERMISSION
import net.whiteravens.dietapp.data.config.LOCAL_NETWORK_PERMISSION_SINCE

/**
 * The runner of the screens' tests. On Android 17 and later it gives the app the permission to
 * reach the local network, where the test instance is (the emulator's host), as a person with a
 * server at home gives it at the first start. Each test runs in an instrumentation of its own,
 * after the app's data and permissions were cleared, so each is given it again.
 */
class DietAppTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SINCE) {
            uiAutomation.grantRuntimePermission(targetContext.packageName, LOCAL_NETWORK_PERMISSION)
        }
        super.onStart()
    }
}
