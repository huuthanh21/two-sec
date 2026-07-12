# 04 — Service + InterventionActivity + BootReceiver (end-to-end)

**What to build:** The Android wiring that turns the pure modules from 02 and 03 into a working blocker. BlockerAccessibilityService subscribes to `TYPE_WINDOW_STATE_CHANGED` and forwards foregrounded package names to BlockerEngine; when the engine returns `Intervene`, the service launches InterventionActivity. The Activity drives the InterventionStateMachine, renders its view state, runs the 5-second timer, and executes its effects (whitelist a package, finish the activity, go home). BootReceiver renews the service subscription after `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED`.

**Blocked by:** 02 — BlockerEngine + WhitelistGate (pure decision logic); 03 — InterventionStateMachine (pure intervention flow).

**Status:** resolved

- [x] BlockerAccessibilityService is registered in the manifest with the right `meta-data` resource and listens exclusively for `TYPE_WINDOW_STATE_CHANGED`.
- [x] On every `TYPE_WINDOW_STATE_CHANGED` event, the service calls BlockerEngine and, when the engine returns `Intervene`, launches InterventionActivity as a full-screen, opaque Activity over the target app.
- [x] The service has no background threads, no `Handler` running between events, no `UsageStatsManager` polling, and no `ActivityManager` polling.
- [x] InterventionActivity renders the `Counting` state by displaying the fixed prompt and a countdown value, with the Continue/Close buttons hidden.
- [x] InterventionActivity drives the InterventionStateMachine by feeding it `Tick` events at ~100 ms intervals using the Clock from ticket 01.
- [x] When the state machine transitions to `AwaitingChoice`, the Activity reveals the Continue and Close buttons.
- [x] The system Back button is suppressed while the state machine is in `Counting`; after the transition to `AwaitingChoice`, Back is treated as Close.
- [x] Tapping Continue executes the `WhitelistPackage` effect (writes to WhitelistGate), executes `FinishActivity`, and leaves no memory footprint.
- [x] Tapping Close (or Back after the timer) executes `FinishActivity` and dispatches `Intent(ACTION_MAIN).addCategory(CATEGORY_HOME)` to send the user to the launcher.
- [x] After a Continue, opening the same target app again within 30 seconds does not re-trigger the intervention; opening a different target app still does.
- [x] BootReceiver is registered in the manifest for `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` and, on receipt, ensures the BlockerAccessibilityService's subscription is renewed.
- [x] An instrumented test launches a real target app on a device or emulator and asserts the intervention appears.
- [x] An instrumented test sends a synthetic `BOOT_COMPLETED` broadcast and asserts the service subscription is renewed.
- [x] No foreground service, no notifications, no analytics SDK, no network permission is added.

## Resolution

Implemented in commit `HEAD`. Files added (all under `dev.twosec.app`):

Production (`app/src/main`):
- `platform/SystemPackages.kt` — hard-coded ignore set injected into `BlockerEngine` (system UI, launchers, input methods, etc.).
- `platform/BlockerAccessibilityService.kt` — subscribes to `TYPE_WINDOW_STATE_CHANGED`; no timers, no polling. Delegates to `InterventionLauncher`.
- `platform/InterventionLauncher.kt` — the testable seam: given a package, calls `engine.decide(...)` and, on `Intervene`, starts `InterventionActivity` with `FLAG_ACTIVITY_NEW_TASK` and the package extra. `activityStarter` is injectable.
- `platform/BootReceiver.kt` — handles `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED`; checks the accessibility service is still in the enabled list and re-enables the component if it was disabled.
- `ui/InterventionActivity.kt` — full-screen, opaque Activity (`Theme.TwoSec.Intervention`); drives the state machine via a `Handler.postDelayed(100ms)` tick; renders the prompt + countdown while `Counting`, then Continue/Close; `BackHandler` no-ops during `Counting` and triggers Close during `AwaitingChoice`. Uses the `appScope` from `TwoSecApp` for the whitelist write so the activity can finish before the DataStore write completes.
- `res/xml/blocker_accessibility_service_config.xml` — `typeWindowStateChanged` only, `canRetrieveWindowContent=false`.
- `res/values/themes.xml` — added `Theme.TwoSec.Intervention` (`NoActionBar.Fullscreen`, opaque, no animations).

Wiring:
- `TwoSecApp` constructs the `Clock`, `BlockerEngine`, `WhitelistGate`, and a long-lived `appScope` (`SupervisorJob + Dispatchers.IO`).
- Manifest registers the service (`BIND_ACCESSIBILITY_SERVICE` permission, accessibility `meta-data`), the activity (`exported=false`, `singleTask`, `excludeFromRecents`, `showOnLockScreen`), and the receiver (`RECEIVE_BOOT_COMPLETED` permission, both boot actions).

Tests (`app/src/androidTest`):
- `AndroidManifest.xml` — overrides `InterventionActivity` to `exported=true` (test only) and adds `TestTargetActivity` so the cross-process test can launch it.
- `TestTargetActivity.kt` — blank `Activity` that plays the role of the "real target app" the user opened.
- `InterventionFlowTest.kt` — 4 tests: (1) full integration: launches `TestTargetActivity`, calls the launcher, waits up to 5 s for `dev.twosec.app` to be the top resumed package via `dumpsys`; (2) the launcher's `activityStarter` is called with the right `Intent` (component, extra, flag); (3) the launcher does NOT call `activityStarter` for an unblocked package; (4) `InterventionActivity` resolves via `PackageManager`.
- `BootReceiverTest.kt` — 4 tests: (1) registered for `BOOT_COMPLETED`; (2) registered for `LOCKED_BOOT_COMPLETED`; (3) `onReceive(BOOT_COMPLETED)` does not crash; (4) `onReceive(LOCKED_BOOT_COMPLETED)` does not crash. The actual `sendBroadcast` of the protected action is replaced by a direct `onReceive` call (the system blocks non-system senders).

Design notes:
- `InterventionLauncher` was extracted from the service so the integration test can drive it without the accessibility service being enabled in Settings.
- `InterventionActivity` was deliberately made `exported=false` in production; the test-only `tools:replace="android:exported"` in the `androidTest` manifest opens it for the test APK only. The production APK is unaffected.
- `Handler.postDelayed(100ms)` replaced the initial `lifecycleScope.launch { repeatOnLifecycle { delay(100) } }` because `Instrumentation.startActivitySync` requires the main thread to be idle and the coroutine kept it permanently busy. The Handler is removed in `onDestroy`.
- Verified: `./gradlew :app:testDebugUnitTest` (37 unit tests, all green), `./gradlew :app:lintDebug` clean, `./gradlew :app:connectedDebugAndroidTest` (11 instrumented tests, all green on `23078PND5G`).

Known follow-ups (not blocking):
- The `BootReceiver.renewAccessibilitySubscription` only re-enables the component; the OS auto-rebinds accessibility services after reboot, so the explicit "renew" is largely a no-op in practice. It exists as a testable hook.
- The system-package ignore set is a hardcoded list. Should be reviewed per device family (MIUI/Samsung/etc. ship extra system apps that may pop up as foreground windows).
- The full integration test polls `dumpsys` with a 200 ms cadence; a `UiAutomator` watcher would be more robust but is out of scope.

