# 03 — InterventionStateMachine (pure intervention flow)

**What to build:** The state machine that owns the InterventionActivity's behavior. It takes a stream of `InterventionEvent`s and returns a stream of `InterventionViewState` and `InterventionEffect`s. It is pure (no Android dependencies) and uses the Clock from ticket 01. The shape prototyped in the spec is the contract.

**Blocked by:** 01 — Config screen + BlocklistStore (foundation).

**Status:** ready-for-agent

- [ ] The state, event, and effect types match the shape prototyped in the spec: `InterventionEvent` (`Tick(now)`, `UserTappedContinue`, `UserTappedClose`, `BackPressed`, `ScreenDestroyed`); `InterventionViewState` (`Counting(millisRemaining)`, `AwaitingChoice`); `InterventionEffect` (`ShowButtons`, `HideButtons`, `FinishActivity`, `GoHome`, `WhitelistPackage(packageName, until)`).
- [ ] Starting state is `Counting` with `millisRemaining` = the full duration.
- [ ] A `Tick(now)` event advances the countdown based on the injected Clock. After 5 seconds of wall time have elapsed (5000 ms ± one tick) the state transitions to `AwaitingChoice` and a single `ShowButtons` effect is emitted.
- [ ] A `BackPressed` event while in `Counting` is a no-op (no state change, no effects).
- [ ] A `BackPressed` event while in `AwaitingChoice` produces the same effect stream as `UserTappedClose` (`FinishActivity` + `GoHome`).
- [ ] `UserTappedContinue` emits `FinishActivity` and `WhitelistPackage(packageName, now + 30s)`.
- [ ] `UserTappedClose` emits `FinishActivity` and `GoHome`.
- [ ] `UserTappedContinue` and `UserTappedClose` while still in `Counting` are ignored (defensive — the Activity is expected to hide the buttons, but the state machine must be safe even if it isn't).
- [ ] `ScreenDestroyed` is a no-op (the cleanup hook is wired in a later ticket).
- [ ] Unit tests cover all of the above scenarios with a fake Clock that the test advances manually. No Android, no Robolectric.
- [ ] InterventionStateMachine has no Android imports.
