package io.github.drjkn7.shoestep.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.R

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

/** Confirmation for irreversible actions (SEC-11). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FilterTouchesWhenObscured()
            Text(text)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Decorative accent-colour dot; the colour name is announced elsewhere where it matters. */
@Composable
fun ColourDot(index: Int, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(accentColour(index).colour),
    )
}
