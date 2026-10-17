package io.github.drjkn7.shoestep.nfc

import android.content.Intent
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Turns Android NFC objects into [TappedTag]s. Everything read from a tag goes through [TagFormat.parse]. */
object AndroidTags {

    private val TAG_ACTIONS = setOf(
        NfcAdapter.ACTION_NDEF_DISCOVERED,
        NfcAdapter.ACTION_TECH_DISCOVERED,
        NfcAdapter.ACTION_TAG_DISCOVERED,
    )

    /** The tag in an NFC intent, or null for any other intent (or one without a tag). */
    fun fromIntent(intent: Intent?): TappedTag? {
        if (intent?.action !in TAG_ACTIONS) return null
        val tag = IntentCompat.getParcelableExtra(intent!!, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return null
        return fromTag(tag)
    }

    fun fromTag(tag: Tag): TappedTag {
        val ndef = Ndef.get(tag)
        val formatable = if (ndef == null) NdefFormatable.get(tag) else null
        val content = when {
            ndef != null -> parse(ndef.cachedNdefMessage)
            formatable != null -> TagContent.Blank
            else -> TagContent.Unsupported
        }
        val handle = if (ndef != null || formatable != null) AndroidTagHandle(tag) else null
        return TappedTag(key = keyFor(tag, content), content = content, handle = handle)
    }

    internal fun parse(message: NdefMessage?): TagContent =
        if (message == null) {
            TagFormat.parse(null, 0)
        } else {
            TagFormat.parse(message.records.map { RawRecord(it.tnf, it.type, it.payload) }, message.byteArrayLength)
        }

    /**
     * The repeat-guard key is the tag's hardware id, so a tag that was just linked (blank before,
     * ours after) is still the same key and can't start a session while the phone rests on it.
     */
    private fun keyFor(tag: Tag, content: TagContent): String {
        val id = tag.id
        if (id != null && id.isNotEmpty()) return id.joinToString("") { "%02x".format(it) }
        return (content as? TagContent.Ours)?.uuid ?: "no-id"
    }

    fun message(uuid: String): NdefMessage = NdefMessage(
        NdefRecord.createMime(TagFormat.MIME_TYPE, uuid.toByteArray(Charsets.US_ASCII)),
        // Android Application Record: a tap opens this app even when it is closed.
        NdefRecord.createApplicationRecord(TagFormat.PACKAGE_NAME),
    )
}

/** Writes, reads back and optionally locks a real tag. Nothing about the tag is logged. */
class AndroidTagHandle(private val tag: Tag) : TagHandle {

    override suspend fun write(uuid: String, lock: Boolean): WriteResult = withContext(Dispatchers.IO) {
        val message = AndroidTags.message(uuid)
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) writeNdef(ndef, message, uuid, lock) else formatBlank(message, lock)
        } catch (_: FormatException) {
            WriteResult.VERIFY_FAILED
        } catch (_: IOException) {
            WriteResult.LOST // includes TagLostException
        } catch (_: SecurityException) {
            WriteResult.LOST // the tag object is out of date (tag removed and tapped again)
        } catch (_: IllegalStateException) {
            WriteResult.LOST
        }
    }

    private fun writeNdef(ndef: Ndef, message: NdefMessage, uuid: String, lock: Boolean): WriteResult = ndef.use {
        it.connect()
        if (!it.isWritable) return WriteResult.READ_ONLY
        if (it.maxSize < message.byteArrayLength) return WriteResult.TOO_SMALL
        it.writeNdefMessage(message)
        // T04: read the tag back and check it really holds what we wrote.
        if (AndroidTags.parse(it.ndefMessage) != TagContent.Ours(uuid)) return WriteResult.VERIFY_FAILED
        if (lock && !(it.canMakeReadOnly() && it.makeReadOnly())) return WriteResult.LOCK_FAILED
        WriteResult.OK
    }

    /**
     * A tag that was never formatted. After formatting, this Tag object can't be read again until
     * the next tap, so success of format() is the check here.
     */
    private fun formatBlank(message: NdefMessage, lock: Boolean): WriteResult {
        val formatable = NdefFormatable.get(tag) ?: return WriteResult.LOST
        formatable.use {
            it.connect()
            if (lock) it.formatReadOnly(message) else it.format(message)
        }
        return WriteResult.OK
    }
}
