# 03 — InterventionStateMachine (pure intervention flow)

**What to build:** The state machine that owns the InterventionActivity's behavior. It takes a stream of `InterventionEvent`s and returns a stream of `InterventionViewState` and `InterventionEffect`s. It is pure (no Android dependencies) and uses the Clock from ticket 01. The shape prototyped in the spec is the contract.

**Blocked by:** 01 — Config screen + BlocklistStore (foundation).

**Status:** resolved

- [x] The state, event, and effect types match the shape prototyped in the spec: `InterventionEvent` (`Tick(now)`, `UserTappedContinue`, `UserTappedClose`, `BackPressed`, `ScreenDestroyed`); `InterventionViewState` (`Counting(millisRemaining)`, `AwaitingChoice`); `InterventionEffect` (`ShowButtons`, `HideButtons`, `FinishActivity`, `GoHome`, `WhitelistPackage(packageName, until)`).
- [x] Starting state is `Counting` with `millisRemaining` = the full duration.
- [x] A `Tick(now)` event advances the countdown based on the injected Clock. After 5 seconds of wall time have elapsed (5000 ms ± one tick) the state transitions to `AwaitingChoice` and a single `ShowButtons` effect is emitted.
- [x] A `BackPressed` event while in `Counting` is a no-op (no state change, no effects).
- [x] A `BackPressed` event while in `AwaitingChoice` produces the same effect stream as `UserTappedClose` (`FinishActivity` + `GoHome`).
- [x] `UserTappedContinue` emits `FinishActivity` and `WhitelistPackage(packageName, now + 30s)`.
- [x] `UserTappedClose` emits `FinishActivity` and `GoHome`.
- [x] `UserTappedContinue` and `UserTappedClose` while still in `Counting` are ignored (defensive — the Activity is expected to hide the buttons, but the state machine must be safe even if it isn't).
- [x] `ScreenDestroyed` is a no-op (the cleanup hook is wired in a later ticket).
- [x] Unit tests cover all of the above scenarios with a fake Clock that the test advances manually. No Android, no Robolectric.
- [x] InterventionStateMachine has no Android imports.

## Resolution

Implemented in commit `daa3e87`. Files added (all under `dev.twosec.app.domain`):
- `InterventionEvent.kt` — sealed interface with `Tick(now)`, `UserTappedContinue`, `UserTappedClose`, `BackPressed`, `ScreenDestroyed`.
- `InterventionViewState.kt` — sealed interface with `Counting(millisRemaining)`, `AwaitingChoice`.
- `InterventionEffect.kt` — sealed interface with `ShowButtons`, `HideButtons`, `FinishActivity`, `GoHome`, `WhitelistPackage(packageName, until)`.
- `InterventionStateMachine.kt` — the state machine. Constructor takes `(packageName, clock)`. Exposes `state: InterventionViewState` (read-only) and `initialEffects: List<InterventionEffect>` (a single `HideButtons` per spec line "emitted on entry to Counting"). `process(event)` returns the effects triggered by that single event.
- `InterventionStateMachineTest.kt` — 16 unit tests covering all 11 ticket bullets; 37 tests pass in total across the module.

Design notes:
- Duration (5000 ms) and whitelist (30 000 ms) are private companion-object `const val`s, not constructor parameters — keeps the API to `(packageName, clock)` so V1 cannot accidentally customize them.
- `startTimeMs` is captured from `clock.now()` at construction; the test fake's `nowMs` is set before construction so this equals the test's chosen start time.
- Code review caught one spec deviation (initial `HideButtons` emission) and one scope-creep concern (configurable duration/whitelist as parameters); both fixed before commit.
- Verified: `./gradlew :app:testDebugUnitTest` (37 unit tests, all green); `./gradlew :app:lintDebug` clean.

Known follow-ups (not blocking):
- The `InterventionActivity` that drives this state machine is wired in ticket 04.
