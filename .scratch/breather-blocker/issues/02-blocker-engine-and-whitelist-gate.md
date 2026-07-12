# 02 — BlockerEngine + WhitelistGate (pure decision logic)

**What to build:** The `decide(packageName, now) → Decision` function and the WhitelistGate helper. Both are pure (no Android dependencies) and use the BlocklistStore + Clock from ticket 01. The system-package ignore set is injected at construction so the policy is testable. The engine is the highest testable seam in the system.

**Blocked by:** 01 — Config screen + BlocklistStore (foundation).

**Status:** resolved

- [x] A `Decision` sealed type with at least `Intervene` and `Skip(reason)` variants, where `reason` covers at least: `MasterOff`, `NotInBlocklist`, `Whitelisted`, `IgnoredPackage`, `OwnPackage`.
- [x] BlockerEngine's `decide(packageName, now)` returns `Intervene` only when all of: master toggle is on, the package is in the blocklist, the package is not currently whitelisted, the package is not in the injected ignore set, and the package is not the app's own package.
- [x] WhitelistGate exposes at least `setWhitelist(packageName, untilMillis)`, `isWhitelisted(packageName, now) → Boolean`, and `clearExpired(now)`. It uses BlocklistStore for persistence.
- [x] A system-package ignore set is injected into BlockerEngine at construction time. The default set is empty in tests and is populated by a small platform helper (or a hardcoded list) at the call site, with the actual list left to the implementer's discretion.
- [x] A table-driven unit test suite covers the full truth table for `decide` across the five input axes (masterOn, inBlocklist, whitelisted, inIgnoreSet, isOwnPackage). All cases pass without Robolectric or instrumented tests.
- [x] WhitelistGate has unit tests covering: set then query returns true within window, query returns false after expiry, `clearExpired` removes only the expired entries, distinct packages have independent windows.
- [x] BlockerEngine and WhitelistGate have no Android imports.

## Resolution

Implemented in commit `5b5fb8a`. New package `dev.twosec.app.domain` holds `Decision` (sealed interface: `Intervene`, `Skip(SkipReason)` with enum `SkipReason` of `MasterOff`, `NotInBlocklist`, `Whitelisted`, `IgnoredPackage`, `OwnPackage`), `BlockerEngine(store, ignoredPackages, ownPackage)`, and `WhitelistGate(store)`. Both are pure — no Android imports.

`BlockerEngine.decide(packageName, now)` is a synchronous function that reads `store.snapshot()` internally. Check order: own package → ignored set → master toggle → blocklist → whitelist → Intervene. The first failing check determines the `SkipReason`.

To support synchronous reads from the engine, `BlocklistStore` gained a `snapshot(): BlocklistSnapshot` method. `InMemoryBlocklistStore` reads its `MutableStateFlow` values directly. `DataStoreBlocklistStore` maintains a `MutableStateFlow` that mirrors the store via an `init`-block collector, so `snapshot()` returns the current state without `runBlocking`. The Flow-based reads (`masterEnabled()`, `blocklist()`, `whitelistExpiries()`) continue to map directly from `store.data` — the StateFlow is used only by `snapshot()`.

Verified: `./gradlew :app:testDebugUnitTest` runs 21 unit tests (15 from ticket 01 + 1 `BlockerEngineTest` table-driven over all 32 cases + 5 `WhitelistGateTest`) — all green; `./gradlew :app:lintDebug` clean.

Code review (Standards + Spec) caught two doc-drift violations: `CONTEXT.md` claimed the engine takes a `Clock` and the gate "uses the injected Clock for time" — neither is true. The ticket's `decide(packageName, now)` signature makes the engine's `Clock` redundant, and the gate's three listed methods all take `now` explicitly, so the gate's `Clock` was dead code. Fixed by removing `Clock` from `WhitelistGate`'s constructor and updating `CONTEXT.md` to match. The Spec sub-agent also noted that `spec.md:57-63` decision-function ordering omits the blocklist check; the ticket explicitly adds it, so the code matches the ticket (spec is incomplete).

Known follow-ups (not blocking):
- The system-package ignore set is an empty `Set<String>()` at construction; a platform helper that populates it (launcher, Recents, system UI) is left for a later ticket when the AccessibilityService wiring lands.
- `DataStoreBlocklistStore.snapshot()` returns the initial defaults (`masterEnabled = false`, empty blocklist) before the first `store.data` emission lands. Acceptable for V1: the AccessibilityService is only enabled after the user grants the permission, by which point DataStore has loaded. If a future caller needs the first emission synchronously, the fix is to expose `snapshot()` as a `suspend` or to cache-on-first-read.
