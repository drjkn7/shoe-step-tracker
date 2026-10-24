package io.github.drjkn7.shoestep.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Embedded

/** A shoe plus statistics computed from its sessions and segments. Nothing here is stored. */
data class ShoeWithStats(
    @Embedded val shoe: ShoeEntity,
    /** Sum of known segment steps (unknown/pending segments count as 0 until resolved). */
    @ColumnInfo(name = "total_steps") val totalSteps: Long,
    /** Sum of known segment distances in metres; null while none is known (shown as "–", never 0). */
    @ColumnInfo(name = "total_distance_m") val totalDistanceMeters: Double?,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "ended_session_count") val endedSessionCount: Int,
    /** Sum of closed segment durations; negative durations (clock changes) count as 0. */
    @ColumnInfo(name = "active_millis") val activeMillis: Long,
    @ColumnInfo(name = "last_used_utc") val lastUsedUtc: Long?,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    /** True while any closed segment is PENDING or PROVISIONAL (UI shows "about"). UNAVAILABLE is settled. */
    @ColumnInfo(name = "has_unfinal_steps") val hasUnfinalSteps: Boolean,
    /** True while any closed segment has no count yet (UI shows "counted later"). */
    @ColumnInfo(name = "has_pending_steps") val hasPendingSteps: Boolean,
) {
    /** Average over ended sessions; null when there are none. */
    val averageStepsPerSession: Long?
        get() = if (endedSessionCount == 0) null else totalSteps / endedSessionCount
}

/** The running or paused session plus the shoe details the Home card shows. */
data class ActiveSessionWithShoe(
    @Embedded val session: SessionEntity,
    @ColumnInfo(name = "shoe_name") val shoeName: String,
    @ColumnInfo(name = "shoe_accent_color") val shoeAccentColor: Int,
)

/** One row of a shoe's session history. */
data class SessionWithSteps(
    @Embedded val session: SessionEntity,
    @ColumnInfo(name = "total_steps") val totalSteps: Long,
    /** Metres; null while no segment of this session has a distance yet. */
    @ColumnInfo(name = "total_distance_m") val totalDistanceMeters: Double?,
    @ColumnInfo(name = "active_millis") val activeMillis: Long,
    @ColumnInfo(name = "segment_count") val segmentCount: Int,
    /** True while any closed segment is PENDING or PROVISIONAL (UI shows "about"). */
    @ColumnInfo(name = "has_unfinal_steps") val hasUnfinalSteps: Boolean,
    /** True while any closed segment has no count yet (UI shows "counted later"). */
    @ColumnInfo(name = "has_pending_steps") val hasPendingSteps: Boolean,
)

/** One session row of the CSV export: the session, its pair's name and computed totals. */
data class SessionExportRow(
    @Embedded val session: SessionEntity,
    @ColumnInfo(name = "shoe_name") val shoeName: String,
    @ColumnInfo(name = "total_steps") val totalSteps: Long,
    @ColumnInfo(name = "active_millis") val activeMillis: Long,
    @ColumnInfo(name = "has_unfinal_steps") val hasUnfinalSteps: Boolean,
    @ColumnInfo(name = "has_pending_steps") val hasPendingSteps: Boolean,
)
