#!/usr/bin/env bash
# Clear old log
echo "=== Starting Compose Debug Log Pipeline ===" > compose_debug.log

EMU_SERIAL=$(adb devices | grep -E '^emulator-[0-9]+' | head -n1 | cut -f1 || true)
ADB_CMD="adb"
if [[ -n "$EMU_SERIAL" ]]; then
    ADB_CMD="adb -s $EMU_SERIAL"
fi

echo "Logging from device: ${EMU_SERIAL:-default}" >> compose_debug.log

# Read logcat in background. We filter for:
# - All error/fatal level logs (*:E, *:F)
# - App-specific logs if tags match (dev.twosec.app or two-sec or BlockerAccessibilityService etc)
# - Suppress everything else (*:S)
exec $ADB_CMD logcat -v time \
    "AndroidRuntime:E" \
    "FATAL:E" \
    "dev.twosec.app:D" \
    "BlockerAccessibilityService:D" \
    "BlockerEngine:D" \
    "InterventionActivity:D" \
    "*:E" \
    "*:S" >> compose_debug.log 2>&1
