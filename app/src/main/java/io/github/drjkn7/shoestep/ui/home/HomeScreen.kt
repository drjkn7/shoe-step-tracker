package io.github.drjkn7.shoestep.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.steps.HealthConnectStepsSource
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.ui.common.AddShoeButton
import io.github.drjkn7.shoestep.ui.common.ColourDot
import io.github.drjkn7.shoestep.ui.common.IconCircleButton
import io.github.drjkn7.shoestep.ui.common.SilverCard
import io.github.drjkn7.shoestep.ui.common.SilverScreen
import io.github.drjkn7.shoestep.ui.common.StatusBadge
import io.github.drjkn7.shoestep.ui.common.StepsLabel
import io.github.drjkn7.shoestep.ui.common.SwitchRow
import io.github.drjkn7.shoestep.ui.common.distanceText
import io.github.drjkn7.shoestep.ui.common.labelRes
import io.github.drjkn7.shoestep.ui.common.labelledSteps
import io.github.drjkn7.shoestep.ui.common.screenContentPadding
import io.github.drjkn7.shoestep.ui.common.stepsCountText
import io.github.drjkn7.shoestep.ui.session.ActiveSessionCard
import io.github.drjkn7.shoestep.ui.session.EndSessionDialog
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import io.github.drjkn7.shoestep.ui.theme.pressedIn

