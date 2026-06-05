# Manual Phone Test Results

## Test Details

- Date/time: 2026-06-05 04:42:22 +05:30
- Phone model: OnePlus 8T
- Device model: KB2001
- Android version: 14
- App version: `0.1.0-debug`
- Build version: `versionCode=1`
- Git commit tested: current working tree on top of `1b87615` with local smoke/source/doc edits
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

- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=basic`
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=cab`
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=food`
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=grocery`
- `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` with `section=negative`

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
| `Luna book a cab from current location to DB Mall` | PASS | Routed into the cab flow and stopped at the ride-type prompt. |
| Cab provider flow | PARTIAL | Ola and Rapido were available locally, but the current-location flow still hit manual-action boundaries on the provider screens. Location permission is missing, and the destination field was not fully accessible. |
| Cab manual boundary | PASS | No final booking or payment happened. |

## Food Results

| Command / Scenario | Result | Notes |
|---|---|---|
| `Luna order paneer pizza from Domino's` | PARTIAL | The restaurant follow-up had to use a parser-friendly `from Domino's` reply, but provider comparison still failed because the supported food apps did not expose usable search/cart controls on this phone. |
| Food provider flow | PARTIAL | Swiggy and Zomato were detected, but Toings remained unavailable to the registry and the screen-inspection step could not move the flow to a selectable provider. |
| Food manual boundary | PASS | No final order or payment happened. |

## Grocery Results

| Command / Scenario | Result | Notes |
|---|---|---|
| `Luna order milk and bread` | PARTIAL | Routed into the grocery flow and asked for brand preference instead of falling into the food branch. |
| Grocery comparison | PARTIAL | `any brand` advanced to comparison across Blinkit, Instamart, and JioMart, but the provider choice still stopped at the manual final-confirmation boundary. |
| Grocery cancellation follow-up | FAIL | `cancel grocery` did not cleanly dismiss the final-confirmation/manual-action state in the latest smoke run. |
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
- Location permission is still missing, which limits the cab flow.
- Cab smoke reaches the cab branch correctly, but provider screens still stop at manual-action boundaries before any final booking.
- Food smoke reaches comparison, but the supported provider screens do not expose usable search/cart controls on this phone.
- Grocery comparison works, but the cancellation follow-up still does not cleanly exit the final-confirmation/manual-action state.

## Logs Captured

- `NovaLunaCommandSmoke`
- `NovaLunaCab`

## SafetyGate And Boundaries

- SafetyGate remained the final authority for the negative safety phrases.
- No autonomous final booking, food order, grocery order, payment, OTP entry, login bypass, or CAPTCHA solving happened.
- `flutter_app/` stayed untouched during the smoke run.
