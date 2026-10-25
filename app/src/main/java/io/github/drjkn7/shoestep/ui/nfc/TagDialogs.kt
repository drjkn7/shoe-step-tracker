package io.github.drjkn7.shoestep.ui.nfc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.nfc.LinkProblem
import io.github.drjkn7.shoestep.nfc.LinkRequest
import io.github.drjkn7.shoestep.nfc.TagCoordinator
import io.github.drjkn7.shoestep.nfc.TagPrompt
import io.github.drjkn7.shoestep.nfc.WriteResult
import io.github.drjkn7.shoestep.ui.common.ColourDot
import io.github.drjkn7.shoestep.ui.common.DialogAction
import io.github.drjkn7.shoestep.ui.common.DialogText
import io.github.drjkn7.shoestep.ui.common.InfoDialog
import io.github.drjkn7.shoestep.ui.common.SilverDialog
import io.github.drjkn7.shoestep.ui.session.SwitchSessionDialog
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import io.github.drjkn7.shoestep.ui.theme.accentBrush
import io.github.drjkn7.shoestep.ui.theme.pressedIn
import io.github.drjkn7.shoestep.ui.theme.raised
import io.github.drjkn7.shoestep.ui.theme.sheenBrush
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Shows whatever the last tag tap needs: a question, the "hold the phone to the tag" window while
 * linking, or the "Tag linked" note. Every pop-up cancels on Back or a tap outside it.
 */
@Composable
fun TagPromptHost(tags: TagCoordinator, pairsFlow: Flow<List<ShoeEntity>>) {
    val pairs by pairsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val prompt by tags.prompt.collectAsStateWithLifecycle()
    val linking by tags.linking.collectAsStateWithLifecycle()
    val notice by tags.notice.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    when (val p = prompt) {
        TagPrompt.NewTag -> NewTagDialog(
            canLinkExisting = pairs.isNotEmpty(),
            onLinkExisting = tags::linkToExistingPair,
            onAddNew = { scope.launch { tags.addNewPair() } },
            onDismiss = tags::dismissPrompt,
        )
        TagPrompt.PickPair -> PickPairDialog(
            pairs = pairs,
            onPick = { id -> scope.launch { tags.pickPair(id) } },
            onDismiss = tags::dismissPrompt,
        )
        is TagPrompt.RunningTap -> RunningTapDialog(
            shoeName = p.shoeName,
            onPause = { scope.launch { tags.pause() } },
            onEnd = { scope.launch { tags.end() } },
            onDismiss = tags::dismissPrompt,
        )
        is TagPrompt.Switch -> SwitchSessionDialog(
            activeShoeName = p.activeShoeName,
            newShoeName = p.newShoeName,
            onConfirm = { scope.launch { tags.confirmSwitch() } },
            onDismiss = tags::dismissPrompt,
        )
        is TagPrompt.Archived -> InfoDialog(
            title = stringResource(R.string.tag_archived_title, p.shoeName),
            text = stringResource(R.string.tag_archived_body),
            onDismiss = tags::dismissPrompt,
        )
        null -> Unit
    }

    linking?.let { request ->
        LinkTagDialog(request, onLockChange = tags::setLock, onCancel = tags::cancelLinking)
    }

    notice?.let {
        val body = stringResource(R.string.tag_linked_body, it.shoeName)
        InfoDialog(
            title = stringResource(R.string.tag_linked_title),
            text = if (it.lockFailed) body + "\n\n" + stringResource(R.string.tag_linked_lock_failed) else body,
            onDismiss = tags::dismissNotice,
        )
    }
}

@Composable
private fun NewTagDialog(canLinkExisting: Boolean, onLinkExisting: () -> Unit, onAddNew: () -> Unit, onDismiss: () -> Unit) {
    SilverDialog(
        title = stringResource(R.string.tag_new_title),
        onDismiss = onDismiss,
        actions = listOf(
            DialogAction(stringResource(R.string.tag_link_existing), DialogAction.Kind.PRIMARY, onLinkExisting, enabled = canLinkExisting),
            DialogAction(stringResource(R.string.tag_add_new_pair), DialogAction.Kind.SECONDARY, onAddNew),
            DialogAction(stringResource(R.string.action_cancel), DialogAction.Kind.TEXT, onDismiss),
        ),
    ) { DialogText(stringResource(R.string.tag_new_body)) }
}

