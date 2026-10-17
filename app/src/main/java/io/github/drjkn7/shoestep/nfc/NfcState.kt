package io.github.drjkn7.shoestep.nfc

import android.content.Context
import android.nfc.NfcAdapter

/** Whether this phone can read tags right now. */
enum class NfcState { UNSUPPORTED, OFF, ON }

fun deviceNfcState(context: Context): NfcState {
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcState.UNSUPPORTED
    return if (adapter.isEnabled) NfcState.ON else NfcState.OFF
}
