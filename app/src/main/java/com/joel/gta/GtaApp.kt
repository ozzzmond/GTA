package com.joel.gta

import android.app.Application
import com.joel.gta.data.logger.AppLogManager

/**
 * Application entry point for GTAR.
 * Initializes the global AppLogManager and uncaught exception handler as early as possible.
 */
class GtaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLogManager.init(this)
        AppLogManager.i("GtaApp", "Application process started: ${BuildConfig.APPLICATION_ID} (Build: ${BuildConfig.BUILD_TYPE})")
    }
}
