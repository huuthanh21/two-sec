#!/usr/bin/env bash
set -euo pipefail
EMU_SERIAL=$(adb devices | grep -E '^emulator-[0-9]+' | head -n1 | cut -f1 || true)
ADB_CMD="adb"
if [[ -n "$EMU_SERIAL" ]]; then
    ADB_CMD="adb -s $EMU_SERIAL"
fi

echo "Dumping active UI tree from device: ${EMU_SERIAL:-default}..."
DUMP_OUTPUT=$($ADB_CMD shell uiautomator dump 2>&1)
echo "$DUMP_OUTPUT"

DUMP_PATH="/sdcard/window_dump.xml"
if [[ "$DUMP_OUTPUT" =~ dumped\ to:[[:space:]]*(.*) ]]; then
    DUMP_PATH=$(echo "${BASH_REMATCH[1]}" | tr -d '\r' | xargs)
fi

echo "Pulling layout XML from $DUMP_PATH to compose_layout.xml..."
$ADB_CMD pull "$DUMP_PATH" compose_layout.xml >/dev/null
echo "UI layout tree dumped successfully to compose_layout.xml!"
