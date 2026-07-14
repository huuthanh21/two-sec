# Emulator Debugging Guide

For local, high-efficiency development loops using KVM acceleration.

## Setup & Boot
Start/bind the emulator and wait for full boot:
```bash
./scripts/manage-emulator.sh
```

## Logs
Continuous filtered runtime logging to `compose_debug.log`:
```bash
./scripts/log-pipeline.sh
```
Keep this running in the background.

## UI Inspection
Dump the active Compose UI hierarchy tree to `compose_layout.xml`:
```bash
./scripts/dump-layout.sh
```

## Running Tests
To execute instrumented tests on the emulator:
```bash
./gradlew :app:connectedDebugAndroidTest
```
