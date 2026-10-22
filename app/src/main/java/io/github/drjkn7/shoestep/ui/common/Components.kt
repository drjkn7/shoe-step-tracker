package io.github.drjkn7.shoestep.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing

/** Gives screens access to app-wide objects without a DI library. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/**
 * SEC-11: ignore touches while another app's window covers this one (tapjacking protection).
 * Applied to the current window's root view; dialogs are separate windows, so call it inside them too.
 */
@Composable
fun FilterTouchesWhenObscured() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.filterTouchesWhenObscured = true
        view.rootView.filterTouchesWhenObscured = true
        onDispose { }
    }
}

/**
 * Confirmation for irreversible actions (SEC-11): Cancel and the action side by side. [destructive]
 * actions (delete, unlink, archive) use the filled danger button; others the primary button.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    SilverDialog(
        title = title,
        onDismiss = onDismiss,
        actions = listOf(
            DialogAction(stringResource(R.string.action_cancel), DialogAction.Kind.TEXT, onDismiss),
            DialogAction(confirmLabel, if (destructive) DialogAction.Kind.DANGER else DialogAction.Kind.PRIMARY, onConfirm),
        ),
    ) { DialogText(text) }
}

/** A message with one OK button. */
@Composable
fun InfoDialog(title: String?, text: String, onDismiss: () -> Unit) {
    SilverDialog(
        title = title,
        onDismiss = onDismiss,
        actions = listOf(DialogAction(stringResource(R.string.action_ok), DialogAction.Kind.PRIMARY, onDismiss)),
    ) { DialogText(text) }
}

/**
 * A pair's colour marker (24 dp circle by default). Decorative: the pair's name is always shown next
 * to it, and the colour name is announced where it matters (the form's swatches).
 */
@Composable
fun ColourDot(index: Int, modifier: Modifier = Modifier, size: Dp = SilverSpacing.marker) {
    Box(
        modifier
            .size(size)
            .clip(SilverShapes.circle)
            .background(accentColour(index).colour),
    )
}
