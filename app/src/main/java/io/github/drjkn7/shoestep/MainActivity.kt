package io.github.drjkn7.shoestep

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.drjkn7.shoestep.nfc.AndroidTags
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
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
            ShoeStepTheme(container.settings) {
                SystemBarsFollowTheme()
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
        // While visible, every NDEF tag comes here (no duplicate screens, T07). Foreground dispatch,
        // unlike reader mode, leaves tap-to-pay working while the app is open. It targets the same
        // entry alias as a tap with the app closed, so both paths are handled the same way.
        val tagIntent = Intent().setClassName(this, tagEntry()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // The system adds the tag to this intent, so it must be mutable (the flag exists from Android 12;
        // before that every PendingIntent was mutable).
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getActivity(this, 0, tagIntent, mutable)
        // Only NDEF-capable tags (ours, blank or other NDEF content); bank cards and the like are skipped.
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply { addDataType("*/*") },
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
        )
        val techLists = arrayOf(arrayOf(Ndef::class.java.name), arrayOf(NdefFormatable::class.java.name))
        nfcAdapter?.enableForegroundDispatch(this, pending, filters, techLists)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    /**
     * Acts only on tag intents that came through our NFC entry alias. On Android 17+ that alias is
     * guarded by DISPATCH_NFC_MESSAGE, so another app can't fake a tap by sending MainActivity an
     * NFC intent (it would arrive without the alias as its component and be ignored).
     */
    private fun handleTagIntent(intent: Intent?) {
        if (intent?.component?.className != tagEntry()) return
        val tapped = AndroidTags.fromIntent(intent) ?: return
        lifecycleScope.launch { container.tagCoordinator.onTag(tapped) }
    }

    /**
     * Status and navigation bar icons dark on the light theme and light on the night theme. Follows the
     * app's theme, which can differ from the phone's (Settings → Appearance).
     */
    @Composable
    private fun SystemBarsFollowTheme() {
        val night = SilverTheme.colors.isNight
        DisposableEffect(night) {
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { night }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose {}
        }
    }

    /**
     * The NFC entry alias enabled on this Android version (see AndroidManifest.xml and values-v37).
     * Alias names come from the code's package, not the application id (which ends in ".debug" in
     * debug builds); MainActivity's name is kept by R8 because the manifest refers to it.
     */
    private fun tagEntry(): String {
        val codePackage = MainActivity::class.java.name.substringBeforeLast('.')
        return if (resources.getBoolean(R.bool.nfc_entry_protected)) "$codePackage.NfcTagEntry" else "$codePackage.NfcTagEntryLegacy"
    }
}
