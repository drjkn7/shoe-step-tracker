package io.github.drjkn7.shoestep.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.steps.SoFarSteps
import io.github.drjkn7.shoestep.ui.common.CardLabel
import io.github.drjkn7.shoestep.ui.common.ColourDot
import io.github.drjkn7.shoestep.ui.common.ConfirmDialog
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.common.PrimaryButton
import io.github.drjkn7.shoestep.ui.common.SecondaryButton
import io.github.drjkn7.shoestep.ui.common.SilverCard
import io.github.drjkn7.shoestep.ui.common.stepsCountText
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme

/**
 * Start when idle; Pause + End while running; Resume + End while paused (PNGs 08–10).
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
        SessionState.RUNNING -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
            SecondaryButton(stringResource(R.string.session_pause), onPause, Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.session_end), onEnd, Modifier.weight(1f))
        }
        SessionState.PAUSED -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
            PrimaryButton(stringResource(R.string.session_resume), onResume, Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.session_end), onEnd, Modifier.weight(1f))
        }
        null, SessionState.ENDED -> PrimaryButton(stringResource(R.string.session_start), onStart, modifier.fillMaxWidth())
    }
}

/**
 * The live count of a running session: "About 3,214" large with "steps so far" beside it, then the
 * distance in accent ink when known ([distanceText], from Phase 5). Read out as one sentence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoFarCount(stepsSoFar: SoFarSteps?, distanceText: String? = null) {
    val c = SilverTheme.colors
    when (stepsSoFar) {
        is SoFarSteps.Count -> {
            val sentence = stringResource(R.string.session_card_steps_so_far, stepsCountText(stepsSoFar.steps))
            val number = Formatters.count(stepsSoFar.steps, LocalConfiguration.current.locales[0])
            FlowRow(
                Modifier.clearAndSetSemantics { text = AnnotatedString(sentence) },
                horizontalArrangement = Arrangement.spacedBy(SilverSpacing.s),
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                Text(stringResource(R.string.steps_about, number), style = SilverTheme.type.display, color = c.textPrimary)
                Text(
                    pluralStringResource(R.plurals.session_card_steps_so_far_unit, stepsSoFar.steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                    style = SilverTheme.type.body.copy(fontSize = SilverTheme.type.section.fontSize),
                    color = c.textMuted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        SoFarSteps.Later -> Text(stringResource(R.string.session_card_steps_later), style = SilverTheme.type.body, color = c.textMuted)
        null -> Unit
    }
    distanceText?.let {
        Text(stringResource(R.string.steps_about, it), style = SilverTheme.type.section.copy(fontWeight = FontWeight.Medium), color = c.accentInk)
    }
}

/** Home's card for the running or paused session (PNG 01); tapping it opens that pair. */
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
    SilverCard(modifier, onClick = onOpen) {
        // Not a heading: the whole card is one button, and TalkBack would call the button a heading.
        CardLabel(stringResource(R.string.session_card_title), heading = false)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
            ColourDot(active.shoeAccentColor)
            Text(status, style = SilverTheme.type.body.copy(fontSize = SilverTheme.type.section.fontSize), color = SilverTheme.colors.textPrimary)
        }
        SoFarCount(stepsSoFar)
        SessionButtons(
            session.state,
            onStart = {},
            onPause = onPause,
            onResume = onResume,
            onEnd = onEnd,
            modifier = Modifier.padding(top = SilverSpacing.s),
        )
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
