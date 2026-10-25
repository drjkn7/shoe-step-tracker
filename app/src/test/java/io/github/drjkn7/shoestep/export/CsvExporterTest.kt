package io.github.drjkn7.shoestep.export

import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.db.SessionExportRow
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {

    private val t0 = Instant.parse("2026-10-17T10:00:00Z").toEpochMilli()

    private fun shoe(
        name: String = "Daily trainers",
        price: Long? = 12_000,
        currency: String = "EUR",
        archived: Boolean = false,
    ) = ShoeEntity(
        id = "s1", tagUuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", name = name, brand = "Brand", model = "Model",
        type = ShoeType.WALKING, priceMinor = price, currencyCode = currency,
        purchaseEpochDay = LocalDate.of(2026, 3, 5).toEpochDay(), accentColor = 2, archived = archived, createdAtUtc = 0,
    )

    private fun stats(
        shoe: ShoeEntity,
        pending: Boolean = false,
        unfinal: Boolean = false,
        lastUsed: Long? = t0,
        meters: Double? = 1_234.5,
        distanceUnfinal: Boolean = false,
        distancePending: Boolean = false,
    ) = ShoeWithStats(shoe, 1_500, meters, 2, 2, 95 * 60_000L, lastUsed, false, unfinal, pending, distanceUnfinal, distancePending)

    private fun pairs(vararg rows: ShoeWithStats) = CsvExporter.pairs(
        rows.toList(),
        typeLabel = { "Walking" },
        colourLabel = { "Amber" },
        zone = ZoneId.of("Europe/Berlin"),
    ).split("\r\n")

    @Test fun pairsHaveAHeaderAndOneRowEach() {
        val lines = pairs(stats(shoe()))
        // Header unquoted: Google Sheets showed a quoted first header (after a BOM) with stray characters.
        assertEquals(
            "Name,Brand,Model,Type,Price,Currency,Purchase date,Colour,Archived," +
                "Sessions,Total steps,Steps status,Walking minutes,Last worn,Total distance in metres,Distance status",
            lines[0],
        )
        // Distance in whole metres, always metric (T38): 1,234.5 m → 1235.
        assertEquals(
            "\"Daily trainers\",\"Brand\",\"Model\",\"Walking\",\"120.00\",\"EUR\",\"2026-03-05\",\"Amber\",\"no\"," +
                "2,1500,\"final\",95,\"2026-10-17 12:00\",1235,\"final\"",
            lines[1],
        )
        assertEquals("ends with a line break", "", lines.last())
    }

    @Test fun pricesUseTheCurrencysDecimalsAndBlanksStayBlank() {
        assertEquals("120.00", CsvExporter.price(12_000, "EUR"))
        assertEquals("500", CsvExporter.price(500, "JPY"))
        assertEquals("1.234", CsvExporter.price(1_234, "BHD"))
        assertEquals("0.00", CsvExporter.price(0, "EUR"))
        val row = pairs(stats(shoe(price = null), lastUsed = null))[1]
        assertEquals(true, row.contains("\"Walking\",,\"EUR\""))
        assertEquals(true, row.endsWith("95,,1235,\"final\""))
    }

    @Test fun unknownDistanceIsABlankCellNeverZero() {
        // Distance not allowed: nothing known yet, still waiting (T34).
        val row = pairs(stats(shoe(), meters = null, distanceUnfinal = true, distancePending = true))[1]
        assertEquals(true, row.endsWith("\"2026-10-17 12:00\",,\"counted later\""))
        val provisional = pairs(stats(shoe(), meters = 800.0, distanceUnfinal = true))[1]
        assertEquals(true, provisional.endsWith(",800,\"provisional\""))
    }

    @Test fun metresAreWholeAndNeverNegative() {
        assertEquals(0L, CsvExporter.meters(0.0))
        assertEquals(1L, CsvExporter.meters(0.5))
        assertEquals(2_414L, CsvExporter.meters(2_414.016))
        assertEquals(0L, CsvExporter.meters(-3.0))
        assertEquals(0L, CsvExporter.meters(Double.NaN))
    }

    @Test fun readStatusWordsMatchTheApp() {
        assertEquals("counted later", CsvExporter.readStatus(hasPending = true, hasUnfinal = true))
        assertEquals("provisional", CsvExporter.readStatus(hasPending = false, hasUnfinal = true))
        assertEquals("final", CsvExporter.readStatus(hasPending = false, hasUnfinal = false))
    }

    @Test fun formulaInAPairNameIsNeutralisedInTheFile() {
        val row = pairs(stats(shoe(name = "=HYPERLINK(\"http://evil\")", archived = true)))[1]
        assertEquals(true, row.startsWith("\"'=HYPERLINK(\"\"http://evil\"\")\""))
        assertEquals(true, row.contains("\"yes\""))
    }

    @Test fun sessionsUseTheirOwnTimeZoneAndStatus() {
        fun session(id: String, end: Long?, state: SessionState, reason: EndReason?, zone: String) =
            SessionEntity(id, "s1", t0, end, zone, state, reason)
        val rows = listOf(
            SessionExportRow(
                session("a", t0 + 3_600_000, SessionState.ENDED, EndReason.USER, "Europe/Berlin"), "Daily trainers", 4_000, 3_600_000, false, false,
                totalDistanceMeters = 3_050.4,
            ),
            SessionExportRow(
                session("b", t0 + 60_000, SessionState.ENDED, EndReason.SWITCH, "America/New_York"), "Daily trainers", 40, 60_000, true, false,
                totalDistanceMeters = 30.0, hasUnfinalDistance = true,
            ),
            SessionExportRow(
                session("c", null, SessionState.RUNNING, null, "not/a-zone"), "+Trail", 0, 0, false, true,
                hasUnfinalDistance = true, hasPendingDistance = true,
            ),
        )
        val lines = CsvExporter.sessions(rows).split("\r\n")
        assertEquals("Pair,Started,Ended,Time zone,Status,Walking minutes,Steps,Steps status,Distance in metres,Distance status", lines[0])
        assertEquals(
            "\"Daily trainers\",\"2026-10-17 12:00\",\"2026-10-17 13:00\",\"Europe/Berlin\",\"ended\",60,4000,\"final\",3050,\"final\"",
            lines[1],
        )
        assertEquals(
            "\"Daily trainers\",\"2026-10-17 06:00\",\"2026-10-17 06:01\",\"America/New_York\",\"ended (switched pairs)\",1,40,\"provisional\",30,\"provisional\"",
            lines[2],
        )
        assertEquals("\"'+Trail\",\"2026-10-17 10:00\",,\"Z\",\"running\",0,0,\"counted later\",,\"counted later\"", lines[3])
    }

    @Test fun sessionsAddedLaterSaySo() {
        val added = SessionEntity("d", "s1", t0, t0 + 1_800_000, "UTC", SessionState.ENDED, EndReason.USER, addedLater = true)
        val line = CsvExporter.sessions(listOf(SessionExportRow(added, "Daily trainers", 3_000, 1_800_000, false, false))).split("\r\n")[1]
        assertEquals(true, line.contains(",\"ended (added later)\",30,3000,"))
    }
}
