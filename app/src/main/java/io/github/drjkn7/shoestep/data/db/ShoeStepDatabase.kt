package io.github.drjkn7.shoestep.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL

@Database(
    entities = [ShoeEntity::class, SessionEntity::class, SegmentEntity::class, ActiveSessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ShoeStepDatabase : RoomDatabase() {
    abstract fun shoeDao(): ShoeDao
    abstract fun sessionDao(): SessionDao
    abstract fun activeSessionDao(): ActiveSessionDao
    abstract fun stepsDao(): StepsDao

    companion object {
        const val FILE_NAME = "shoestep.db"

        /** [fileName] is only changed by tests that need a real file without touching the app's data. */
        fun build(context: Context, fileName: String = FILE_NAME): ShoeStepDatabase =
            Room.databaseBuilder(context.applicationContext, ShoeStepDatabase::class.java, fileName)
                .configure()
                .build()

        /** For tests: a fresh database that lives only in memory. */
        fun buildInMemory(context: Context): ShoeStepDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, ShoeStepDatabase::class.java)
                .configure()
                .build()

        private fun RoomDatabase.Builder<ShoeStepDatabase>.configure() =
            setDriver(AndroidSQLiteDriver()).addCallback(SingleActiveRowGuard)
    }
}

/**
 * Database-level guarantee that active_session never holds a row other than id 1,
 * so there can never be two active sessions. Created on every open (idempotent),
 * which also covers databases created by future migrations.
 */
internal object SingleActiveRowGuard : RoomDatabase.Callback() {
    private val statements = listOf(
        """
        CREATE TRIGGER IF NOT EXISTS active_session_single_row_insert
        BEFORE INSERT ON active_session WHEN NEW.id != 1
        BEGIN SELECT RAISE(ABORT, 'active_session only allows id 1'); END
        """,
        """
        CREATE TRIGGER IF NOT EXISTS active_session_single_row_update
        BEFORE UPDATE OF id ON active_session WHEN NEW.id != 1
        BEGIN SELECT RAISE(ABORT, 'active_session only allows id 1'); END
        """,
    )

    override suspend fun onOpen(connection: SQLiteConnection) {
        statements.forEach { connection.execSQL(it) }
    }
}
