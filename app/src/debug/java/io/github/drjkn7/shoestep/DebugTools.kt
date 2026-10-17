package io.github.drjkn7.shoestep

import android.os.StrictMode

/** Debug build only: flags disk/network work on the main thread and leaked resources in logcat. */
object DebugTools {
    fun install() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
    }
}
