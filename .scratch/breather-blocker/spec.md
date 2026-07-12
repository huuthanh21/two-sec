# Spec: V1 "Breather" Blocker

Status: ready-for-agent

## Problem Statement

A user who wants to reduce mindless scrolling on their Android phone has no lightweight, free, on-device way to insert a friction barrier between the impulse to open a distracting app and the act of using it. Existing solutions either require paid subscriptions, ship telemetry, or run continuous background polling that drains the battery. The user wants a set-and-forget blocker that wakes up only when a target app is about to be used, forces a short pause, and then stays completely dormant.

## Solution

two-sec ships as a single-process Android app that registers an `AccessibilityService` tuned to listen for `TYPE_WINDOW_STATE_CHANGED` events. When a foregrounded window belongs to an app the user has selected, the service launches a full-screen, opaque Intervention Activity that displays a fixed prompt and runs a 5-second countdown. During the countdown the system Back button is suppressed and no controls are shown. When the timer expires, two buttons appear: **Continue** dismisses the intervention and temporarily exempts that package for 30 seconds so the underlying app's regaining focus does not retrigger the barrier; **Close** dismisses the intervention and sends the user to the launcher. A minimal configuration screen lets the user pick which installed apps to block, toggle the whole blocker on or off, and read a static requirements section listing the Android settings and permissions (Accessibility permission, battery-optimization exemption) the user must enable manually. A boot receiver restarts the service after reboot. Permission grants and battery-optimization exemption are the user's responsibility to set up via system settings.

The application is dormant (zero CPU) outside of the AccessibilityService event handler and the brief lifetime of an Intervention Activity. The decision of *whether* to intervene is computed by a pure function; the timer, button enabling, and whitelist side-effects live in a domain object that the Activity drives. Both are unit-testable without the Android framework.

## User Stories

1. As a user trying to cut down on scrolling, I want the app to intercept a blocked app the instant it comes to the foreground, so that I cannot start using it on autopilot.
2. As a user trying to cut down on scrolling, I want the intervention screen to launch fully opaque over the target app, so that I cannot see the target app's content during the pause.
3. As a user trying to cut down on scrolling, I want the intervention to display a simple, fixed prompt ("Take a deep breath"), so that I am gently reminded to pause rather than lectured.
4. As a user trying to cut down on scrolling, I want a mandatory 5-second countdown, so that the friction is long enough to break the impulse but short enough to feel tolerable.
5. As a user trying to cut down on scrolling, I want the system Back button to be suppressed during the countdown, so that I cannot skip the pause with one tap.
6. As a user who just waited out the timer, I want Continue and Close buttons to appear only after the countdown completes, so that the friction is real.
7. As a user who hits Continue, I want the target app to come back into focus without immediately retriggering the barrier, so that the app is usable once I have committed to opening it.
8. As a user who hits Continue, I want the temporary exemption to last a bounded time (30 seconds) and then automatically expire, so that re-engagement after a long break is also caught.
9. As a user who hits Close, I want to be returned to the Android home screen, so that I can choose a different activity rather than the one I just resisted.
10. As a user who selected an app by mistake, I want the intervention Activity to be fully destroyed after my choice, so that no memory is held in the background.
11. As a first-time user, I want a single configuration screen that lists my installed apps, so that I can pick which ones to block without digging through sub-menus.
12. As a user picking apps to block, I want checkboxes next to each installed app, so that my selections persist between launches.
13. As a user who has finished configuring, I want a master toggle to enable or disable the entire blocker, so that I can pause the system on demand (weekends, travel) without losing my app list.
14. As a first-time user, I want a static requirements section on the config screen that lists every Android setting and permission I need to enable for the app to work (Accessibility permission, battery-optimization exemption), so that I know what to do even though the app does not guide me to it.
15. As a user with notifications disabled, I do not want the app to surface any user-visible notification, so that it stays out of my way.
16. As a user whose phone rebooted, I want the blocker to come back online automatically, so that I do not have to remember to relaunch the app.
17. As a user whose phone has sat idle for hours, I want the blocker to still be alive when I next open a target app, so that deep doze mode does not silently disable my protection.
18. As a user on a non-blocked app, I want the app to use zero CPU, so that my battery is unaffected.
19. As a user who opens and closes the target app repeatedly, I want the 30-second exemption to be reset by each new foregrounding of that app, so that I cannot chain quick continuations to bypass the barrier.
20. As a user who switched to a different blocked app during the exemption window, I want the new target to still be blocked normally, so that exemptions are per-package.
21. As a user, I want the blocker to ignore its own package, so that the configuration screen itself does not trigger an intervention.
22. As a user, I want the blocker to ignore system launchers and other non-blockable system windows, so that I am not interrupted when I press Home or open Recents.
23. As a user who has never used the app before, I want it to ship with an empty blocklist by default, so that nothing is blocked until I opt in.
24. As a user, I want all of my configuration to persist across app restarts, reboots, and updates, so that I configure once and forget.

