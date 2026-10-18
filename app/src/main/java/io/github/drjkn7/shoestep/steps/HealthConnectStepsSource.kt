package io.github.drjkn7.shoestep.steps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads steps from Health Connect (READ_STEPS only). Health Connect merges phone and wearable
 * data using the user's app priority, so a watch and a phone are not double-counted.
 * Errors are turned into [StepsAccess.UNAVAILABLE] / [StepsRead.Failed] and never logged.
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
            HealthConnectClient.SDK_AVAILABLE -> {
                val granted = clientOrNull()?.permissionController?.getGrantedPermissions().orEmpty()
                if (granted.containsAll(PERMISSIONS)) StepsAccess.READY else StepsAccess.NEEDS_PERMISSION
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StepsAccess.NEEDS_INSTALL
            else -> StepsAccess.UNAVAILABLE
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        StepsAccess.UNAVAILABLE
    }

    override suspend fun read(startUtc: Long, endUtc: Long): StepsRead {
        if (endUtc <= startUtc) return StepsRead.Count(0)
        return try {
            val healthConnect = clientOrNull() ?: return StepsRead.Failed
            val result = healthConnect.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startUtc),
                        Instant.ofEpochMilli(endUtc),
                    ),
                ),
            )
            // Null means Health Connect has no step records for this time.
            result[StepsRecord.COUNT_TOTAL]?.let { StepsRead.Count(it.coerceAtLeast(0)) } ?: StepsRead.NoData
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Includes SecurityException (permission revoked) and reads older than Health Connect allows.
            StepsRead.Failed
        }
    }

    companion object {
        /** The only Health Connect permission this app asks for (SEC-04). */
        val PERMISSIONS: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

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
