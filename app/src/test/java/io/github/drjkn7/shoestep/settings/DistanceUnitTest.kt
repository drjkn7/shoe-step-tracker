package io.github.drjkn7.shoestep.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class DistanceUnitTest {

    @Test fun regionPicksTheStartingUnit() {
        assertEquals(DistanceUnit.MILES, DistanceUnit.defaultFor(Locale.UK))
        assertEquals(DistanceUnit.MILES, DistanceUnit.defaultFor(Locale.US))
        assertEquals(DistanceUnit.KILOMETRES, DistanceUnit.defaultFor(Locale.GERMANY))
        assertEquals(DistanceUnit.KILOMETRES, DistanceUnit.defaultFor(Locale.forLanguageTag("en-IN")))
        assertEquals(DistanceUnit.KILOMETRES, DistanceUnit.defaultFor(Locale.ENGLISH)) // no country
    }

    @Test fun storedValuesRoundTripAndAnythingElseIsNotChosen() {
        DistanceUnit.entries.forEach { assertEquals(it, DistanceUnit.fromStored(it.name)) }
        assertNull(DistanceUnit.fromStored(null))
        assertNull(DistanceUnit.fromStored("miles"))
        assertNull(DistanceUnit.fromStored("FURLONGS"))
    }

    @Test fun conversionLeavesMetresUnchanged() {
        assertEquals(1.0, DistanceUnit.KILOMETRES.fromMeters(1_000.0), 0.0)
        assertEquals(1.0, DistanceUnit.MILES.fromMeters(1_609.344), 1e-12)
        assertEquals(26.2188, DistanceUnit.MILES.fromMeters(42_195.0), 1e-4) // a marathon
    }
}
