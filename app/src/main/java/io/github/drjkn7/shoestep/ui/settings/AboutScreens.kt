package io.github.drjkn7.shoestep.ui.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R

/** The privacy statement (same text as PRIVACY.md in the repository). */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    TextPage(title = stringResource(R.string.settings_privacy), onBack = onBack) {
        Text(stringResource(R.string.privacy_short), style = MaterialTheme.typography.titleMedium)
        listOf(
            R.string.privacy_reads_title to R.string.privacy_reads,
            R.string.privacy_stores_title to R.string.privacy_stores,
            R.string.privacy_shares_title to R.string.privacy_shares,
            R.string.privacy_delete_title to R.string.privacy_delete,
        ).forEach { (title, body) ->
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Open-source parts and the full text of each licence (bundled in res/raw, read from the APK). */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    TextPage(title = stringResource(R.string.settings_licences), onBack = onBack) {
        Text(stringResource(R.string.licences_intro), style = MaterialTheme.typography.bodyMedium)
        LicenceBlock(R.string.licences_apache_title, R.string.licences_apache_parts, R.raw.licence_apache_2_0)
        LicenceBlock(R.string.licences_mit_title, R.string.licences_mit_parts, R.raw.licence_mit_checker_qual)
        LicenceBlock(R.string.licences_ofl_title, R.string.licences_ofl_parts, R.raw.licence_ofl_chakra_petch)
    }
}

@Composable
private fun LicenceBlock(title: Int, parts: Int, @RawRes text: Int) {
    val resources = LocalResources.current
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(parts), style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) R.string.licences_hide_text else R.string.licences_show_text))
    }
    if (expanded) {
        val body = remember(resources, text) { resources.openRawResource(text).bufferedReader().use { it.readText() } }
        Text(body, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}
