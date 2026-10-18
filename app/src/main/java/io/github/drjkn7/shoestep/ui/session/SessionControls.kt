package io.github.drjkn7.shoestep.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.ui.common.ColourDot
import io.github.drjkn7.shoestep.ui.common.ConfirmDialog
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.common.stepsCountText
import io.github.drjkn7.shoestep.steps.SoFarSteps

/**
 * Start when idle; Pause + End while running; Resume + End while paused.
 * [state] is this pair's session state, or null when it has none.
 */
@Composable
fun SessionButtons(
    state: SessionState?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SessionState.RUNNING -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.session_pause)) }
            Button(onClick = onEnd, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.session_end)) }
        }
        SessionState.PAUSED -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.session_resume)) }
            OutlinedButton(onClick = onEnd, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.session_end)) }
        }
        null, SessionState.ENDED -> Button(onClick = onStart, modifier = modifier.fillMaxWidth()) {
            Text(stringResource(R.string.session_start))
        }
    }
}

/** Home's card for the running or paused session; tapping it opens that pair. */
@Composable
fun ActiveSessionCard(
    active: ActiveSessionWithShoe,
    /** Null while the first read is in progress (nothing shown yet). */
    stepsSoFar: SoFarSteps?,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val session = active.session
    val status = if (session.state == SessionState.PAUSED) {
        stringResource(R.string.session_card_paused, active.shoeName)
    } else {
        stringResource(
            R.string.session_card_running,
            active.shoeName,
            Formatters.time(session.startUtc, session.zoneId, locale),
        )
    }
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                ColourDot(active.shoeAccentColor, size = 20.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(stringResource(R.string.session_card_title), style = MaterialTheme.typography.labelLarge)
                    Text(status, style = MaterialTheme.typography.titleMedium)
                    val stepsLine = when (stepsSoFar) {
                        is SoFarSteps.Count ->
                            stringResource(R.string.session_card_steps_so_far, stepsCountText(stepsSoFar.steps))
                        SoFarSteps.Later -> stringResource(R.string.session_card_steps_later)
                        null -> null
                    }
                    stepsLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            SessionButtons(session.state, onStart = {}, onPause = onPause, onResume = onResume, onEnd = onEnd)
        }
    }
}

/** "End this session?" — End is the only session action that asks first. */
@Composable
fun EndSessionDialog(shoeName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.session_end_title),
        text = stringResource(R.string.session_end_body, shoeName),
        confirmLabel = stringResource(R.string.session_end),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = false,
    )
}

@Composable
fun SwitchSessionDialog(activeShoeName: String, newShoeName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.session_switch_title),
        text = stringResource(R.string.session_switch_body, activeShoeName, newShoeName),
        confirmLabel = stringResource(R.string.session_switch_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = false,
    )
}