@Composable
private fun PickPairDialog(pairs: List<ShoeEntity>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    SilverDialog(
        title = stringResource(R.string.tag_pick_title),
        onDismiss = onDismiss,
        actions = listOf(DialogAction(stringResource(R.string.action_cancel), DialogAction.Kind.TEXT, onDismiss)),
    ) {
        // A plain column: the pop-up body already scrolls.
        Column {
            pairs.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = SilverSpacing.touch)
                        .clickable(role = Role.Button) { onPick(item.id) }
                        .padding(vertical = SilverSpacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColourDot(item.accentColor)
                    Text(
                        item.name,
                        style = SilverTheme.type.button,
                        color = SilverTheme.colors.textPrimary,
                        modifier = Modifier.padding(start = SilverSpacing.m),
                    )
                }
            }
        }
    }
}

/** A tag tapped while its pair's session runs (PNG 15). Cancel only closes the pop-up. */
@Composable
private fun RunningTapDialog(shoeName: String, onPause: () -> Unit, onEnd: () -> Unit, onDismiss: () -> Unit) {
    SilverDialog(
        title = stringResource(R.string.tag_running_title, shoeName),
        onDismiss = onDismiss,
        actions = listOf(
            DialogAction(stringResource(R.string.session_pause), DialogAction.Kind.SECONDARY, onPause),
            DialogAction(stringResource(R.string.session_end), DialogAction.Kind.PRIMARY, onEnd),
            DialogAction(stringResource(R.string.action_cancel), DialogAction.Kind.TEXT, onDismiss),
        ),
    ) { DialogText(stringResource(R.string.tag_running_body)) }
}

/** "Hold your phone to the tag" while linking (PNG 18), with the optional permanent lock. */
@Composable
private fun LinkTagDialog(request: LinkRequest, onLockChange: (Boolean) -> Unit, onCancel: () -> Unit) {
    val c = SilverTheme.colors
    SilverDialog(
        title = stringResource(R.string.tag_linking_title, request.shoeName),
        onDismiss = onCancel,
        actions = listOf(DialogAction(stringResource(R.string.action_cancel), DialogAction.Kind.TEXT, onCancel)),
    ) {
        DialogText(stringResource(R.string.tag_linking_body))
        request.problem?.let {
            Text(problemText(it), style = SilverTheme.type.body, color = c.danger)
        }
        NfcRings(Modifier.align(Alignment.CenterHorizontally))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = SilverSpacing.touch)
                .toggleable(value = request.lock, role = Role.Checkbox, onValueChange = onLockChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SilverCheckbox(request.lock)
            Text(
                stringResource(R.string.tag_lock),
                style = SilverTheme.type.button.copy(fontSize = SilverTheme.type.section.fontSize),
                color = c.textPrimary,
                modifier = Modifier.padding(start = SilverSpacing.m),
            )
        }
        // T05: the warning is always visible before the tag is tapped; outlined once the lock is ticked.
        Text(
            stringResource(R.string.tag_lock_warning),
            style = SilverTheme.type.body,
            color = c.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .background(c.danger.copy(alpha = if (c.isNight) 0.16f else 0.12f), SilverShapes.input)
                .then(if (request.lock) Modifier.border(1.dp, c.danger, SilverShapes.input) else Modifier)
                .padding(SilverSpacing.m),
        )
    }
}

/** Three accent rings around a raised "NFC" disc. Decorative: the text above says what to do. */
@Composable
internal fun NfcRings(modifier: Modifier = Modifier) {
    val c = SilverTheme.colors
    val ring = c.accent.first()
    Box(modifier.size(180.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        listOf(170.dp to 0.25f, 126.dp to 0.45f, 84.dp to 0.70f).forEach { (size, alpha) ->
            Box(Modifier.size(size).border(3.dp, ring.copy(alpha = alpha), SilverShapes.circle))
        }
        Box(
            Modifier.size(60.dp).raised(c, SilverShapes.circle, c.sheenBrush()),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.nfc_label), style = SilverTheme.type.label.copy(fontSize = 14.sp), color = c.accentInk)
        }
    }
}

/** Square tick box: pressed in when off, accent gradient with a white tick when on. */
@Composable
private fun SilverCheckbox(checked: Boolean) {
    val c = SilverTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(28.dp).then(if (checked) Modifier.background(c.accentBrush(), shape) else Modifier.pressedIn(c, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.textOnAccent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun problemText(problem: LinkProblem): String = when (problem) {
    is LinkProblem.InUse -> stringResource(R.string.tag_problem_in_use, problem.shoeName)
    LinkProblem.CantUse -> stringResource(R.string.tag_problem_cant_use)
    is LinkProblem.Write -> stringResource(
        when (problem.result) {
            WriteResult.READ_ONLY -> R.string.tag_problem_read_only
            WriteResult.TOO_SMALL -> R.string.tag_problem_too_small
            WriteResult.LOST -> R.string.tag_problem_lost
            WriteResult.VERIFY_FAILED, WriteResult.OK, WriteResult.LOCK_FAILED -> R.string.tag_problem_verify
        },
    )
}
