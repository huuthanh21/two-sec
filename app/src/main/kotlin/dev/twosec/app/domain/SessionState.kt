package dev.twosec.app.domain

sealed interface SessionState {
    val packageName: String?
    data object Idle : SessionState {
        override val packageName: String? = null
    }
    data class InForeground(override val packageName: String) : SessionState
    data class PendingIntervention(override val packageName: String) : SessionState
    data class InIntervention(override val packageName: String) : SessionState
}
