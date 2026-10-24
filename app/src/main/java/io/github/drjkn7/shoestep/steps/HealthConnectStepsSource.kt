package io.github.drjkn7.shoestep.steps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads steps and distance from Health Connect (READ_STEPS and READ_DISTANCE only). Health Connect
 * merges phone and wearable data using the user's app priority, so a watch and a phone are not
 * double-counted (official "Read aggregated data" guide: steps and distance are de-duplicated).
 * Errors are turned into [StepsAccess.UNAVAILABLE] / Failed reads and never logged.
 */
class HealthConnectStepsSource(context: Context) : StepsSource {
    private val appContext = context.applicationContext

    @Volatile
    private var client: HealthConnectClient? = null

    private suspend fun sdkStatus(): Int =
        withContext(Dispatchers.Default) { HealthConnectClient.getSdkStatus(appContext) }

    private suspend fun clientOrNull(): HealthConnectClient? {
        client?.let { return it }
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return null
        return HealthConnectClient.getOrCreate(appContext).also { client = it }
    }

    override suspend fun access(): StepsAccess = try {
        when (sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE ->
                if (STEPS_PERMISSION in grantedPermissions()) StepsAccess.READY else StepsAccess.NEEDS_PERMISSION
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StepsAccess.NEEDS_INSTALL
            else -> StepsAccess.UNAVAILABLE
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        StepsAccess.UNAVAILABLE
    }

    override suspend fun distanceAllowed(): Boolean = try {
        sdkStatus() == HealthConnectClient.SDK_AVAILABLE && DISTANCE_PERMISSION in grantedPermissions()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private suspend fun grantedPermissions(): Set<String> =
        clientOrNull()?.permissionController?.getGrantedPermissions().orEmpty()

    override suspend fun read(startUtc: Long, endUtc: Long): StepsRead {
        if (endUtc <= startUtc) return StepsRead.Count(0)
        return when (val total = aggregate(StepsRecord.COUNT_TOTAL, startUtc, endUtc)) {
            is Total.Value -> StepsRead.Count(total.value.coerceAtLeast(0))
            Total.None -> StepsRead.NoData
            Total.Failed -> StepsRead.Failed
        }
    }

    override suspend fun readDistance(startUtc: Long, endUtc: Long): DistanceRead {
        if (endUtc <= startUtc) return DistanceRead.Meters(0.0)
        return when (val total = aggregate(DistanceRecord.DISTANCE_TOTAL, startUtc, endUtc)) {
            // A negative or not-a-number total would be a broken record; treat it like no answer.
            is Total.Value -> total.value.inMeters.takeIf { it.isFinite() && it >= 0 }
                ?.let { DistanceRead.Meters(it) } ?: DistanceRead.Failed
            Total.None -> DistanceRead.NoData
            Total.Failed -> DistanceRead.Failed
        }
    }

    /** What an aggregate read returned: a total, nothing recorded, or an error. */
    private sealed interface Total<out T> {
        data class Value<T>(val value: T) : Total<T>
        data object None : Total<Nothing>
        data object Failed : Total<Nothing>
    }

    /**
     * Health Connect's own total of [metric] between two instants. Health Connect removes duplicates
     * (phone and watch both recording) using the user's app priority before adding up.
     */
    private suspend fun <T : Any> aggregate(metric: AggregateMetric<T>, startUtc: Long, endUtc: Long): Total<T> = try {
        val healthConnect = clientOrNull()
        if (healthConnect == null) {
            Total.Failed
        } else {
            val result = healthConnect.aggregate(
                AggregateRequest(
                    metrics = setOf(metric),
                    timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(startUtc), Instant.ofEpochMilli(endUtc)),
                ),
            )
            // Null means Health Connect has no records of this kind for this time.
            result[metric]?.let { Total.Value(it) } ?: Total.None
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Includes SecurityException (permission revoked or refused) and reads older than Health Connect allows.
        Total.Failed
    }

    companion object {
        /** Reading steps: what makes step counting work (SEC-04). */
        val STEPS_PERMISSION: String = HealthPermission.getReadPermission(StepsRecord::class)

        /** Reading distance (M2): optional; steps work without it (SEC-04). */
        val DISTANCE_PERMISSION: String = HealthPermission.getReadPermission(DistanceRecord::class)

        /** What "Allow" asks for: steps and distance together. The user may allow either or both. */
        val PERMISSIONS: Set<String> = setOf(STEPS_PERMISSION, DISTANCE_PERMISSION)

        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

        /** Opens Health Connect's permission screen for this app. */
        fun permissionContract() = PermissionController.createRequestPermissionResultContract()

        /** Opens the Play Store page to install or update Health Connect (Android 13 and lower). */
        fun installIntent(): Intent =
            Intent(Intent.ACTION_VIEW, "market://details?id=$HEALTH_CONNECT_PACKAGE".toUri())
                .setPackage("com.android.vending")

        /** The same page in the browser, for phones without the Play Store app (this app stays offline). */
        fun installWebIntent(): Intent =
            Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE".toUri())

        /** Play Store app first, then the web page; does nothing if neither can be opened. */
        fun openInstallPage(context: Context) {
            for (intent in listOf(installIntent(), installWebIntent())) {
                try {
                    context.startActivity(intent)
                    return
                } catch (_: ActivityNotFoundException) {
                    // try the next way
                }
            }
        }
    }
}
