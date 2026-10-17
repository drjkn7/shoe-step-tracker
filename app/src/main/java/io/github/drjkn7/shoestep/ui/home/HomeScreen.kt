package io.github.drjkn7.shoestep.ui.home

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.steps.HealthConnectStepsSource
import io.github.drjkn7.shoestep.ui.common.ColourDot
import io.github.drjkn7.shoestep.ui.common.StepsLabel
import io.github.drjkn7.shoestep.ui.common.labelledSteps
import io.github.drjkn7.shoestep.ui.common.stepsCountText
import io.github.drjkn7.shoestep.ui.session.ActiveSessionCard
import io.github.drjkn7.shoestep.ui.session.EndSessionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddShoe: () -> Unit,
    onOpenShoe: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val shoes by viewModel.shoes.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val active by viewModel.activeSession.collectAsStateWithLifecycle()
    val stepsAccess by viewModel.stepsAccess.collectAsStateWithLifecycle()
    val stepsSoFar by viewModel.stepsSoFar.collectAsStateWithLifecycle()
    var confirmEndId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectStepsSource.permissionContract()) {
        viewModel.onStepsPermissionResult()
    }
    // Access can change while the app is away (e.g. in Health Connect settings), so re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSteps() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_home)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.nav_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddShoe) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.nav_add_shoe))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            active?.let { current ->
                val id = current.session.id
                ActiveSessionCard(
                    active = current,
                    stepsSoFar = stepsSoFar,
                    onOpen = { onOpenShoe(current.session.shoeId) },
                    onPause = { viewModel.pause(id) },
                    onResume = { viewModel.resume(id) },
                    onEnd = { confirmEndId = id },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            stepsAccess?.let { access ->
                StepsAccessCard(
                    access = access,
                    onAllow = { permissionLauncher.launch(HealthConnectStepsSource.PERMISSIONS) },
                    onInstall = {
                        try {
                            context.startActivity(HealthConnectStepsSource.installIntent())
                        } catch (_: ActivityNotFoundException) {
                            // No Play Store on this phone: the card's text already explains what is needed.
                        }
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            FilterChip(
                selected = showArchived,
                onClick = { viewModel.setShowArchived(!showArchived) },
                label = { Text(stringResource(R.string.home_show_archived)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val list = shoes
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() && showArchived -> EmptyMessage(title = null, body = stringResource(R.string.home_empty_archived))
                list.isEmpty() -> EmptyMessage(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.shoe.id }) { item ->
                        val paused = active?.session?.let { it.shoeId == item.shoe.id && it.state == SessionState.PAUSED } == true
                        ShoeRow(item, paused, onClick = { onOpenShoe(item.shoe.id) })
                    }
                }
            }
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
}

@Composable
private fun ShoeRow(item: ShoeWithStats, paused: Boolean, onClick: () -> Unit) {
    val shoe = item.shoe
    val subtitle = listOf(shoe.brand, shoe.model).filter { it.isNotEmpty() }.joinToString(" ")
    // "(counted later)" while any segment has no count yet, "About" while counts may still change.
    val stepsText = labelledSteps(
        stepsCountText(item.totalSteps),
        StepsLabel.of(item.hasPendingSteps, item.hasUnfinalSteps),
    )
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColourDot(shoe.accentColor, size = 20.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(shoe.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(stepsText, style = MaterialTheme.typography.bodyMedium)
            }
            if (item.isActive) {
                val badge = if (paused) R.string.home_paused_badge else R.string.home_running_badge
                SuggestionChip(onClick = onClick, label = { Text(stringResource(badge)) })
            } else if (shoe.archived) {
                Text(
                    stringResource(R.string.home_archived_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(title: String?, body: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (title != null) {
            Icon(
                painterResource(R.drawable.ic_directions_walk),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
