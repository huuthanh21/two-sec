#!/usr/bin/env bash
set -euo pipefail

AVD_NAME="compose_debug"
SDK_DIR="/opt/android-sdk"
EMULATOR_BIN="$SDK_DIR/emulator/emulator"
AVD_MANAGER="$SDK_DIR/cmdline-tools/latest/bin/avdmanager"
SDK_MANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

# 1. Ensure emulator and target image are installed
if [[ ! -f "$EMULATOR_BIN" ]]; then
    echo "Emulator binary not found, installing..."
    yes | "$SDK_MANAGER" "emulator" "system-images;android-34;google_apis;x86_64"
fi

# 2. Check if AVD exists
if ! "$AVD_MANAGER" list avd | grep -q "$AVD_NAME"; then
    echo "Creating AVD $AVD_NAME..."
    echo "no" | "$AVD_MANAGER" create avd -n "$AVD_NAME" -k "system-images;android-34;google_apis;x86_64" --force
fi

# Override the default AVD screen size (QEMU gives 320x640 @ 160dpi,
# which is unusable for coordinate-based taps). Pixel 2 baseline.
CONFIG_INI="$HOME/.android/avd/$AVD_NAME.avd/config.ini"
if [[ -f "$CONFIG_INI" ]]; then
    sed -i.bak \
        -e 's/^hw.lcd.width=.*/hw.lcd.width=1080/' \
        -e 's/^hw.lcd.height=.*/hw.lcd.height=1920/' \
        -e 's/^hw.lcd.density=.*/hw.lcd.density=440/' \
        "$CONFIG_INI"
    echo "Resized AVD display to 1080x1920 @ 440dpi (Pixel 2 baseline)."
fi

# 3. Check if emulator is already running
RUNNING_EMULATOR=$(adb devices | grep -E '^emulator-[0-9]+[[:space:]]+device' | head -n1 | cut -f1 || true)

if [[ -n "$RUNNING_EMULATOR" ]]; then
    echo "Found active emulator: $RUNNING_EMULATOR. Binding to it."
    exit 0
fi

# 4. Boot AVD
echo "Booting AVD $AVD_NAME in background..."
"$EMULATOR_BIN" -avd "$AVD_NAME" -no-window -no-audio -gpu swiftshader_indirect > /dev/null 2>&1 &

# Wait for adb to find the emulator device
echo "Waiting for emulator device connection..."
EMU_SERIAL=""
for i in {1..30}; do
    EMU_SERIAL=$(adb devices | grep -E '^emulator-[0-9]+' | head -n1 | cut -f1 || true)
    if [[ -n "$EMU_SERIAL" ]]; then
        break
    fi
    sleep 2
done

if [[ -z "$EMU_SERIAL" ]]; then
    echo "Emulator failed to register in ADB."
    exit 1
fi

echo "Found emulator: $EMU_SERIAL. Waiting for device status..."
adb -s "$EMU_SERIAL" wait-for-device

# Wait for boot completion
echo "Waiting for system boot completion..."
until [ "$(adb -s "$EMU_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 2
done

echo "Emulator $EMU_SERIAL booted successfully and ready!"
