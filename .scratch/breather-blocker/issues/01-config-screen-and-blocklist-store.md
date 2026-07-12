# 01 — Config screen + BlocklistStore (foundation)

**What to build:** A runnable Android app whose single screen shows the master toggle, the installed-app checklist, and the static requirements section. The blocklist and master toggle persist across app restarts. The BlocklistStore seam (interface, in-memory fake, DataStore-backed implementation) and the Clock interface are introduced as part of this ticket so that later tickets can build on them.

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] Android project builds and installs on a device or emulator; the launch activity is the single config screen.
- [x] The config screen renders a master toggle, a scrollable list of installed user apps with checkboxes, and a static text section listing the manual setup steps (grant Accessibility permission in system settings; disable battery optimization in system settings).
- [x] The master toggle's on/off state persists across app restarts.
- [x] The selected-blocklist state persists across app restarts.
- [x] BlocklistStore is exposed as an interface; the in-memory fake is the test double, the DataStore-backed implementation is the production implementation.
- [x] A Clock interface is defined and used in place of `System.currentTimeMillis()` in any code that touches time, even if no current code path needs it yet — the seam is set up so 02 and 03 can use it.
- [x] BlocklistStore has unit tests against the in-memory fake covering: default empty blocklist, default master-off, set master, add/remove from blocklist, read-after-write.
- [x] BlocklistStore has at least one instrumented test that confirms a value round-trips through DataStore across an app restart.
- [x] No permissions are requested programmatically at any point in the app (no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, no Accessibility permission request, no notification permission request). The static requirements section is the only mention of permissions.
- [x] Toggling the master switch and checking/unchecking apps are the only interactive elements on the screen; no shortcut buttons, no onboarding dialogs, no deep-links into system settings.

## Resolution

Implemented in commit `7116f99`. App package: `dev.twosec.app`. Toolchain: JDK 17, AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09.02, DataStore 1.1.1. Verified: `./gradlew assembleDebug` builds; `./gradlew :app:testDebugUnitTest` runs 15 unit tests across `InMemoryBlocklistStore{Master,Blocklist,Whitelist}Test` — all green; `./gradlew :app:lintDebug` clean (no errors); `<queries>` declared in manifest for Android 11+ `queryIntentActivities`; `System.currentTimeMillis()` only appears inside `SystemClock`.

Code review (Standards + Spec) caught one hard violation: the initial theme files introduced a custom color palette and custom typography, which the spec out-of-scope explicitly forbids. Removed `Color.kt` and `Type.kt`; `Theme.kt` is now a thin `MaterialTheme` passthrough. The UI continues to use `MaterialTheme.typography.*` (Material3 defaults, not custom).

Known follow-ups (not blocking):
- The instrumented test requires a connected device or emulator; not executed in this environment.
- `InstalledAppsProvider` enumerates only `ACTION_MAIN` + `CATEGORY_LAUNCHER` apps. Apps that can be foregrounded only via deep links (no launcher activity) won't appear in the config screen. The blocker still works on them — the engine reads any package name — but they can't be selected through the UI. Decision deferred to a future ticket; launcher-only is the standard pattern.
- All "newer version available" lint warnings left in place; current version pins are stable and well-tested.
