#!/usr/bin/env bash
# Enable the two-sec accessibility service on the running emulator.
#
# This does two things, both of which are required:
#   1. Writes the ENABLED_ACCESSIBILITY_SERVICES setting (so the framework
#      knows the service exists). This part is what `settings put` does.
#   2. Drives the Settings UI to accept the BIND_ACCESSIBILITY_SERVICE
#      "Allow full control" dialog. BIND_ACCESSIBILITY_SERVICE is a
#      signature-protected permission that cannot be granted via `pm grant`;
#      the only way to obtain it is the user (or a script tapping as the
#      user) accepting the security warning in Settings.
#
# On Android 13+ Restricted Settings and Android 14+ Enhanced Confirmation
# Mode mean the "Allow full control" dialog is mandatory — there is no
# adb-only path. This script automates the dialog using uiautomator dump
# to find the right tap coordinates.
#
# AOSP API 34 assumed. On other Android versions or OEM skins the
# "Use service" / "Allow" button text may differ — the script tries the
# AOSP values first and falls back to common alternates.

set -euo pipefail

PKG="dev.twosec.app"
SVC="$PKG/.platform.BlockerAccessibilityService"
SVC_LABEL="two-sec"
DUMP_FILE="$(mktemp -t enable-a11y.XXXXXX.xml)"
DUMP_FILE_LINES="$DUMP_FILE.lines"

cleanup() { rm -f "$DUMP_FILE" "$DUMP_FILE_LINES"; }
trap cleanup EXIT

EMU_SERIAL=$(adb devices | grep -E '^emulator-[0-9]+[[:space:]]+device' | head -n1 | cut -f1 || true)
if [[ -z "$EMU_SERIAL" ]]; then
    echo "ERROR: no running emulator found. Run ./scripts/manage-emulator.sh first."
    exit 1
fi
ADB_CMD="adb -s $EMU_SERIAL"
echo "Using emulator: $EMU_SERIAL"

# Parse the uiautomator dump to a local file. Returns 0 on success.
dump_ui() {
    $ADB_CMD shell uiautomator dump >/dev/null 2>&1 || return 1
    $ADB_CMD pull /sdcard/window_dump.xml "$DUMP_FILE" >/dev/null 2>&1 || return 1
    # uiautomator dump is single-line. Split on <node boundaries so grep
    # returns one node per match instead of the whole hierarchy.
    sed 's|<node |\n<node |g' "$DUMP_FILE" > "$DUMP_FILE_LINES"
    return 0
}

