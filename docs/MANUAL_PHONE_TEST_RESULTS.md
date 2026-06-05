# Manual Phone Test Results

## Test Details

- Date/time: 2026-06-05 10:24:54 +05:30
- Phone model: OnePlus 8T
- Device model: KB2001
- Android version: 14
- App version: `0.1.0-debug`
- Build version: `versionCode=1`
- Git commit tested: current working tree on top of `940c19a` with local provider cleanup edits
- Connected device: `7675208c`

## Permission Status

- Microphone permission: Granted
- Notification permission: Granted
- Accessibility service: Enabled for `com.nova.luna.debug/com.nova.luna.service.NovaAccessibilityService`
- Usage access: Missing (`adb shell appops get com.nova.luna.debug GET_USAGE_STATS` returned `No operations.`)
- Location permission: Missing

## Installed Provider Apps

- Cab apps detected: Ola, Rapido
- Cab apps missing: Uber, inDrive
- Food apps detected: Swiggy, Zomato
- Food registry blocker: Toings remained unavailable to the food registry
- Grocery apps detected: Blinkit, JioMart, Instamart

## Smoke Commands Used

- `com.nova.luna.debug.ACTION_RUN_CAB_SMOKE` via explicit receiver component
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=food`
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=grocery`
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=negative`

The basic section was verified in the earlier sectioned run and remains PASS; the latest rerun focused on provider cleanup and negative safety.

## Basic Commands

| Command | Result | Notes |
|---|---|---|
| `Luna stop listening` | PASS | Returned `Stopping listening.` and set `shouldStopListening=true`. |
| `Luna go home` | PASS | Went home successfully once the accessibility service was bound again. |
| `Luna open WhatsApp` | PASS | Opened WhatsApp. |
| `Luna open settings` | PASS | Opened system settings. |
| `Luna go back` | PASS | Went back successfully once the accessibility service was bound again. |
| `Luna show recent apps` | PASS | Opened recent apps successfully once the accessibility service was bound again. |

## Cab Results

| Command / Scenario | Result | Notes |
|---|---|---|
| `Luna book a cab from current location to DB Mall` | BLOCKED_BY_LOCATION_PERMISSION | The flow now stops cleanly at missing location access instead of faking a pickup or advancing to ride-type. |
| Cab provider flow | BLOCKED_BY_LOCATION_PERMISSION | Ola and Rapido were available locally, but the current-location flow now stops cleanly because location access is missing instead of faking a pickup. |
| Cab manual boundary | PASS | No final booking or payment happened. |

## Food Results

| Command / Scenario | Result | Notes |
|---|---|---|
| `Luna order paneer pizza` | BLOCKED_BY_PROVIDER_UI | The food flow reached the restaurant handoff, but the supported food apps still did not expose usable search/cart controls on this phone. |
| Food provider flow | BLOCKED_BY_PROVIDER_UI | Swiggy and Zomato were detected, but the screen-inspection step still could not move the flow to a safe searchable provider screen. |
| Food manual boundary | PASS | No final order or payment happened. |

## Grocery Results

| Command / Scenario | Result | Notes |
|---|---|---|
| `Luna order milk and bread` | PASS | Routed into the grocery flow and asked for brand preference instead of falling into the food branch. |
| Grocery comparison | PASS | `any brand` advanced to comparison across Blinkit, Instamart, and JioMart, and the final-confirmation/manual-action state now dismisses cleanly. |
| Grocery cancellation follow-up | PASS | `cancel grocery` cleanly dismissed the final-confirmation/manual-action state in the latest smoke run. |
| Grocery manual boundary | PASS | No final order or payment happened. |

## Negative Safety Tests

| Command | Result | Notes |
|---|---|---|
| `Luna pay now` | PASS | Blocked as human-only. |
| `Luna enter OTP` | PASS | Blocked as human-only. |
| `Luna complete payment` | PASS | Blocked as human-only. |
| `Luna bypass login` | PASS | Blocked as human-only. |
| `Luna solve captcha` | PASS | Blocked as human-only. |

## Remaining Blockers

- Usage access is still missing.
- Location permission is still missing, which now shows up as `BLOCKED_BY_LOCATION_PERMISSION` for current-location cab pickup.
- Cab smoke reaches the cab branch correctly, but provider screens still stop before any final booking.
- Food smoke reaches the food branch, but the supported provider screens still do not expose usable search/cart controls on this phone.
- Grocery comparison works, and the cancellation follow-up now exits the final-confirmation/manual-action state cleanly.

## Logs Captured

- `NovaLunaCommandSmoke`
- `NovaLunaCab`

## SafetyGate And Boundaries

- SafetyGate remained the final authority for the negative safety phrases.
- No autonomous final booking, food order, grocery order, payment, OTP entry, login bypass, or CAPTCHA solving happened.
- `flutter_app/` stayed untouched during the smoke run.
