# Testing

## Unit tests (JVM)

```bash
./gradlew :app:testDebugUnitTest
```

15 unit tests cover `InMemoryBlocklistStore` across three files (master, blocklist, whitelist). All use `runTest` + `Turbine` for flow assertions — no Android framework dependencies.

## Instrumented tests (device or emulator)

```bash
# Phone must be connected and authorised first (see below)
./gradlew :app:connectedDebugAndroidTest
```

3 tests cover `DataStoreBlocklistStore` round-trips through `DataStore<Preferences>`.

### Wireless ADB (no USB)

1. On phone: **Developer options** → **Wireless debugging** → enable → tap the pairing code.
2. Pair (one-time):

    ```bash
    export JAVA_HOME=/home/trht/.local/jdk17
    export ANDROID_HOME=/home/trht/.local/android-sdk
    export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

    adb pair <IP:pairing-port>
    # Enter the 6-digit pairing code shown on the phone
    ```

3. Connect:

    ```bash
    adb connect <IP:port>
    # Use the main port shown on the phone (different from the pairing port)
    adb devices -l   # confirm the device is listed
    ```

4. **Keep the phone screen unlocked** during install — Android prompts for install permission, and `adb install` fails silently if the phone is locked or the prompt is ignored.

### `close()` and DataStore scope

`DataStore` throws if a second instance opens the same file while the first is still alive. Instrumented tests use this pattern:

```kotlin
// Write via store A, close, reopen as store B, read.
DataStoreBlocklistStore(context, storeName = "test-name").run {
    runBlocking { setMasterEnabled(true) }
    close()                               // releases the file handle
}
DataStoreBlocklistStore(context, storeName = "test-name").run {
    assertEquals(true, runBlocking { masterEnabled().first() })
    close()
}
```

`close()` cancels the internal `CoroutineScope` and awaits child completion so the file is released synchronously. Always use `runBlocking` (not `runTest`) in instrumented tests that open and close DataStore — `runTest` does not guarantee scope cleanup before the next statement.

## Lint

```bash
./gradlew :app:lintDebug
```

The HTML report is at `app/build/reports/lint-results-debug.html`. The toolchain in CI should ignore "newer version available" warnings (they are informational, not errors).
