# Current Architecture Report

## Product Summary

Nova / Luna is an Android-first, offline-first voice automation assistant starter.
It is designed to:

- Listen locally on device
- Parse user commands
- Apply a safety gate before execution
- Automate only user-controlled Android UI/app actions
- Speak replies locally with TextToSpeech

Nova is the male voice profile.
Luna is the female voice profile.

## Module Map

- `app`
  - Phone UI
  - Voice foreground service
  - AccessibilityService
  - Safety gate
  - Command brain
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
6. Listening resumes only after speech completes or an error backoff expires.

## Command Flow

1. Speech text is normalized.
2. The parser maps text to `CommandIntent`.
3. The resolver resolves app aliases for open-app commands.
4. The safety gate classifies the intent.
5. `CommandRouter` sends the action to the executor.
6. The result is spoken and written to the command history database.

## Safety Flow

- Stop and cancel commands are handled first.
- Payment, banking, checkout, OTP, CAPTCHA, and password-related commands are blocked.
- Sensitive commands such as settings changes and screenshots are gated.
- The starter does not implement payment, banking, shopping checkout, or OTP extraction.
- The AccessibilityService is used only for user-controlled actions like navigation, tapping, scrolling, and typing.

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
- Bank/payment/order/checkout automation is intentionally blocked.
- `FLAG_SECURE` content cannot be bypassed.
- Screenshot capture is scaffolded only.
- Call automation is scaffolded only.
- The starter depends on Android system speech and accessibility capabilities, which vary by device and OEM.

