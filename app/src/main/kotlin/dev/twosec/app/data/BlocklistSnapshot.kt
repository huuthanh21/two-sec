package dev.twosec.app.data

data class BlocklistSnapshot(
    val masterEnabled: Boolean,
    val blocklist: Set<String>,
    val whitelistExpiries: Map<String, Long>,
)
