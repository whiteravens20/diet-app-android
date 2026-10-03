// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point. Hilt builds the dependency graph from here. */
@HiltAndroidApp
class DietApp : Application()
