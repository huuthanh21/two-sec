package dev.twosec.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dev.twosec.app.ui.ConfigScreen
import dev.twosec.app.ui.ConfigViewModel
import dev.twosec.app.ui.theme.TwoSecTheme

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
                ConfigScreen(viewModel = viewModel)
            }
        }
    }
}
