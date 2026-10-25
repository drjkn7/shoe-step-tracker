package io.github.drjkn7.shoestep.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.StepsStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every schema version is created from its exported JSON (app/schemas), migrated step by step, and
 * checked against the next JSON and the current entities; the data must survive unchanged (T37).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dbFile = context.getDatabasePath(TEST_DB)

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, dbFile, AndroidSQLiteDriver(), ShoeStepDatabase::class)

    /** The scratch file must not survive between runs (the app stays installed after tests). */
    @Before @After
    fun deleteScratchDatabase() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun version1To2KeepsEveryRowAndStartsDistanceAsPending() = runTest {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO shoes (id, tag_uuid, name, brand, model, type, price_minor, currency_code, " +
                    "purchase_epoch_day, accent_color, archived, created_at_utc) VALUES " +
                    "('a', '3f0c8a2e-1b4d-4c6e-9a7b-2d5e8f1a0b3c', 'Old pair', 'Brand', 'Model', 'WALKING', 5000, 'EUR', 20000, 1, 0, 100), " +
                    "('b', NULL, 'Archived pair', '', '', 'HIKING', NULL, 'GBP', NULL, 3, 1, 200)",
            )
            execSQL(
                "INSERT INTO sessions (id, shoe_id, start_utc, end_utc, zone_id, state, end_reason) VALUES " +
                    "('s1', 'a', 1000, 5000, 'Europe/London', 'ENDED', 'SWITCH'), " +
                    "('s2', 'a', 6000, NULL, 'Europe/London', 'RUNNING', NULL)",
            )
            execSQL(
                "INSERT INTO segments (id, session_id, start_utc, end_utc, steps, steps_status, last_read_utc) VALUES " +
                    "('g1', 's1', 1000, 3000, 1234, 'FINAL', 9000), " +
                    "('g2', 's1', 3500, 5000, NULL, 'UNAVAILABLE', 9000), " +
                    "('g3', 's2', 6000, NULL, NULL, 'PENDING', NULL)",
            )
            execSQL("INSERT INTO active_session (id, session_id) VALUES (1, 's2')")
            close()
        }

        // Checks the migrated tables against the exported v2 schema.
        helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).close()

        // Opening with the app's own builder checks it against the current entities and migrations.
        val db = ShoeStepDatabase.build(context, TEST_DB)
        try {
            val a = db.shoeDao().getById("a")!!
            assertEquals("Old pair", a.name)
            assertEquals("3f0c8a2e-1b4d-4c6e-9a7b-2d5e8f1a0b3c", a.tagUuid)
            assertEquals(5000L, a.priceMinor)
            assertEquals(20000L, a.purchaseEpochDay)
            assertEquals(true, db.shoeDao().getById("b")!!.archived)

            val s1 = db.sessionDao().getSession("s1")!!
            assertEquals(SessionState.ENDED, s1.state)
            assertEquals(EndReason.SWITCH, s1.endReason)
            assertEquals("Europe/London", s1.zoneId)
            assertEquals("s2", db.activeSessionDao().getActiveSessionId())

            val segments = db.sessionDao().getSegments("s1") + db.sessionDao().getSegments("s2")
            assertEquals(listOf("g1", "g2", "g3"), segments.map { it.id })
            assertEquals(listOf(1234L, null, null), segments.map { it.steps })
            assertEquals(listOf(StepsStatus.FINAL, StepsStatus.UNAVAILABLE, StepsStatus.PENDING), segments.map { it.stepsStatus })
            assertEquals(listOf(9000L, 9000L, null), segments.map { it.lastReadUtc })
            // Distance is new: unknown everywhere, waiting to be read (backfill).
            segments.forEach {
                assertNull(it.distanceMeters)
                assertEquals(StepsStatus.PENDING, it.distanceStatus)
                assertNull(it.distanceReadUtc)
            }

            // Totals are unchanged by the migration.
            val stats = db.shoeDao().observeWithStatsById("a").first()!!
            assertEquals(1234L, stats.totalSteps)
            assertEquals(2, stats.sessionCount)
            assertNull(stats.totalDistanceMeters)
        } finally {
            db.close()
        }
    }

    @Test
    fun version2To3KeepsEveryRowAndMarksNoSessionAsAddedLater() = runTest {
        helper.createDatabase(2).apply {
            execSQL(
                "INSERT INTO shoes (id, tag_uuid, name, brand, model, type, price_minor, currency_code, " +
                    "purchase_epoch_day, accent_color, archived, created_at_utc) VALUES " +
                    "('a', NULL, 'Daily pair', 'Brand', 'Model', 'WALKING', 9000, 'GBP', NULL, 2, 0, 100)",
            )
            execSQL(
                "INSERT INTO sessions (id, shoe_id, start_utc, end_utc, zone_id, state, end_reason) VALUES " +
                    "('s1', 'a', 1000, 5000, 'Europe/London', 'ENDED', 'USER'), " +
                    "('s2', 'a', 6000, NULL, 'Europe/London', 'PAUSED', NULL)",
            )
            execSQL(
                "INSERT INTO segments (id, session_id, start_utc, end_utc, steps, steps_status, last_read_utc, " +
                    "distance_m, distance_status, distance_read_utc) VALUES " +
                    "('g1', 's1', 1000, 5000, 4321, 'FINAL', 9000, 3210.5, 'FINAL', 9000), " +
                    "('g2', 's2', 6000, 7000, NULL, 'PENDING', NULL, NULL, 'PENDING', NULL)",
            )
            execSQL("INSERT INTO active_session (id, session_id) VALUES (1, 's2')")
            close()
        }

        helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)).close()

        val db = ShoeStepDatabase.build(context, TEST_DB)
        try {
            val s1 = db.sessionDao().getSession("s1")!!
            val s2 = db.sessionDao().getSession("s2")!!
            assertEquals(false, s1.addedLater)
            assertEquals(false, s2.addedLater)
            assertEquals(5000L, s1.endUtc)
            assertEquals(SessionState.PAUSED, s2.state)
            assertEquals("s2", db.activeSessionDao().getActiveSessionId())
            // Recorded sessions stay undeletable.
            assertEquals(0, db.sessionDao().deleteAddedLater("s1"))

            val stats = db.shoeDao().observeWithStatsById("a").first()!!
            assertEquals(4321L, stats.totalSteps)
            assertEquals(3210.5, stats.totalDistanceMeters!!, 0.0)
            assertEquals(2, stats.sessionCount)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
