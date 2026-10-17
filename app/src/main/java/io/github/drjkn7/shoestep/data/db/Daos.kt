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
    (SELECT COUNT(*) FROM sessions x WHERE x.shoe_id = s.id) AS session_count,
    (SELECT COUNT(*) FROM sessions x WHERE x.shoe_id = s.id AND x.state = 'ENDED') AS ended_session_count,
    COALESCE((SELECT SUM(MAX(0, g.end_utc - g.start_utc)) FROM segments g JOIN sessions x ON g.session_id = x.id
              WHERE x.shoe_id = s.id AND g.end_utc IS NOT NULL), 0) AS active_millis,
    (SELECT MAX(x.start_utc) FROM sessions x WHERE x.shoe_id = s.id) AS last_used_utc,
    EXISTS(SELECT 1 FROM active_session a JOIN sessions x ON a.session_id = x.id
           WHERE x.shoe_id = s.id) AS is_active,
    EXISTS(SELECT 1 FROM segments g JOIN sessions x ON g.session_id = x.id
           WHERE x.shoe_id = s.id AND g.end_utc IS NOT NULL AND g.steps_status != 'FINAL') AS has_unfinal_steps
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

    @Query("SELECT $SHOE_STATS_COLUMNS FROM shoes s WHERE s.archived = :archived ORDER BY s.name COLLATE NOCASE, s.id")
    fun observeWithStats(archived: Boolean): Flow<List<ShoeWithStats>>

    @Query("SELECT $SHOE_STATS_COLUMNS FROM shoes s WHERE s.id = :id")
    fun observeWithStatsById(id: String): Flow<ShoeWithStats?>

    @Query("SELECT $SHOE_STATS_COLUMNS FROM shoes s WHERE s.id = :id")
    suspend fun getWithStatsById(id: String): ShoeWithStats?
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

    /** Closed segments whose step count may still change (reconciled on app foreground). */
    @Query("SELECT * FROM segments WHERE end_utc IS NOT NULL AND steps_status IN (:statuses) ORDER BY end_utc")
    suspend fun getClosedSegmentsWithStatus(statuses: List<StepsStatus>): List<SegmentEntity>

    @Query(
        """
        SELECT x.*,
            COALESCE((SELECT SUM(g.steps) FROM segments g WHERE g.session_id = x.id), 0) AS total_steps,
            COALESCE((SELECT SUM(MAX(0, g.end_utc - g.start_utc)) FROM segments g
                      WHERE g.session_id = x.id AND g.end_utc IS NOT NULL), 0) AS active_millis,
            (SELECT COUNT(*) FROM segments g WHERE g.session_id = x.id) AS segment_count,
            EXISTS(SELECT 1 FROM segments g WHERE g.session_id = x.id
                   AND g.end_utc IS NOT NULL AND g.steps_status != 'FINAL') AS has_unfinal_steps
        FROM sessions x WHERE x.shoe_id = :shoeId ORDER BY x.start_utc DESC, x.id
        """,
    )
    fun observeHistory(shoeId: String): Flow<List<SessionWithSteps>>
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
