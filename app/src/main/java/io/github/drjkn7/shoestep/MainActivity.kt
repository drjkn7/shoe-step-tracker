package io.github.drjkn7.shoestep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ShoeStepApp).container
        setContent {
            ShoeStepTheme {
                AppNavHost(container)
            }
        }
        // Step counts are filled in only while the app is visible (no background reads): once each
        // time it comes to the foreground, and again whenever a session starts, pauses, resumes or ends.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.sessionManager.activeSession
                    .map { it?.session?.id to it?.session?.state }
                    .distinctUntilChanged()
                    .collect { container.stepReconciler.reconcile() }
            }
        }
    }
}
