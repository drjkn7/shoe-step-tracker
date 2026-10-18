package io.github.drjkn7.shoestep.export

import org.junit.Assert.assertEquals
import org.junit.Test

/** T23: text that a spreadsheet could run as a formula is neutralised; CSV quoting is RFC 4180. */
class CsvTest {

    @Test fun formulaLikeTextGetsAnApostrophe() {
        listOf(
            "=HYPERLINK(\"http://x\",\"click\")",
            "+1+1",
            "-2+3",
            "@SUM(A1)",
            "\tcmd",
            "\rcmd",
            "   =1+1", // spaces before the formula
            "＝1+1", // full-width equals
        ).forEach { assertEquals("'$it", Csv.neutralise(it)) }
    }

    @Test fun ordinaryTextIsUnchanged() {
        listOf("", "Daily trainers", "Nike Pegasus 41", "ASICS – Gel", "50% off", "Brooks (blue)", "a=b")
            .forEach { assertEquals(it, Csv.neutralise(it)) }
    }

    @Test fun textIsAlwaysQuotedWithQuotesDoubled() {
        assertEquals("\"Daily trainers\"", Csv.text("Daily trainers"))
        assertEquals("\"The \"\"fast\"\" pair\"", Csv.text("The \"fast\" pair"))
        assertEquals("\"line one\nline two\"", Csv.text("line one\nline two"))
        assertEquals("\"a, b\"", Csv.text("a, b"))
        assertEquals("\"'=1+1\"", Csv.text("=1+1"))
    }

    @Test fun rowsMixTextNumbersAndBlanksAndEndInCrLf() {
        assertEquals("\"Pair\",1500,,12.50\r\n", Csv.row("Pair", 1_500L, null, 12.5.toBigDecimal().setScale(2)))
        assertEquals("\r\n", Csv.row())
    }
}
