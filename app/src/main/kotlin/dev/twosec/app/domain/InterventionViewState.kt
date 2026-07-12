package dev.twosec.app.domain

sealed interface InterventionViewState {
    data class Counting(val millisRemaining: Long) : InterventionViewState
    data object AwaitingChoice : InterventionViewState
}
