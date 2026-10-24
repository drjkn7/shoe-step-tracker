package io.github.drjkn7.shoestep.ui.gallery

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.steps.SoFarSteps
import io.github.drjkn7.shoestep.ui.common.AddShoeButton
import io.github.drjkn7.shoestep.ui.common.CardLabel
import io.github.drjkn7.shoestep.ui.common.DangerButton
import io.github.drjkn7.shoestep.ui.common.DialogAction
import io.github.drjkn7.shoestep.ui.common.DialogPanel
import io.github.drjkn7.shoestep.ui.common.DialogText
import io.github.drjkn7.shoestep.ui.common.HistoryRow
import io.github.drjkn7.shoestep.ui.common.IconCircleButton
import io.github.drjkn7.shoestep.ui.common.PickerField
import io.github.drjkn7.shoestep.ui.common.PrimaryButton
import io.github.drjkn7.shoestep.ui.common.SecondaryButton
import io.github.drjkn7.shoestep.ui.common.SegmentedControl
import io.github.drjkn7.shoestep.ui.common.SilverCard
import io.github.drjkn7.shoestep.ui.common.SilverChip
import io.github.drjkn7.shoestep.ui.common.SilverTextField
import io.github.drjkn7.shoestep.ui.common.StatItem
import io.github.drjkn7.shoestep.ui.common.StatsGrid
import io.github.drjkn7.shoestep.ui.common.StatusBadge
import io.github.drjkn7.shoestep.ui.common.SwitchRow
import io.github.drjkn7.shoestep.ui.common.TextActionButton
import io.github.drjkn7.shoestep.ui.home.ShoeRow
import io.github.drjkn7.shoestep.ui.nfc.NfcRings
import io.github.drjkn7.shoestep.ui.session.ActiveSessionCard
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import io.github.drjkn7.shoestep.ui.theme.backdrop

/*
 * Every Silver Matte component with sample data, for review in Android Studio's Preview pane and on a
 * device (ComponentGalleryTest saves pictures). Sample text is developer-facing, not app strings.
 */

internal enum class ComponentSection { BUTTONS, CHIPS_BADGES, FIELDS, CONTROLS, SHOE_ROWS, SESSION_CARD, STATS, HISTORY, DIALOGS, NFC }