## Implementation Decisions

### Modules and ownership

- **BlockerAccessibilityService** — the Android `AccessibilityService` subclass. Its only responsibility is subscribing to `AccessibilityEvent`s of type `TYPE_WINDOW_STATE_CHANGED`, extracting the foregrounded package, and forwarding that single piece of information to the BlockerEngine. It owns no decision logic and no persistence of its own. It is the only place the app touches the Android framework on the detection path.
- **BlockerEngine** — a pure-ish domain object that owns the intervene-or-not decision. It is constructed with a `BlocklistStore` and a `Clock`. Given `(packageName, now)`, it returns a `Decision` of `Intervene` or `Skip(reason)`. It is the highest testable seam.
- **InterventionStateMachine** — a domain object that owns the InterventionActivity's behavior. Given a stream of events (`Tick`, `UserTappedContinue`, `UserTappedClose`, `BackPressed`, `ScreenDestroyed`), it returns a sequence of `Effect`s (e.g. `ShowButtons`, `FinishActivity`, `GoHome`, `WhitelistPackage`) and the next `ViewState`. It is constructed with a `Clock` and a `WhitelistGate` (or both are passed in by the engine). It is the second testable seam.
- **InterventionActivity** — a thin Android Activity that renders the `ViewState` produced by the InterventionStateMachine and forwards user input back to it. It is the only place on the intervention path that touches `Handler`, `setContentView`, or the `Activity` lifecycle.
- **BlocklistStore** — the persistence seam for the master toggle, the set of blocked package names, and the per-package whitelist expiry timestamp. It is the third testable seam. It exposes synchronous read/write to the three pieces of state.
- **ConfigActivity** — the single configuration screen. Renders the master toggle, the installed-app checklist, and a static requirements section that lists the Android settings and permissions the user must enable manually (Accessibility permission, battery-optimization exemption). The requirements text is a fixed string, not interactive. It is the only place the app reads `PackageManager` to enumerate user-installed apps.
- **BootReceiver** — a `BroadcastReceiver` registered in the manifest for `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` whose only job is to ensure the `BlockerAccessibilityService`'s subscription is renewed if the OS dropped it. It does not launch any UI.
- **WhitelistGate** — a small helper used by both the engine and the intervention state machine to record "this package is exempt until `now + 30s`" and to query whether a given package is currently exempt. It delegates to `BlocklistStore` for persistence and uses the injected `Clock` for time, so it is testable without `System.currentTimeMillis()`.

### Decision function

The BlockerEngine decision is:

- Skip if `packageName == two-sec's own package`.
- Skip if `packageName` is in the system-ignored set (launchers, Recents, system UI) — final list determined empirically; the engine accepts a set injected at construction time so the policy is testable.
- Skip if the master toggle is off.
- Skip if the package is currently whitelisted (`now < expiryTimestamp`).
- Otherwise return `Intervene`.

