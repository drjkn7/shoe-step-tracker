package io.github.drjkn7.shoestep.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

/*
 * Schema migrations. Each one only adds to what is there: no table is rebuilt and no row is changed
 * or removed. MigrationTest checks every step against the exported schema files (app/schemas).
 */

/**
 * v1 → v2 (M2 Phase 4): distance per segment. Every existing segment starts with distance PENDING,
 * so StepReconciler fills it in where Health Connect still allows the read (backfill) and settles
 * the rest as UNAVAILABLE. Steps are untouched.
 */
val MIGRATION_1_2 = Migration(1, 2) { connection ->
    connection.execSQL("ALTER TABLE `segments` ADD COLUMN `distance_m` REAL")
    connection.execSQL("ALTER TABLE `segments` ADD COLUMN `distance_status` TEXT NOT NULL DEFAULT 'PENDING'")
    connection.execSQL("ALTER TABLE `segments` ADD COLUMN `distance_read_utc` INTEGER")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_segments_distance_status` ON `segments` (`distance_status`)")
}

/**
 * v2 → v3 (M2 Phase 6): sessions added afterwards for a forgotten tap are marked "added later". Every
 * existing session was recorded live, so it starts as not added later.
 */
val MIGRATION_2_3 = Migration(2, 3) { connection ->
    connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `added_later` INTEGER NOT NULL DEFAULT 0")
}

/** Every migration, in order. A missing step fails loudly when the database opens; data is never wiped. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
