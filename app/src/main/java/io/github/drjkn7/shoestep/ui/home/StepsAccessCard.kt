package io.github.drjkn7.shoestep.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.steps.StepsAccess

/** Home banner shown while steps can't be read: asks for permission or explains why not. */
@Composable
fun StepsAccessCard(
    access: StepsAccess,
    onAllow: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (access == StepsAccess.READY) return
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.steps_access_title), style = MaterialTheme.typography.titleMedium)
            val body = when (access) {
                StepsAccess.NEEDS_PERMISSION -> R.string.steps_access_permission_body
                StepsAccess.NEEDS_INSTALL -> R.string.steps_access_install_body
                else -> R.string.steps_access_unavailable_body
            }
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            when (access) {
                StepsAccess.NEEDS_PERMISSION ->
                    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.steps_access_allow))
                    }
                StepsAccess.NEEDS_INSTALL ->
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.steps_access_install))
                    }
                else -> Unit
            }
        }
    }
}
