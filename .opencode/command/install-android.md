---
description: Build and install the two-sec debug APK on the wirelessly-connected device, then launch it.
agent: build
---

Build, install, and launch two-sec on the wirelessly-connected device.

1. **Confirm a device is connected.** `adb devices -l` must list one. If none, ask the user to pair via Developer options → Wireless debugging, then `adb pair <ip:port>` + `adb connect <ip:port>`. The phone must be **unlocked** — locked screens silently fail `adb install`.

2. **Build.** `./gradlew :app:assembleDebug`. Stop and report on build failure.

3. **Install.** `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

4. **Launch.** `adb shell am start -n dev.twosec.app/.MainActivity`.

5. **Confirm running.** `adb shell pidof dev.twosec.app`. If empty, the app crashed at launch — pull `adb logcat -d -t 200 | grep -i twosec` and surface the relevant lines.
