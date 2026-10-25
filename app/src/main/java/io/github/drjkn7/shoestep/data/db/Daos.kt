package io.github.drjkn7.shoestep.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import io.github.drjkn7.shoestep.data.model.StepsStatus
import kotlinx.coroutines.flow.Flow

// All queries use bound parameters (SEC-07). Never build SQL from strings.

private const val SHOE_STATS_COLUMNS = """
    s.*,
    COALESCE((SELECT SUM(g.steps) FROM segments g JOIN sessions x ON g.session_id = x.id
              WHERE x.shoe_id = s.id), 0) AS total_steps,
    (SELECT SUM(g.distance_m) FROM segments g JOIN sessions x ON g.session_id = x.id
     WHERE x.shoe_id = s.id) AS total_distance_m,
    (SELECT COUNT(*) FROM sessions x WHERE x.shoe_id = s.id) AS session_count,
    (SELECT COUNT(*) FROM sessions x WHERE x.shoe_id = s.id AND x.state = 'ENDED') AS ended_session_count,
    COALESCE((SELECT SUM(MAX(0, g.end_utc - g.start_utc)) FROM segments g JOIN sessions x ON g.session_id = x.id
              WHERE x.shoe_id = s.id AND g.end_utc IS NOT NULL), 0) AS active_millis,
    (SELECT MAX(x.start_utc) FROM sessions x WHERE x.shoe_id = s.id) AS last_used_utc,
    EXISTS(SELECT 1 FROM active_session a JOIN sessions x ON a.session_id = x.id
           WHERE x.shoe_id = s.id) AS is_active,
    EXISTS(SELECT 1 FROM segments g JOIN sessions x ON g.session_id = x.id
           WHERE x.shoe_id = s.id AND g.end_utc IS NOT NULL
           AND g.steps_status IN ('PENDING', 'PROVISIONAL')) AS has_unfinal_steps,
    EXISTS(SELECT 1 FROM segments g JOIN sessions x ON g.session_id = x.id
           WHERE x.shoe_id = s.id AND g.end_utc IS NOT NULL AND g.steps_status = 'PENDING') AS has_pending_steps,
    EXISTS(SELECT 1 FROM segments g JOIN sessions x ON g.session_id = x.id
           WHERE x.shoe_id = s.id AND g.end_utc IS NOT NULL
           AND g.distance_status IN ('PENDING', 'PROVISIONAL')) AS has_unfinal_distance,
    EXISTS(SELECT 1 FROM segments g JOIN sessions x ON g.session_id = x.id
           WHERE x.shoe_id = s.id AND g.end_utc IS NOT NULL AND g.distance_status = 'PENDING') AS has_pending_distance
"""

@Dao
interface ShoeDao {
    /** Fails with a constraint error if the id or tag UUID already exists. */
    @Insert
    suspend fun insert(shoe: ShoeEntity)

    @Update
    suspend fun update(shoe: ShoeEntity): Int

    @Query("DELETE FROM shoes WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("UPDATE shoes SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean): Int

    @Query("SELECT * FROM shoes WHERE id = :id")
    suspend fun getById(id: String): ShoeEntity?

    @Query("SELECT * FROM shoes WHERE tag_uuid = :tagUuid")
    suspend fun getByTagUuid(tagUuid: String): ShoeEntity?

    /** Links or unlinks a tag. Only RoomTagStore calls this; the unique index blocks shared tags. */
    @Query("UPDATE shoes SET tag_uuid = :tagUuid WHERE id = :id")
    suspend fun setTagUuid(id: String, tagUuid: String?): Int

    @Query("SELECT $SHOE_STATS_COLUMNS FROM shoes s WHERE s.archived = :archived ORDER BY s.name COLLATE NOCASE, s.id")
    fun observeWithStats(archived: Boolean): Flow<List<ShoeWithStats>>

    @Query("SELECT $SHOE_STATS_COLUMNS FROM shoes s WHERE s.id = :id")
    fun observeWithStatsById(id: String): Flow<ShoeWithStats?>

    @Query("SELECT $SHOE_STATS_COLUMNS FROM shoes s WHERE s.id = :id")
    suspend fun getWithStatsById(id: String): ShoeWithStats?

