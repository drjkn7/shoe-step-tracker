package io.github.drjkn7.shoestep.ui.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.ui.common.CardLabel
import io.github.drjkn7.shoestep.ui.common.PrimaryButton
import io.github.drjkn7.shoestep.ui.common.SilverCard
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme

/** Home banner shown while steps can't be read (PNG 02): asks for permission or explains why not. */
@Composable
fun StepsAccessCard(
    access: StepsAccess,
    onAllow: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (access == StepsAccess.READY) return
    SilverCard(modifier) {
        CardLabel(stringResource(R.string.steps_access_title))
        StepsAccessContent(access, onAllow, onInstall)
    }
}

/** The banner's text and button without the card, for Settings → Step counting. */
@Composable
fun ColumnScope.StepsAccessContent(access: StepsAccess, onAllow: () -> Unit, onInstall: () -> Unit) {
    val body = when (access) {
        StepsAccess.READY -> return
        StepsAccess.NEEDS_PERMISSION -> R.string.steps_access_permission_body
        StepsAccess.NEEDS_INSTALL -> R.string.steps_access_install_body
        else -> R.string.steps_access_unavailable_body
    }
    Text(stringResource(body), style = SilverTheme.type.body.copy(fontSize = SilverTheme.type.button.fontSize), color = SilverTheme.colors.textPrimary)
    val buttonModifier = Modifier.fillMaxWidth().padding(top = SilverSpacing.s)
    when (access) {
        StepsAccess.NEEDS_PERMISSION -> PrimaryButton(stringResource(R.string.steps_access_allow), onAllow, buttonModifier)
        StepsAccess.NEEDS_INSTALL -> PrimaryButton(stringResource(R.string.steps_access_install), onInstall, buttonModifier)
        else -> Unit
    }
}