/**
 * Home (PNGs 01–03): "My shoes", the current session, the steps and distance cards, the pairs, the "Show
 * archived" switch, and "Add pair" at the bottom end. Everything scrolls together under the gear.
 */
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
    val showDistanceCard by viewModel.showDistanceCard.collectAsStateWithLifecycle()
    var confirmEndId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectStepsSource.permissionContract()) {
        viewModel.onStepsPermissionResult()
    }
    // Access can change while the app is away (e.g. in Health Connect settings), so re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSteps() }

    SilverScreen(
        actions = {
            IconCircleButton(painterResource(R.drawable.ic_settings), stringResource(R.string.nav_settings), onOpenSettings)
        },
        floatingButton = { AddShoeButton(onAddShoe) },
    ) {
        val list = shoes
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(bottomSpace = ADD_BUTTON_SPACE, top = 0.dp),
            verticalArrangement = Arrangement.spacedBy(SilverSpacing.m),
        ) {
            item(key = "title") {
                Text(
                    stringResource(R.string.title_home),
                    style = SilverTheme.type.display,
                    color = SilverTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = SilverSpacing.s).semantics { heading() },
                )
            }
            active?.let { current ->
                item(key = "session") {
                    val id = current.session.id
                    ActiveSessionCard(
                        active = current,
                        stepsSoFar = stepsSoFar,
                        onOpen = { onOpenShoe(current.session.shoeId) },
                        onPause = { viewModel.pause(id) },
                        onResume = { viewModel.resume(id) },
                        onEnd = { confirmEndId = id },
                    )
                }
            }
            stepsAccess?.takeIf { it != StepsAccess.READY }?.let { access ->
                item(key = "steps") {
                    StepsAccessCard(
                        access = access,
                        onAllow = { permissionLauncher.launch(HealthConnectStepsSource.PERMISSIONS) },
                        onInstall = { HealthConnectStepsSource.openInstallPage(context) },
                    )
                }
            }
            if (showDistanceCard) {
                item(key = "distance") {
                    DistanceAccessCard(
                        onAllow = { permissionLauncher.launch(setOf(HealthConnectStepsSource.DISTANCE_PERMISSION)) },
                        onNotNow = viewModel::dismissDistanceCard,
                    )
                }
            }
            when {
                // The heading shows at once; the pairs follow a moment later from the database.
                list == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(SilverSpacing.xl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SilverTheme.colors.accentInk)
                    }
                }
                list.isEmpty() && showArchived -> item(key = "empty") { EmptyCard(title = null, body = stringResource(R.string.home_empty_archived)) }
                list.isEmpty() -> item(key = "empty") {
                    EmptyCard(title = stringResource(R.string.home_empty_title), body = stringResource(R.string.home_empty_body))
                }
                else -> items(list, key = { it.shoe.id }) { item ->
                    val paused = active?.session?.let { it.shoeId == item.shoe.id && it.state == SessionState.PAUSED } == true
                    ShoeRow(item, paused, onClick = { onOpenShoe(item.shoe.id) })
                }
            }
            if (list != null) item(key = "archived") {
                SilverCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = SilverSpacing.xs)) {
                    SwitchRow(
                        label = stringResource(R.string.home_show_archived),
                        checked = showArchived,
                        onCheckedChange = viewModel::setShowArchived,
                    )
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

/** Room under the list so the last card can scroll clear of "Add pair". */
private val ADD_BUTTON_SPACE = 96.dp

/** A pair on Home (PNG 01): colour circle, name and type; total steps with distance under; status badge. */
@Composable
internal fun ShoeRow(item: ShoeWithStats, paused: Boolean, onClick: () -> Unit) {
    val c = SilverTheme.colors
    val shoe = item.shoe
    // "(counted later)" while any segment has no count yet, "About" while counts may still change.
    val stepsLabel = StepsLabel.of(item.hasPendingSteps, item.hasUnfinalSteps)
    val stepsText = labelledSteps(stepsCountText(item.totalSteps), stepsLabel)
    // Settled at zero (a new pair, or sessions with no steps): "No steps yet" instead of "0 steps".
    val noSteps = item.totalSteps == 0L && stepsLabel == StepsLabel.EXACT
    val distance = distanceText(item.totalDistanceMeters, item.hasUnfinalDistance)
    val nameAndType = @Composable { modifier: Modifier ->
        Column(modifier) {
            Text(shoe.name, style = SilverTheme.type.section.copy(fontSize = 18.sp), color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(shoe.type.labelRes()), style = SilverTheme.type.body, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (shoe.archived) {
                Text(stringResource(R.string.home_archived_badge), style = SilverTheme.type.note, color = c.textMuted)
            }
        }
    }
    val totals = @Composable { modifier: Modifier, align: Alignment.Horizontal ->
        Column(modifier, horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(SilverSpacing.xs)) {
            if (item.isActive) {
                StatusBadge(
                    stringResource(if (paused) R.string.home_paused_badge else R.string.home_running_badge),
                    running = !paused,
                )
            }
            if (noSteps) {
                // Nothing to read yet: said quietly, so the eye goes to the pairs with real totals.
                Text(stringResource(R.string.steps_none_yet), style = SilverTheme.type.body, color = c.textMuted)
            } else {
                Text(stepsText, style = SilverTheme.type.button.copy(fontSize = 16.sp), color = c.accentInk)
            }
            if (!noSteps || item.totalDistanceMeters != null) {
                Text(
                    distance.shown,
                    style = SilverTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textMuted,
                    modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(distance.spoken) },
                )
            }
        }
    }
    SilverCard(onClick = onClick) {
        if (LocalDensity.current.fontScale < LARGE_FONT_SCALE) {
            BoxWithConstraints {
                val half = maxWidth / 2
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
                    ColourDot(shoe.accentColor)
                    nameAndType(Modifier.weight(1f))
                    // The totals sit at the end and never take more than half the row, so a long count
                    // never squeezes the name.
                    totals(Modifier.widthIn(max = half), Alignment.End)
                }
            }
        } else {
            // Large text: the totals go under the name instead of beside it.
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
                ColourDot(shoe.accentColor, Modifier.padding(top = SilverSpacing.xs))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SilverSpacing.s)) {
                    nameAndType(Modifier)
                    totals(Modifier, Alignment.Start)
                }
            }
        }
    }
}

/** From this font size (Android's text-size setting) Home rows stack their totals under the name. */
private const val LARGE_FONT_SCALE = 1.5f

/** No pairs (PNG 02): a walking figure in a pressed-in circle, a title and what to do next. */
@Composable
private fun EmptyCard(title: String?, body: String) {
    val c = SilverTheme.colors
    SilverCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
            if (title != null) {
                Box(Modifier.size(72.dp).pressedIn(c, SilverShapes.circle), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_directions_walk), contentDescription = null, tint = c.accentInk, modifier = Modifier.size(32.dp))
                }
                Text(title, style = SilverTheme.type.section.copy(fontSize = 20.sp, lineHeight = 26.sp), color = c.textPrimary, textAlign = TextAlign.Center)
            }
            Text(body, style = SilverTheme.type.body.copy(fontSize = SilverTheme.type.button.fontSize), color = c.textPrimary, textAlign = TextAlign.Center)
        }
    }
}
