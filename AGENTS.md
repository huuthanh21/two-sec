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

## Compose semantics convention

Every interactive Compose element in `app/src/main/kotlin/dev/twosec/app/ui/` carries both a `testTag` and a `contentDescription` (or an explicit justification for omitting one). The point is twofold: blind users must be able to use the app via TalkBack, and the E2E tooling in `scripts/click.py` (and any future `composeTestRule` test) must be able to target elements by id.

**Rule of thumb.** Anything with an `onClick`, `onCheckedChange`, `onValueChange`, `onSelect`, or `onToggle` parameter gets a `testTag`. If the element has no visible text and isn't wrapped in a labelled `Row`, it also gets a `contentDescription` — formatted via `stringResource(R.string.*, args)` so it localizes.

**testTag convention.**
- snake_case, dev-facing, never localized.
- Per-list elements parameterize by their stable id: `widget_type:{row_id}`. Example: `app_checkbox:com.android.chrome`.
- Per-element examples: `master_toggle`, `search_clear`, `intervention_continue`, `intervention_close`.

**contentDescription convention.**
- Sentence case, user-facing, localized via `stringResource`.
- When the element sits in a `Row` next to a `Text` that already names it, prefer `Modifier.semantics(mergeDescendants = true)` on the parent `Row` instead of duplicating the label as `contentDescription` on the element. The two are not equivalent — the Row-merge approach keeps visible-label and a11y-label in sync.
- Decorative `Icon`s (e.g., the leading `Icon` inside `SearchBar`'s input field) keep `contentDescription = null`. The enclosing `IconButton`, if any, still gets a `testTag`.
- Per-list examples: `"Block Chrome"` (the per-app `Checkbox`), `"Search apps"`, `"Clear search"`.

**Adding a new widget.** Add the testTag/contentDescription when you add the widget. The `ComposeSemanticsConventionTest` JVM test fails the build if a `Switch(`, `Checkbox(`, `IconButton(`, or `Button(` call ships without a `testTag` within ten lines. If you add a new interactive widget to `ui/`, add it to the test's regex at the same time.

## Git identity

Project-scoped (set in `.git/config`, not global):

- name: `huuthanh21`
- email: `ththanh.work@gmail.com`

If commits show different credentials, fix with `git config --local user.name "huuthanh21"` and `git config --local user.email "ththanh.work@gmail.com"`.
