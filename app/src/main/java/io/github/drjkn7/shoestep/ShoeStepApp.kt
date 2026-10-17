package io.github.drjkn7.shoestep

import android.app.Application

class ShoeStepApp : Application() {

    /** Created on first use, not at start-up. */
    val container: AppContainer by lazy { AppContainer() }

    override fun onCreate() {
        super.onCreate()
        // StrictMode in debug builds; a no-op in release (see src/debug and src/release).
        DebugTools.install()
    }
}
