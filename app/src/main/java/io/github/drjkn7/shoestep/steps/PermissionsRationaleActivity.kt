package io.github.drjkn7.shoestep.steps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme

/**
 * Privacy explanation that Health Connect shows when the user asks why this app wants steps.
 * Exported (Health Connect and the system open it), so it shows static text only: it reads no
 * intent extras, no data, and offers no way into the rest of the app (SEC-03).
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShoeStepTheme {
                RationaleScreen(onClose = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RationaleScreen(onClose: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.rationale_title)) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(
                R.string.rationale_what,
                R.string.rationale_why,
                R.string.rationale_stays,
                R.string.rationale_control,
            ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rationale_close)) }
        }
    }
}
