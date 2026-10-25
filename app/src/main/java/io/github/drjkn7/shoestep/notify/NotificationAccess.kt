package io.github.drjkn7.shoestep.notify

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Whether Android lets the app show notifications. Tests pass a fake so they never depend on the phone. */
interface NotificationAccess {
    /** True when a posted notification would be shown. */
    fun isAllowed(): Boolean

    /** True on Android 13+ while the app has not been given the notification permission (it must ask). */
    fun needsPermission(): Boolean
}

class AndroidNotificationAccess(context: Context) : NotificationAccess {
    private val context = context.applicationContext

    override fun isAllowed(): Boolean =
        !needsPermission() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun needsPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    companion object {
        /**
         * Android's runtime permission (Android 13+), spelled out because the constant only exists from 13.
         * Asking for it on older versions returns "refused" at once, with no prompt.
         */
        const val PERMISSION = "android.permission.POST_NOTIFICATIONS"

        /** Opens Android's notification settings for this app, where a refused permission can be turned on. */
        fun openSystemSettings(context: Context) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}
