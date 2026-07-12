# 01 — Config screen + BlocklistStore (foundation)

**What to build:** A runnable Android app whose single screen shows the master toggle, the installed-app checklist, and the static requirements section. The blocklist and master toggle persist across app restarts. The BlocklistStore seam (interface, in-memory fake, DataStore-backed implementation) and the Clock interface are introduced as part of this ticket so that later tickets can build on them.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Android project builds and installs on a device or emulator; the launch activity is the single config screen.
- [ ] The config screen renders a master toggle, a scrollable list of installed user apps with checkboxes, and a static text section listing the manual setup steps (grant Accessibility permission in system settings; disable battery optimization in system settings).
- [ ] The master toggle's on/off state persists across app restarts.
- [ ] The selected-blocklist state persists across app restarts.
- [ ] BlocklistStore is exposed as an interface; the in-memory fake is the test double, the DataStore-backed implementation is the production implementation.
- [ ] A Clock interface is defined and used in place of `System.currentTimeMillis()` in any code that touches time, even if no current code path needs it yet — the seam is set up so 02 and 03 can use it.
- [ ] BlocklistStore has unit tests against the in-memory fake covering: default empty blocklist, default master-off, set master, add/remove from blocklist, read-after-write.
- [ ] BlocklistStore has at least one instrumented test that confirms a value round-trips through DataStore across an app restart.
- [ ] No permissions are requested programmatically at any point in the app (no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, no Accessibility permission request, no notification permission request). The static requirements section is the only mention of permissions.
- [ ] Toggling the master switch and checking/unchecking apps are the only interactive elements on the screen; no shortcut buttons, no onboarding dialogs, no deep-links into system settings.