### Intervention behavior

- The InterventionActivity is launched with `FLAG_ACTIVITY_NEW_TASK` and a full-screen, opaque theme so the user cannot see the underlying target app's content.
- The InterventionStateMachine starts in `Counting`, schedules a `Tick` every 100 ms against the injected `Clock`, and transitions to `AwaitingChoice` once 5 seconds of wall time have elapsed (5000 ms ± one tick).
- The buttons remain absent in `Counting`. The Activity is responsible for actually hiding them.
- A `BackPressed` event received while in `Counting` is a no-op (the Activity overrides `onBackPressed` and forwards nothing, or forwards it to the state machine which emits no effect — implementation detail of the seam).
- A `BackPressed` event received in `AwaitingChoice` is treated the same as `Close`.
- `UserTappedContinue` emits `FinishActivity` plus `WhitelistPackage(packageName, now + 30s)`. The activity's `onDestroy` emits `ScreenDestroyed`, which is currently a no-op but is the seam through which any future cleanup passes.
- `UserTappedClose` emits `FinishActivity` plus `GoHome` (an `Intent(ACTION_MAIN).addCategory(CATEGORY_HOME)`).
- The whitelist is per-package, not global. The engine does not allow a `Continue` from package A to exempt package B.

### State shape (prototyped)

The InterventionStateMachine's state and event types are:

```
sealed interface InterventionEvent {
    data class Tick(val now: Long) : InterventionEvent
    data object UserTappedContinue : InterventionEvent
    data object UserTappedClose : InterventionEvent
    data object BackPressed : InterventionEvent
    data object ScreenDestroyed : InterventionEvent
}

sealed interface InterventionViewState {
    data class Counting(val millisRemaining: Long) : InterventionViewState
    data object AwaitingChoice : InterventionViewState
}

sealed interface InterventionEffect {
    data object ShowButtons : InterventionEffect
    data object HideButtons : InterventionEffect       // emitted on entry to Counting
    data object FinishActivity : InterventionEffect
    data object GoHome : InterventionEffect
    data class WhitelistPackage(val packageName: String, val until: Long) : InterventionEffect
}
```

This shape was chosen so the Activity is a pure renderer/effect-executor and the state machine is fully testable without Android.

### Persistence

- The master toggle, blocklist, and per-package whitelist expiry timestamps are persisted via `DataStore<Preferences>` (or a small equivalent) — final choice to be made during implementation, but the BlocklistStore interface is what tests target, not the storage engine.
- Reads in the decision path are synchronous (cached at the top of each AccessibilityService event).
- Whitelist entries are written through `WhitelistGate`; the engine never writes the whitelist directly.

### Permissions and manifest

- `BIND_ACCESSIBILITY_SERVICE` declared in the manifest with a `meta-data` resource declaring the event types the service wants (`typeWindowStateChanged`) and a package-name filter that includes all packages (Android does not allow per-package filter for this event type; filtering happens inside the engine).
- `RECEIVE_BOOT_COMPLETED` declared; `BootReceiver` registered for both `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED`.
- V1 declares no runtime-permission requests and no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. The Accessibility permission and battery-optimization exemption are configured by the user manually in system settings, as documented in the config screen's static requirements section.
- No `FOREGROUND_SERVICE`, no `POST_NOTIFICATIONS`, no analytics SDKs, no network permission.

### What the AccessibilityService does and does not do

- It does not poll `UsageStatsManager`, `ActivityManager`, or any process table.
- It does not start any background thread, coroutine scope, or scheduled job.
- It does not keep a `Handler` alive between events; the only `Handler`-driven work in the app is the 5-second timer inside the InterventionStateMachine, scoped to the lifetime of the InterventionActivity.
- Its sole subscription is `setServiceInfo` with the `typeWindowStateChanged` flag at service-attach time.

## Testing Decisions

### What makes a good test

