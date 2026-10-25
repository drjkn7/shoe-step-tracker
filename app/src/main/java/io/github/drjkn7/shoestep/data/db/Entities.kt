package io.github.drjkn7.shoestep.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.data.model.StepsStatus

// Times are UTC epoch milliseconds. Money is minor units + ISO 4217 code. No location data anywhere.

@Entity(
    tableName = "shoes",
    indices = [Index(value = ["tag_uuid"], unique = true)],
)
data class ShoeEntity(
    @PrimaryKey val id: String,
    /** Random UUID written to the NFC tag; unique so one tag can never belong to two shoes. */
    @ColumnInfo(name = "tag_uuid") val tagUuid: String?,
    val name: String,
    val brand: String,
    val model: String,
    val type: ShoeType,
    @ColumnInfo(name = "price_minor") val priceMinor: Long?,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "purchase_epoch_day") val purchaseEpochDay: Long?,
    /** Index into the fixed 8-colour palette (0..7). */
    @ColumnInfo(name = "accent_color") val accentColor: Int,
    val archived: Boolean,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ShoeEntity::class,
            parentColumns = ["id"],
            childColumns = ["shoe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("shoe_id")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shoe_id") val shoeId: String,
    @ColumnInfo(name = "start_utc") val startUtc: Long,
    @ColumnInfo(name = "end_utc") val endUtc: Long?,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val state: SessionState,
    @ColumnInfo(name = "end_reason") val endReason: EndReason?,
    // Schema v3: typed in afterwards for a forgotten tap ("Added later"). Only these can be deleted.
    @ColumnInfo(name = "added_later", defaultValue = "0") val addedLater: Boolean = false,
)

@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id"), Index("steps_status"), Index("distance_status")],
)
data class SegmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "start_utc") val startUtc: Long,
    @ColumnInfo(name = "end_utc") val endUtc: Long?,
    val steps: Long?,
    @ColumnInfo(name = "steps_status") val stepsStatus: StepsStatus,
    /** When the steps were last read (named before distance existed). */
    @ColumnInfo(name = "last_read_utc") val lastReadUtc: Long?,
    // Schema v2: distance from Health Connect, filled in separately from steps (StepReconciler), so
    // refusing distance never holds steps back. Metres; null until known. Same statuses as steps.
    @ColumnInfo(name = "distance_m") val distanceMeters: Double? = null,
    @ColumnInfo(name = "distance_status", defaultValue = "PENDING") val distanceStatus: StepsStatus = StepsStatus.PENDING,
    @ColumnInfo(name = "distance_read_utc") val distanceReadUtc: Long? = null,
)

/**
 * Pointer to the one running or paused session. The primary key is always [SINGLE_ROW_ID], so the
 * table can hold at most one row; a trigger (see [ShoeStepDatabase]) rejects any other id.
 */
@Entity(
    tableName = "active_session",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id"], unique = true)],
)
data class ActiveSessionEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "session_id") val sessionId: String,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
