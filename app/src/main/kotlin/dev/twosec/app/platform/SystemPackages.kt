package dev.twosec.app.platform

object SystemPackages {

    val ignored: Set<String> = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.launcher",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.wallpaper",
    )
}
