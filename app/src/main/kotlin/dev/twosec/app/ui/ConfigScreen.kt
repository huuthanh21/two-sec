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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.twosec.app.R
import dev.twosec.app.platform.InstalledApp

@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConfigScreenContent(
        state = state,
        onMasterToggle = viewModel::onMasterToggle,
        onAppChecked = viewModel::onAppChecked,
        modifier = modifier,
    )
}

@Composable
private fun ConfigScreenContent(
    state: ConfigUiState,
    onMasterToggle: (Boolean) -> Unit,
    onAppChecked: (String, Boolean) -> Unit,
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
            modifier = Modifier.fillMaxWidth(),
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
            )
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.blocklist_header),
            style = MaterialTheme.typography.titleMedium,
        )

        if (state.installedApps.isEmpty() && !state.isLoading) {
            Text(
                text = stringResource(R.string.blocklist_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
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
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
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
