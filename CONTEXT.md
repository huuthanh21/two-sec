# Context

two-sec is a free, on-device Android app that inserts a brief pause between a user and the apps they tend to open on autopilot. When the user opens a chosen app, two-sec shows a breathing screen for a few seconds, then lets them continue or close (back to the home screen).

This file is the single source of truth for domain vocabulary. Use the terms here in code, tests, and tickets. If a concept does not appear here, the implementer is either inventing language the project does not use (reconsider) or has found a real gap (add the term).

## Glossary

- **App**: a user-installed Android application (e.g. Instagram, TikTok, Reddit). Identified by its package name.
- **Blocklist**: the set of package names the user has chosen to block. The engine never blocks anything not in the blocklist.
- **BlockerEngine**: the pure decision function. Given `(packageName, now)`, returns either `Intervene` or `Skip(reason)`. Constructed with a `BlocklistStore`, an ignored-package set, and the app's own package name. Highest testable seam.
- **BlockerAccessibilityService**: the `AccessibilityService` subclass that subscribes to `TYPE_WINDOW_STATE_CHANGED` and forwards foregrounded package names to the `BlockerEngine`. Owns no decision logic.
- **BlocklistStore**: the persistence seam for the master toggle, the blocklist, and the per-package whitelist expiry timestamps. Exposes `Flow`s for reads and `suspend` writes. Production implementation: `DataStoreBlocklistStore`. Test fake: `InMemoryBlocklistStore`.
- **BootReceiver**: the `BroadcastReceiver` registered for `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED`. Its only job is to renew the `BlockerAccessibilityService` subscription after a reboot.
- **Clock**: a `fun interface` with a single `now(): Long` method. Production: `SystemClock`. Test fake: `FakeClock`. The decision and intervention code use it instead of `System.currentTimeMillis()`.
- **ConfigActivity** (V1: `MainActivity`): the single configuration screen. Master toggle, installed-app checklist, static requirements section.
- **ConfigViewModel**: the `ViewModel` for `ConfigActivity`. Combines the master toggle flow, the blocklist flow, and the installed-apps snapshot into a single `ConfigUiState` and exposes toggle/check callbacks.
- **ConfigUiState**: the rendered state of the config screen — master toggle, installed apps, current blocklist, loading flag.
- **Home / Launcher packages**: the launcher or home screen packages configured in the system.
- **IME / Input Method**: an input method editor (e.g. keyboard) enabled in the Android system settings.
- **InstalledApp**: a UI-layer value type with a `packageName` and a `label`, produced by `InstalledAppsProvider`.
- **InstalledAppsProvider**: a thin wrapper over `PackageManager` that returns the launchable user apps on the device. The only place in the app that reads `PackageManager` to query user-facing apps, home/launcher packages, and enabled system input methods.
- **InterventionLauncher**: the platform-layer glue between `BlockerAccessibilityService` and `BlockerEngine`. Tracks the last foreground package so repeated `TYPE_WINDOW_STATE_CHANGED` events for the same package (in-app navigation) are filtered out as `Skip(AlreadyInForeground)` before the engine runs. When the engine returns `Intervene`, invokes the wired `onIntervene` callback with the package name; the service supplies that callback and is responsible for building the `Intent` and starting `InterventionActivity`.
- **Decision**: the return type of `BlockerEngine.decide(packageName, now)`. Sealed type with `Intervene` and `Skip(reason)` variants, where `reason` covers `MasterOff`, `NotInBlocklist`, `Whitelisted`, `IgnoredPackage`, `OwnPackage`, `AlreadyInForeground`. `AlreadyInForeground` is produced by `InterventionLauncher` (not the engine) when the foreground package has not changed since the last event — it filters in-app navigation before the engine runs.
- **Effect**: a side-effect the intervention state machine requests the activity to perform. `ShowButtons`, `HideButtons`, `FinishActivity`, `GoHome`, `WhitelistPackage(packageName, until)`.
- **Intervention**: the five-second pause the user sees when they open a blocked app. Renders a fixed prompt, suppresses the Back button, and shows Continue/Close buttons only after the countdown completes.
- **InterventionActivity**: the full-screen, opaque activity that renders the intervention. The only place on the intervention path that touches the `Activity` lifecycle, `setContentView`, or `Handler`.
- **InterventionEvent**: an input to the intervention state machine. `Tick(now)`, `UserTappedContinue`, `UserTappedClose`, `BackPressed`, `ScreenDestroyed`.
- **InterventionStateMachine**: the pure state machine that owns the intervention flow. Given a stream of `InterventionEvent`s, returns a stream of `InterventionViewState`s and `InterventionEffect`s. Second testable seam.
- **InterventionViewState**: the rendered state of the intervention. `Counting(millisRemaining)` or `AwaitingChoice`.
- **Master toggle**: the on/off switch for the entire blocker. When off, the engine always returns `Skip(MasterOff)`.
- **Skip(reason)**: the `Decision` variant that means the engine chose not to intervene. The `reason` is one of `MasterOff`, `NotInBlocklist`, `Whitelisted`, `IgnoredPackage`, `OwnPackage`, `AlreadyInForeground`.
- **ViewState**: short for `InterventionViewState` in the intervention context.
- **Whitelist expiry**: a future timestamp until which a package is exempt from blocking. Set by the intervention when the user taps Continue (now + 30s). Read by the engine on every event.
- **WhitelistGate**: the small helper that records and queries whitelist expiries. Delegates to `BlocklistStore` for persistence.
- **LogFileTree**: the `Timber.Tree` that appends every log line to a rolling file in app-internal storage. Owns the rotation policy (1 MB cap, two generations, UTF-8 bytes), the per-line format, and the async write queue. Takes a `Clock` so the timestamp is testable. The only place on the logging path that touches disk.
- **ShareIntentFactory**: the `fun interface` that turns the share shape (action, mime, uris, flags) into an `Intent`. Production: `AndroidShareIntentFactory`. Test fake: a `CapturingFactory` that records the call. Lets `LogSharer` stay free of `Intent` construction.
- **LogSharer**: the small helper that builds the share `Intent` for both log files. Delegates the actual `Intent` construction to a `ShareIntentFactory`. Pure delegation, unit-testable via a capturing factory.
- **Extract logs**: the user action that opens the system share sheet with both log files. Powered by `LogSharer`. Developer-facing.
- **User-facing package**: a package that is either a launchable app, a home/launcher package, or the two-sec app itself.

## Privacy

The log file contains the package names of the apps the user has added to the blocklist. The user already sees these in the config screen, so the log does not add new exposure — but anyone who receives a shared extract can read them. Do not log anything the user has not already seen in-app.
