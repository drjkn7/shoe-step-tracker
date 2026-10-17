package io.github.drjkn7.shoestep.nfc

import java.util.UUID

/** One NDEF record as plain bytes, so parsing can be unit-tested without Android classes. */
class RawRecord(val tnf: Short, val type: ByteArray, val payload: ByteArray)

/** What is on a tag, after strict parsing. Tags are untrusted input (SEC-07): never trust, never log. */
sealed interface TagContent {
    /** One of our tags: exactly one record of [TagFormat.MIME_TYPE] holding a random UUID. */
    data class Ours(val uuid: String) : TagContent

    /** No NDEF data yet (new or erased tag). */
    data object Blank : TagContent

    /** Valid NDEF data from something else (a URL, text, another app). */
    data object Foreign : TagContent

    /** Claims to be ours but breaks a rule, or is too big: ignored safely. */
    data object Malformed : TagContent

    /** Not an NDEF tag at all (e.g. a bank card): can't be read or written. */
    data object Unsupported : TagContent
}

/**
 * The on-tag format: an NDEF message with one MIME record of [MIME_TYPE] whose payload is a random
 * (version 4) UUID in lowercase ASCII, plus an Android Application Record so a tap opens this app.
 */
object TagFormat {
    const val MIME_TYPE = "application/vnd.io.github.drjkn7.shoestep"
    const val PACKAGE_NAME = "io.github.drjkn7.shoestep"

    /** NdefRecord TNF values (copied so this file needs no Android classes). */
    const val TNF_EMPTY: Short = 0
    const val TNF_MIME_MEDIA: Short = 2

    /** Messages bigger than this are rejected before looking inside (NTAG216 holds 868 bytes). */
    const val MAX_MESSAGE_BYTES = 1_024
    const val MAX_RECORDS = 8

    private const val UUID_LENGTH = 36
    private val UUID_V4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    fun newUuid(): String = UUID.randomUUID().toString()

    fun isValidUuid(value: String): Boolean = value.length == UUID_LENGTH && UUID_V4.matches(value)

    /**
     * Strict parser. [records] null = the tag has no NDEF message; [messageBytes] is the size the
     * tag reported, checked before anything else.
     */
    fun parse(records: List<RawRecord>?, messageBytes: Int): TagContent {
        if (messageBytes > MAX_MESSAGE_BYTES) return TagContent.Malformed
        if (records == null || records.isEmpty()) return TagContent.Blank
        if (records.size > MAX_RECORDS) return TagContent.Malformed
        if (records.all { it.tnf == TNF_EMPTY }) return TagContent.Blank

        val ours = records.filter { it.tnf == TNF_MIME_MEDIA && isOurType(it.type) }
        if (ours.isEmpty()) return TagContent.Foreign
        if (ours.size > 1) return TagContent.Malformed

        val payload = ours.single().payload
        if (payload.size != UUID_LENGTH || payload.any { it < 0x20 || it > 0x7e }) return TagContent.Malformed
        val uuid = String(payload, Charsets.US_ASCII)
        return if (isValidUuid(uuid)) TagContent.Ours(uuid) else TagContent.Malformed
    }

    /** MIME types are case-insensitive; anything that isn't plain ASCII can't be ours. */
    private fun isOurType(type: ByteArray): Boolean =
        type.size == MIME_TYPE.length &&
            type.all { it in 0x20..0x7e } &&
            String(type, Charsets.US_ASCII).equals(MIME_TYPE, ignoreCase = true)
}
