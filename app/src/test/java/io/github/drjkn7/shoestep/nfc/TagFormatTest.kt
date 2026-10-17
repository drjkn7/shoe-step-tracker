package io.github.drjkn7.shoestep.nfc

import io.github.drjkn7.shoestep.nfc.TagFormat.MIME_TYPE
import io.github.drjkn7.shoestep.nfc.TagFormat.TNF_EMPTY
import io.github.drjkn7.shoestep.nfc.TagFormat.TNF_MIME_MEDIA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T15: the parser accepts exactly our format and nothing else. */
class TagFormatTest {

    private val uuid = "3f2b8c1e-9a4d-4c2b-8e7f-1a2b3c4d5e6f"
    private val tnfWellKnown: Short = 1
    private val tnfExternal: Short = 4

    private fun ours(payload: String = uuid, type: String = MIME_TYPE) =
        RawRecord(TNF_MIME_MEDIA, type.toByteArray(Charsets.US_ASCII), payload.toByteArray(Charsets.US_ASCII))

    private val aar = RawRecord(tnfExternal, "android.com:pkg".toByteArray(), TagFormat.PACKAGE_NAME.toByteArray())
    private val text = RawRecord(tnfWellKnown, byteArrayOf('T'.code.toByte()), "\u0002enhello".toByteArray())

    private fun parse(vararg records: RawRecord, size: Int = 120) = TagFormat.parse(records.toList(), size)

    @Test fun ourRecordWithApplicationRecordIsRecognised() {
        assertEquals(TagContent.Ours(uuid), parse(ours(), aar))
        assertEquals(TagContent.Ours(uuid), parse(aar, ours()))
        assertEquals("MIME types are case-insensitive", TagContent.Ours(uuid), parse(ours(type = MIME_TYPE.uppercase())))
    }

    @Test fun emptyTagsAreBlank() {
        assertEquals(TagContent.Blank, TagFormat.parse(null, 0))
        assertEquals(TagContent.Blank, TagFormat.parse(emptyList(), 0))
        assertEquals(TagContent.Blank, parse(RawRecord(TNF_EMPTY, ByteArray(0), ByteArray(0)), size = 3))
    }

    @Test fun otherContentIsForeign() {
        assertEquals(TagContent.Foreign, parse(text))
        assertEquals(TagContent.Foreign, parse(aar))
        assertEquals(TagContent.Foreign, parse(ours(type = "application/vnd.someone.else")))
        assertEquals(TagContent.Foreign, parse(RawRecord(TNF_MIME_MEDIA, byteArrayOf(-61, -87), ByteArray(36))))
    }

    @Test fun brokenVersionsOfOurRecordAreMalformed() {
        val bad = listOf(
            uuid.uppercase(), // only lowercase is ever written
            uuid.dropLast(1),
            uuid + "0",
            "3f2b8c1e-9a4d-1c2b-8e7f-1a2b3c4d5e6f", // version 1, not random
            "3f2b8c1e-9a4d-4c2b-7e7f-1a2b3c4d5e6f", // wrong variant
            "3f2b8c1e+9a4d+4c2b+8e7f+1a2b3c4d5e6f",
            "../../../../etc/passwd/../../../../x",
            "",
        )
        bad.forEach { assertEquals("payload '$it'", TagContent.Malformed, parse(ours(payload = it))) }

        val nonAscii = ours().payload.copyOf().also { it[0] = 0x00 }
        assertEquals(TagContent.Malformed, parse(RawRecord(TNF_MIME_MEDIA, MIME_TYPE.toByteArray(), nonAscii)))
        assertEquals("two of our records", TagContent.Malformed, parse(ours(), ours()))
    }

    @Test fun oversizedOrCrowdedMessagesAreRejectedFirst() {
        assertEquals(TagContent.Malformed, parse(ours(), size = TagFormat.MAX_MESSAGE_BYTES + 1))
        assertEquals(TagContent.Ours(uuid), parse(ours(), size = TagFormat.MAX_MESSAGE_BYTES))
        val many = Array(TagFormat.MAX_RECORDS + 1) { text }
        assertEquals(TagContent.Malformed, parse(*many))
    }

    @Test fun newCodesAreRandomVersion4() {
        val codes = List(50) { TagFormat.newUuid() }
        assertTrue(codes.all(TagFormat::isValidUuid))
        assertEquals(50, codes.toSet().size)
        assertFalse(TagFormat.isValidUuid("not-a-uuid"))
    }
}
