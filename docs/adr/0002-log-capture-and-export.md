# ADR 0002: Log capture and share-sheet export

Status: Accepted (2026-07-15)

## Context

two-sec is in active development and many of the harder bugs only repro on a physical phone, not in the emulator. The current logging setup (`Timber.plant(Timber.DebugTree())` in `BuildConfig.DEBUG` only, no persistent storage) is unusable in this workflow — logcat is volatile, release builds log nothing at all, and the user has no way to ship logs off the device without a computer. We need a way for the user to capture durable logs and get them off the device on demand.

## Decision

Add a persistent log file the user can extract via the Android share sheet:

- **Capture** — a custom `LogFileTree` (`Timber.Tree`) writes every log line to `filesDir/logs/two-sec.log`. A `Executors.newSingleThreadExecutor()` performs the disk I/O so calls from the accessibility service (which runs on the main thread) do not jank the UI. The tree is planted unconditionally in both debug and release. Timestamps come from an injected `Clock` (matches the seam in ADR 0001).
- **Rotation** — when the current file's UTF-8 byte length plus the incoming line's UTF-8 byte length would exceed 1 MB, the current file is renamed to `two-sec.log.1` (overwriting the prior backup) and a fresh file is started. Effective retention: ~2 MB total, always the most recent activity.
- **Session header** — on every `TwoSecApp.onCreate`, a single multi-line `Timber.i(...)` call writes the session delimiter, app version, device model + Android version, and the sorted blocklist. The blocklist is read synchronously (`snapshot()` uses `runBlocking` on DataStore) before the call, so the entire header is captured in one captured-args object and lands in the file as a single executor task with no interleaving from other loggers. The first line carries the timestamp; the rest are continuations. **Cost:** the snapshot blocks the main thread for the duration of one DataStore read on cold start. This is the same pattern `BlockerEngine.decide()` already uses (via the accessibility service), so the trade-off is not new — it is recorded here so the next reader knows the onCreate block is intentional, not a regression.
- **Logging coverage** — every `BlockerEngine.decide()` call, every `InterventionStateMachine` event and transition, every `TYPE_WINDOW_STATE_CHANGED` in the accessibility service, every `BootReceiver` action, and `TwoSecApp.onCreate`. The engine and state machine retain their pure seams: the engine splits into a public `decide` (3 lines: capture result, log, return) wrapping a private `compute()`; the state machine splits `process` into a capture-then-dispatch-then-log wrapper around the existing private `onTick` / `onTapped*` methods. The private functions remain unit-testable in isolation.
- **Export** — an outlined "Extract logs" button in the config screen's static setup section triggers `LogSharer.buildShareIntent(uris)`, which delegates to a `ShareIntentFactory` (production: `AndroidShareIntentFactory`). The activity then starts the system chooser with `Intent.createChooser(intent, null)`. No zipping, no concatenation, no chooser instrumentation.
- **Test seam** — `LogSharer` is unit-testable in pure JVM via a `CapturingFactory` that records the (action, mime, uris, flags) tuple. The production `AndroidShareIntentFactory` is the only place that knows about `Intent` extras and is the trust boundary with the Android framework.
- **Tests** — unit-test rotation with a temp dir and `FakeClock`; unit-test `LogSharer` via `CapturingFactory`. Skip the system chooser entirely.

## Consequences

- The log file persists across app restarts but is wiped on uninstall (default for `filesDir`). Confirmed acceptable.
- The log directory is excluded from cloud backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`) so the blocklist cannot leak through Google Drive or device migration — extends the privacy stance recorded in `CONTEXT.md`.
- The blocklist (a list of package names the user has flagged) ends up in the log. The user already sees this list in the app, so there is no new exposure — but the privacy trade-off is recorded in `CONTEXT.md` so future contributors do not add more sensitive data without thinking.
- Two `Timber` trees are planted in debug (`DebugTree` + `LogFileTree`); one in release (`LogFileTree` only). Release builds are more verbose than before; if a future ticket wants release logs gated behind a toggle, it must change the plant site.
- The single-thread executor lives for the process lifetime. It is not explicitly shut down; on process death any in-flight write is lost. Acceptable for a debug log. Disk-write failures are surfaced to logcat via `android.util.Log.e` (the file is the only debug channel, so a silent failure would defeat its purpose). `android.util.Log` is used here rather than `Timber` because the write happens inside a `Timber.Tree`; routing through Timber would re-enter the tree and either recurse forever or re-trigger the failure path.
- The user-visible file names in the share are `two-sec.log` and `two-sec.log.1`. Clear enough prefix; renaming to "current/previous" via `file_paths.xml` mapping was considered and rejected as low-value.
- The system chooser is opened with a null title; the OS supplies a default. Adding a localized title was considered and rejected as out of scope.
- The pure decision and state-machine seams have a logging wrapper in their public method (see the "Logging coverage" bullet under Decision for the exact split). The wrappers are 3 lines for the engine and ~12 for the state machine; the private functions remain unit-testable in isolation. Future work could move logging into a decorator; today the cost of the seam softening is judged smaller than the cost of an extra layer.

## Alternatives considered

- **In-memory ring buffer behind a diagnostic toggle** — cheaper to build but loses everything on app kill and requires the user to remember to turn it on before the bug, which is exactly the failure mode that motivated this ticket.
- **Dump logcat via adb** — requires a computer, does not survive app restart, and is dominated by other apps' logcat noise.
- **Save the log to `/Download/`** — works without a chooser but requires `MediaStore` on Android 10+ and is fiddlier than `FileProvider` + `ACTION_SEND_MULTIPLE`.
- **ZIP both log files** — keeps the two-generation history in a single attached file but adds a zip dependency for marginal value when `SEND_MULTIPLE` already handles multiple files natively.
- **In-app log viewer** — adds a screen and a parser for marginal benefit; the user can open the shared file in any text editor.