# Find the first clickable node whose text OR content-desc matches $1.
# Prints "cx cy" (tap coordinates) to stdout, or "NOTFOUND".
find_clickable() {
    local needle="$1"
    dump_ui || { echo "NOTFOUND"; return 0; }
    # Match nodes that have the text (or content-desc) AND clickable="true".
    # Escape regex specials in $needle so "two-sec" doesn't break on "-".
    # Note: `/` is not in the class because the sed delimiter is `#`, so
    # `/` in the needle would otherwise be emitted as `\/` and trigger a
    # "stray \ before /" warning in grep.
    local escaped
    escaped=$(printf '%s' "$needle" | sed 's#[][\.*^$()+?{|]#\\&#g')
    local line
    line=$(grep -E "(text|content-desc)=\"$escaped\"" "$DUMP_FILE_LINES" \
        | grep 'clickable="true"' | head -n1 || true)
    if [[ -z "$line" ]]; then
        echo "NOTFOUND"
        return 0
    fi
    local bounds
    bounds=$(echo "$line" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
        | head -n1 | grep -oE '[0-9]+')
    if [[ -z "$bounds" ]]; then
        echo "NOTFOUND"
        return 0
    fi
    local x1 y1 x2 y2
    x1=$(echo "$bounds" | sed -n 1p)
    y1=$(echo "$bounds" | sed -n 2p)
    x2=$(echo "$bounds" | sed -n 3p)
    y2=$(echo "$bounds" | sed -n 4p)
    echo $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

# Like find_clickable but returns the match with the largest x-center.
# Use this for list-page row taps where the text label and the icon are
# both clickable and only the text label navigates to the detail page.
#
# Note: we do NOT filter on clickable="true". On AOSP API 34 the text
# "two-sec" lives in a non-clickable TextView inside a clickable row, so
# requiring clickable on the same node would miss it. The text node's
# center is always inside a clickable area (the row), so tapping the
# text center dispatches to the row's onClickListener.
# Prints "cx cy" (tap coordinates) to stdout, or "NOTFOUND".
find_clickable_rightmost() {
    local needle="$1"
    dump_ui || { echo "NOTFOUND"; return 0; }
    local escaped
    escaped=$(printf '%s' "$needle" | sed 's#[][\.*^$()+?{|]#\\&#g')
    local best_x=-1 best_coords="NOTFOUND"
    local line nums x1 y1 x2 y2 cx
    while IFS= read -r line; do
        [[ -z "$line" ]] && continue
        nums=$(echo "$line" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -n1 | grep -oE '[0-9]+' || true)
        [[ -z "$nums" ]] && continue
        x1=$(echo "$nums" | sed -n 1p)
        y1=$(echo "$nums" | sed -n 2p)
        x2=$(echo "$nums" | sed -n 3p)
        y2=$(echo "$nums" | sed -n 4p)
        cx=$(( (x1 + x2) / 2 ))
        if (( cx > best_x )); then
            best_x=$cx
            best_coords="$cx $(( (y1 + y2) / 2 ))"
        fi
    done < <(grep -E "(text|content-desc)=\"$escaped\"" "$DUMP_FILE_LINES" || true)
    echo "$best_coords"
}

tap_text() {
    local label="$1"
    local coords
    coords=$(find_clickable "$label")
    if [[ "$coords" == "NOTFOUND" ]]; then
        return 1
    fi
    echo "  -> tapping '$label' at $coords"
    $ADB_CMD shell input tap $coords >/dev/null
    return 0
}

tap_text_rightmost() {
    local label="$1"
    local coords
    coords=$(find_clickable_rightmost "$label")
    if [[ "$coords" == "NOTFOUND" ]]; then
        return 1
    fi
    echo "  -> tapping '$label' at $coords (rightmost)"
    $ADB_CMD shell input tap $coords >/dev/null
    return 0
}

# Same as tap_text but with a retry loop to absorb slow AVD boot and
# animation timing. Each attempt re-dumps the UI and re-finds. Does NOT
# retry the tap itself (a single tap is enough if the target is right).
tap_text_retry() {
    local label="$1"
    local attempt coords
    for attempt in 1 2 3; do
        coords=$(find_clickable "$label")
        if [[ "$coords" != "NOTFOUND" ]]; then
            echo "  -> tapping '$label' at $coords (attempt $attempt)"
            $ADB_CMD shell input tap $coords >/dev/null
            return 0
        fi
        echo "  (attempt $attempt: '$label' not found, retrying)"
        sleep 1
    done
    return 1
}

# Retry variant of tap_text_rightmost — same rationale as tap_text_retry.
tap_text_rightmost_retry() {
    local label="$1"
    local attempt coords
    for attempt in 1 2 3; do
        coords=$(find_clickable_rightmost "$label")
        if [[ "$coords" != "NOTFOUND" ]]; then
            echo "  -> tapping '$label' at $coords (attempt $attempt, rightmost)"
            $ADB_CMD shell input tap $coords >/dev/null
            return 0
        fi
        echo "  (attempt $attempt: '$label' not found, retrying)"
        sleep 1
    done
    return 1
}

# Find the first node whose resource-id matches $1. No clickable filter
# — AOSP exposes the accessibility switch as a non-clickable wrapper
# that contains a clickable Switch, so the wrapper's bounds are what
# we want to tap.
# Prints "cx cy" (tap coordinates) to stdout, or "NOTFOUND".
find_by_resource_id() {
    local rid="$1"
    dump_ui || { echo "NOTFOUND"; return 0; }
    local escaped
    escaped=$(printf '%s' "$rid" | sed 's#[][\.*^$()+?{|]#\\&#g')
    local line
    line=$(grep -E "resource-id=\"$escaped\"" "$DUMP_FILE_LINES" | head -n1 || true)
    if [[ -z "$line" ]]; then
        echo "NOTFOUND"
        return 0
    fi
    local bounds
    bounds=$(echo "$line" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
        | head -n1 | grep -oE '[0-9]+')
    if [[ -z "$bounds" ]]; then
        echo "NOTFOUND"
        return 0
    fi
    local x1 y1 x2 y2
    x1=$(echo "$bounds" | sed -n 1p)
    y1=$(echo "$bounds" | sed -n 2p)
    x2=$(echo "$bounds" | sed -n 3p)
    y2=$(echo "$bounds" | sed -n 4p)
    echo $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

tap_resource_id() {
    local rid="$1"
    local coords
    coords=$(find_by_resource_id "$rid")
    if [[ "$coords" == "NOTFOUND" ]]; then
        return 1
    fi
    echo "  -> tapping resource-id='$rid' at $coords"
    $ADB_CMD shell input tap $coords >/dev/null
    return 0
}

# Retry variant of tap_resource_id — same rationale as tap_text_retry.
tap_resource_id_retry() {
    local rid="$1"
    local attempt coords
    for attempt in 1 2 3; do
        coords=$(find_by_resource_id "$rid")
        if [[ "$coords" != "NOTFOUND" ]]; then
            echo "  -> tapping resource-id='$rid' at $coords (attempt $attempt)"
            $ADB_CMD shell input tap $coords >/dev/null
            return 0
        fi
        echo "  (attempt $attempt: resource-id='$rid' not found, retrying)"
        sleep 1
    done
    return 1
}

# ---------------------------------------------------------------------
# Step 1: write the setting. Always works; tells the framework the
# service is enabled. The bind still needs the dialog (step 3+).
# ---------------------------------------------------------------------
echo "Writing enabled_accessibility_services setting..."
$ADB_CMD shell settings put secure enabled_accessibility_services "$SVC"
$ADB_CMD shell settings put secure accessibility_enabled 1

# Short-circuit if the service is already bound. dumpsys accessibility
# shows the service label in the Bound services line on API 34.
if $ADB_CMD shell dumpsys accessibility 2>/dev/null | grep -qE "Bound services.*$SVC_LABEL"; then
    echo "Service is already enabled and bound. Done."
    $ADB_CMD shell dumpsys accessibility | grep -E "Bound services|Enabled services" | head -4
    exit 0
fi

# ---------------------------------------------------------------------
# Step 2: open Accessibility settings. Force-stop both the app AND the
# Settings app — a previous test run can leave Settings foreground, in
# which case `am start` is a no-op and a new window-state event never
# fires.
# ---------------------------------------------------------------------
echo "Opening Accessibility settings..."
$ADB_CMD shell am force-stop com.android.settings >/dev/null
$ADB_CMD shell am force-stop "$PKG" >/dev/null
$ADB_CMD shell am start -a android.settings.ACCESSIBILITY_SETTINGS >/dev/null
sleep 2

# ---------------------------------------------------------------------
# Step 3: tap the service row. This opens the service detail page.
#
# Some AOSP builds put Downloaded services below the fold — scroll the
# list up first, then dump+find. After tapping the row, no further
# scroll is needed.
#
# The row's icon container (left edge) and text label (right side) are
# BOTH clickable. Tapping the icon toggles the inline switch instead of
# navigating to the detail page — so prefer the rightmost clickable
# match (the text label).
# ---------------------------------------------------------------------
echo "Finding service row '$SVC_LABEL'..."
# On AOSP API 34 the row is visible on initial load. If not found, scroll
# up to reveal Downloaded services (some AOSP builds put it below the
# fold) and retry. A pre-scroll would move the row off-screen on builds
# where it's already visible.
if ! tap_text_rightmost_retry "$SVC_LABEL"; then
    echo "  ('$SVC_LABEL' not visible, scrolling to reveal Downloaded services)"
    $ADB_CMD shell input swipe 540 1500 540 500 300 >/dev/null
    sleep 1
    if ! tap_text_rightmost_retry "$SVC_LABEL"; then
        echo "ERROR: could not find '$SVC_LABEL' in Accessibility settings."
        echo "Make sure the app is installed (adb install), then re-run this script."
        exit 1
    fi
fi
sleep 1

# ---------------------------------------------------------------------
# Step 4: tap the "Use $SVC_LABEL" toggle. AOSP exposes the switch as
# `resource-id="android:id/switch_widget"`, which only exists on the
# service detail page. The label is "Use $SVC_LABEL" (e.g. "Use two-sec"),
# not the generic "Use service". If both miss, fail with a pointer to
# dump-layout.sh — no class-based guessing (the list-page Switch and
# the detail-page Switch have the same class, so a class fallback would
# toggle the wrong one).
# ---------------------------------------------------------------------
echo "Toggling 'Use $SVC_LABEL'..."
if tap_resource_id_retry "android:id/switch_widget"; then
    echo "  (matched via resource-id)"
elif tap_text_retry "Use $SVC_LABEL"; then
    echo "  (matched via text)"
else
    echo "ERROR: could not find the 'Use $SVC_LABEL' switch."
    echo "Expected either resource-id='android:id/switch_widget' on the"
    echo "service detail page, or text='Use $SVC_LABEL'."
    echo "Run ./scripts/dump-layout.sh to inspect the current UI."
    exit 1
fi
sleep 1

# ---------------------------------------------------------------------
# Step 5: tap "Allow" on the "Allow full control of your device?" dialog.
# AOSP shows a full-screen confirmation with an "Allow" button at the
# bottom. Some OEMs use "OK" — try that as a fallback.
# ---------------------------------------------------------------------
echo "Accepting 'Allow full control' dialog..."
if ! tap_text_retry "Allow"; then
    if ! tap_text_retry "OK"; then
        echo "ERROR: could not find 'Allow' (or 'OK') on the confirmation dialog."
        echo "Run ./scripts/dump-layout.sh to inspect the current UI."
        exit 1
    fi
fi
sleep 2

# ---------------------------------------------------------------------
# Step 6: verify.
#
# dumpsys accessibility formats the service differently in the two lists:
#   Enabled services:{{<pkg>/<pkg>.<class>}}   (full path, no leading dot)
#   Bound services:{Service[label=<label>, ...]}   (label only, no path)
# On API 34 there is no component name in the Bound services line, so
# we match the label there and the package name in the Enabled line.
# ---------------------------------------------------------------------
DUMPSYS=$($ADB_CMD shell dumpsys accessibility 2>/dev/null || true)
if echo "$DUMPSYS" | grep -qE "Bound services.*$SVC_LABEL" \
   && echo "$DUMPSYS" | grep -qE "Enabled services.*$PKG"; then
    echo "OK. Accessibility service enabled and bound."
    echo "$DUMPSYS" | grep -E "Bound services|Enabled services" | head -4
    exit 0
else
    echo "FAILED. Service is not both enabled and bound. Final state:"
    echo "$DUMPSYS"
    exit 1
fi
