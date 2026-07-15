package dev.twosec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.twosec.app.R
import dev.twosec.app.platform.InstalledApp

@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel,
    onExtractLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConfigScreenContent(
        state = state,
        onMasterToggle = viewModel::onMasterToggle,
        onAppChecked = viewModel::onAppChecked,
        onQueryChange = viewModel::onQueryChange,
        onExtractLogs = onExtractLogs,
        modifier = modifier,
    )
}

@Composable
private fun ConfigScreenContent(
    state: ConfigUiState,
    onMasterToggle: (Boolean) -> Unit,
    onAppChecked: (String, Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onExtractLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.screen_header),
            style = MaterialTheme.typography.headlineMedium,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.master_toggle_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Switch(
                checked = state.masterOn,
                onCheckedChange = onMasterToggle,
                modifier = Modifier.testTag("master_toggle"),
            )
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.blocklist_header),
            style = MaterialTheme.typography.titleMedium,
        )

        AppSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
        )

        when {
            state.installedApps.isEmpty() && state.isLoading -> Unit
            state.installedApps.isEmpty() && state.query.isNotBlank() -> {
                Text(
                    text = stringResource(R.string.blocklist_no_match, state.query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            state.installedApps.isEmpty() -> {
                Text(
                    text = stringResource(R.string.blocklist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = state.installedApps,
                        key = { it.packageName },
                        contentType = { "app_row" },
                    ) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in state.blocklist,
                            onCheckedChange = { onAppChecked(app.packageName, it) },
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.setup_header),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.setup_step_accessibility),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.setup_step_battery),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExtractLogs,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("extract_logs"),
        ) {
            Text(stringResource(R.string.extract_logs))
        }

        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { keyboardController?.hide() },
                expanded = false,
                onExpandedChange = { },
                placeholder = { Text(stringResource(R.string.search_apps_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.testTag("search_clear"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_clear),
                            )
                        }
                    }
                },
            )
        },
        expanded = false,
        onExpandedChange = { },
        modifier = Modifier.fillMaxWidth(),
    ) { }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val blockLabel = stringResource(R.string.app_check_label, app.label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .semantics { contentDescription = blockLabel }
                .testTag("app_checkbox:${app.packageName}"),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Box(Modifier.size(0.dp))
    }
}
