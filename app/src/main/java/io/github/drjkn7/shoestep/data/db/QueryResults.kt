package io.github.drjkn7.shoestep.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Embedded

/** A shoe plus statistics computed from its sessions and segments. Nothing here is stored. */
data class ShoeWithStats(
    @Embedded val shoe: ShoeEntity,
    /** Sum of known segment steps (unknown/pending segments count as 0 until resolved). */
    @ColumnInfo(name = "total_steps") val totalSteps: Long,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "ended_session_count") val endedSessionCount: Int,
    /** Sum of closed segment durations; negative durations (clock changes) count as 0. */
    @ColumnInfo(name = "active_millis") val activeMillis: Long,
    @ColumnInfo(name = "last_used_utc") val lastUsedUtc: Long?,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    /** True while any closed segment's steps are not FINAL yet (UI shows "provisional"). */
    @ColumnInfo(name = "has_unfinal_steps") val hasUnfinalSteps: Boolean,
) {
    /** Average over ended sessions; null when there are none. */
    val averageStepsPerSession: Long?
        get() = if (endedSessionCount == 0) null else totalSteps / endedSessionCount
}

/** One row of a shoe's session history. */
data class SessionWithSteps(
    @Embedded val session: SessionEntity,
    @ColumnInfo(name = "total_steps") val totalSteps: Long,
    @ColumnInfo(name = "active_millis") val activeMillis: Long,
    @ColumnInfo(name = "segment_count") val segmentCount: Int,
    @ColumnInfo(name = "has_unfinal_steps") val hasUnfinalSteps: Boolean,
)
