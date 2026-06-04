# Current Architecture Report

## Product Summary

Nova / Luna is an Android-first, offline-first voice automation assistant starter.
It is designed to:

- Listen locally on device
- Parse user commands
- Parse user commands through a structured local BrainService layer
- Route phone-only requests through a BrainRouter that selects a role-specific local model path before validation
- Apply a safety gate before execution
- Keep `LocalMockBrainProvider` as the guaranteed fallback when a role model is unavailable or rejected
- Automate only user-controlled Android UI/app actions
- Speak replies locally with Android TextToSpeech

Nova and Luna are local TTS profiles that adjust pitch and speech rate only.
Exact male/female voice selection is not guaranteed across devices because it depends on the installed Android TTS engine.

Nova is the male voice profile.
Luna is the female voice profile.

## Module Map

- `app`
  - Phone UI
  - Voice foreground service
  - AccessibilityService
  - Structured brain service, router, and safety gate
  - Safety gate
  - Command brain
  - Cab booking flow
  - Grocery booking flow
  - Room database
  - DataStore preferences
  - TTS manager
- `wear`
  - Wear OS quick-command UI
  - Voice relay scaffold
  - Phone message relay scaffold
- `shared`
  - Message channel constants

## Voice Flow

1. The user taps Start Listening.
2. `VoiceCommandService` starts in the foreground with a persistent notification.
3. `SpeechRecognizer` listens with a controlled restart loop.
4. Final or partial text is sent into `CommandBrain`.
5. `TextToSpeechManager` speaks the safe response.
   - It uses local Android TextToSpeech and applies the selected Nova/Luna pitch and rate profile.
6. Listening resumes only after speech completes or an error backoff expires.

## Command Flow

1. Speech text is normalized.
2. `BrainService` routes the text through `BrainRouter`, selects the phone model role, and gathers a BrainAction candidate.
3. `BrainActionValidator` rejects invalid or dangerous candidates before any command execution path can use them.
4. Invalid or unavailable role models fall back to `LocalMockBrainProvider`.
5. `CommandRouter` asks `SafetyGate` to approve the final action before execution.
6. The router either returns a confirmation prompt, blocks a human-only command, or forwards the safe action to the executor.
7. The result is spoken and written to the command history database.
8. Cab booking requests are routed into a dedicated local state machine after the user confirms the preparatory step.
9. Food ordering requests are routed into a dedicated local state machine after the parser detects a food-ordering command.

## Safety Flow

- Stop and cancel commands are handled first.
- Payment, banking, checkout, OTP, CAPTCHA, and password-related commands are blocked.
- Sensitive commands such as settings changes and screenshots are gated.
- The starter does not implement payment, banking, shopping checkout, or OTP extraction.
- `PhoneGemmaRuntime` is the production phone reasoning scaffold for Gemma.
- `GemmaBrainModel` delegates to `PhoneGemmaRuntime` when the runtime is ready.
- `ActionJsonModel` emits strict safe BrainAction JSON for cab, food, and task planning.
- `LiteCommandModel` handles fast offline command phrases such as stop, cancel, go home, and open app.
- `ScreenUnderstandingModel` is read-only only and reserved for future screen analysis.
- The role-specific models are local-only, `LocalMockBrainProvider` remains the guaranteed fallback, and `SafetyGate` is still the final authority before execution.
- The AccessibilityService is used only for user-controlled actions like navigation, tapping, scrolling, and typing.
- Cab booking stays local-first: the app opens installed providers, compares visible fares, and requires an explicit final user confirmation before any booking tap.
- Final booking, payment, OTP, login, CAPTCHA, and similar human-only screens stay outside automation.
- Grocery booking stays local-first: the app opens installed grocery providers, compares visible cart totals, applies visible or user-supplied coupons when safe, and requires an explicit final user confirmation before any final order tap.
- Grocery booking also stops on payment, OTP, login, CAPTCHA, replacement-selection, unavailable-item, and other human-only screens.

## Cab Booking Flow

