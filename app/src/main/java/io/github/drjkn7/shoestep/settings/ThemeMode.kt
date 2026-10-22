package io.github.drjkn7.shoestep.settings

import android.app.UiModeManager
import android.content.Context
import android.os.Build

/** Settings → Appearance → Theme. SYSTEM (the default) follows the phone's dark theme setting. */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    /** Whether the app is dark, given whether the phone currently is. */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemIsDark
    }

    companion object {
        /** Reads a stored value; anything missing or unexpected in the file counts as SYSTEM. */
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Tells Android which theme the app uses, so the window and the launch screen match it before the app
 * draws anything (Android 12+, `UiModeManager.setApplicationNightMode`, which Android keeps until the
 * app changes it or its data is cleared). Below Android 12 there is no such call: the launch window
 * follows the phone for a moment, then the app draws in the chosen theme.
 *
 * Call it only when the user changes the setting: Android applies it as a configuration change and
 * recreates the screen.
 */
fun interface SystemNightMode {
    fun apply(mode: ThemeMode)
}

class AndroidSystemNightMode(private val context: Context) : SystemNightMode {
    override fun apply(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = context.getSystemService(UiModeManager::class.java) ?: return
        uiModeManager.setApplicationNightMode(
            when (mode) {
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            },
        )
    }
}
