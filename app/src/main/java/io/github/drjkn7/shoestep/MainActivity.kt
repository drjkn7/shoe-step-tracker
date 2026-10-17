package io.github.drjkn7.shoestep

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.drjkn7.shoestep.nfc.AndroidTags
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as ShoeStepApp).container
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = container
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
        // A tag tap that launched the app; not again when the activity is recreated (e.g. rotation).
        if (savedInstanceState == null) handleTagIntent(intent)
    }

    /** Tag taps while the app is open (foreground dispatch below), or that reopened it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTagIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // While visible, every tag comes here (no duplicate screens, T07). Foreground dispatch,
        // unlike reader mode, leaves tap-to-pay working while the app is open.
        val tagIntent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // The system adds the tag to this intent, so it must be mutable (the flag exists from Android 12;
        // before that every PendingIntent was mutable).
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getActivity(this, 0, tagIntent, mutable)
        nfcAdapter?.enableForegroundDispatch(this, pending, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    private fun handleTagIntent(intent: Intent?) {
        val tapped = AndroidTags.fromIntent(intent) ?: return
        lifecycleScope.launch { container.tagCoordinator.onTag(tapped) }
    }
}
