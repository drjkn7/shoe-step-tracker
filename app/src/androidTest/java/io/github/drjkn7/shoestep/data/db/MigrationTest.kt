package io.github.drjkn7.shoestep.data.db

import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test scaffold. Each future schema version adds a test here that creates the
 * previous version from its exported JSON, runs the migration, and checks the data survived.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dbFile = instrumentation.targetContext.getDatabasePath(TEST_DB)

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, dbFile, AndroidSQLiteDriver(), ShoeStepDatabase::class)

    /** The scratch file must not survive between runs (the app stays installed after tests). */
    @Before @After
    fun deleteScratchDatabase() {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun version1SchemaMatchesCurrentEntitiesAndKeepsData() = runTest {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO shoes (id, tag_uuid, name, brand, model, type, price_minor, currency_code, " +
                    "purchase_epoch_day, accent_color, archived, created_at_utc) " +
                    "VALUES ('a', NULL, 'Old pair', '', '', 'WALKING', 5000, 'EUR', NULL, 1, 0, 0)",
            )
            close()
        }

        // Validates the stored schema against the exported v1 JSON.
        helper.runMigrationsAndValidate(1, emptyList()).close()

        // Opening with the real database class validates it against the current entities.
        val db = Room.databaseBuilder(instrumentation.targetContext, ShoeStepDatabase::class.java, TEST_DB)
            .setDriver(AndroidSQLiteDriver())
            .build()
        try {
            assertEquals("Old pair", db.shoeDao().getById("a")?.name)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
