# two-sec

A free, on-device Android app that puts a brief pause between you and the apps you tend to open on autopilot. When you open a chosen app, two-sec shows a breathing screen for a few seconds, then lets you continue or close (back to your home screen).

two-sec is a free clone of [one-sec](https://one-sec.app). It runs entirely on your device, with no network, no accounts, and no analytics.

> [!NOTE]
> two-sec needs the Android **Accessibility** permission to detect when a blocked app is brought to the foreground. It does not collect input, does not log typed text, and does not see anything outside the foreground window.

## How it works

1. Pick the apps you tend to open on autopilot (Instagram, TikTok, Reddit — whatever you choose).
2. When you open one, two-sec takes over the screen with a short, calm prompt instead of the app.
3. After a few seconds, you can tap **Continue** to go in, or **Close** to go back to your home screen.
4. Tapping **Continue** quietly remembers your choice for 30 seconds, so the next open during that window goes straight through.

That's it. No streaks, no scoreboards, no notifications nudging you later.

## Setup

1. **Install** the APK and open two-sec.
2. **Enable the Accessibility service**: *System Settings → Accessibility → two-sec → Use*. This is the only way an app can observe foreground windows on Android.
3. **Disable battery optimisation**: *System Settings → Apps → two-sec → Battery → Unrestricted*. Without this, most manufacturers kill the service after a few hours of screen-off time.
4. **Pick the apps** you want two-sec to pause on. The home screen shows a list of installed launchable apps; tick the ones you tend to open on autopilot.

You can turn the whole thing off any time with the **Block apps** toggle on the home screen.

## Privacy

- No network. The app has no internet permission.
- No accounts. Nothing leaves the device.
- No analytics, no crash reporting, no telemetry.
- The Accessibility service is used only to read the name of the foreground app. It does not capture screen content, typed text, or anything else.
- The blocklist, the master toggle, and short-term "I meant to do that" timers are stored in your app's private files via `DataStore`. Uninstalling two-sec deletes them.

## For contributors

The codebase is structured around three independent, pure pieces of logic — a decision function, an intervention state machine, and a persistence layer — wrapped in thin Android adapters (`AccessibilityService`, `Activity`, `BroadcastReceiver`). The pure pieces are unit-tested on the JVM with JUnit, Turbine, and `runTest`; the Android adapters are covered by a small set of Espresso tests.

The full domain vocabulary lives in [CONTEXT.md](CONTEXT.md), and the architecture rationale in [docs/adr/](docs/adr/).

### Build and test

```bash
# Build the debug APK
./gradlew :app:assembleDebug

# Unit tests (JVM, no device needed)
./gradlew :app:testDebugUnitTest

# Instrumented tests (needs a connected device or emulator)
./gradlew :app:connectedDebugAndroidTest

# Lint
./gradlew :app:lintDebug
```

> [!IMPORTANT]
> Instrumented tests need a device reachable by `adb`. Use the local emulator setup: `./scripts/manage-emulator.sh` to boot the emulator.

JDK 17 and the Android SDK (platform 34, build-tools 34) are required. The Gradle wrapper pins the toolchain.

## Further reading

- [CONTEXT.md](CONTEXT.md) — single source of truth for domain vocabulary.
- [docs/adr/](docs/adr/) — accepted architectural decisions.
- [AGENTS.md](AGENTS.md) — agent workflow, git identity, and test commands.
