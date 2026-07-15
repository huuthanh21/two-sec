package dev.twosec.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import dev.twosec.app.data.LogSharer
import dev.twosec.app.ui.ConfigScreen
import dev.twosec.app.ui.ConfigViewModel
import dev.twosec.app.ui.theme.TwoSecTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val viewModel: ConfigViewModel by viewModels {
        val app = application as TwoSecApp
        ConfigViewModel.factory(
            store = app.blocklistStore,
            installedAppsProvider = app.installedAppsProvider,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TwoSecTheme {
                ConfigScreen(
                    viewModel = viewModel,
                    onExtractLogs = ::launchLogShare,
                )
            }
        }
    }

    private fun launchLogShare() {
        val app = application as TwoSecApp
        val authority = "$packageName.logfileprovider"
        val files = listOf(app.logFileTree.currentFile, app.logFileTree.backupFile)
        val uris = files.mapNotNull { file ->
            runCatching { FileProvider.getUriForFile(this, authority, file) }
                .onFailure { Timber.w(it, "FileProvider lookup failed for %s", file) }
                .getOrNull()
        }
        if (uris.isEmpty()) {
            Timber.w("extract logs requested but no log files are accessible")
            return
        }
        val intent = LogSharer.buildShareIntent(uris.map { it.toString() })
        startActivity(Intent.createChooser(intent, null))
    }
}
