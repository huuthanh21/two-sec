# 02 — BlockerEngine + WhitelistGate (pure decision logic)

**What to build:** The `decide(packageName, now) → Decision` function and the WhitelistGate helper. Both are pure (no Android dependencies) and use the BlocklistStore + Clock from ticket 01. The system-package ignore set is injected at construction so the policy is testable. The engine is the highest testable seam in the system.

**Blocked by:** 01 — Config screen + BlocklistStore (foundation).

**Status:** ready-for-agent

- [ ] A `Decision` sealed type with at least `Intervene` and `Skip(reason)` variants, where `reason` covers at least: `MasterOff`, `NotInBlocklist`, `Whitelisted`, `IgnoredPackage`, `OwnPackage`.
- [ ] BlockerEngine's `decide(packageName, now)` returns `Intervene` only when all of: master toggle is on, the package is in the blocklist, the package is not currently whitelisted, the package is not in the injected ignore set, and the package is not the app's own package.
- [ ] WhitelistGate exposes at least `setWhitelist(packageName, untilMillis)`, `isWhitelisted(packageName, now) → Boolean`, and `clearExpired(now)`. It uses BlocklistStore for persistence and the injected Clock for time.
- [ ] A system-package ignore set is injected into BlockerEngine at construction time. The default set is empty in tests and is populated by a small platform helper (or a hardcoded list) at the call site, with the actual list left to the implementer's discretion.
- [ ] A table-driven unit test suite covers the full truth table for `decide` across the five input axes (masterOn, inBlocklist, whitelisted, inIgnoreSet, isOwnPackage). All cases pass without Robolectric or instrumented tests.
- [ ] WhitelistGate has unit tests covering: set then query returns true within window, query returns false after expiry, `clearExpired` removes only the expired entries, distinct packages have independent windows.
- [ ] BlockerEngine and WhitelistGate have no Android imports.
