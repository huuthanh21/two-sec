# Emulator Debugging Guide

For local, high-efficiency development loops using KVM acceleration.

## Setup & Boot
Start/bind the emulator and wait for full boot:
```bash
./scripts/manage-emulator.sh
```

The AVD is resized to **1080×1920 @ 440dpi** (Pixel 2 baseline) on first boot — the QEMU default 320×640 makes tap targets unusable. The resize is idempotent and lives in `~/.android/avd/$AVD_NAME.avd/config.ini`.

## First-time accessibility service setup

The `BlockerAccessibilityService` must be enabled in the system before any a11y-driven features work. On Android 13+ this requires accepting the "Allow full control" dialog that `settings put secure enabled_accessibility_services` alone can't trigger — `BIND_ACCESSIBILITY_SERVICE` is signature-protected, and the only way to obtain the bind is the user (or a script tapping as the user) accepting the security warning in Settings:

```bash
./scripts/enable-a11y.sh
```

The script writes the `enabled_accessibility_services` setting, then drives the Settings UI to accept the "Allow" button on the confirmation dialog. It is idempotent — running it on an already-enabled service is a no-op. The service stays bound across emulator restarts unless you `am force-stop dev.twosec.app`; to deliberately unbind for re-testing, run `adb shell settings delete secure enabled_accessibility_services` after the force-stop.

## Logs
Continuous filtered runtime logging to `compose_debug.log`:
```bash
./scripts/log-pipeline.sh
```
Keep this running in the background.

### Logcat process keeps dying
`nohup ... &` and `disown` alone aren't enough — when the spawning shell exits, the child receives SIGHUP and dies. Use `setsid` to put the logcat in a new session:
```bash
setsid bash -c 'adb -s emulator-5554 logcat -v time <Tag>:D *:S > /tmp/logcat.log 2>&1' < /dev/null &
```

### Don't restart logcat to get a clean window
`adb logcat -c` clears the device's ring buffer but does not clear the file the logcat process is writing to. Restarting logcat mid-session takes minutes and often exceeds the bash timeout. Start logcat once with the `setsid` incantation above, then `grep` or `awk` the output file to isolate the time window you need:
```bash
grep 'BlockerA11y' /tmp/logcat.log
awk '/00:40:00/,/00:41:00/' /tmp/logcat.log
```

## UI Inspection
Dump the active Compose UI hierarchy tree to `compose_layout.xml`:
```bash
./scripts/dump-layout.sh
```

## Accessibility event gotchas

The `BlockerAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED`. Not every UI transition fires one.

### Dismissing the IME does not fire a new event for the underlying app
The soft keyboard is a separate window overlaid on top of the foreground app. Pressing back to dismiss it removes the overlay but does NOT fire a new `TYPE_WINDOW_STATE_CHANGED` for the app underneath — the app's window was already there, so the system sees no state change. To get a fresh event for the foreground app, perform a real activity transition: open a new tab, switch tabs, navigate back, etc.

### `am start -a VIEW` with an existing instance just delivers the intent
If the target activity is already in the foreground, `am start -a android.intent.action.VIEW -d <url> -n <package>/<activity>` prints `Activity not started, its current task has been brought to the front` and does not fire a new window-state event. Use in-app UI (menu, tab switcher) to force a new activity if you need a fresh event.

### `force-stop` + `am start` routes through the launcher
`adb shell am force-stop <package>` kills the app, the launcher (`com.google.android.apps.nexuslauncher` on AOSP, OEM equivalent otherwise) comes to the foreground, then `am start` brings the app back. The launcher visit fires its own `TYPE_WINDOW_STATE_CHANGED` with a different `packageName`, which can clobber in-memory state in ways that don't match a purely-in-app repro. For in-app repros, use in-app navigation (tab switcher, new tab, back-then-forward) instead of `force-stop`.

### Chrome first-run flow on a fresh AVD
On a clean AVD, Chrome shows `Welcome to Chrome` → `Add account to device` / `Use without an account` → `Allow Chrome to send you notifications?` / `No thanks`. Each is a separate window with a different `packageName`, and each fires its own accessibility event. Budget 3-4 taps to clear the flow before Chrome's address bar is visible.

## Session management

### Emulators crash mid-session
`adb devices` may stop showing the emulator with no error. Re-run `./scripts/manage-emulator.sh` to reboot. DataStore state on disk survives, so blocklist and whitelist settings persist. Accessibility service settings also survive, but the binding does not — re-enable with `./scripts/enable-a11y.sh` (see "First-time accessibility service setup" above).

### Whitelist makes emulator iteration slow
The production whitelist is 30s (`InterventionStateMachine.WHITELIST_MS = 30_000L`). Every iteration of an emulator repro that needs the whitelist to expire costs 30s. Iterate the fix on the unit test with `FakeClock` (`./gradlew :app:testDebugUnitTest`) and use the emulator only for final validation.

## Running Tests
To execute instrumented tests on the emulator:
```bash
./gradlew :app:connectedDebugAndroidTest
```
