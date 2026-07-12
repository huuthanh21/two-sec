package dev.twosec.app.ui

import dev.twosec.app.platform.InstalledApp

fun filterApps(apps: List<InstalledApp>, query: String): List<InstalledApp> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return apps
    val needle = trimmed.lowercase()
    return apps.filter { it.label.lowercase().contains(needle) }
}
