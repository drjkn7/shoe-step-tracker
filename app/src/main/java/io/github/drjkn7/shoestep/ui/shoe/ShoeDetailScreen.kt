package io.github.drjkn7.shoestep.ui.shoe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.core.CostCalculator
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.SessionWithSteps
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.ui.common.ColourDot
import io.github.drjkn7.shoestep.ui.common.ConfirmDialog
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.common.StepsLabel
import io.github.drjkn7.shoestep.ui.common.labelRes
import io.github.drjkn7.shoestep.ui.common.labelledSteps
import io.github.drjkn7.shoestep.ui.common.stepsCountText
import io.github.drjkn7.shoestep.ui.session.EndSessionDialog
import io.github.drjkn7.shoestep.ui.session.SessionButtons
import io.github.drjkn7.shoestep.ui.session.SwitchSessionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoeDetailScreen(
    viewModel: ShoeDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val active by viewModel.activeSession.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val askSwitch by viewModel.askSwitch.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    var confirmEndId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(deleted) { if (deleted) onBack() }

    val loaded = state as? DetailState.Loaded
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        loaded?.shoe?.shoe?.name ?: stringResource(R.string.title_shoe_detail),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    if (loaded != null) {
                        IconButton(onClick = onEdit) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                val archived = loaded.shoe.shoe.archived
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive)) },
                                    leadingIcon = {
                                        Icon(painterResource(if (archived) R.drawable.ic_unarchive else R.drawable.ic_archive), null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        // Archiving a pair with a session in progress ends it, so ask first.
                                        if (!archived && loaded.shoe.isActive) confirmArchive = true else viewModel.setArchived(!archived)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            DetailState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            DetailState.NotFound -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringResource(R.string.form_not_found), style = MaterialTheme.typography.bodyLarge)
            }
            is DetailState.Loaded -> DetailContent(
                item = s.shoe,
                active = active,
                history = history,
                onStart = viewModel::start,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onEnd = { confirmEndId = it },
                modifier = Modifier.padding(padding),
            )
        }
    }

    val endId = confirmEndId
    val current = active
    if (endId != null && current != null && current.session.id == endId) {
        EndSessionDialog(
            shoeName = current.shoeName,
            onConfirm = {
                confirmEndId = null
                viewModel.end(endId)
            },
            onDismiss = { confirmEndId = null },
        )
    }

    if (askSwitch && loaded != null && current != null) {
        SwitchSessionDialog(
            activeShoeName = current.shoeName,
            newShoeName = loaded.shoe.shoe.name,
            onConfirm = viewModel::confirmSwitch,
            onDismiss = viewModel::dismissSwitch,
        )
    }

    if (confirmArchive && loaded != null) {
        ConfirmDialog(
            title = stringResource(R.string.archive_title, loaded.shoe.shoe.name),
            text = stringResource(R.string.archive_running_body),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = {
                confirmArchive = false
                viewModel.setArchived(true)
            },
            onDismiss = { confirmArchive = false },
        )
    }

    if (confirmDelete && loaded != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title, loaded.shoe.shoe.name),
            text = stringResource(R.string.delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DetailContent(
    item: ShoeWithStats,
    active: ActiveSessionWithShoe?,
    history: List<SessionWithSteps>,
    onStart: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onEnd: (String) -> Unit,
    modifier: Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val shoe = item.shoe
    val dash = stringResource(R.string.dash)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColourDot(shoe.accentColor, size = 24.dp)
                    Text(
                        shoe.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                val subtitle = listOf(shoe.brand, shoe.model).filter { it.isNotEmpty() }.joinToString(" ")
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(shoe.type.labelRes()), style = MaterialTheme.typography.bodyMedium)
                Text(
                    shoe.priceMinor?.let { Formatters.price(it, shoe.currencyCode, locale) }
                        ?: stringResource(R.string.detail_no_price),
                    style = MaterialTheme.typography.bodyMedium,
                )
                shoe.purchaseEpochDay?.let {
                    Text(stringResource(R.string.detail_purchased, Formatters.date(it, locale)), style = MaterialTheme.typography.bodyMedium)
                }
                if (shoe.archived) {
                    Text(
                        stringResource(R.string.detail_archived_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.detail_stats_title), style = MaterialTheme.typography.titleMedium)
                StatRow(
                    stringResource(R.string.detail_total_steps),
                    labelledSteps(
                        Formatters.count(item.totalSteps, locale),
                        StepsLabel.of(item.hasPendingSteps, item.hasUnfinalSteps),
                    ),
                )
                StatRow(stringResource(R.string.detail_sessions), Formatters.count(item.sessionCount.toLong(), locale))
                val cost = CostCalculator.cost(shoe.priceMinor, shoe.currencyCode, item.totalSteps)
                StatRow(
                    stringResource(R.string.detail_cost_per_1000),
                    Formatters.costPerThousand(cost, shoe.currencyCode, locale) ?: dash,
                )
            }
        }

        // Archived pairs can't start a session (archiving ends any running one).
        if (!shoe.archived) {
            val mine = active?.session?.takeIf { it.shoeId == shoe.id }
            val status = when {
                mine?.state == SessionState.RUNNING ->
                    stringResource(R.string.detail_session_running, Formatters.time(mine.startUtc, mine.zoneId, locale))
                mine?.state == SessionState.PAUSED -> stringResource(R.string.detail_session_paused)
                active != null -> stringResource(R.string.detail_other_active, active.shoeName)
                else -> null
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status != null) Text(status, style = MaterialTheme.typography.bodyLarge)
                SessionButtons(
                    state = mine?.state,
                    onStart = onStart,
                    onPause = { mine?.let { onPause(it.id) } },
                    onResume = { mine?.let { onResume(it.id) } },
                    onEnd = { mine?.let { onEnd(it.id) } },
                )
            }
        }

        HistoryCard(history)
    }
}

@Composable
private fun HistoryCard(history: List<SessionWithSteps>) {
    val locale = LocalConfiguration.current.locales[0]
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium)
            if (history.isEmpty()) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium)
            }
            history.forEach { row ->
                val session = row.session
                val steps = labelledSteps(
                    stepsCountText(row.totalSteps),
                    StepsLabel.of(row.hasPendingSteps, row.hasUnfinalSteps),
                )
                val (hours, minutes) = Formatters.hoursAndMinutes(row.activeMillis)
                val detail = when {
                    session.state != SessionState.ENDED -> stringResource(R.string.history_in_progress)
                    hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
                    else -> stringResource(R.string.duration_minutes, minutes)
                }
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                    Text(
                        Formatters.dateTime(session.startUtc, session.zoneId, locale),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(
                            R.string.history_row,
                            detail,
                            steps,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (session.endReason == EndReason.SWITCH) {
                        Text(
                            stringResource(R.string.history_switched),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
