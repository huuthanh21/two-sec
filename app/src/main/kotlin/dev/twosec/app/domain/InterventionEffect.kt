package dev.twosec.app.domain

sealed interface InterventionEffect {
    data object ShowButtons : InterventionEffect
    data object HideButtons : InterventionEffect
    data object FinishActivity : InterventionEffect
    data object GoHome : InterventionEffect
    data class WhitelistPackage(val packageName: String, val until: Long) : InterventionEffect
}
