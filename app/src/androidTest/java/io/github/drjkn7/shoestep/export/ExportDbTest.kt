package io.github.drjkn7.shoestep.export

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.data.model.StepsStatus
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The export straight from a real (in-memory) database: both files, BOM, archived pairs, T23. */
@RunWith(AndroidJUnit4::class)
class ExportDbTest {

    private lateinit var db: ShoeStepDatabase
    private lateinit var exporter: CsvExportRepository

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = ShoeStepDatabase.buildInMemory(context)
        exporter = CsvExportRepository(db, context)
        fun shoe(id: String, name: String, archived: Boolean) = ShoeEntity(
            id = id, tagUuid = null, name = name, brand = "", model = "", type = ShoeType.HIKING, priceMinor = 9_999,
            currencyCode = "EUR", purchaseEpochDay = null, accentColor = 4, archived = archived, createdAtUtc = 0,
        )
        db.shoeDao().insert(shoe("a", "=cmd|' /C calc'!A0", archived = false))
        db.shoeDao().insert(shoe("b", "Old boots", archived = true))
        db.sessionDao().insertSession(SessionEntity("s1", "a", 0, 1_800_000, "UTC", SessionState.ENDED, EndReason.USER))
        db.sessionDao().insertSegment(SegmentEntity("g1", "s1", 0, 1_800_000, 2_345, StepsStatus.FINAL, 1))
    }

    @After fun tearDown() = db.close()

    private suspend fun export(kind: ExportKind): String {
        val out = ByteArrayOutputStream()
        exporter.writeTo(kind, out)
        val bytes = out.toByteArray()
        assertArrayEquals("UTF-8 BOM first", Csv.UTF8_BOM, bytes.copyOfRange(0, 3))
        // Straight after the BOM comes a plain letter, never a quote (Google Sheets showed the quotes).
        assertTrue("first header cell is unquoted", bytes[3] != '"'.code.toByte())
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }

    @Test fun pairsFileIncludesArchivedPairsAndNeutralisesFormulas() = runTest {
        val lines = export(ExportKind.PAIRS).split("\r\n")
        assertEquals(4, lines.size) // header, 2 pairs, final empty line
        assertTrue(lines[1].startsWith("\"'=cmd|' /C calc'!A0\",\"\",\"\",\"Hiking\",\"99.99\",\"EUR\",,\"Teal\",\"no\",1,2345,\"final\",30,"))
        assertTrue(lines[2].startsWith("\"Old boots\""))
        assertTrue(lines[2].contains("\"yes\",0,0,\"final\",0,"))
    }

    @Test fun sessionsFileHasOneRowPerSession() = runTest {
        val lines = export(ExportKind.SESSIONS).split("\r\n")
        assertEquals(
            "\"'=cmd|' /C calc'!A0\",\"1970-01-01 00:00\",\"1970-01-01 00:30\",\"UTC\",\"ended\",30,2345,\"final\"",
            lines[1],
        )
        assertEquals(3, lines.size)
    }

    @Test fun suggestedFileNamesCarryTheDate() {
        val day = LocalDate.of(2026, 10, 18)
        assertEquals("shoe-step-pairs-2026-10-18.csv", exporter.fileName(ExportKind.PAIRS, day))
        assertEquals("shoe-step-sessions-2026-10-18.csv", exporter.fileName(ExportKind.SESSIONS, day))
    }
}
