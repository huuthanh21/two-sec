#!/usr/bin/env python3
"""Tap a UI node by its visible text, resource-id, or raw coordinates.

Dumps the current UI hierarchy (or reads a pre-dumped XML), finds a node
matching the selector, computes the center of its bounds, and taps it via
`adb shell input tap`. Or pass --coords to skip the XML lookup entirely.
Intended for AI agents and humans iterating quickly on the emulator.

Exit codes:
  0  Tapped.
  1  No matching node (clickable-node list is printed to stderr).
  2  No device / $ANDROID_SERIAL does not exist (or --coords was malformed).
  3  uiautomator dump or adb pull failed.
"""
import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def resolve_device(arg_serial):
    if arg_serial:
        return arg_serial
    env_serial = os.environ.get("ANDROID_SERIAL")
    if env_serial:
        return env_serial
    proc = subprocess.run(
        ["adb", "devices"], capture_output=True, text=True, check=True
    )
    devices = []
    for line in proc.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) == 2 and parts[1] == "device":
            devices.append(parts[0])
    if not devices:
        return None
    if len(devices) == 1:
        return devices[0]
    emulators = [d for d in devices if d.startswith("emulator-")]
    if emulators:
        return emulators[0]
    return None


def adb_cmd(serial):
    return ["adb", "-s", serial] if serial else ["adb"]


def dump_xml(serial, dest):
    base = adb_cmd(serial)
    proc = subprocess.run(
        base + ["shell", "uiautomator", "dump"],
        capture_output=True, text=True
    )
    if proc.returncode != 0:
        return False, f"uiautomator dump failed: {proc.stderr.strip()}"
    m = re.search(r"dumped to:\s*(\S+)", proc.stdout)
    dump_path = m.group(1).strip() if m else "/sdcard/window_dump.xml"
    pull = subprocess.run(
        base + ["pull", dump_path, dest], capture_output=True, text=True
    )
    if pull.returncode != 0:
        return False, f"adb pull failed: {pull.stderr.strip()}"
    return True, None


BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def parse_bounds(bounds_str):
    m = BOUNDS_RE.match(bounds_str or "")
    if not m:
        return None
    x1, y1, x2, y2 = (int(g) for g in m.groups())
    if x2 <= x1 or y2 <= y1:
        return None
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_matches(root, needle, attr):
    needle = needle.lower()
    candidates = []
    for node in root.iter("node"):
        value = (node.get(attr) or "").lower()
        if needle in value:
            candidates.append((node, value))
    if not candidates:
        return []
    exact = [c for c in candidates if c[1] == needle]
    return exact if exact else candidates


def identifier(node):
    text = (node.get("text") or "").strip()
    if text:
        return text, "text"
    desc = (node.get("content-desc") or "").strip()
    if desc:
        return desc, "desc"
    rid = (node.get("resource-id") or "").strip()
    if rid:
        return rid, "id"
    return "", None


def format_node(node):
    cls = (node.get("class") or "?").split(".")[-1]
    bounds = node.get("bounds") or "?"
    label, src = identifier(node)
    if src == "text":
        return f"  [{cls}] {label!r:<30} bounds={bounds}"
    return f"  [{cls}] {src}={label!r:<30} bounds={bounds}"


def list_clickable_nodes(root):
    results = []

    def walk(node, in_clickable):
        is_clickable = node.get("clickable") == "true"
        here_clickable = in_clickable or is_clickable
        if here_clickable and identifier(node)[0]:
            results.append(format_node(node))
        for child in node:
            walk(child, here_clickable)

    walk(root, False)
    return results


def main():
    parser = argparse.ArgumentParser(
        description="Tap a UI node by text, resource-id, or raw coordinates."
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--text", help="Match against node text=")
    group.add_argument("--id", dest="resource_id", help="Match against node resource-id=")
    group.add_argument("--coords", help="Tap raw X,Y (escape hatch; skips XML lookup)")
    parser.add_argument("--xml", help="Path to a pre-dumped XML file")
    parser.add_argument("--serial", help="Target device serial (else $ANDROID_SERIAL, then auto-pick)")
    parser.add_argument("--wait", type=float, default=0.5,
                        help="Seconds to sleep after the tap (default: 0.5)")
    args = parser.parse_args()

    serial = resolve_device(args.serial)
    if not serial:
        sys.stderr.write(
            "No device available. Pass --serial, set $ANDROID_SERIAL, "
            "or connect a device.\n"
        )
        return 2

    if args.coords is not None:
        try:
            x_str, y_str = args.coords.split(",")
            cx, cy = int(x_str), int(y_str)
        except (ValueError, AttributeError):
            sys.stderr.write(
                f"--coords must be 'X,Y' with integers, got {args.coords!r}\n"
            )
            return 2
        base = adb_cmd(serial)
        tap = subprocess.run(
            base + ["shell", "input", "tap", str(cx), str(cy)],
            capture_output=True, text=True
        )
        if tap.returncode != 0:
            sys.stderr.write(f"adb input tap failed: {tap.stderr.strip()}\n")
            return 3
        time.sleep(args.wait)
        print(f"Tapped coords ({cx}, {cy})")
        return 0

    xml_path = args.xml
    if not xml_path:
        xml_path = "compose_layout.xml"
        ok, err = dump_xml(serial, xml_path)
        if not ok:
            sys.stderr.write(err + "\n")
            return 3

    try:
        tree = ET.parse(xml_path)
    except ET.ParseError as e:
        sys.stderr.write(f"Failed to parse {xml_path}: {e}\n")
        return 3
    root = tree.getroot()

    attr = "text" if args.text is not None else "resource-id"
    needle = args.text if args.text is not None else args.resource_id
    matches = find_matches(root, needle, attr)
    if not matches:
        sys.stderr.write(
            f"No node matched {attr}={needle!r} (case-insensitive).\n"
        )
        clickable = list_clickable_nodes(root)
        if clickable:
            sys.stderr.write("Clickable nodes on screen:\n")
            for line in clickable:
                sys.stderr.write(line + "\n")
        else:
            sys.stderr.write("No clickable nodes on screen.\n")
        return 1

    if len(matches) > 1:
        labels = [m[0].get(attr) for m in matches]
        sys.stderr.write(
            f"Matched {len(matches)} nodes for {attr}={needle!r}, tapping first. "
            f"Other matches: {labels[1:]}\n"
        )

    node, _ = matches[0]
    bounds = node.get("bounds")
    center = parse_bounds(bounds)
    if center is None:
        sys.stderr.write(f"Matched node has invalid bounds: {bounds!r}\n")
        return 3
    cx, cy = center
    text = node.get("text") or ""
    rid = node.get("resource-id") or ""

    base = adb_cmd(serial)
    tap = subprocess.run(
        base + ["shell", "input", "tap", str(cx), str(cy)],
        capture_output=True, text=True
    )
    if tap.returncode != 0:
        sys.stderr.write(f"adb input tap failed: {tap.stderr.strip()}\n")
        return 3

    time.sleep(args.wait)

    label = f"text={text!r}" if text else f"resource-id={rid!r}"
    print(f"Tapped {label} at ({cx}, {cy}) bounds={bounds}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
