package dev.twosec.app.domain

sealed interface InterventionEvent {
    data class Tick(val now: Long) : InterventionEvent
    data object UserTappedContinue : InterventionEvent
    data object UserTappedClose : InterventionEvent
    data object BackPressed : InterventionEvent
    data object ScreenDestroyed : InterventionEvent
}