A test is good if it asserts externally observable behavior of a seam — the decision returned by the engine, the view state and effects emitted by the state machine, the values persisted by the store — without asserting on the internals of the Android framework, the order of method calls inside a class, or private fields.

### Seams and their tests

- **BlockerEngine** is tested as a pure function: a table of `(packageName, masterOn, blocklist, whitelist, now)` → expected `Decision` is encoded as parameterized unit tests. No Android, no Robolectric. A fake `Clock` is injected; a `BlocklistStore` is faked in memory.
- **InterventionStateMachine** is tested as a state machine: sequences of `InterventionEvent`s are fed in (with a fake `Clock` advancing time), and the resulting `InterventionViewState` and `Effect` stream is asserted. Tests cover: counting → awaiting choice, back-during-counting is ignored, back-after-counting is treated as Close, Continue emits the correct whitelist expiry, Close emits GoHome. No Android, no Robolectric.
- **BlocklistStore** is tested with an in-memory fake for unit tests, plus a single instrumented test that confirms values round-trip through `DataStore`. The interface is what V1 tests lock down, not the storage engine.
- **BlockerAccessibilityService** and **InterventionActivity** are covered by a small set of instrumented Espresso/UI tests, not unit tests. Their job is to confirm wiring (the engine is called, the state machine drives the view) rather than to re-test decision logic.
- **BootReceiver** is covered by a single instrumented test that sends a synthetic `BOOT_COMPLETED` broadcast and asserts the service subscription is renewed.

### Prior art

This is a greenfield repo. Prior art is the conventional Android testing split: pure logic in JUnit, Activity/Service wiring in instrumented Espresso.

## Out of Scope

The following are explicitly excluded from V1 to keep the build small and the seams shallow:

- Analytics, daily usage graphs, success-rate statistics, or any telemetry.
- Customizing the intervention timer duration, the prompt text, or the whitelist duration.
- Per-time-of-day or per-day-of-week blocking rules.
- Any in-app onboarding flow, guided permission requests, or deep-links into system settings. The app lists the manual setup steps; it does not guide the user through them.
- Multiple blocklists, profiles, or per-block exceptions within a blocked app.
- A custom theme, animations, transitions, or a dark-mode toggle.
- A notification-surface for the service.
- Scheduling the blocker to auto-disable on weekends or at night.
- A widget or quick-tile for toggling the master switch.
- Exporting or importing the blocklist.
- Localization beyond English.
- A tablet-optimized layout; the V1 UI is phone-form-factor only.

## Further Notes

- **5-second value source**: the PRD specifies 5 seconds and the 30-second whitelist explicitly. These are the only knobs in the system; they are encoded as named constants in the state machine and engine respectively so a future customization feature has a single place to thread a config through.
- **System-package ignore list**: the engine takes the ignored-package set as a constructor argument. The default set (launcher, Recents/overview, system UI, the app's own package) is decided at startup time. The set itself is not part of this spec.
- **AccessibilityService reliability on OEM Android**: OEMs (Xiaomi, Samsung, OnePlus) frequently kill accessibility services even when the user has exempted the app from battery optimization. This is a known platform issue; the V1 spec does not solve it. The in-app mitigation is the BootReceiver; the user-driven mitigation is exempting the app from battery optimization in system settings (as listed in the config screen's requirements section). If real-world testing shows the service is killed even with both mitigations in place, that is a follow-up spec.
- **No ADRs yet**: this is a greenfield repo, so no `docs/adr/` entries exist. The first architectural decision worth recording is probably the engine/state-machine split itself; the implementer should file `0001-engine-and-state-machine-seams.md` once that choice is locked in.
- **No glossary yet**: `CONTEXT.md` does not exist. The vocabulary introduced in this spec — BlockerEngine, InterventionStateMachine, BlocklistStore, WhitelistGate, Decision, ViewState, Effect — is the proposed initial glossary. The implementer can lift it into `CONTEXT.md` if they want to formalize the language.
