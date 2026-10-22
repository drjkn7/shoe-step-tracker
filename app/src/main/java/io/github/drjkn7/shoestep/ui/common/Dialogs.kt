package io.github.drjkn7.shoestep.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import io.github.drjkn7.shoestep.ui.theme.floating

/** A button at the bottom of a [SilverDialog]. */
data class DialogAction(val label: String, val kind: Kind, val onClick: () -> Unit, val enabled: Boolean = true) {
    enum class Kind { PRIMARY, SECONDARY, TEXT, DANGER }
}

/**
 * Every pop-up in the app (docs/DESIGN.md "Dialog"): centred floating panel, radius 28, on a dimmed
 * page. Two actions sit side by side (they wrap onto two lines at large font sizes); three or more
 * stack full width. Back or a tap outside calls [onDismiss]. Tapjacking protection (SEC-11) is on for
 * the pop-up's own window. The body scrolls if it does not fit.
 */
@Composable
fun SilverDialog(
    title: String?,
    onDismiss: () -> Unit,
    actions: List<DialogAction>,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FilterTouchesWhenObscured()
        DimBehind()
        Box(Modifier.fillMaxWidth().padding(SilverSpacing.l), contentAlignment = Alignment.Center) {
            DialogPanel(title, actions, content)
        }
    }
}

/** The pop-up's panel itself, without its window (also drawn in the component previews). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogPanel(title: String?, actions: List<DialogAction>, content: @Composable ColumnScope.() -> Unit) {
    val c = SilverTheme.colors
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .floating(c, SilverShapes.dialog)
            .padding(SilverSpacing.l),
        verticalArrangement = Arrangement.spacedBy(SilverSpacing.m),
    ) {
        title?.let {
            Text(
                it,
                style = SilverTheme.type.section.copy(fontSize = 20.sp, lineHeight = 26.sp),
                color = c.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SilverSpacing.m),
            content = content,
        )
        if (actions.size <= 2) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SilverSpacing.s, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(SilverSpacing.s),
            ) {
                actions.forEach { ActionButton(it, Modifier) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SilverSpacing.s)) {
                actions.forEach { ActionButton(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** Body text of a pop-up. */
@Composable
fun DialogText(text: String) {
    Text(text, style = SilverTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = SilverTheme.colors.textPrimary)
}

@Composable
private fun ActionButton(action: DialogAction, modifier: Modifier) = when (action.kind) {
    DialogAction.Kind.PRIMARY -> PrimaryButton(action.label, action.onClick, modifier, action.enabled)
    DialogAction.Kind.SECONDARY -> SecondaryButton(action.label, action.onClick, modifier, action.enabled)
    DialogAction.Kind.TEXT -> TextActionButton(action.label, action.onClick, modifier, action.enabled)
    DialogAction.Kind.DANGER -> DangerButton(action.label, action.onClick, modifier, action.enabled)
}

/** The page behind a pop-up is dimmed by the pop-up's window (DESIGN.md "scrim": 42% light, 60% night). */
@Composable
private fun DimBehind() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val amount = if (SilverTheme.colors.isNight) 0.60f else 0.42f
    SideEffect { window?.setDimAmount(amount) }
}

/** An entry in a [SilverMenu]; [danger] for Delete. */
data class MenuEntry(val label: String, val onClick: () -> Unit, val danger: Boolean = false)

/**
 * The ⋮ menu (PNG 11): a floating panel under the More button with plain text entries, each at
 * least 48 dp tall. Material's menu underneath handles placement, focus and dismissal.
 */
@Composable
fun SilverMenu(expanded: Boolean, onDismiss: () -> Unit, entries: List<MenuEntry>) {
    val c = SilverTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = SilverShapes.panel,
        containerColor = c.dialog.first(),
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, c.edgeHighlight),
        modifier = Modifier.widthIn(min = 200.dp),
    ) {
        entries.forEach { entry ->
            DropdownMenuItem(
                text = {
                    Text(
                        entry.label,
                        style = SilverTheme.type.body.copy(fontSize = 17.sp, lineHeight = 24.sp),
                        color = if (entry.danger) c.danger else c.textPrimary,
                    )
                },
                onClick = entry.onClick,
                modifier = Modifier.heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = SilverSpacing.l),
            )
        }
    }
}
