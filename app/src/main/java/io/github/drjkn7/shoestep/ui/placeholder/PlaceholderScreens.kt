package io.github.drjkn7.shoestep.ui.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R

// Phase 1 placeholder screens: real screens replace these in Phases 3-7.

@Composable
fun HomePlaceholder(
    onAddShoe: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExampleShoe: () -> Unit,
) {
    PlaceholderScaffold(
        title = R.string.title_home,
        onBack = null,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.nav_settings),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddShoe) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.nav_add_shoe),
                )
            }
        },
    ) {
        Text(stringResource(R.string.placeholder_home), style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(onClick = onOpenExampleShoe) {
            Text(stringResource(R.string.placeholder_open_example))
        }
    }
}

@Composable
fun AddEditShoePlaceholder(isEdit: Boolean, onBack: () -> Unit) {
    PlaceholderScaffold(
        title = if (isEdit) R.string.title_edit_shoe else R.string.title_add_shoe,
        onBack = onBack,
    ) {
        Text(stringResource(R.string.placeholder_add_edit), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ShoeDetailPlaceholder(onBack: () -> Unit, onEdit: () -> Unit) {
    PlaceholderScaffold(title = R.string.title_shoe_detail, onBack = onBack) {
        Text(stringResource(R.string.placeholder_detail), style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(onClick = onEdit) {
            Text(stringResource(R.string.placeholder_edit))
        }
    }
}

@Composable
fun SettingsPlaceholder(onBack: () -> Unit) {
    PlaceholderScaffold(title = R.string.title_settings, onBack = onBack) {
        Text(stringResource(R.string.placeholder_settings), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScaffold(
    @StringRes title: Int,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.nav_back),
                            )
                        }
                    }
                },
                actions = { actions() },
            )
        },
        floatingActionButton = floatingActionButton,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            content()
        }
    }
}
