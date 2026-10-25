package io.github.drjkn7.shoestep.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.drjkn7.shoestep.MainActivity
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.session.SessionManager
import io.github.drjkn7.shoestep.settings.SettingsStore
import io.github.drjkn7.shoestep.ui.common.Formatters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Shows, updates and removes the session notification (M2 Phase 7, design decision F2). It is an
 * ordinary ongoing notification, not a foreground service: nothing runs in the background, and the app
 * posts it again whenever it is opened or a session changes. Private on the lock screen.
 */
class SessionNotifier(
    context: Context,
    private val sessions: SessionManager,
    private val settings: SettingsStore,
    private val access: NotificationAccess,
) {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /** What the notification should show, following the session and the setting. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun notices(): Flow<SessionNotice?> =
        combine(sessions.activeSession, settings.sessionNotification) { active, on -> active to on }
            .mapLatest { (active, on) -> SessionNotice.from(active, active?.let { sessions.segments(it.session.id) }.orEmpty(), on) }
            .distinctUntilChanged()

    /** Brings the notification up to date once (after a notification button, or a permission answer). */
    suspend fun refresh() = show(notices().first())

    /** Posts [notice], or removes the notification when it is null or Android doesn't allow notifications. */
    @SuppressLint("MissingPermission") // checked by access.isAllowed(); a late refusal is caught below
    fun show(notice: SessionNotice?) {
        if (notice == null || !access.isAllowed()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        createChannel()
        try {
            manager.notify(NOTIFICATION_ID, build(notice))
        } catch (_: SecurityException) {
            // Notifications were refused between the check and posting: nothing to show.
        }
    }

    private fun createChannel() {
        // Low importance: no sound or pop-up, just the icon and the notification in the shade.
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel))
            .setDescription(context.getString(R.string.notification_channel_description))
            .setShowBadge(false)
            .build()
        manager.createNotificationChannel(channel)
    }

    private fun build(notice: SessionNotice): Notification {
        val locale = context.resources.configuration.locales[0]
        val builder = base()
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(base().setContentTitle(context.getString(R.string.notification_public)).build())
            .setContentIntent(openApp(REQUEST_OPEN, null))
        when (notice) {
            is SessionNotice.Running -> builder
                .setContentTitle(context.getString(R.string.notification_running_title, notice.shoeName))
                .setContentText(context.getString(R.string.notification_running_text, Formatters.time(notice.startUtc, notice.zoneId, locale)))
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(notice.timerBaseUtc)
                .addAction(0, context.getString(R.string.session_pause), action(SessionActions.PAUSE, notice.sessionId))
            is SessionNotice.Paused -> builder
                .setContentTitle(context.getString(R.string.notification_paused_title, notice.shoeName))
                .setContentText(
                    context.getString(
                        R.string.notification_paused_text,
                        Formatters.time(notice.pausedAtUtc, notice.zoneId, locale),
                        duration(notice.activeMillis),
                    ),
                )
                .setShowWhen(false)
                .addAction(0, context.getString(R.string.session_resume), action(SessionActions.RESUME, notice.sessionId))
        }
        // End asks first, like everywhere in the app: it opens the app's "End this session?" pop-up.
        builder.addAction(0, context.getString(R.string.session_end), openApp(REQUEST_END, notice.sessionId))
        return builder.build()
    }

    /** What both versions share: the walking icon in the accent colour. */
    private fun base() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_walk)
        .setColor(ACCENT)

    private fun duration(millis: Long): String {
        val (hours, minutes) = Formatters.hoursAndMinutes(millis)
        return if (hours > 0) {
            context.getString(R.string.duration_hours_minutes, hours, minutes)
        } else {
            context.getString(R.string.duration_minutes, minutes)
        }
    }

    /** Pause or Resume: handled by [SessionActionReceiver] without opening the app. */
    private fun action(action: String, sessionId: String): PendingIntent {
        val intent = Intent(context, SessionActionReceiver::class.java)
            .setAction(action)
            .putExtra(SessionActions.EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getBroadcast(context, requestCode(action), intent, FLAGS)
    }

    /**
     * Opens the app through its notification entry, which other apps can't start (not exported). With
     * [endSessionId], the app asks "End this session?" for that session.
     */
    private fun openApp(requestCode: Int, endSessionId: String?): PendingIntent {
        val intent = Intent().setClassName(context, notificationEntry())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        endSessionId?.let { intent.putExtra(SessionActions.EXTRA_END_SESSION_ID, it) }
        return PendingIntent.getActivity(context, requestCode, intent, FLAGS)
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "session"

        /** DESIGN.md: the notification's accent is the app's purple. */
        private const val ACCENT = 0xFF7942DE.toInt()

        private const val REQUEST_OPEN = 0
        private const val REQUEST_END = 1
        private fun requestCode(action: String) = if (action == SessionActions.PAUSE) 2 else 3

        /** Immutable: nobody can change where the buttons lead. Update: each post carries the current session id. */
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        /** The notification entry alias (AndroidManifest.xml); named after the code's package, like the tag entry. */
        fun notificationEntry(): String = MainActivity::class.java.name.substringBeforeLast('.') + ".NotificationEntry"
    }
}
