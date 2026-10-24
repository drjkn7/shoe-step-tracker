package io.github.drjkn7.shoestep

import androidx.compose.runtime.Composable

/** Release build: debug tooling is intentionally absent. */
object DebugTools {
    fun install() = Unit

    /** Nothing: the sample-data card exists only in the test app. */
    @Composable
    fun SettingsExtras() = Unit
}
