package io.github.drjkn7.shoestep.ui.nfc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.nfc.LinkProblem
import io.github.drjkn7.shoestep.nfc.LinkRequest
import io.github.drjkn7.shoestep.nfc.TagCoordinator
import io.github.drjkn7.shoestep.nfc.TagPrompt
import io.github.drjkn7.shoestep.nfc.WriteResult
import io.github.drjkn7.shoestep.ui.common.ColourDot
import io.github.drjkn7.shoestep.ui.common.FilterTouchesWhenObscured
import io.github.drjkn7.shoestep.ui.session.SwitchSessionDialog
import kotlinx.coroutines.launch

/**
 * Shows whatever the last tag tap needs: a question, the "hold the phone to the tag" window while
 * linking, or the "Tag linked" note. Every pop-up cancels on Back or a tap outside it.
 */
@Composable
fun TagPromptHost(tags: TagCoordinator, pairs: List<ShoeWithStats>) {
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_new_title)) },
        text = {
            FilterTouchesWhenObscured()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tag_new_body))
                Button(onClick = onLinkExisting, enabled = canLinkExisting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tag_link_existing))
                }
                OutlinedButton(onClick = onAddNew, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tag_add_new_pair))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PickPairDialog(pairs: List<ShoeWithStats>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_pick_title)) },
        text = {
            FilterTouchesWhenObscured()
            LazyColumn {
                items(pairs, key = { it.shoe.id }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { onPick(item.shoe.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColourDot(item.shoe.accentColor, size = 20.dp)
                        Text(
                            item.shoe.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RunningTapDialog(shoeName: String, onPause: () -> Unit, onEnd: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_running_title, shoeName)) },
        text = {
            FilterTouchesWhenObscured()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tag_running_body))
                OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.session_pause))
                }
                Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.session_end))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun LinkTagDialog(request: LinkRequest, onLockChange: (Boolean) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.tag_linking_title, request.shoeName)) },
        text = {
            FilterTouchesWhenObscured()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tag_linking_body))
                request.problem?.let {
                    Text(problemText(it), color = MaterialTheme.colorScheme.error)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = request.lock, role = Role.Checkbox, onValueChange = onLockChange),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = request.lock, onCheckedChange = null)
                    Text(stringResource(R.string.tag_lock), modifier = Modifier.padding(start = 12.dp))
                }
                // T05: the warning is always visible before the tag is tapped.
                Text(
                    stringResource(R.string.tag_lock_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (request.lock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
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

@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FilterTouchesWhenObscured()
            Text(text)
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}
