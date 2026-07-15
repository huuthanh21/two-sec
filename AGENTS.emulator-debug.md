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

## UI driving — `scripts/click.py`

For E2E testing and AI-driven debug loops, the dump-then-tap loop is four `adb` round-trips (`uiautomator dump` → read XML → compute center → `input tap`). `click.py` collapses that into one command:

```bash
./scripts/click.py --text "Continue"
```

Each invocation dumps the current UI, finds a node matching the selector, computes the center of its `bounds`, taps it, sleeps `--wait` seconds (default `0.5`), and prints a one-line confirmation to stdout. Stdlib only.

### Flags

- `--text "<text>"` / `--id "<resource-id-substring>"` / `--coords "X,Y"` — exactly one required, mutually exclusive. `--coords` is an escape hatch that skips the XML lookup entirely; use it for clickable nodes with no identifying attribute (e.g. icon-only buttons, the master toggle, app checkboxes — the very things the recovery list can't surface). Bounds for these come from `./scripts/dump-layout.sh` + a `grep` for `clickable="true"`.
- `--xml <path>` — reuse a pre-dumped XML instead of dumping fresh. If omitted, dumps to `compose_layout.xml` in the cwd. Ignored when `--coords` is set.
- `--serial <serial>` — target device. Falls back to `$ANDROID_SERIAL`, then auto-pick: 1 device → use it, multiple devices → prefer `emulator-*`.
- `--wait <seconds>` — sleep after the tap so the next call sees a settled UI. Default `0.5`.

### Match resolution (case-insensitive)

1. Exact match on the selector attribute.
2. Substring fallback if no exact hit.
3. If still nothing → exit `1` with a clickable-node dump on stderr (see "Recovery" below).

Multiple matches → tap the first in document order, print a one-line warning on stderr naming the others.

### Exit codes

- `0` — tapped. Stdout: `Tapped text="Continue" at (540, 1620) bounds=[504,1584][576,1656]` (or `Tapped coords (540, 1620)` with `--coords`).
- `1` — no matching node. Stderr: reason + clickable-node list (see below). Only used by `--text` / `--id`.
- `2` — no device / `$ANDROID_SERIAL` does not exist (or `--coords` was malformed).
- `3` — `uiautomator dump`, `adb pull`, or `adb input tap` failed. Raw adb stderr echoed.

### Recovery when the selector is wrong

`click.py` always echoes the available clickable identifiers on `exit 1`, so an agent that miscounts the label can read stderr and retry with the exact string. The list includes any `text=` (or `content-desc=` / `resource-id=`) inside a clickable ancestor, so Compose buttons whose label is a child TextView, icon buttons with no text, and resource-id-only nodes all show up. Each line is prefixed with the source attribute when it isn't `text`:

```
No node matched text='Continu' (case-insensitive).
Clickable nodes on screen:
  [Button] 'Continue'                 bounds=[504,1584][576,1656]
  [Button] 'Continue to app'          bounds=[420,210][580,250]
  [Button] 'Close'                    bounds=[504,1784][576,1856]
  [ImageView] desc='Search'           bounds=[900,100][980,180]
  [Button] id='com.app:id/save_btn'   bounds=[100,1700][300,1760]
```

### When the dump itself is the problem

If the agent needs to *inspect* the UI without tapping, fall back to `./scripts/dump-layout.sh` and read `compose_layout.xml` directly. `click.py --xml <path>` will reuse a hand-dumped file the same way it reuses its own.

If the agent needs to tap a node that has no `text`, `content-desc`, or `resource-id` (icon-only buttons, the master toggle, the per-app checkboxes), use `--coords "X,Y"` directly — bounds for these come from the dumped XML, e.g. `./scripts/dump-layout.sh && grep -oE 'clickable="true"[^/]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' compose_layout.xml`. The advantage over raw `adb shell input tap` is that `--coords` still respects `--serial` / `$ANDROID_SERIAL` auto-pick and `--wait`.

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
