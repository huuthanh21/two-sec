# ADR 0001: Engine and state-machine seams

Status: Accepted (2026-07-12)

## Context

The V1 spec describes an Android app with three pure pieces of logic — a decision function, an intervention flow, and a persistence layer — and three pieces of Android framework wiring — an `AccessibilityService`, an `InterventionActivity`, and a `BootReceiver`. The pure pieces drive the behavior; the Android pieces only translate events and render effects.

We need to draw a clean line so that:

- the decision logic is testable without an emulator, Robolectric, or any Android dependency;
- the intervention flow is testable without an `Activity`, a `Handler`, or the Android lifecycle;
- the persistence layer can be swapped (in-memory fake for unit tests, `DataStore<Preferences>` for production) behind a single interface;
- the Android code is reduced to event translation and effect execution, and any bug in the Android layer is small enough to be caught by a handful of instrumented tests.

## Decision

Adopt three independent seams, each as a Kotlin `interface` in the `data` package, with one production implementation and one in-memory fake:

| Seam | Interface | Production | Test fake |
| --- | --- | --- | --- |
| Time | `Clock` | `SystemClock` | `FakeClock` |
| Persistence | `BlocklistStore` | `DataStoreBlocklistStore` | `InMemoryBlocklistStore` |
| Decision (ticket 02) | `BlockerEngine` | direct impl | direct impl (pure) |
| Intervention flow (ticket 03) | `InterventionStateMachine` | direct impl | direct impl (pure) |
| Session state (ADR-0002) | `InterventionLifecycle` | direct impl | direct impl (pure) |

The Android side (ticket 04) consumes these interfaces and adds no decision logic of its own.

`BlocklistStore` exposes synchronous read/write from the caller's perspective: each read returns a `Flow` and each write is a `suspend fun` that resolves once the change is durable. This is the contract the decision and intervention code will use.

`Clock` is a `fun interface` with a single `now(): Long` method. The production class wraps `System.currentTimeMillis()`; the test fake exposes a mutable `nowMs` and an `advance(deltaMs)` helper.

## Consequences

- The decision logic in ticket 02 and the intervention logic in ticket 03 are unit-testable with JUnit + `runTest` only.
- The Android code in ticket 04 is small enough that a small set of instrumented Espresso tests can lock down wiring.
- We commit to the `BlocklistStore` interface now (with master toggle, blocklist, and whitelist expiries) so ticket 02 does not have to extend it under pressure.

## Alternatives considered

- **Putting all persistence behind a single `Settings` interface.** Rejected: it couples the blocklist, the master toggle, and the whitelist expiries into one bag, which is harder to reason about and harder to fake narrowly.
- **Using SharedPreferences directly.** Rejected: `DataStore<Preferences>` is the current AndroidX recommendation, exposes a `Flow` natively, and is straightforward to fake.
- **A single `BlockerFacade` that combines decision + intervention + persistence.** Rejected: the whole point of the seams is that they are independently testable; a facade would re-couple them.
