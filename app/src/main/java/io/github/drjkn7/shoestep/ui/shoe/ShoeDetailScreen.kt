package io.github.drjkn7.shoestep.ui.shoe

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.core.CostCalculator
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.SessionWithSteps
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.steps.SoFarSteps
import io.github.drjkn7.shoestep.ui.common.CardLabel
import io.github.drjkn7.shoestep.ui.common.ColourDot
import io.github.drjkn7.shoestep.ui.common.ConfirmDialog
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.common.HistoryRow
import io.github.drjkn7.shoestep.ui.common.IconCircleButton
import io.github.drjkn7.shoestep.ui.common.MenuEntry
import io.github.drjkn7.shoestep.ui.common.NavIcon
import io.github.drjkn7.shoestep.ui.common.ScreenColumn
import io.github.drjkn7.shoestep.ui.common.ScreenLoading
import io.github.drjkn7.shoestep.ui.common.SecondaryButton
import io.github.drjkn7.shoestep.ui.common.SilverCard
import io.github.drjkn7.shoestep.ui.common.SilverMenu
import io.github.drjkn7.shoestep.ui.common.SilverScreen
import io.github.drjkn7.shoestep.ui.common.StatItem
import io.github.drjkn7.shoestep.ui.common.StatsGrid
import io.github.drjkn7.shoestep.ui.common.StepsLabel
import io.github.drjkn7.shoestep.ui.common.TextActionButton
import io.github.drjkn7.shoestep.ui.common.labelRes
import io.github.drjkn7.shoestep.ui.common.labelledSteps
import io.github.drjkn7.shoestep.ui.common.stepsCountText
import io.github.drjkn7.shoestep.ui.session.EndSessionDialog
import io.github.drjkn7.shoestep.ui.session.SessionButtons
import io.github.drjkn7.shoestep.ui.session.SoFarCount
import io.github.drjkn7.shoestep.ui.session.SwitchSessionDialog
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme

