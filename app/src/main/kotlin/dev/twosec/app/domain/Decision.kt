package dev.twosec.app.domain

sealed interface Decision {
    data object Intervene : Decision
    data class Skip(val reason: SkipReason) : Decision
}

enum class SkipReason {
    MasterOff,
    NotInBlocklist,
    Whitelisted,
    IgnoredPackage,
    OwnPackage,
    AlreadyInForeground,
}
