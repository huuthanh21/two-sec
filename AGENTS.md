# AGENTS.md

## Project

two-sec — a free clone of one-sec. It's used to put a brief pause between you and apps you tend to open on autopilot, so you have a moment to decide whether you really want to scroll. When you open a chosen app, two-sec shows a breathing screen for a few seconds, then lets you continue or close (back to your home screen).

Framework: Kotlin + Jetpack Compose Android app.

## Agent skills

### Issue tracker

GitHub Issues — use the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` at the repo root, plus `docs/adr/`. See `docs/agents/domain.md`.

### Running tests

- **Unit tests** (JVM, no device): `./gradlew :app:testDebugUnitTest`
- **Instrumented tests** (device or emulator): `./gradlew :app:connectedDebugAndroidTest`
- **Lint**: `./gradlew :app:lintDebug`

Before running instrumented tests, ensure a device is connected (e.g. running the local emulator). See `docs/agents/testing.md` for the full workflow and for notes on the DataStore close/reopen pattern in tests.

### Emulator debugging
See [AGENTS.emulator-debug.md](file:///home/huuthanh21/repos/two-sec/AGENTS.emulator-debug.md) for using local high-efficiency KVM emulators, log streams, and layout dumps.

## Git identity

Project-scoped (set in `.git/config`, not global):

- name: `huuthanh21`
- email: `ththanh.work@gmail.com`

If commits show different credentials, fix with `git config --local user.name "huuthanh21"` and `git config --local user.email "ththanh.work@gmail.com"`.
