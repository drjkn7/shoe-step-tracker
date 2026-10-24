package io.github.drjkn7.shoestep.ui.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.ui.common.NavIcon
import io.github.drjkn7.shoestep.ui.common.ScreenColumn
import io.github.drjkn7.shoestep.ui.common.SilverCard
import io.github.drjkn7.shoestep.ui.common.SilverScreen
import io.github.drjkn7.shoestep.ui.common.TextActionButton
import io.github.drjkn7.shoestep.ui.theme.SilverTheme

/*
 * Not mocked up: both pages are cards of text on the backdrop, in the same style as Settings.
 */

/** The privacy statement (same text as PRIVACY.md in the repository). */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    TextPage(title = stringResource(R.string.settings_privacy), onBack = onBack) {
        SilverCard { Heading(stringResource(R.string.privacy_short)) }
        listOf(
            R.string.privacy_reads_title to R.string.privacy_reads,
            R.string.privacy_stores_title to R.string.privacy_stores,
            R.string.privacy_shares_title to R.string.privacy_shares,
            R.string.privacy_delete_title to R.string.privacy_delete,
        ).forEach { (title, body) ->
            SilverCard {
                Heading(stringResource(title))
                Body(stringResource(body))
            }
        }
    }
}

/** Open-source parts and the full text of each licence (bundled in res/raw, read from the APK). */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    TextPage(title = stringResource(R.string.settings_licences), onBack = onBack) {
        SilverCard { Body(stringResource(R.string.licences_intro)) }
        LicenceBlock(R.string.licences_apache_title, R.string.licences_apache_parts, R.raw.licence_apache_2_0)
        LicenceBlock(R.string.licences_mit_title, R.string.licences_mit_parts, R.raw.licence_mit_checker_qual)
        LicenceBlock(R.string.licences_ofl_title, R.string.licences_ofl_parts, R.raw.licence_ofl_chakra_petch)
    }
}

@Composable
private fun LicenceBlock(title: Int, parts: Int, @RawRes text: Int) {
    val resources = LocalResources.current
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    SilverCard {
        Heading(stringResource(title))
        Body(stringResource(parts))
        TextActionButton(
            stringResource(if (expanded) R.string.licences_hide_text else R.string.licences_show_text),
            onClick = { expanded = !expanded },
            accent = true,
        )
        if (expanded) {
            val body = remember(resources, text) { resources.openRawResource(text).bufferedReader().use { it.readText() } }
            Text(body, style = SilverTheme.type.note.copy(fontFamily = FontFamily.Monospace), color = SilverTheme.colors.textPrimary)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = SilverTheme.type.section, color = SilverTheme.colors.textPrimary, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Body(text: String) {
    Text(text, style = SilverTheme.type.body.copy(fontSize = SilverTheme.type.button.fontSize, lineHeight = 22.sp), color = SilverTheme.colors.textPrimary)
}

@Composable
private fun TextPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    SilverScreen(title = title, navIcon = NavIcon.BACK, onNavigate = onBack) {
        ScreenColumn(content = content)
    }
}
