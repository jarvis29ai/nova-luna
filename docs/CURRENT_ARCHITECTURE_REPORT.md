# Current Architecture Report

## Product Summary

Nova / Luna is an Android-first, offline-first voice automation assistant starter.
It is designed to:

- Listen locally on device
- Parse user commands
- Apply a safety gate before execution
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
  - Safety gate
  - Command brain
  - Cab booking flow
  - Food ordering flow
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
2. The parser maps text to `CommandIntent`.
3. The resolver resolves app aliases for open-app commands.
4. The safety gate classifies the intent.
5. `CommandRouter` sends the action to the executor.
6. The result is spoken and written to the command history database.
7. Cab booking requests are routed into a dedicated local state machine when the parser detects ride-hailing commands.
8. Food ordering requests are routed into a dedicated local state machine when the parser detects food-ordering commands.

## Safety Flow

- Stop and cancel commands are handled first.
- Payment, banking, checkout, OTP, CAPTCHA, and password-related commands are blocked.
- Unsafe food items such as alcohol, tobacco, medicines, and other restricted items are blocked.
- Sensitive commands such as settings changes and screenshots are gated.
- The starter does not implement payment, banking, shopping checkout, or OTP extraction.
- The AccessibilityService is used only for user-controlled actions like navigation, tapping, scrolling, and typing.
- Cab booking stays local-first: the app opens installed providers, compares visible fares, and requires an explicit final user confirmation before any booking tap.
- Food ordering stays local-first: the app opens installed providers, compares visible payable amounts, tries visible coupons, and requires an explicit final user confirmation before any order-placement tap.

## Cab Booking Flow

- `RuleBasedCommandParser` detects cab-booking phrases and maps them to `IntentType.CAB_BOOKING` and `ActionType.CAB_BOOKING`.
- `CabBookingOrchestrator` drives a state machine with pickup, drop, ride-type, provider comparison, platform choice, final confirmation, manual-action, success, and failure states.
- `PermissionUtils.hasLocationPermission()` is used to check whether the app can resolve a local pickup from the device's last known location.
- `CabProviderRegistry` checks installed provider apps locally.
- Supported providers are Uber, Ola, Rapido, and inDrive.
- `CabDeepLinkBuilder` opens the provider app and passes trip extras.
- `CabAccessibilityService` reads visible fare, ETA, coupon, and discount text from the provider screen, fills trip details when safe, and stops on OTP, login, payment, CAPTCHA, and other manual-action screens.
- `CabFareComparator` normalizes and sorts fare options from lowest to highest.
- The final booking tap stays blocked until the user explicitly confirms.

## Food Ordering Flow

- `RuleBasedCommandParser` detects food-ordering phrases and maps them to `IntentType.FOOD_ORDER` and `ActionType.FOOD_ORDER`.
- `FoodBookingOrchestrator` drives a state machine with food item, restaurant, provider comparison, platform choice, final confirmation, manual-action, success, and failure states.
- `FoodProviderRegistry` checks installed provider apps locally.
- Supported providers are Swiggy, Zomato, and Toings.
- `FoodDeepLinkBuilder` opens the provider app and passes food extras.
- `FoodAccessibilityService` reads visible payable, delivery fee, tax, coupon, discount, and ETA text from the provider screen, fills order details when safe, and stops on OTP, login, payment, CAPTCHA, and other manual-action screens.
- `FoodCouponEngine` extracts visible coupon candidates and chooses a best-effort coupon to try.
- `FoodPriceComparator` normalizes and sorts quote options from lowest to highest.
- The final place-order tap stays blocked until the user explicitly confirms.

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
- The food flow uses the same accessibility surface, but only for provider-app handoff and screen inspection that the user has already requested.

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
- Cab booking orchestration
- Food ordering orchestration

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
- Bank/payment/order/checkout automation is intentionally blocked.
- `FLAG_SECURE` content cannot be bypassed.
- Screenshot capture is scaffolded only.
- Call automation is scaffolded only.
- The starter depends on Android system speech and accessibility capabilities, which vary by device and OEM.
- The exact audible voice can vary because Android TTS engine availability differs by device and installed engine.
- Food apps may expose different layouts, coupon flows, and checkout screens across providers, so the food flow is best-effort and may stop for manual handling earlier than the cab flow.
