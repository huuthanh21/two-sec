package dev.twosec.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TwoSecTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Box(Modifier.semantics { testTagsAsResourceId = true }) {
            content()
        }
    }
}
