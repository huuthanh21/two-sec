# 04 — Service + InterventionActivity + BootReceiver (end-to-end)

**What to build:** The Android wiring that turns the pure modules from 02 and 03 into a working blocker. BlockerAccessibilityService subscribes to `TYPE_WINDOW_STATE_CHANGED` and forwards foregrounded package names to BlockerEngine; when the engine returns `Intervene`, the service launches InterventionActivity. The Activity drives the InterventionStateMachine, renders its view state, runs the 5-second timer, and executes its effects (whitelist a package, finish the activity, go home). BootReceiver renews the service subscription after `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED`.

**Blocked by:** 02 — BlockerEngine + WhitelistGate (pure decision logic); 03 — InterventionStateMachine (pure intervention flow).

**Status:** ready-for-agent

- [ ] BlockerAccessibilityService is registered in the manifest with the right `meta-data` resource and listens exclusively for `TYPE_WINDOW_STATE_CHANGED`.
- [ ] On every `TYPE_WINDOW_STATE_CHANGED` event, the service calls BlockerEngine and, when the engine returns `Intervene`, launches InterventionActivity as a full-screen, opaque Activity over the target app.
- [ ] The service has no background threads, no `Handler` running between events, no `UsageStatsManager` polling, and no `ActivityManager` polling.
- [ ] InterventionActivity renders the `Counting` state by displaying the fixed prompt and a countdown value, with the Continue/Close buttons hidden.
- [ ] InterventionActivity drives the InterventionStateMachine by feeding it `Tick` events at ~100 ms intervals using the Clock from ticket 01.
- [ ] When the state machine transitions to `AwaitingChoice`, the Activity reveals the Continue and Close buttons.
- [ ] The system Back button is suppressed while the state machine is in `Counting`; after the transition to `AwaitingChoice`, Back is treated as Close.
- [ ] Tapping Continue executes the `WhitelistPackage` effect (writes to WhitelistGate), executes `FinishActivity`, and leaves no memory footprint.
- [ ] Tapping Close (or Back after the timer) executes `FinishActivity` and dispatches `Intent(ACTION_MAIN).addCategory(CATEGORY_HOME)` to send the user to the launcher.
- [ ] After a Continue, opening the same target app again within 30 seconds does not re-trigger the intervention; opening a different target app still does.
- [ ] BootReceiver is registered in the manifest for `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` and, on receipt, ensures the BlockerAccessibilityService's subscription is renewed.
- [ ] An instrumented test launches a real target app on a device or emulator and asserts the intervention appears.
- [ ] An instrumented test sends a synthetic `BOOT_COMPLETED` broadcast and asserts the service subscription is renewed.
- [ ] No foreground service, no notifications, no analytics SDK, no network permission is added.
