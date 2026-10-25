package io.github.drjkn7.shoestep.ui.shoe

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The times the Add past session form opens with (code review 25 Oct 2026: never already in error). */
class PastTimesTest {

    private val zone = ZoneId.of("Europe/London")
    private fun at(hour: Int, minute: Int) = ZonedDateTime.of(2026, 10, 18, hour, minute, 0, 0, zone)
    private val day = LocalDate.of(2026, 10, 18).toEpochDay()

    @Test fun theHalfHourUpToNowRoundedDownToFiveMinutes() {
        assertEquals(PastTimes(day, 7 * 60 + 10, 7 * 60 + 40), AddPastSessionViewModel.defaultTimes(at(7, 43)))
        assertEquals(PastTimes(day, 0, 30), AddPastSessionViewModel.defaultTimes(at(0, 30)))
    }

    @Test fun justAfterMidnightItUsesYesterdaysLastHalfHour() {
        // 00:02 would give 00:00–00:00 ("end before start"); 00:20 would cross into yesterday.
        for (minute in listOf(0, 2, 4, 20, 29)) {
            val times = AddPastSessionViewModel.defaultTimes(at(0, minute))
            assertEquals(PastTimes(day - 1, 23 * 60 + 25, 23 * 60 + 55), times)
            assertEquals(true, times.endMinute > times.startMinute)
        }
    }
}