- `RuleBasedCommandParser` detects cab-booking phrases and maps them to `IntentType.CAB_BOOKING` and `ActionType.CAB_BOOKING`.
- `BrainService` now produces a structured `BrainAction` first, then the router and safety gate decide whether the action can reach the executor.
- `CabBookingOrchestrator` drives a state machine with pickup, drop, ride-type, provider comparison, platform choice, final confirmation, manual-action, success, and failure states, and keeps the active session alive across follow-up replies like current location, auto, book cheapest, yes, and cancel.
- `CabLocationResolver` checks location permission and resolves current-location pickup from the device's last known location when available.
- `CabProviderRegistry` checks installed provider apps locally.
- Supported providers are Uber, Ola, Rapido, and inDrive.
- `CabDeepLinkBuilder` opens the provider app, prefers provider-specific deep links where available, and falls back to geo/package launch plans with trip extras.
- `CabAccessibilityService` reads visible fare, ETA, coupon, and discount text from the provider screen, fills trip details when safe, uses provider-specific destination fallbacks for Rapido and Ola, extracts fare candidates for comparison, and stops on OTP, login, payment, CAPTCHA, permission, and other manual-action screens.
- `CabSmokeReceiver` runs the debug-only cab smoke, resets the UI to Home before the preflight snapshot and between scenarios, and records per-step results without tapping any final booking or payment action.
- `CabFareComparator` normalizes and sorts fare options from lowest to highest.
- The final booking tap stays blocked until the user explicitly confirms, and the flow never automates payment or OTP entry.

## Grocery Booking Flow

- `GroceryIntentParser` detects grocery-booking phrases, parses item baskets, quantities, brand preferences, provider preferences, coupon cues, and comparison requests, and keeps food-ordering phrases out of the grocery path unless grocery cues are present.
- `GroceryBookingOrchestrator` drives a state machine with item collection, brand preference, provider comparison, provider choice, final confirmation, manual-action, success, and failure states, and keeps the active session alive across follow-up replies like add item, remove item, compare, book cheapest, book from Blinkit, and proceed.
- `GroceryProviderRegistry` checks installed Blinkit, JioMart, and Instamart / Swiggy packages locally.
- `GroceryDeepLinkBuilder` opens the provider app or its market page and prepares per-item search intents.
- `GroceryAccessibilityService` reads visible cart totals, ETA, coupon text, unavailable items, and replacement items, applies safe visible coupons, and stops on OTP, login, payment, CAPTCHA, permission, address, replacement, unavailable-item, and other manual-action screens.
- `GroceryPriceComparator` normalizes and sorts cart candidates from best to worst by final payable value, ETA, coupon state, and unavailable-item penalties.
- The final order tap stays blocked until the user explicitly confirms, and the flow never automates payment or OTP entry.

## Accessibility Flow

- `NovaAccessibilityService` is the execution surface for global actions and node actions.
- It supports:
  - `goHome()`
  - `goBack()`
  - `openRecents()`
  - `openNotifications()`
  - `clickByTextOrDescription(query)`
  - `scrollForward()`
  - `scrollBackward()`
  - `typeText(text)`
- Notification state text is captured as a scaffold for future notification reading.
- The cab flow uses the same accessibility surface, but only for provider-app handoff and screen inspection that the user has already requested.

## Data Flow

- DataStore stores:
  - voice profile
  - wake phrase
  - auto-start preference
  - assistant enabled flag
- Room stores:
  - command history
  - audit log fields
  - custom automation rule scaffolding
- A local command history screen can read the same Room table and show recent command results, safety decisions, and timestamps without any cloud sync.

## Wear OS Flow

- The watch app is a companion scaffold.
- It can:
  - relay a typed command to the phone
  - start a watch mic capture scaffold
  - send quick commands like home and stop
- `MessageChannels.COMMAND` and `MessageChannels.REPLY` define the channel names.

## Current MVP Scope

- Local voice input
- Local TTS response
- Rule-based parsing
- Safety blocking
- Open app commands
- Navigation commands
- Tap, scroll, and type scaffolding
- Command history and audit logs

## Post-MVP Scope

- Custom rule editor
- Better notification parsing
- Finer-grained package aliasing
- Wearable sync enhancements
- Local AI/TFLite intent classifier
- Routine automation engine
- More robust command confirmations

## Technical Stack

- Kotlin
- Android Gradle project
- Min SDK 29
- Jetpack DataStore
- Room
- SpeechRecognizer
- TextToSpeech
- AccessibilityService
- BiometricPrompt scaffold
- Wear OS scaffold

## Limitations

- No backend and no cloud API path exists in this starter.
- No backend or cloud server is required for the current architecture.
- No remote LLM path exists for production; phone-only role routing is local, the Gemma phone runtime is the production direction, and `LocalMockBrainProvider` stays available offline.
- Bank/payment/checkout automation is intentionally blocked.
- `FLAG_SECURE` content cannot be bypassed.
- Screenshot capture is scaffolded only.
- Call automation is scaffolded only.
- The starter depends on Android system speech and accessibility capabilities, which vary by device and OEM.
- The exact audible voice can vary because Android TTS engine availability differs by device and installed engine.
