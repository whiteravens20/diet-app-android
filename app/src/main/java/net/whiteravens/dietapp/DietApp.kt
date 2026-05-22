package net.whiteravens.dietapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point. Hilt builds the dependency graph from here. */
@HiltAndroidApp
class DietApp : Application()