@Composable
internal fun ComponentGallery(section: ComponentSection) {
    Column(
        Modifier.backdrop(SilverTheme.colors).padding(SilverSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(SilverSpacing.m),
    ) {
        when (section) {
            ComponentSection.BUTTONS -> Buttons()
            ComponentSection.CHIPS_BADGES -> ChipsAndBadges()
            ComponentSection.FIELDS -> Fields()
            ComponentSection.CONTROLS -> Controls()
            ComponentSection.SHOE_ROWS -> ShoeRows()
            ComponentSection.SESSION_CARD -> SessionCards()
            ComponentSection.STATS -> Stats()
            ComponentSection.HISTORY -> History()
            // A real pop-up is limited to the window height; give the panels the same kind of limit here.
            ComponentSection.DIALOGS -> Column(Modifier.heightIn(max = 2000.dp), verticalArrangement = Arrangement.spacedBy(SilverSpacing.m)) { Dialogs() }
            ComponentSection.NFC -> SilverCard { NfcRings(Modifier.align(Alignment.CenterHorizontally)) }
        }
    }
}

@Composable
private fun Buttons() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
        IconCircleButton(painterResource(R.drawable.ic_arrow_back), "Back", {})
        IconCircleButton(painterResource(R.drawable.ic_settings), "Settings", {})
        IconCircleButton(painterResource(R.drawable.ic_close), "Close", {})
        IconCircleButton(painterResource(R.drawable.ic_more_vert), "More options", {})
    }
    PrimaryButton("Start session", {}, Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
        SecondaryButton("Pause", {}, Modifier.weight(1f))
        PrimaryButton("End session", {}, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton("Replace tag", {}, Modifier.weight(1f))
        TextActionButton("Unlink tag", {}, Modifier.weight(1f), danger = true)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        TextActionButton("Cancel", {}, Modifier.weight(1f))
        DangerButton("Delete", {}, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton("Save", {}, Modifier.weight(1f), enabled = false)
        TextActionButton("Show 12 older sessions", {}, Modifier.weight(1f), accent = true)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AddShoeButton({}) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsAndBadges() {
    SilverCard {
        CardLabel("Type")
        var selected by remember { mutableStateOf("Trail running") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.s), verticalArrangement = Arrangement.spacedBy(SilverSpacing.s)) {
            listOf("Road running", "Trail running", "Hiking", "Walking", "Gym / training").forEach {
                SilverChip(it, selected == it, { selected = it })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.s), modifier = Modifier.padding(top = SilverSpacing.s)) {
            StatusBadge("Running", running = true)
            StatusBadge("Paused", running = false)
        }
    }
}

@Composable
private fun Fields() {
    SilverCard(register = true) {
        SilverTextField("Trail Runner 2", {}, "Name", helper = "14/60")
        SilverTextField("", {}, "Brand (optional)", placeholder = "For example, Saucony")
        SilverTextField("-5", {}, "Price (optional)", error = "The price can't be negative.")
        Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
            Column(Modifier.weight(1f)) { PickerField("Currency", "GBP", painterResource(R.drawable.ic_chevron_right), {}) }
            Column(Modifier.weight(1.4f)) { PickerField("Purchase date", "Not set", painterResource(R.drawable.ic_calendar_month), {}, placeholder = true) }
        }
    }
}

@Composable
private fun Controls() {
    SilverCard {
        var archived by remember { mutableStateOf(false) }
        var notify by remember { mutableStateOf(true) }
        SwitchRow("Show archived", archived, { archived = it })
        SwitchRow("Session notification", notify, { notify = it }, note = "Show a notification with a timer while a session runs")
    }
    SilverCard {
        CardLabel("Units")
        var unit by remember { mutableStateOf("Kilometres") }
        SegmentedControl(listOf("Kilometres", "Miles"), unit, { it }, { unit = it }, description = "Distance")
        CardLabel("Theme", Modifier.padding(top = SilverSpacing.s))
        var theme by remember { mutableStateOf("System") }
        SegmentedControl(listOf("Light", "Dark", "System"), theme, { it }, { theme = it }, description = "Theme")
    }
}

private fun sampleShoe(name: String, type: ShoeType, colour: Int, archived: Boolean = false) = ShoeEntity(
    id = name, tagUuid = null, name = name, brand = "", model = "", type = type, priceMinor = 12900,
    currencyCode = "GBP", purchaseEpochDay = null, accentColor = colour, archived = archived, createdAtUtc = 0,
)

private fun sampleStats(shoe: ShoeEntity, steps: Long, active: Boolean, about: Boolean = false) = ShoeWithStats(
    shoe = shoe, totalSteps = steps, totalDistanceMeters = null, sessionCount = 148, endedSessionCount = 147, activeMillis = 0,
    lastUsedUtc = null, isActive = active, hasUnfinalSteps = about, hasPendingSteps = false,
)

@Composable
private fun ShoeRows() {
    ShoeRow(sampleStats(sampleShoe("Trail Runner 2", ShoeType.TRAIL_RUNNING, 4), 612_940, active = true), paused = false) {}
    ShoeRow(sampleStats(sampleShoe("Daily Court", ShoeType.WALKING, 6), 455_120, active = true), paused = true) {}
    ShoeRow(sampleStats(sampleShoe("Peak Hiker", ShoeType.HIKING, 1, archived = true), 216_250, active = false, about = true), paused = false) {}
}

@Composable
private fun SessionCards() {
    val session = SessionEntity(
        id = "s", shoeId = "Trail Runner 2", startUtc = 1_792_220_520_000L, endUtc = null, zoneId = "UTC",
        state = SessionState.RUNNING, endReason = null,
    )
    ActiveSessionCard(ActiveSessionWithShoe(session, "Trail Runner 2", 4), SoFarSteps.Count(3_214), {}, {}, {}, {})
    ActiveSessionCard(ActiveSessionWithShoe(session.copy(state = SessionState.PAUSED), "Trail Runner 2", 4), SoFarSteps.Later, {}, {}, {}, {})
}

@Composable
private fun Stats() {
    SilverCard {
        CardLabel("Stats")
        StatsGrid(
            listOf(
                StatItem("Total steps", "612,940"), StatItem("Total distance", "459.7 km"),
                StatItem("Sessions", "148"), StatItem("Walking time", "96 h 20 min"),
                StatItem("Average per session", "4,142 steps", extra = "3.1 km"), StatItem("Last worn", "18 Oct 2026"),
                StatItem("Cost per 1,000 steps", "£0.21"), StatItem("Cost per km", "–"),
            ),
        )
    }
}

@Composable
private fun History() {
    SilverCard {
        CardLabel("History")
        HistoryRow("19 Oct 2026, 07:42 · 47 min", null, "In progress", null)
        HistoryRow("18 Oct 2026, 18:10 · 1 h 12 min", null, "About 7,862 steps", "–")
        HistoryRow("17 Oct 2026, 08:05 · 38 min", "Ended by switching pairs", "4,120 steps", "–")
        TextActionButton("Show 12 older sessions", {}, accent = true)
    }
}

@Composable
private fun Dialogs() {
    DialogPanel(
        "End this session?",
        listOf(DialogAction("Cancel", DialogAction.Kind.TEXT, {}), DialogAction("End session", DialogAction.Kind.PRIMARY, {})),
    ) { DialogText("The session for Trail Runner 2 stops now. You can start a new one any time.") }
    DialogPanel(
        "Trail Runner 2 session",
        listOf(
            DialogAction("Pause", DialogAction.Kind.SECONDARY, {}),
            DialogAction("End session", DialogAction.Kind.PRIMARY, {}),
            DialogAction("Cancel", DialogAction.Kind.TEXT, {}),
        ),
    ) { DialogText("The session is running. What would you like to do?") }
}

@Preview(name = "Light", widthDp = 390)
@Preview(name = "Night", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light 200%", widthDp = 390, fontScale = 2f)
annotation class ComponentPreviews

@ComponentPreviews @Composable private fun ButtonsPreview() = ShoeStepTheme { ComponentGallery(ComponentSection.BUTTONS) }
@ComponentPreviews @Composable private fun ChipsPreview() = ShoeStepTheme { ComponentGallery(ComponentSection.CHIPS_BADGES) }
@ComponentPreviews @Composable private fun FieldsPreview() = ShoeStepTheme { ComponentGallery(ComponentSection.FIELDS) }
@ComponentPreviews @Composable private fun ControlsPreview() = ShoeStepTheme { ComponentGallery(ComponentSection.CONTROLS) }
@ComponentPreviews @Composable private fun ShoeRowsPreview() = ShoeStepTheme { ComponentGallery(ComponentSection.SHOE_ROWS) }
@ComponentPreviews @Composable private fun SessionCardPreview() = ShoeStepTheme { ComponentGallery(ComponentSection.SESSION_CARD) }
@ComponentPreviews @Composable private fun StatsPreview() = ShoeStepTheme { ComponentGallery(ComponentSection.STATS) }
@ComponentPreviews @Composable private fun HistoryPreview() = ShoeStepTheme { ComponentGallery(ComponentSection.HISTORY) }
@ComponentPreviews @Composable private fun DialogsPreview() = ShoeStepTheme { ComponentGallery(ComponentSection.DIALOGS) }
@ComponentPreviews @Composable private fun NfcPreview() = ShoeStepTheme { ComponentGallery(ComponentSection.NFC) }
