package dev.twosec.app.platform

object SystemPackages {

    const val AOSP_LATIN_IME = "com.android.inputmethod.latin"
    const val GOOGLE_LATIN_IME = "com.google.android.inputmethod.latin"

    val ignored: Set<String> = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.launcher",
        AOSP_LATIN_IME,
        GOOGLE_LATIN_IME,
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
