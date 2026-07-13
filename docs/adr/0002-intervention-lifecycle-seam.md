# ADR 0002: Intervention lifecycle as a fourth seam

Status: Accepted (2026-07-13)

## Context

PR #5 fixed a bug where reopening a blocked app after the user tapped **Close** was filtered as `Skip(AlreadyInForeground)`. The cause was a sticky field on `InterventionLauncher.lastForegroundPackage`: once the launcher saw a blocked package, the state stayed on that package until a `TYPE_WINDOW_STATE_CHANGED` for *some other* package arrived. The intervention activity's event, the home launcher's event — either was expected to overwrite the state. If the home screen's event arrived with `event.packageName == null` (which `BlockerAccessibilityService.onAccessibilityEvent` silently drops via `?: return`), the state stayed on the blocked package and the next reopen matched and was filtered.

The PR cleared the field on `Intervene` and added two regression tests. It also flagged, in its architectural note, that the deeper cause is the launcher inferring the user's location from accessibility events rather than from the intervention lifecycle. The inference is fragile because it depends on every transition firing, which Android does not guarantee.

ADR-0001 set up three seams — engine, intervention flow, persistence — with the rule that the Android side adds no decision logic of its own. The launcher obeys that rule for *decisions* (it just calls the engine), but it still owns a piece of platform-derived state (`lastForegroundPackage`) that should be a property of the intervention lifecycle, not an inference from missing events.

## Decision

Introduce a fourth seam: an `InterventionLifecycle` module that owns the foreground + intervention session state explicitly as a sealed type:

```
Idle | InForeground(p) | PendingIntervention(p) | InIntervention(p)
```

The module exposes three mutators:

- `onForegroundApp(packageName: String): Decision` — called by the service on every `TYPE_WINDOW_STATE_CHANGED`. The module updates the state, runs the engine, returns the `Decision`.
- `onInterventionShown(packageName: String): Unit` — called by the activity in `onCreate`.
- `onInterventionDismissed(packageName: String): Unit` — called by the activity in `onDestroy`.

The module owns the `BlockerEngine` call. The service feeds it events and pattern-matches on `Decision.Intervene` to start `InterventionActivity`. The activity feeds it lifecycle callbacks. Neither the service nor the activity owns session state.

`InterventionLauncher` is deleted. Its only callers are the service (now wired directly to the new module) and its own test (rewritten as `InterventionLifecycleTest`).

As a side effect of the same pass, the activity's `processEventsReversed(events: List<InterventionEvent>)` is deleted and the state machine's `process(event)` contract is sharpened: effects are returned in the order the activity should apply them. `UserTappedContinue` becomes `[WhitelistPackage(pkg, now+30s), FinishActivity]`; `UserTappedClose` and `BackPressed` become `[GoHome, FinishActivity]`. The activity applies effects in the order the state machine emits them, with no reversal.

`InterventionLifecycle` is constructed in `TwoSecApp.onCreate` and reached by both the service and the activity via `application as TwoSecApp`. The module lives in the `domain` package — it is pure logic over a `BlockerEngine` and a `Clock` (the same dependencies the launcher had, minus the callback).

## Consequences

- The bug class from PR #5 is closed at the root. The session state is explicit, not inferred; missing accessibility events cannot strand it on a stale package.
- The intervention lifecycle is named and local. Lifecycle bugs live in one module, behind one interface, with one test surface.
- The `Skip(AlreadyInForeground)` filter becomes a property of the session state, not a comparison on a sticky field. Tests assert on state, not on a callback.
- The activity's effect-application logic is a `forEach`; the state machine owns effect ordering. The reversal goes away; the dead `List<InterventionEvent>` parameter goes with it.
- One new file pair: `InterventionLifecycle.kt` + `SessionState.kt`. One new test file: `InterventionLifecycleTest.kt`.
- One file deleted: `InterventionLauncher.kt` and its test.
- The state machine swaps the order of effects for `UserTappedContinue` and `UserTappedClose`/`BackPressed`; `InterventionStateMachineTest` updates its expected lists for those three cases.
- The seam table in ADR-0001 grows by one row.
- The dual-clock setup in the activity (`Handler.postDelayed(100ms)` + state machine's `startTimeMs`) is left as-is. It is a real smell but not in scope; refactoring it is orthogonal to the lifecycle seam.

## Alternatives considered

- **Keep `InterventionLauncher`, add the activity callbacks to it.** Rejected: the launcher becomes a god-object that knows about both accessibility events and activity lifecycle. The seam between "track session" and "decide" gets fuzzier, not sharper, and the module is still named after the wrong thing.
- **Add the lifecycle callbacks to `BlockerEngine`.** Rejected: the engine is a pure function over a `BlocklistSnapshot`. Lifecycle is a separate concern; coupling them re-introduces the coupling ADR-0001 worked to remove.
- **Drive the state machine with a coroutine `Flow` instead of `Decision` returns.** Rejected: the activity must be started synchronously after the engine decides (`Decision.Intervene` triggers `startActivity(...)` immediately). Coroutines add scheduling and cancellation concerns for no gain in this code path.
- **Leave `lastForegroundPackage` as-is and only fix the null-event drop in the service.** Rejected: the bug was a symptom of the launcher pattern itself, not just of one dropped event. PR #5's architectural note already flagged this; doing the minimum here would re-suggest the same fragility to a future architecture review.
- **Track foreground state in the store (`BlocklistStore`).** Rejected: the store is a persistence seam, not a runtime-state seam. Mixing them would conflate "what is configured" with "what the user is currently doing" and break the snapshot's read-only contract.