    /** Every pair, archived included, for the CSV export. */
    @Query("SELECT $SHOE_STATS_COLUMNS FROM shoes s ORDER BY s.name COLLATE NOCASE, s.id")
    suspend fun getAllWithStats(): List<ShoeWithStats>
}

/**
 * Session and segment storage. Write methods here must only be called by SessionManager
 * (Phase 4), which wraps them in transactions; screens only read.
 */
@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity): Int

    @Insert
    suspend fun insertSegment(segment: SegmentEntity)

    @Update
    suspend fun updateSegment(segment: SegmentEntity): Int

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: String): SessionEntity?

    @Query("SELECT * FROM segments WHERE session_id = :sessionId ORDER BY start_utc, id")
    suspend fun getSegments(sessionId: String): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE session_id = :sessionId AND end_utc IS NULL")
    suspend fun getOpenSegment(sessionId: String): SegmentEntity?

    /** Running or paused sessions; normally at most one (the active one). Used by launch repair. */
    @Query("SELECT * FROM sessions WHERE state != 'ENDED' ORDER BY start_utc, id")
    suspend fun getUnendedSessions(): List<SessionEntity>

    /**
     * The earliest session of any pair that shares time with [startUtc]..[endUtc] (touching ends don't
     * count). A session without an end (running or paused) reaches into the future.
     */
    @Query(
        """
        SELECT x.*, s.name AS shoe_name FROM sessions x JOIN shoes s ON x.shoe_id = s.id
        WHERE x.start_utc < :endUtc AND (x.end_utc IS NULL OR x.end_utc > :startUtc)
        ORDER BY x.start_utc, x.id LIMIT 1
        """,
    )
    suspend fun getFirstOverlapping(startUtc: Long, endUtc: Long): SessionWithShoeName?

    /** Deletes a session added later (its segments go with it). Sessions recorded live are never deleted. */
    @Query("DELETE FROM sessions WHERE id = :id AND added_later = 1 AND state = 'ENDED'")
    suspend fun deleteAddedLater(id: String): Int

    @Query(
        """
        SELECT x.*,
            COALESCE((SELECT SUM(g.steps) FROM segments g WHERE g.session_id = x.id), 0) AS total_steps,
            (SELECT SUM(g.distance_m) FROM segments g WHERE g.session_id = x.id) AS total_distance_m,
            COALESCE((SELECT SUM(MAX(0, g.end_utc - g.start_utc)) FROM segments g
                      WHERE g.session_id = x.id AND g.end_utc IS NOT NULL), 0) AS active_millis,
            (SELECT COUNT(*) FROM segments g WHERE g.session_id = x.id) AS segment_count,
            EXISTS(SELECT 1 FROM segments g WHERE g.session_id = x.id
                   AND g.end_utc IS NOT NULL AND g.steps_status IN ('PENDING', 'PROVISIONAL')) AS has_unfinal_steps,
            EXISTS(SELECT 1 FROM segments g WHERE g.session_id = x.id
                   AND g.end_utc IS NOT NULL AND g.steps_status = 'PENDING') AS has_pending_steps,
            EXISTS(SELECT 1 FROM segments g WHERE g.session_id = x.id
                   AND g.end_utc IS NOT NULL AND g.distance_status IN ('PENDING', 'PROVISIONAL')) AS has_unfinal_distance
        FROM sessions x WHERE x.shoe_id = :shoeId ORDER BY x.start_utc DESC, x.id
        """,
    )
    fun observeHistory(shoeId: String): Flow<List<SessionWithSteps>>

    /** Every session with its pair's name and the same totals as the history list, for the CSV export. */
    @Query(
        """
        SELECT x.*, s.name AS shoe_name,
            COALESCE((SELECT SUM(g.steps) FROM segments g WHERE g.session_id = x.id), 0) AS total_steps,
            (SELECT SUM(g.distance_m) FROM segments g WHERE g.session_id = x.id) AS total_distance_m,
            COALESCE((SELECT SUM(MAX(0, g.end_utc - g.start_utc)) FROM segments g
                      WHERE g.session_id = x.id AND g.end_utc IS NOT NULL), 0) AS active_millis,
            EXISTS(SELECT 1 FROM segments g WHERE g.session_id = x.id
                   AND g.end_utc IS NOT NULL AND g.steps_status IN ('PENDING', 'PROVISIONAL')) AS has_unfinal_steps,
            EXISTS(SELECT 1 FROM segments g WHERE g.session_id = x.id
                   AND g.end_utc IS NOT NULL AND g.steps_status = 'PENDING') AS has_pending_steps,
            EXISTS(SELECT 1 FROM segments g WHERE g.session_id = x.id
                   AND g.end_utc IS NOT NULL AND g.distance_status IN ('PENDING', 'PROVISIONAL')) AS has_unfinal_distance,
            EXISTS(SELECT 1 FROM segments g WHERE g.session_id = x.id
                   AND g.end_utc IS NOT NULL AND g.distance_status = 'PENDING') AS has_pending_distance
        FROM sessions x JOIN shoes s ON x.shoe_id = s.id
        ORDER BY x.start_utc, x.id
        """,
    )
    suspend fun getAllForExport(): List<SessionExportRow>
}

