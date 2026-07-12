package dev.twosec.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.twosec.app.R
import dev.twosec.app.TwoSecApp
import dev.twosec.app.domain.InterventionEffect
import dev.twosec.app.domain.InterventionEvent
import dev.twosec.app.domain.InterventionStateMachine
import dev.twosec.app.domain.InterventionViewState
import kotlinx.coroutines.launch

class InterventionActivity : ComponentActivity() {

    private lateinit var packageName: String
    private lateinit var stateMachine: InterventionStateMachine
    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val app = application as TwoSecApp
            stateMachine.process(InterventionEvent.Tick(app.clock.now()))
                .forEach { applyEffect(it) }
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            ?: error("InterventionActivity requires $EXTRA_PACKAGE_NAME")

        val app = application as TwoSecApp
        stateMachine = InterventionStateMachine(
            packageName = packageName,
            clock = app.clock,
        )
        stateMachine.initialEffects.forEach { applyEffect(it) }

        setContent {
            InterventionScreen(
                state = stateMachine.state,
                onContinue = { onUserContinue() },
                onClose = { onUserClose() },
            )
        }

        handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        stateMachine.process(InterventionEvent.ScreenDestroyed)
        super.onDestroy()
    }

    private fun onUserContinue() {
        stateMachine.process(InterventionEvent.UserTappedContinue)
            .forEach { applyEffect(it) }
    }

    private fun onUserClose() {
        stateMachine.process(InterventionEvent.UserTappedClose)
            .forEach { applyEffect(it) }
    }

    private fun applyEffect(effect: InterventionEffect) {
        when (effect) {
            InterventionEffect.FinishActivity -> finish()
            InterventionEffect.GoHome -> goHome()
            is InterventionEffect.WhitelistPackage -> whitelist(effect.until)
            InterventionEffect.HideButtons, InterventionEffect.ShowButtons -> Unit
        }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        startActivity(home)
    }

    private fun whitelist(until: Long) {
        val app = application as TwoSecApp
        app.appScope.launch { app.whitelistGate.setWhitelist(packageName, until) }
    }

    @Composable
    private fun InterventionScreen(
        state: InterventionViewState,
        onContinue: () -> Unit,
        onClose: () -> Unit,
    ) {
        val showButtons = state is InterventionViewState.AwaitingChoice

        BackHandler(enabled = true) {
            if (showButtons) onClose()
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.intervention_prompt),
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (state is InterventionViewState.Counting) {
                    val secondsRemaining = (state.millisRemaining + 999L) / 1000L
                    Text(
                        text = secondsRemaining.toString(),
                        style = MaterialTheme.typography.displayLarge,
                    )
                }
                if (showButtons) {
                    Button(onClick = onContinue) {
                        Text(stringResource(R.string.intervention_continue))
                    }
                    Button(onClick = onClose) {
                        Text(stringResource(R.string.intervention_close))
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME: String = "dev.twosec.app.extra.PACKAGE_NAME"
        private const val TICK_INTERVAL_MS: Long = 100L
    }
}
