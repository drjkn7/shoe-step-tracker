package io.github.drjkn7.shoestep.export

/**
 * RFC 4180 CSV with spreadsheet-formula protection (SEC-08 / T23).
 *
 * Text cells are always quoted. A text cell that a spreadsheet could treat as a formula (it starts
 * with = + - @, a tab or a carriage return, possibly after spaces; full-width forms included) gets
 * a leading apostrophe, so it is shown as text and never run. Numbers the app computes itself are
 * written plain so spreadsheets can add them up.
 */
object Csv {
    const val LINE_END = "\r\n"

    /** UTF-8 byte order mark, so spreadsheet apps read accented names correctly. */
    val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r', '＝', '＋', '－', '＠')

    fun neutralise(text: String): String {
        val first = text.firstOrNull() ?: return text
        val firstAfterSpaces = text.trimStart(' ').firstOrNull()
        val risky = first in FORMULA_STARTS || firstAfterSpaces in FORMULA_STARTS
        return if (risky) "'$text" else text
    }

    fun text(value: String): String = "\"" + neutralise(value).replace("\"", "\"\"") + "\""

    /**
     * The header line, written without quotes. The file starts with a byte order mark, and some
     * spreadsheet apps (e.g. Google Sheets) keep that mark as part of the first cell; a quoted first
     * cell then no longer starts with a quote and shows its quotes as text. Header names are fixed
     * plain words chosen by the app, so they never need quoting (checked here).
     */
    fun header(names: List<String>): String {
        require(names.all { name -> name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == ' ' } }) {
            "Header names must be plain words"
        }
        return names.joinToString(",") + LINE_END
    }

    /**
     * One line. Strings are quoted text; Long/Int/plain numbers the app made are written as they are;
     * null is an empty cell.
     */
    fun row(vararg cells: Any?): String = cells.joinToString(",") { cell ->
        when (cell) {
            null -> ""
            is String -> text(cell)
            is Long, is Int -> cell.toString()
            is Number -> cell.toString()
            else -> text(cell.toString())
        }
    } + LINE_END
}
