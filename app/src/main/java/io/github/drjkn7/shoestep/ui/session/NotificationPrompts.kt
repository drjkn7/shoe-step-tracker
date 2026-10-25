package io.github.drjkn7.shoestep.ui.session

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.notify.AndroidNotificationAccess
import io.github.drjkn7.shoestep.ui.common.DialogAction
import io.github.drjkn7.shoestep.ui.common.DialogText
import io.github.drjkn7.shoestep.ui.common.SilverDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The session notification's pop-ups, above every screen:
 * - "Show sessions in notifications?" (PNG 12), once, the first time a session runs on Android 13+
 *   without the permission. Continue leads to Android's own prompt; Not now (or refusing there) turns
 *   the setting off, and it can be turned on again in Settings.
 * - "End this session?" after the notification's End session button, if that session is still active.
 */
@Composable
fun NotificationPromptHost(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val settings = container.settings
    val active by container.sessionManager.activeSession.collectAsStateWithLifecycle(initialValue = null)
    val on by settings.sessionNotification.collectAsStateWithLifecycle(initialValue = false)
    val asked by settings.notificationAsked.collectAsStateWithLifecycle(initialValue = true)

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            if (!granted) settings.setSessionNotification(false)
            container.sessionNotifier.refresh()
        }
    }
    // Back or a tap outside is not an answer: the question is hidden until the app is next opened.
    var hidden by rememberSaveable { mutableStateOf(false) }
    if (active != null && on && !asked && !hidden && container.notificationAccess.needsPermission()) {
        AskNotificationsDialog(
            onContinue = {
                scope.launch { settings.markNotificationAsked() }
                permission.launch(AndroidNotificationAccess.PERMISSION)
            },
            onDismiss = { hidden = true },
            onNotNow = {
                scope.launch {
                    settings.markNotificationAsked()
                    settings.setSessionNotification(false)
                }
            },
        )
    }

    // A stale request (that session ended meanwhile, or another one is active) is dropped unseen.
    val endRequest by container.endRequests.collectAsStateWithLifecycle()
    var toEnd by remember { mutableStateOf<ActiveSessionWithShoe?>(null) }
    LaunchedEffect(endRequest) {
        val requested = endRequest ?: return@LaunchedEffect
        val current = container.sessionManager.activeSession.first()
        if (current?.session?.id == requested) toEnd = current else container.endRequests.value = null
    }
    toEnd?.let { session ->
        val close = {
            toEnd = null
            container.endRequests.value = null
        }
        EndSessionDialog(
            shoeName = session.shoeName,
            onConfirm = {
                close()
                scope.launch { container.sessionManager.end(session.session.id) }
            },
            onDismiss = close,
        )
    }
}

@Composable
private fun AskNotificationsDialog(onContinue: () -> Unit, onDismiss: () -> Unit, onNotNow: () -> Unit) {
    SilverDialog(
        title = stringResource(R.string.notify_ask_title),
        onDismiss = onDismiss,
        actions = listOf(
            DialogAction(stringResource(R.string.notify_ask_not_now), DialogAction.Kind.TEXT, onNotNow),
            DialogAction(stringResource(R.string.notify_ask_continue), DialogAction.Kind.PRIMARY, onContinue),
        ),
    ) {
        DialogText(stringResource(R.string.notify_ask_body))
    }
}
