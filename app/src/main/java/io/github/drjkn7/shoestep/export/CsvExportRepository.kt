package io.github.drjkn7.shoestep.export

import android.content.Context
import android.net.Uri
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import io.github.drjkn7.shoestep.ui.common.accentColour
import io.github.drjkn7.shoestep.ui.common.labelRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId

enum class ExportKind(val filePrefix: String) {
    PAIRS("shoe-step-pairs"),
    SESSIONS("shoe-step-sessions"),
}

/**
 * The only way data leaves the app (PRIVACY.md): the user picks a file in the system file picker
 * (no storage permission needed) and the CSV is written there. Nothing is logged.
 */
class CsvExportRepository(private val db: ShoeStepDatabase, context: Context) {
    private val appContext = context.applicationContext

    suspend fun csv(kind: ExportKind): String = when (kind) {
        ExportKind.PAIRS -> CsvExporter.pairs(
            rows = db.shoeDao().getAllWithStats(),
            typeLabel = { appContext.getString(it.labelRes()) },
            colourLabel = { appContext.getString(accentColour(it).nameRes) },
            zone = ZoneId.systemDefault(),
        )
        ExportKind.SESSIONS -> CsvExporter.sessions(db.sessionDao().getAllForExport())
    }

    /** Suggested file name, e.g. "shoe-step-pairs-2026-10-18.csv" (the user can change it). */
    fun fileName(kind: ExportKind, today: LocalDate): String = "${kind.filePrefix}-$today.csv"

    /**
     * Builds the CSV and writes it to [out] as UTF-8 without a byte order mark (Sheets, Numbers and
     * LibreOffice read it as is; Excel's double-click may garble accented letters, its import doesn't).
     * The caller closes [out].
     */
    suspend fun writeTo(kind: ExportKind, out: OutputStream) {
        val text = csv(kind)
        withContext(Dispatchers.IO) { write(out, text) }
    }

    /** Writes to the file the user picked. False if it couldn't be written (no partial success claimed). */
    suspend fun writeTo(kind: ExportKind, uri: Uri): Boolean = try {
        val text = csv(kind)
        withContext(Dispatchers.IO) {
            appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                write(out, text)
                true
            } ?: false
        }
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun write(out: OutputStream, text: String) {
        out.write(text.toByteArray(Charsets.UTF_8))
        out.flush()
    }
}