@Composable
fun ShoeDetailScreen(
    viewModel: ShoeDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    hasNfc: Boolean,
    isNfcOn: () -> Boolean,
    onLinkTag: () -> Unit,
    onUnlinkTag: () -> Unit,
) {
    val context = LocalContext.current
    var confirmUnlink by rememberSaveable { mutableStateOf(false) }
    var nfcOff by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val active by viewModel.activeSession.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val askSwitch by viewModel.askSwitch.collectAsStateWithLifecycle()
    val stepsSoFar by viewModel.stepsSoFar.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSteps() }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    var confirmEndId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(deleted) { if (deleted) onBack() }

    val loaded = state as? DetailState.Loaded
    SilverScreen(
        title = stringResource(R.string.title_shoe_detail),
        navIcon = NavIcon.BACK,
        onNavigate = onBack,
        actions = {
            if (loaded != null) {
                Box {
                    IconCircleButton(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more), onClick = { menuOpen = true })
                    val archived = loaded.shoe.shoe.archived
                    // PNG 11: Edit, Archive (or Unarchive), Delete.
                    SilverMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        entries = listOf(
                            MenuEntry(stringResource(R.string.action_edit), onClick = {
                                menuOpen = false
                                onEdit()
                            }),
                            MenuEntry(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive), onClick = {
                                menuOpen = false
                                // Archiving a pair with a session in progress ends it, so ask first.
                                if (!archived && loaded.shoe.isActive) confirmArchive = true else viewModel.setArchived(!archived)
                            }),
                            MenuEntry(stringResource(R.string.action_delete), danger = true, onClick = {
                                menuOpen = false
                                confirmDelete = true
                            }),
                        ),
                    )
                }
            }
        },
    ) {
        when (val s = state) {
            DetailState.Loading -> ScreenLoading()
            DetailState.NotFound -> ScreenColumn {
                SilverCard { Text(stringResource(R.string.form_not_found), style = SilverTheme.type.body, color = SilverTheme.colors.textPrimary) }
            }
            is DetailState.Loaded -> DetailContent(
                item = s.shoe,
                active = active,
                history = history,
                stepsSoFar = stepsSoFar,
                onStart = viewModel::start,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onEnd = { confirmEndId = it },
                tagCard = {
                    TagCard(
                        linked = s.shoe.shoe.tagUuid != null,
                        archived = s.shoe.shoe.archived,
                        hasNfc = hasNfc,
                        onLink = { if (isNfcOn()) onLinkTag() else nfcOff = true },
                        onUnlink = { confirmUnlink = true },
                    )
                },
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

    if (confirmUnlink && loaded != null) {
        ConfirmDialog(
            title = stringResource(R.string.tag_unlink_title),
            text = stringResource(R.string.tag_unlink_body, loaded.shoe.shoe.name),
            confirmLabel = stringResource(R.string.tag_unlink),
            onConfirm = {
                confirmUnlink = false
                onUnlinkTag()
            },
            onDismiss = { confirmUnlink = false },
        )
    }

    if (nfcOff) {
        ConfirmDialog(
            title = stringResource(R.string.tag_nfc_off_title),
            text = stringResource(R.string.tag_nfc_off_body),
            confirmLabel = stringResource(R.string.tag_open_settings),
            destructive = false,
            onConfirm = {
                nfcOff = false
                try {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    // No NFC settings screen on this phone: the message already explains what to do.
                }
            },
            onDismiss = { nfcOff = false },
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
    stepsSoFar: SoFarSteps?,
    onStart: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onEnd: (String) -> Unit,
    tagCard: @Composable () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val shoe = item.shoe
    val dash = stringResource(R.string.dash)
    ScreenColumn {
        PairHeader(item)

        SilverCard {
            CardLabel(stringResource(R.string.detail_stats_title))
            val stepsLabel = StepsLabel.of(item.hasPendingSteps, item.hasUnfinalSteps)
            val (hours, minutes) = Formatters.hoursAndMinutes(item.activeMillis)
            val cost = CostCalculator.cost(shoe.priceMinor, shoe.currencyCode, item.totalSteps)
            // Distance figures show a dash until distance is read from Health Connect (M2 Phase 5).
            StatsGrid(
                listOf(
                    StatItem(stringResource(R.string.detail_total_steps), labelledSteps(Formatters.count(item.totalSteps, locale), stepsLabel)),
                    StatItem(stringResource(R.string.detail_total_distance), dash),
                    StatItem(stringResource(R.string.detail_sessions), Formatters.count(item.sessionCount.toLong(), locale)),
                    StatItem(
                        stringResource(R.string.detail_walking_time),
                        if (hours > 0) stringResource(R.string.duration_hours_minutes, hours, minutes)
                        else stringResource(R.string.duration_minutes, minutes),
                    ),
                    StatItem(
                        stringResource(R.string.detail_average_steps),
                        item.averageStepsPerSession?.let { labelledSteps(Formatters.count(it, locale), stepsLabel) } ?: dash,
                        extra = dash,
                    ),
                    StatItem(
                        stringResource(R.string.detail_last_worn),
                        item.lastUsedUtc?.let { Formatters.date(Formatters.localEpochDay(it), locale) } ?: dash,
                    ),
                    StatItem(stringResource(R.string.detail_cost_per_1000), Formatters.costPerThousand(cost, shoe.currencyCode, locale) ?: dash),
                    StatItem(stringResource(R.string.detail_cost_per_km), dash),
                ),
                modifier = Modifier.padding(top = SilverSpacing.s),
            )
        }

        // Archived pairs can't start a session (archiving ends any running one).
        if (!shoe.archived) {
            val mine = active?.session?.takeIf { it.shoeId == shoe.id }
            val buttons = @Composable { modifier: Modifier ->
                SessionButtons(
                    state = mine?.state,
                    onStart = onStart,
                    onPause = { mine?.let { onPause(it.id) } },
                    onResume = { mine?.let { onResume(it.id) } },
                    onEnd = { mine?.let { onEnd(it.id) } },
                    modifier = modifier,
                )
            }
            when {
                // PNGs 09 and 10: this pair's session, with its live count.
                mine != null -> SilverCard {
                    CardLabel(
                        if (mine.state == SessionState.PAUSED) stringResource(R.string.detail_session_paused)
                        else stringResource(R.string.detail_session_running, Formatters.time(mine.startUtc, mine.zoneId, locale)),
                    )
                    SoFarCount(stepsSoFar)
                    buttons(Modifier.padding(top = SilverSpacing.s))
                }
                // Another pair is out: Start asks whether to switch.
                active != null -> SilverCard {
                    Text(
                        stringResource(R.string.detail_other_active, active.shoeName),
                        style = SilverTheme.type.body.copy(fontSize = SilverTheme.type.button.fontSize),
                        color = SilverTheme.colors.textPrimary,
                    )
                    buttons(Modifier.padding(top = SilverSpacing.s))
                }
                // PNG 08: just the Start button.
                else -> buttons(Modifier)
            }
        }

        tagCard()

        HistoryCard(history)
    }
}

/**
 * The pair's name on the backdrop (PNG 08): colour circle and name, then "type · brand model" and
 * "price · Purchased date". Read out as one item, with the name as the page heading.
 */
@Composable
private fun PairHeader(item: ShoeWithStats) {
    val c = SilverTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val shoe = item.shoe
    val subtitleStyle = SilverTheme.type.body.copy(fontSize = SilverTheme.type.button.fontSize)
    Row(
        Modifier.fillMaxWidth().padding(bottom = SilverSpacing.s).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m),
    ) {
        ColourDot(shoe.accentColor, Modifier.padding(top = 6.dp), size = 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SilverSpacing.xs)) {
            Text(
                shoe.name,
                style = SilverTheme.type.display.copy(fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
                color = c.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            val brandModel = listOf(shoe.brand, shoe.model).filter { it.isNotEmpty() }.joinToString(" ")
            Text(
                listOf(stringResource(shoe.type.labelRes()), brandModel).filter { it.isNotEmpty() }.joinToString(SEPARATOR),
                style = subtitleStyle,
                color = c.textMuted,
            )
            Text(
                listOfNotNull(
                    shoe.priceMinor?.let { Formatters.price(it, shoe.currencyCode, locale) } ?: stringResource(R.string.detail_no_price),
                    shoe.purchaseEpochDay?.let { stringResource(R.string.detail_purchased, Formatters.date(it, locale)) },
                ).joinToString(SEPARATOR),
                style = subtitleStyle,
                color = c.textMuted,
            )
            if (shoe.archived) {
                Text(stringResource(R.string.detail_archived_note), style = subtitleStyle, color = c.textPrimary)
            }
        }
    }
}

/** The pair's NFC tag (PNG 08): linked or not, with Link / Replace / Unlink (not for archived pairs). */
@Composable
private fun TagCard(linked: Boolean, archived: Boolean, hasNfc: Boolean, onLink: () -> Unit, onUnlink: () -> Unit) {
    SilverCard {
        CardLabel(stringResource(R.string.tag_card_title))
        val status = when {
            linked -> R.string.tag_linked
            !hasNfc -> R.string.tag_no_nfc
            else -> R.string.tag_not_linked
        }
        Text(stringResource(status), style = SilverTheme.type.body.copy(fontSize = SilverTheme.type.button.fontSize), color = SilverTheme.colors.textPrimary)
        if (hasNfc && !archived) {
            if (linked) {
                Row(Modifier.fillMaxWidth().padding(top = SilverSpacing.s), horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
                    SecondaryButton(stringResource(R.string.tag_replace), onLink, Modifier.weight(1f))
                    TextActionButton(stringResource(R.string.tag_unlink), onUnlink, Modifier.weight(1f), danger = true)
                }
            } else {
                SecondaryButton(stringResource(R.string.tag_link), onLink, Modifier.fillMaxWidth().padding(top = SilverSpacing.s))
            }
        }
    }
}

/** Joins the parts of the header lines: "Trail running · Saucony Peregrine 15". */
private const val SEPARATOR = " · "

@Composable
private fun HistoryCard(history: List<SessionWithSteps>) {
    val locale = LocalConfiguration.current.locales[0]
    val dash = stringResource(R.string.dash)
    // T26: a long-used pair can have thousands of sessions; build only the newest ones, a page at a time.
    var shown by rememberSaveable { mutableIntStateOf(HISTORY_PAGE_SIZE) }
    SilverCard {
        CardLabel(stringResource(R.string.history_title), Modifier.padding(bottom = SilverSpacing.s))
        if (history.isEmpty()) {
            Text(stringResource(R.string.history_empty), style = SilverTheme.type.body, color = SilverTheme.colors.textMuted)
        }
        history.take(shown).forEach { row ->
            val session = row.session
            val ended = session.state == SessionState.ENDED
            val (hours, minutes) = Formatters.hoursAndMinutes(row.activeMillis)
            val duration = if (hours > 0) {
                stringResource(R.string.duration_hours_minutes, hours, minutes)
            } else {
                stringResource(R.string.duration_minutes, minutes)
            }
            HistoryRow(
                title = stringResource(R.string.history_row, Formatters.dateTime(session.startUtc, session.zoneId, locale), duration),
                note = if (session.endReason == EndReason.SWITCH) stringResource(R.string.history_switched) else null,
                steps = if (ended) {
                    labelledSteps(stepsCountText(row.totalSteps), StepsLabel.of(row.hasPendingSteps, row.hasUnfinalSteps))
                } else {
                    stringResource(R.string.history_in_progress)
                },
                // Distance per session arrives in M2 Phase 5.
                distance = if (ended) dash else null,
            )
        }
        val remaining = history.size - shown
        if (remaining > 0) {
            val page = remaining.coerceAtMost(HISTORY_PAGE_SIZE)
            TextActionButton(
                pluralStringResource(R.plurals.history_show_older, page, page),
                onClick = { shown += HISTORY_PAGE_SIZE },
                accent = true,
            )
        }
    }
}

/** How many history rows are built at first and per "Show older sessions" tap. */
private const val HISTORY_PAGE_SIZE = 50
