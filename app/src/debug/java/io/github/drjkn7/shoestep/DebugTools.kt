package io.github.drjkn7.shoestep

import android.os.StrictMode
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.drjkn7.shoestep.ui.common.CardLabel
import io.github.drjkn7.shoestep.ui.common.InfoDialog
import io.github.drjkn7.shoestep.ui.common.LocalAppContainer
import io.github.drjkn7.shoestep.ui.common.SecondaryButton
import io.github.drjkn7.shoestep.ui.common.SilverCard
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import kotlinx.coroutines.launch

/** Debug build only: StrictMode, plus a Settings card for adding sample data. */
object DebugTools {
    /** Flags disk/network work on the main thread and leaked resources in logcat. */
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

    /** Last card in Settings, test app only: adds [SampleData]. English only; never shipped. */
    @Composable
    fun SettingsExtras() {
        val container = LocalAppContainer.current
        val scope = rememberCoroutineScope()
        var busy by rememberSaveable { mutableStateOf(false) }
        var message by rememberSaveable { mutableStateOf<String?>(null) }
        SilverCard {
            CardLabel("Test build")
            Text(
                "Only in the test app. Adds five sample pairs (one archived) with past sessions, and starts a session.",
                style = SilverTheme.type.body,
                color = SilverTheme.colors.textPrimary,
            )
            SecondaryButton(
                "Add sample pairs",
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        message = when (SampleData.add(container)) {
                            SampleData.Outcome.ADDED -> "Sample pairs added. Go back to Home to see them."
                            SampleData.Outcome.SESSION_ACTIVE -> "End the current session first, then try again."
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = SilverSpacing.s),
            )
        }
        message?.let { InfoDialog(title = null, text = it, onDismiss = { message = null }) }
    }
}
