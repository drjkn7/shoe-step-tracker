package io.github.drjkn7.shoestep.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.drjkn7.shoestep.ShoeStepApp
import io.github.drjkn7.shoestep.session.SessionManager
import io.github.drjkn7.shoestep.session.SessionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The notification's Pause and Resume buttons. */
object SessionActions {
    const val PAUSE = "io.github.drjkn7.shoestep.action.PAUSE"
    const val RESUME = "io.github.drjkn7.shoestep.action.RESUME"
    const val EXTRA_SESSION_ID = "session_id"

    /** On the notification's End session button: the session the app should offer to end. */
    const val EXTRA_END_SESSION_ID = "end_session_id"

    /**
     * Pauses or resumes [sessionId]. Through [SessionManager] like every session change, so an old
     * notification (a session that has ended, or another pair's) changes nothing.
     */
    suspend fun handle(action: String?, sessionId: String?, sessions: SessionManager): SessionResult {
        if (sessionId.isNullOrEmpty()) return SessionResult.NoChange
        return when (action) {
            PAUSE -> sessions.pause(sessionId)
            RESUME -> sessions.resume(sessionId)
            else -> SessionResult.NoChange
        }
    }
}

/**
 * Receives the Pause and Resume buttons. Not exported: only the app's own notification can reach it.
 * Steps are not read here; they are filled in the next time the app is opened, as always.
 */
class SessionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as ShoeStepApp).container
        val action = intent.action
        val sessionId = intent.getStringExtra(SessionActions.EXTRA_SESSION_ID)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                SessionActions.handle(action, sessionId, container.sessionManager)
                container.sessionNotifier.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