/**
 * Step counts and distances of segments. Only StepReconciler (via RoomStepStore) writes here; SessionManager
 * creates segments as PENDING and never touches steps or distance afterwards.
 */
@Dao
interface StepsDao {
    /**
     * Closed segments whose step count is missing or may still change: PENDING first (so re-checks
     * of provisional counts can never crowd them out), then oldest first.
     */
    @Query(
        """
        SELECT * FROM segments WHERE end_utc IS NOT NULL AND steps_status IN ('PENDING', 'PROVISIONAL')
        ORDER BY CASE steps_status WHEN 'PENDING' THEN 0 ELSE 1 END, end_utc, id LIMIT :limit
        """,
    )
    suspend fun getUnresolvedClosedSegments(limit: Int): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE session_id = :sessionId ORDER BY start_utc, id")
    suspend fun getSegments(sessionId: String): List<SegmentEntity>

    /**
     * Saves a step result only if the segment still ends at [expectedEndUtc] and is not FINAL or
     * UNAVAILABLE yet, so a stale read can never overwrite newer data. Returns rows changed (0 or 1).
     */
    @Query(
        """
        UPDATE segments SET steps = :steps, steps_status = :status, last_read_utc = :readAtUtc
        WHERE id = :segmentId AND end_utc = :expectedEndUtc AND steps_status IN ('PENDING', 'PROVISIONAL')
        """,
    )
    suspend fun saveSteps(segmentId: String, expectedEndUtc: Long, steps: Long?, status: StepsStatus, readAtUtc: Long): Int

    /** Like [getUnresolvedClosedSegments], for distance (its own status, separate from steps). */
    @Query(
        """
        SELECT * FROM segments WHERE end_utc IS NOT NULL AND distance_status IN ('PENDING', 'PROVISIONAL')
        ORDER BY CASE distance_status WHEN 'PENDING' THEN 0 ELSE 1 END, end_utc, id LIMIT :limit
        """,
    )
    suspend fun getUnresolvedClosedDistanceSegments(limit: Int): List<SegmentEntity>

    /** Like [saveSteps], for distance: saved only if the segment is unchanged and not settled yet. */
    @Query(
        """
        UPDATE segments SET distance_m = :meters, distance_status = :status, distance_read_utc = :readAtUtc
        WHERE id = :segmentId AND end_utc = :expectedEndUtc AND distance_status IN ('PENDING', 'PROVISIONAL')
        """,
    )
    suspend fun saveDistance(segmentId: String, expectedEndUtc: Long, meters: Double?, status: StepsStatus, readAtUtc: Long): Int
}

@Dao
interface ActiveSessionDao {
    /** Replaces any existing row: there is only ever one active session. */
    @Upsert
    suspend fun setActive(active: ActiveSessionEntity)

    @Query("DELETE FROM active_session")
    suspend fun clear()

    @Query("SELECT session_id FROM active_session WHERE id = 1")
    suspend fun getActiveSessionId(): String?

    @Query("SELECT x.* FROM active_session a JOIN sessions x ON a.session_id = x.id WHERE a.id = 1")
    fun observeActiveSession(): Flow<SessionEntity?>

    @Query(
        """
        SELECT x.*, s.name AS shoe_name, s.accent_color AS shoe_accent_color
        FROM active_session a JOIN sessions x ON a.session_id = x.id JOIN shoes s ON x.shoe_id = s.id
        WHERE a.id = 1
        """,
    )
    fun observeActiveSessionWithShoe(): Flow<ActiveSessionWithShoe?>
}
